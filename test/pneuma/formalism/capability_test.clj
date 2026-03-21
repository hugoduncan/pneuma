(ns pneuma.formalism.capability-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.test.check.generators :as gen]
              [malli.core :as m]
              [pneuma.protocol :as p]
              [pneuma.formalism.capability :as cap]))

;; Tests for the CapabilitySet formalism using the dogfood instances
;; from dogfood-protocol.md §3.2 and a full three-set capability.

(def dogfood-formalism-caps
     "Dogfood: formalism records must implement these operations."
     {:id :formalism-record
      :dispatch #{:->schema :->monitor :->gen :->gap-type
                  :extract-refs}})

(def dogfood-morphism-caps
     "Dogfood: morphism records must implement these operations."
     {:id :morphism-record
      :dispatch #{:check}})

(def full-caps
     "A full capability set with all three dimensions."
     {:id :test-runner
      :dispatch #{:session/inject :session/request}
      :subscribe #{:session/messages :session/states}
      :query #{:session/git-status}})

(deftest constructor-test
  ;; The capability-set constructor validates input and normalizes
  ;; optional fields.
         (testing "capability-set"
                  (testing "accepts dogfood formalism caps"
                           (is (some? (cap/capability-set dogfood-formalism-caps))))

                  (testing "accepts dogfood morphism caps"
                           (is (some? (cap/capability-set dogfood-morphism-caps))))

                  (testing "accepts full three-set caps"
                           (is (some? (cap/capability-set full-caps))))

                  (testing "defaults subscribe and query to empty sets"
                           (let [cs (cap/capability-set dogfood-formalism-caps)]
                                (is (= #{} (:subscribe cs)))
                                (is (= #{} (:query cs)))))

                  (testing "rejects missing :id"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (cap/capability-set {:dispatch #{:foo}}))))

                  (testing "rejects missing :dispatch"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (cap/capability-set {:id :bad}))))

                  (testing "rejects non-set dispatch"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (cap/capability-set {:id :bad
                                                             :dispatch [:foo]}))))))

(deftest schema-projection-test
  ;; ->schema produces a Malli :enum schema of all allowed operations.
         (let [cs (cap/capability-set full-caps)
               schema (p/->schema cs)]

              (testing "->schema"
                       (testing "produces a valid Malli schema"
                                (is (m/schema? (m/schema schema))))

                       (testing "validates a dispatch operation"
                                (is (m/validate schema :session/inject)))

                       (testing "validates a subscribe operation"
                                (is (m/validate schema :session/messages)))

                       (testing "validates a query operation"
                                (is (m/validate schema :session/git-status)))

                       (testing "rejects an operation not in any set"
                                (is (not (m/validate schema :bogus)))))))

(deftest monitor-projection-test
  ;; ->monitor checks capability-checks entries against the
  ;; declared bounds.
         (let [cs (cap/capability-set full-caps)
               monitor (p/->monitor cs)]

              (testing "->monitor"
                       (testing "returns :ok for in-bounds dispatch"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:capability-checks
                                                  [{:kind :dispatch
                                                    :op :session/inject}]})))))

                       (testing "returns :ok for in-bounds subscribe"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:capability-checks
                                                  [{:kind :subscribe
                                                    :op :session/messages}]})))))

                       (testing "returns :ok for in-bounds query"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:capability-checks
                                                  [{:kind :query
                                                    :op :session/git-status}]})))))

                       (testing "returns :ok for empty checks"
                                (is (= :ok
                                       (:verdict (monitor {:capability-checks []})))))

                       (testing "returns :violation for out-of-bounds dispatch"
                                (let [result (monitor {:capability-checks
                                                       [{:kind :dispatch
                                                         :op :admin/delete}]})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :unauthorized
                                            (-> result :violations first :detail :kind)))))

                       (testing "returns :violation for out-of-bounds subscribe"
                                (let [result (monitor {:capability-checks
                                                       [{:kind :subscribe
                                                         :op :admin/logs}]})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :unauthorized
                                            (-> result :violations first :detail :kind)))))

                       (testing "reports multiple violations"
                                (let [result (monitor {:capability-checks
                                                       [{:kind :dispatch :op :bad1}
                                                        {:kind :query :op :bad2}]})]
                                     (is (= :violation (:verdict result)))
                                     (is (= 2 (count (:violations result)))))))))

(deftest generator-projection-test
  ;; ->gen produces a generator selecting from all allowed operations.
         (let [cs (cap/capability-set full-caps)
               g (p/->gen cs)
               schema (p/->schema cs)
               samples (gen/sample g 30)]

              (testing "->gen"
                       (testing "produces a generator"
                                (is (some? g)))

                       (testing "generated values are within bounds (A24)"
                                (doseq [sample samples]
                                       (is (m/validate schema sample)
                                           (str "sample out of bounds: " (pr-str sample))))))))

(deftest gap-type-projection-test
  ;; ->gap-type returns the failure taxonomy for capability sets.
         (let [cs (cap/capability-set full-caps)
               gt (p/->gap-type cs)]

              (testing "->gap-type"
                       (testing "has :formalism key"
                                (is (= :capability-set (:formalism gt))))

                       (testing "has gap-kinds set"
                                (is (contains? (:gap-kinds gt) :unauthorized)))

                       (testing "has statuses set"
                                (is (= #{:conforms :absent :diverges} (:statuses gt)))))))

(deftest referenceable-test
  ;; extract-refs returns the capability sets keyed by ref-kind.
         (let [cs (cap/capability-set full-caps)]

              (testing "extract-refs"
                       (testing "returns dispatch set for :dispatch-refs"
                                (is (= #{:session/inject :session/request}
                                       (p/extract-refs cs :dispatch-refs))))

                       (testing "returns subscribe set for :subscribe-refs"
                                (is (= #{:session/messages :session/states}
                                       (p/extract-refs cs :subscribe-refs))))

                       (testing "returns query set for :query-refs"
                                (is (= #{:session/git-status}
                                       (p/extract-refs cs :query-refs))))

                       (testing "returns union for :all-refs"
                                (is (= #{:session/inject :session/request
                                         :session/messages :session/states
                                         :session/git-status}
                                       (p/extract-refs cs :all-refs))))

                       (testing "returns empty set for unknown ref-kind"
                                (is (= #{} (p/extract-refs cs :unknown)))))))

(deftest dogfood-caps-test
  ;; The dogfood capability sets construct and have the expected
  ;; operation counts.
         (testing "dogfood formalism caps"
                  (let [cs (cap/capability-set dogfood-formalism-caps)]
                       (testing "has five dispatch operations"
                                (is (= 5 (count (p/extract-refs cs :dispatch-refs)))))

                       (testing "includes all IProjectable methods"
                                (is (every? (p/extract-refs cs :dispatch-refs)
                                            #{:->schema :->monitor :->gen :->gap-type})))

                       (testing "includes extract-refs"
                                (is (contains? (p/extract-refs cs :dispatch-refs)
                                               :extract-refs)))))

         (testing "dogfood morphism caps"
                  (let [cs (cap/capability-set dogfood-morphism-caps)]
                       (testing "has one dispatch operation"
                                (is (= 1 (count (p/extract-refs cs :dispatch-refs)))))

                       (testing "includes :check"
                                (is (contains? (p/extract-refs cs :dispatch-refs)
                                               :check))))))
