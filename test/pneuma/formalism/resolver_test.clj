(ns pneuma.formalism.resolver-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.test.check :as tc]
              [clojure.test.check.generators :as gen]
              [clojure.test.check.properties :as prop]
              [malli.core :as m]
              [pneuma.protocol :as p]
              [pneuma.formalism.resolver :as resolver]))

;; Three resolvers: local with chaining, and one external.
;; session-msgs requires :session/id, produces :session/messages.
;; msg-count chains off session-msgs output.
;; git-status is external.
(def sample-declarations
     {:declarations
      [{:id     :session-msgs
        :input  #{:session/id}
        :output #{:session/messages}
        :source :local}
       {:id     :msg-count
        :input  #{:session/messages}
        :output #{:session/count}
        :source :local}
       {:id     :git-status
        :input  #{:session/dir}
        :output #{:git/status :git/branch}
        :source [:external :git]}]})

(deftest constructor-test
  ;; resolver-graph validates input and rejects duplicates or invalid input.
         (testing "resolver-graph"
                  (testing "accepts valid declarations"
                           (is (some? (resolver/resolver-graph sample-declarations))))

                  (testing "accepts single minimal resolver"
                           (is (some? (resolver/resolver-graph
                                       {:declarations [{:id :r :input #{:a} :output #{:b}}]}))))

                  (testing "accepts resolver with empty input"
                           (is (some? (resolver/resolver-graph
                                       {:declarations [{:id :r :input #{} :output #{:b}}]}))))

                  (testing "rejects missing :declarations"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (resolver/resolver-graph {}))))

                  (testing "rejects declaration missing :id"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (resolver/resolver-graph
                                         {:declarations [{:input #{:a} :output #{:b}}]}))))

                  (testing "rejects declaration missing :input"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (resolver/resolver-graph
                                         {:declarations [{:id :r :output #{:b}}]}))))

                  (testing "rejects duplicate resolver ids"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (resolver/resolver-graph
                                         {:declarations [{:id :r :input #{:a} :output #{:b}}
                                                         {:id :r :input #{:c} :output #{:d}}]}))))))

(deftest chase-test
  ;; reachable-attributes computes the fixpoint of the chase algorithm.
         (let [rg (resolver/resolver-graph sample-declarations)
               decls (:declarations rg)]

              (testing "reachable-attributes"
                       (testing "reaches direct outputs from matching input"
                                (is (contains? (resolver/reachable-attributes decls #{:session/id})
                                               :session/messages)))

                       (testing "reaches chained outputs transitively"
                                (let [reachable (resolver/reachable-attributes decls #{:session/id})]
                                     (is (contains? reachable :session/messages))
                                     (is (contains? reachable :session/count))))

                       (testing "includes the input attributes in result"
                                (is (contains? (resolver/reachable-attributes decls #{:session/id})
                                               :session/id)))

                       (testing "does not reach unrelated attributes"
                                (is (not (contains?
                                          (resolver/reachable-attributes decls #{:session/id})
                                          :git/status))))

                       (testing "reaches all from full input set"
                                (let [reachable (resolver/reachable-attributes
                                                 decls #{:session/id :session/dir})]
                                     (is (contains? reachable :session/count))
                                     (is (contains? reachable :git/status))
                                     (is (contains? reachable :git/branch))))

                       (testing "returns input unchanged when no resolvers fire"
                                (is (= #{:unknown}
                                       (resolver/reachable-attributes decls #{:unknown})))))))

(deftest schema-projection-test
  ;; ->schema produces a Malli :multi schema dispatching on :resolver-id.
         (let [rg     (resolver/resolver-graph sample-declarations)
               schema (p/->schema rg)]

              (testing "->schema"
                       (testing "produces a valid Malli schema"
                                (is (m/schema? (m/schema schema))))

                       (testing "validates conforming entry"
                                (is (m/validate schema
                                                {:resolver-id :session-msgs
                                                 :input-attrs {:session/id "s1"}})))

                       (testing "validates entry with output-attrs"
                                (is (m/validate schema
                                                {:resolver-id :session-msgs
                                                 :input-attrs {:session/id "s1"}
                                                 :output-attrs {:session/messages []}})))

                       (testing "rejects unknown resolver-id"
                                (is (not (m/validate schema {:resolver-id :bogus
                                                             :input-attrs {}})))))))

(deftest monitor-projection-test
  ;; ->monitor checks trace entries for missing resolvers and wrong output.
         (let [rg      (resolver/resolver-graph sample-declarations)
               monitor (p/->monitor rg)]

              (testing "->monitor"
                       (testing "returns :ok for conforming entry"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:resolver-id :session-msgs
                                                  :input-attrs {:session/id "s1"}})))))

                       (testing "returns :ok when output has all declared attrs"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:resolver-id :session-msgs
                                                  :input-attrs {:session/id "s1"}
                                                  :output-attrs {:session/messages ["hi"]}})))))

                       (testing "returns :violation for absent resolver"
                                (let [result (monitor {:resolver-id :no-such
                                                       :input-attrs {}})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :missing-resolver
                                            (-> result :violations first :detail :kind)))))

                       (testing "returns :violation for missing output attribute"
                                (let [result (monitor {:resolver-id :git-status
                                                       :input-attrs {:session/dir "/tmp"}
                                                       :output-attrs {:git/status "clean"}})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :wrong-output
                                            (-> result :violations first :detail :kind)))
                                     (is (= #{:git/branch}
                                            (-> result :violations first :detail :missing))))))))

(deftest generator-projection-test
  ;; ->gen produces entries conforming to ->schema (axiom A24).
         (let [rg      (resolver/resolver-graph sample-declarations)
               g       (p/->gen rg)
               schema  (p/->schema rg)
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
                  (let [rg     (resolver/resolver-graph sample-declarations)
                        g      (p/->gen rg)
                        schema (p/->schema rg)
                        result (tc/quick-check
                                100
                                (prop/for-all [v g]
                                              (m/validate schema v)))]
                       (is (:pass? result)
                           (str "A24 failure: "
                                (pr-str (:shrunk result)))))))

(deftest gap-type-projection-test
  ;; ->gap-type returns the failure taxonomy for resolver graphs.
         (let [rg (resolver/resolver-graph sample-declarations)
               gt (p/->gap-type rg)]

              (testing "->gap-type"
                       (testing "has :resolver formalism key"
                                (is (= :resolver (:formalism gt))))

                       (testing "has expected gap-kinds"
                                (is (contains? (:gap-kinds gt) :missing-resolver))
                                (is (contains? (:gap-kinds gt) :unreachable-attribute))
                                (is (contains? (:gap-kinds gt) :wrong-output)))

                       (testing "has expected statuses"
                                (is (= #{:conforms :absent :diverges} (:statuses gt)))))))

(deftest referenceable-test
  ;; extract-refs returns cross-formalism reference sets keyed by ref-kind.
         (let [rg (resolver/resolver-graph sample-declarations)]

              (testing "extract-refs"
                       (testing "returns resolver ids for :resolver-ids"
                                (is (= #{:session-msgs :msg-count :git-status}
                                       (p/extract-refs rg :resolver-ids))))

                       (testing "returns input attributes for :input-attributes"
                                (is (= #{:session/id :session/messages :session/dir}
                                       (p/extract-refs rg :input-attributes))))

                       (testing "returns output attributes for :output-attributes"
                                (is (= #{:session/messages :session/count :git/status :git/branch}
                                       (p/extract-refs rg :output-attributes))))

                       (testing "returns external sources for :external-sources"
                                (is (= #{:git}
                                       (p/extract-refs rg :external-sources))))

                       (testing "returns empty set for unknown ref-kind"
                                (is (= #{} (p/extract-refs rg :unknown)))))))
