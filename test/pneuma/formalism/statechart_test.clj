(ns pneuma.formalism.statechart-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.test.check :as tc]
              [clojure.test.check.generators :as gen]
              [clojure.test.check.properties :as prop]
              [malli.core :as m]
              [pneuma.protocol :as p]
              [pneuma.formalism.statechart :as sc]))

;; Hierarchical chart with parallel composition — the session/conversation model.
(def example-chart
     {:label "test SC"
      :states #{:idle :generating :awaiting-approval
                :tool-executing :tool-error
                :extensions-idle :extension-running}
      :hierarchy {:session/root #{:conversation :extensions}
                  :conversation #{:idle :generating :awaiting-approval
                                  :tool-executing :tool-error}
                  :extensions #{:extensions-idle :extension-running}}
      :parallel #{:session/root}
      :initial {:session/root :conversation
                :conversation :idle
                :extensions :extensions-idle}
      :transitions
      [{:source :idle :event :user-submit :target :generating}
       {:source :generating :event :generation-complete :target :idle}
       {:source :generating :event :tool-requested :target :awaiting-approval}
       {:source :generating :event :user-cancel :target :idle}
       {:source :awaiting-approval :event :user-approved :target :tool-executing}
       {:source :tool-executing :event :tool-complete :target :generating}
       {:source :tool-executing :event :tool-error-ev :target :tool-error}
       {:source :tool-error :event :retry-tool :target :tool-executing}
       {:source :tool-error :event :skip-tool :target :generating}
       {:source :tool-error :event :user-cancel :target :idle}
       {:source :extensions-idle :event :extension-activated :target :extension-running}
       {:source :extension-running :event :extension-complete :target :extensions-idle}]})

;; Flat chart with a virtual root wrapper to keep the algorithm uniform.
(def flat-chart
     {:label "test SC"
      :states #{:a :b :c}
      :hierarchy {:_root #{:a :b :c}}
      :initial {:_root :a}
      :transitions [{:source :a :event :go :target :b}
                    {:source :b :event :next :target :c}
                    {:source :c :event :reset :target :a}]})

(deftest constructor-test
  ;; statechart constructor validates input schema and domain invariants.
         (testing "statechart"
                  (testing "accepts valid hierarchical chart"
                           (is (some? (sc/statechart example-chart))))

                  (testing "accepts valid flat chart"
                           (is (some? (sc/statechart flat-chart))))

                  (testing "rejects missing :states"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (sc/statechart {:label "test SC" :initial {} :transitions []}))))

                  (testing "rejects missing :transitions"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (sc/statechart {:label "test SC" :states #{:a} :initial {}}))))

                  (testing "rejects missing :initial"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (sc/statechart {:label "test SC" :states #{:a} :transitions []}))))

                  (testing "rejects hierarchy child not in states"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (sc/statechart
                                         {:label "test SC"
                                          :states #{:a}
                                          :hierarchy {:root #{:a :unknown}}
                                          :initial {:root :a}
                                          :transitions []}))))

                  (testing "rejects parallel member not a hierarchy key"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (sc/statechart
                                         {:label "test SC"
                                          :states #{:a :b}
                                          :hierarchy {:root #{:a :b}}
                                          :parallel #{:not-a-key}
                                          :initial {:root :a}
                                          :transitions []}))))

                  (testing "rejects initial key not in hierarchy"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (sc/statechart
                                         {:label "test SC"
                                          :states #{:a :b}
                                          :hierarchy {:root #{:a :b}}
                                          :initial {:root :a :no-such :b}
                                          :transitions []}))))

                  (testing "rejects transition source not in states"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (sc/statechart
                                         {:label "test SC"
                                          :states #{:a :b}
                                          :hierarchy {:root #{:a :b}}
                                          :initial {:root :a}
                                          :transitions [{:source :ghost :event :go :target :b}]}))))

                  (testing "rejects transition target not in states"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (sc/statechart
                                         {:label "test SC"
                                          :states #{:a :b}
                                          :hierarchy {:root #{:a :b}}
                                          :initial {:root :a}
                                          :transitions [{:source :a :event :go :target :ghost}]}))))))

(deftest leaf-states-test
  ;; Leaf states are those not appearing as keys in the hierarchy.
         (testing "statechart"
                  (let [chart (sc/statechart flat-chart)
                        schema (p/->schema chart)]
                       (testing "leaf states for flat chart excludes virtual root"
                         ;; :_root is in hierarchy, so only :a :b :c are leaves
                                (is (m/validate schema #{:a})))

                       (testing "schema rejects virtual root as leaf"
                                (is (not (m/validate schema #{:_root}))))

                       (let [hchart (sc/statechart example-chart)
                             hschema (p/->schema hchart)]
                            (testing "leaf states for hierarchical chart excludes composites"
                                     (is (m/validate hschema #{:idle :extensions-idle})))

                            (testing "schema rejects composite state as leaf"
                                     (is (not (m/validate hschema #{:conversation}))))))))

(deftest initial-config-test
  ;; initial-config returns the set of leaf states active at startup.
         (testing "statechart"
                  (let [flat   (sc/statechart flat-chart)
                        hchart (sc/statechart example-chart)]
                       (testing "initial config for flat chart"
                         ;; :_root → :a via initial map; :a is a leaf
                                (let [g (p/->gen flat)
                               ;; generate many to detect non-initial configs
                                      samples (into #{} (gen/sample g 30))]
                                     (testing "includes initial state :a"
                                              (is (contains? samples #{:a})))))

                       (testing "initial config for parallel chart"
                         ;; session/root is parallel so both subtrees start
                         ;; conversation starts at :idle, extensions at :extensions-idle
                                (let [g       (p/->gen hchart)
                                      samples (into #{} (gen/sample g 30))]
                                     (testing "includes the parallel initial config"
                                              (is (contains? samples #{:idle :extensions-idle}))))))))

(deftest step-test
  ;; step computes δ(config, event) → new-config.
         (testing "statechart"
                  (let [chart (sc/statechart flat-chart)]
                       (testing "step"
                                (testing "fires matching transition"
                                  ;; :a --:go--> :b
                                         (let [monitor (p/->monitor chart)
                                               result  (monitor {:config-before #{:a}
                                                                 :event         :go
                                                                 :config-after  #{:b}})]
                                              (is (= :ok (:verdict result)))))

                                (testing "returns config unchanged when no transition fires"
                                         (let [monitor (p/->monitor chart)
                                               result  (monitor {:config-before #{:a}
                                                                 :event         :no-such-event
                                                                 :config-after  #{:a}})]
                                              (is (= :ok (:verdict result)))))

                                (testing "detects wrong config-after"
                                         (let [monitor (p/->monitor chart)
                                               result  (monitor {:config-before #{:a}
                                                                 :event         :go
                                                                 :config-after  #{:c}})]
                                              (is (= :violation (:verdict result)))))

                                (let [hchart  (sc/statechart example-chart)
                                      monitor (p/->monitor hchart)]
                                     (testing "fires transition in parallel subtree"
                                       ;; extensions-idle --:extension-activated--> extension-running
                                       ;; conversation stays at :idle
                                              (let [result (monitor {:config-before #{:idle :extensions-idle}
                                                                     :event         :extension-activated
                                                                     :config-after  #{:idle :extension-running}})]
                                                   (is (= :ok (:verdict result))))))))))

(deftest reachability-test
  ;; reachable-configs explores all configs reachable by BFS over events.
         (testing "statechart"
                  (let [chart (sc/statechart flat-chart)
                        g     (p/->gen chart)
                        schema (p/->schema chart)
                 ;; generate enough samples to likely see all 3 reachable configs
                        samples (into #{} (gen/sample g 60))]
                       (testing "generator"
                                (testing "all generated configs conform to schema"
                                         (doseq [s samples]
                                                (is (m/validate schema s)
                                                    (str "non-conforming config: " (pr-str s)))))

                                (testing "generates each reachable config"
                                  ;; flat chart has configs #{:a} #{:b} #{:c}
                                         (is (contains? samples #{:a}))
                                         (is (contains? samples #{:b}))
                                         (is (contains? samples #{:c})))))))

(deftest schema-projection-test
  ;; ->schema validates that a config contains only known leaf states.
         (testing "->schema"
                  (let [chart  (sc/statechart example-chart)
                        schema (p/->schema chart)]
                       (testing "produces a valid Malli schema"
                                (is (m/schema? (m/schema schema))))

                       (testing "validates conforming config"
                                (is (m/validate schema #{:idle :extensions-idle})))

                       (testing "rejects composite state in config"
                                (is (not (m/validate schema #{:conversation :extensions-idle}))))

                       (testing "rejects unknown state in config"
                                (is (not (m/validate schema #{:idle :bogus})))))))

(deftest monitor-projection-test
  ;; ->monitor checks trace entries for valid transitions.
         (testing "->monitor"
                  (let [chart   (sc/statechart example-chart)
                        monitor (p/->monitor chart)]
                       (testing "returns :ok for conforming step"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:config-before #{:idle :extensions-idle}
                                                  :event         :user-submit
                                                  :config-after  #{:generating :extensions-idle}})))))

                       (testing "returns :violation for invalid config-before"
                                (let [result (monitor {:config-before #{:conversation}
                                                       :event         :user-submit
                                                       :config-after  #{:generating :extensions-idle}})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :invalid-config
                                            (-> result :violations first :detail :kind)))))

                       (testing "returns :violation for invalid config-after"
                                (let [result (monitor {:config-before #{:idle :extensions-idle}
                                                       :event         :user-submit
                                                       :config-after  #{:bogus}})]
                                     (is (= :violation (:verdict result)))))

                       (testing "returns :violation for wrong transition"
                                (let [result (monitor {:config-before #{:idle :extensions-idle}
                                                       :event         :user-submit
                                                       :config-after  #{:idle :extensions-idle}})]
                                     (is (= :violation (:verdict result))))))))

(deftest generator-projection-test
  ;; ->gen produces configs conforming to ->schema (axiom A24).
         (testing "->gen"
                  (let [chart   (sc/statechart example-chart)
                        g       (p/->gen chart)
                        schema  (p/->schema chart)
                        samples (gen/sample g 20)]
                       (testing "produces a generator"
                                (is (some? g)))

                       (testing "generated configs conform to schema (A24)"
                                (doseq [s samples]
                                       (is (m/validate schema s)
                                           (str "config failed: " (pr-str s))))))))

(deftest a24-property-test
  ;; Axiom A24: for all generated values, the value conforms to the
  ;; schema projected by the same formalism. Tested as a proper
  ;; generative property with shrinking.
         (testing "A24: ->gen output conforms to ->schema"
                  (doseq [[label chart-data] [["flat" flat-chart]
                                              ["hierarchical" example-chart]]]
                         (testing (str "for " label " chart")
                                  (let [chart  (sc/statechart chart-data)
                                        g      (p/->gen chart)
                                        schema (p/->schema chart)
                                        result (tc/quick-check
                                                100
                                                (prop/for-all [v g]
                                                              (m/validate schema v)))]
                                       (is (:pass? result)
                                           (str label " A24 failure: "
                                                (pr-str (:shrunk result)))))))))

(deftest gap-type-projection-test
  ;; ->gap-type returns the failure taxonomy for statecharts.
         (testing "->gap-type"
                  (let [chart (sc/statechart flat-chart)
                        gt    (p/->gap-type chart)]
                       (testing "has :statechart formalism key"
                                (is (= :statechart (:formalism gt))))

                       (testing "has expected gap-kinds"
                                (is (contains? (:gap-kinds gt) :missing-state))
                                (is (contains? (:gap-kinds gt) :missing-transition))
                                (is (contains? (:gap-kinds gt) :unreachable-state))
                                (is (contains? (:gap-kinds gt) :invalid-config)))

                       (testing "has expected statuses"
                                (is (= #{:conforms :absent :diverges} (:statuses gt)))))))

(deftest referenceable-test
  ;; extract-refs returns cross-formalism reference sets.
         (testing "extract-refs"
                  (let [chart (sc/statechart example-chart)]
                       (testing "returns all states for :state-ids"
                                (is (= (:states example-chart)
                                       (p/extract-refs chart :state-ids))))

                       (testing "returns all events for :event-ids"
                                (is (= #{:user-submit :generation-complete :tool-requested
                                         :user-cancel :user-approved :tool-complete
                                         :tool-error-ev :retry-tool :skip-tool
                                         :extension-activated :extension-complete}
                                       (p/extract-refs chart :event-ids))))

                       (testing "returns empty set for :raised-events when no :raise keys"
                                (is (= #{} (p/extract-refs chart :raised-events))))

                       (testing "returns empty set for unknown ref-kind"
                                (is (= #{} (p/extract-refs chart :unknown))))

                       (let [raise-chart
                             (sc/statechart
                              {:label "test SC"
                               :states #{:a :b :c}
                               :hierarchy {:root #{:a :b :c}}
                               :initial {:root :a}
                               :transitions [{:source :a :event :go :target :b
                                              :raise :internal-ev}
                                             {:source :b :event :next :target :c
                                              :raise [:ev1 :ev2]}]})]
                            (testing "collects single :raise keyword"
                                     (is (contains? (p/extract-refs raise-chart :raised-events) :internal-ev)))

                            (testing "collects vector :raise keywords"
                                     (is (contains? (p/extract-refs raise-chart :raised-events) :ev1))
                                     (is (contains? (p/extract-refs raise-chart :raised-events) :ev2)))))))
