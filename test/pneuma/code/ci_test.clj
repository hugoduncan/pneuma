(ns pneuma.code.ci-test
    (:require [clojure.string :as str]
              [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.existential :as ex]
              [pneuma.fills :as fills]
              [pneuma.code.ci :as ci]
              [pneuma.code.existential]))

(deftest validate-fills-test
  ;; Verifies fill validation reports missing fills as errors
  ;; and orphaned fills as warnings.
         (testing "validate-fills"
                  (let [r (fills/make-registry)
                        manifest {:a/needed {:args '[x] :returns :int :doc "needed"}
                                  :a/also {:args '[x y] :returns :str :doc "also"}}]

                       (testing "with all fills present"
                                (fills/reg-fill r :a/needed (fn [_x] 1))
                                (fills/reg-fill r :a/also (fn [_x _y] "s"))
                                (let [result (ci/validate-fills r manifest)]
                                     (is (:ok? result))
                                     (is (empty? (:missing result)))))

                       (testing "with a missing fill"
                                (let [r2 (fills/make-registry)]
                                     (fills/reg-fill r2 :a/needed (fn [_x] 1))
                                     (let [result (ci/validate-fills r2 manifest)]
                                          (is (not (:ok? result)))
                                          (is (= [:a/also] (:missing result))))))

                       (testing "with an orphaned fill"
                                (let [r3 (fills/make-registry)]
                                     (fills/reg-fill r3 :a/needed (fn [_x] 1))
                                     (fills/reg-fill r3 :a/also (fn [_x _y] "s"))
                                     (fills/reg-fill r3 :a/extra (fn [] nil))
                                     (let [result (ci/validate-fills r3 manifest)]
                                          (is (:ok? result))
                                          (is (= [:a/extra] (:orphaned result)))))))))

(deftest format-fill-report-test
  ;; Verifies the report formatter produces human-readable output.
         (testing "format-fill-report"
                  (let [manifest {:a/ok {:args '[x] :returns :int :doc "ok"}
                                  :a/miss {:args '[y] :returns :str :doc "missing"}}
                        result {:ok? false
                                :ok [:a/ok]
                                :missing [:a/miss]
                                :orphaned []
                                :arity-mismatch []}
                        report (ci/format-fill-report result manifest)]

                       (testing "mentions missing fills"
                                (is (str/includes? report "a/miss")))

                       (testing "includes summary"
                                (is (str/includes? report "Summary"))))))

(deftest generate-morphism-tests-test
  ;; Verifies morphism test generation produces test data for
  ;; morphisms and discovered paths.
         (testing "generate-morphism-tests"
                  (let [caps (cap/capability-set
                              {:label "caps" :id :agent :dispatch #{:op-a}})
                        sig (es/effect-signature
                             {:label "ES"
                              :operations {:op-a {:input {:x :String} :output :Bool}}})
                        morph (ex/existential-morphism
                               {:id :caps->ops
                                :from :capability-set
                                :to :effect-signature
                                :source-ref-kind :dispatch-refs
                                :target-ref-kind :operation-ids})
                        result (ci/generate-morphism-tests
                                {:registry {:caps->ops morph}
                                 :formalisms {:capability-set caps
                                              :effect-signature sig}
                                 :test-ns 'test.morphisms})]

                       (testing "has correct test namespace"
                                (is (= 'test.morphisms (:test-ns result))))

                       (testing "generates morphism tests"
                                (is (= 1 (:morphism-count result))))

                       (testing "no cycles means no path tests"
                                (is (= 0 (:path-count result)))))))

(deftest check-fill-contracts-test
  ;; Verifies fill contract checking reports ok and arity mismatches.
         (testing "check-fill-contracts"
                  (let [r (fills/make-registry)
                        manifest {:a/ok {:args '[x] :returns :int}
                                  :a/mismatch {:args '[x y z] :returns :str}}]
                       (fills/reg-fill r :a/ok (fn [_x] 1))
                       (fills/reg-fill r :a/mismatch
                                       (with-meta (fn [_x _y] "s")
                                                  {:pneuma/arity 2}))
                       (let [results (ci/check-fill-contracts r manifest)]

                            (testing "reports ok for matching fill"
                                     (is (some #(and (= :a/ok (:fill-point %))
                                                     (= :ok (:status %)))
                                               results)))

                            (testing "reports arity mismatch"
                                     (is (some #(and (= :a/mismatch (:fill-point %))
                                                     (= :arity-mismatch (:status %)))
                                               results)))))))
