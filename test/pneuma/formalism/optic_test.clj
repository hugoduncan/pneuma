(ns pneuma.formalism.optic-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.test.check :as tc]
              [clojure.test.check.generators :as gen]
              [clojure.test.check.properties :as prop]
              [malli.core :as m]
              [pneuma.protocol :as p]
              [pneuma.formalism.optic :as optic]))

;; Three optic declarations: a lens, a fold, and a derived subscription.
;; Exercises all three optic-type branches.
(def sample-declarations
     {:label "test optics"
      :declarations
      [{:id         :session-msgs
        :optic-type :Lens
        :params     [{:name :sid :type :String}]
        :path       [:sessions :sid :messages]}

       {:id         :all-ids
        :optic-type :Fold
        :path       [:session-ids]}

       {:id         :msg-count
        :optic-type :Derived
        :params     [{:name :sid :type :String}]
        :sources    {:msgs [:sessions :sid :messages]}
        :derivations {:count [:length :msgs]
                      :first-msg [:first :msgs]}}]})

(deftest constructor-test
  ;; optic-declaration validates input and rejects duplicates or invalid input.
         (testing "optic-declaration"
                  (testing "accepts valid declarations"
                           (is (some? (optic/optic-declaration sample-declarations))))

                  (testing "accepts single minimal lens"
                           (is (some? (optic/optic-declaration
                                       {:label "test optics"
                                        :declarations [{:id :x :optic-type :Lens :path [:a]}]}))))

                  (testing "accepts single derived optic"
                           (is (some? (optic/optic-declaration
                                       {:label "test optics"
                                        :declarations [{:id :d :optic-type :Derived
                                                        :sources {:a [:x]}
                                                        :derivations {:n [:length :a]}}]}))))

                  (testing "rejects missing :declarations key"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (optic/optic-declaration {:label "test optics"}))))

                  (testing "rejects declaration missing :id"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (optic/optic-declaration
                                         {:label "test optics"
                                          :declarations [{:optic-type :Lens :path [:a]}]}))))

                  (testing "rejects declaration missing :optic-type"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (optic/optic-declaration
                                         {:label "test optics"
                                          :declarations [{:id :x :path [:a]}]}))))

                  (testing "rejects unknown :optic-type"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (optic/optic-declaration
                                         {:label "test optics"
                                          :declarations [{:id :x :optic-type :Bogus :path [:a]}]}))))

                  (testing "rejects duplicate optic ids"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (optic/optic-declaration
                                         {:label "test optics"
                                          :declarations [{:id :x :optic-type :Lens :path [:a]}
                                                         {:id :x :optic-type :Fold :path [:b]}]}))))))

(deftest schema-projection-test
  ;; ->schema produces a Malli :multi schema dispatching on :optic-id.
         (let [od     (optic/optic-declaration sample-declarations)
               schema (p/->schema od)]

              (testing "->schema"
                       (testing "produces a valid Malli schema"
                                (is (m/schema? (m/schema schema))))

                       (testing "validates conforming :session-msgs entry"
                                (is (m/validate schema
                                                {:optic-id :session-msgs
                                                 :params   {:sid "s1"}})))

                       (testing "validates conforming :all-ids entry (no params)"
                                (is (m/validate schema {:optic-id :all-ids})))

                       (testing "validates conforming :msg-count entry"
                                (is (m/validate schema
                                                {:optic-id :msg-count
                                                 :params   {:sid "s1"}})))

                       (testing "rejects unknown optic-id"
                                (is (not (m/validate schema {:optic-id :bogus}))))

                       (testing "rejects wrong param type"
                                (is (not (m/validate schema
                                                     {:optic-id :session-msgs
                                                      :params   {:sid 999}})))))))

(deftest monitor-projection-test
  ;; ->monitor checks trace entries for missing subscriptions and broken paths.
         (let [od      (optic/optic-declaration sample-declarations)
               monitor (p/->monitor od)]

              (testing "->monitor"
                       (testing "returns :ok for lens with resolvable path"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:optic-id     :session-msgs
                                                  :params       {:sid "s1"}
                                                  :state-before {:sessions {"s1" {:messages []}}}})))))

                       (testing "returns :ok for fold with resolvable path"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:optic-id     :all-ids
                                                  :state-before {:session-ids #{:a :b}}})))))

                       (testing "returns :ok for derived with resolvable sources"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:optic-id     :msg-count
                                                  :params       {:sid "s1"}
                                                  :state-before {:sessions {"s1" {:messages ["hi"]}}}})))))

                       (testing "returns :violation for absent optic-id"
                                (let [result (monitor {:optic-id :no-such
                                                       :state-before {}})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :missing-subscription
                                            (-> result :violations first :detail :kind)))))

                       (testing "returns :violation for broken lens path"
                                (let [result (monitor {:optic-id     :session-msgs
                                                       :params       {:sid "s1"}
                                                       :state-before {}})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :broken-path
                                            (-> result :violations first :detail :kind)))))

                       (testing "returns :violation for broken derived source path"
                                (let [result (monitor {:optic-id     :msg-count
                                                       :params       {:sid "s1"}
                                                       :state-before {}})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :broken-path
                                            (-> result :violations first :detail :kind)))))

                       (testing "returns :violation for wrong derivation value"
                                (let [result (monitor {:optic-id      :msg-count
                                                       :params        {:sid "s1"}
                                                       :state-before  {:sessions {"s1" {:messages ["a" "b"]}}}
                                                       :derived-value {:count 99 :first-msg "wrong"}})]
                                     (is (= :violation (:verdict result)))
                                     (is (some #(= :wrong-derivation (-> % :detail :kind))
                                               (:violations result))))))))

(deftest generator-projection-test
  ;; ->gen produces entries conforming to ->schema (axiom A24).
         (let [od      (optic/optic-declaration sample-declarations)
               g       (p/->gen od)
               schema  (p/->schema od)
               samples (gen/sample g 20)]

              (testing "->gen"
                       (testing "produces a generator"
                                (is (some? g)))

                       (testing "generated values conform to schema (A24)"
                                (doseq [sample samples]
                                       (is (m/validate schema sample)
                                           (str "sample failed: " (pr-str sample))))))))

(deftest a24-property-test
  ;; Axiom A24: for all generated values, the value conforms to the
  ;; schema projected by the same formalism.
         (testing "A24: ->gen output conforms to ->schema"
                  (let [od     (optic/optic-declaration sample-declarations)
                        g      (p/->gen od)
                        schema (p/->schema od)
                        result (tc/quick-check
                                100
                                (prop/for-all [v g]
                                              (m/validate schema v)))]
                       (is (:pass? result)
                           (str "A24 failure: "
                                (pr-str (:shrunk result)))))))

(deftest gap-type-projection-test
  ;; ->gap-type returns the failure taxonomy for optic declarations.
         (let [od (optic/optic-declaration sample-declarations)
               gt (p/->gap-type od)]

              (testing "->gap-type"
                       (testing "has :optic formalism key"
                                (is (= :optic (:formalism gt))))

                       (testing "has expected gap-kinds"
                                (is (contains? (:gap-kinds gt) :broken-path))
                                (is (contains? (:gap-kinds gt) :wrong-derivation))
                                (is (contains? (:gap-kinds gt) :missing-subscription)))

                       (testing "has expected statuses"
                                (is (= #{:conforms :absent :diverges} (:statuses gt)))))))

(deftest referenceable-test
  ;; extract-refs returns cross-formalism reference sets keyed by ref-kind.
         (let [od (optic/optic-declaration sample-declarations)]

              (testing "extract-refs"
                       (testing "returns optic ids for :optic-ids"
                                (is (= #{:session-msgs :all-ids :msg-count}
                                       (p/extract-refs od :optic-ids))))

                       (testing "returns paths for :paths (lens/fold only)"
                                (is (= #{[:sessions :sid :messages] [:session-ids]}
                                       (p/extract-refs od :paths))))

                       (testing "returns source paths for :source-optic-refs"
                                (is (= #{[:sessions :sid :messages]}
                                       (p/extract-refs od :source-optic-refs))))

                       (testing "returns empty set for unknown ref-kind"
                                (is (= #{} (p/extract-refs od :unknown)))))))
