(ns pneuma.code.core-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.statechart :as sc]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.morphism.existential :as ex]
              [pneuma.fills :as fills]
              [pneuma.code.core :as code]))

(deftest emit-project-test
  ;; Verifies emit-project composes multiple formalisms and morphisms
  ;; into a project-level code generation plan with sources, tests,
  ;; manifest, and fills skeleton.
         (testing "emit-project"
                  (let [chart (sc/statechart
                               {:label "session"
                                :states #{:idle :active}
                                :initial {}
                                :transitions [{:source :idle :event :go :target :active}]})
                        sig (es/effect-signature
                             {:label "effects"
                              :operations {:do-thing {:input {:x :String} :output :Bool}}})
                        caps (cap/capability-set
                              {:label "caps" :id :agent :dispatch #{:do-thing}})
                        morph (ex/existential-morphism
                               {:id :caps->ops
                                :from :capability-set
                                :to :effect-signature
                                :source-ref-kind :dispatch-refs
                                :target-ref-kind :operation-ids})
                        result (code/emit-project
                                {:formalisms {:statechart chart
                                              :effect-signature sig
                                              :capability-set caps}
                                 :registry {:caps->ops morph}
                                 :opts {:target-ns {:statechart 'a.handlers
                                                    :effect-signature 'a.effects
                                                    :capability-set 'a.caps}}})]

                       (testing "produces sources for each formalism"
                                (is (= 3 (count (:sources result)))))

                       (testing "produces morphism test data"
                                (is (contains? (:tests result) :caps->ops)))

                       (testing "produces a merged manifest"
                                (is (map? (:manifest result)))
                                (is (seq (:manifest result))))

                       (testing "produces manifest string"
                                (is (string? (:manifest-str result)))))))

(deftest code-diff-test
  ;; Verifies code-diff detects structural differences between two
  ;; code fragments.
         (testing "code-diff"
                  (let [old-code {:forms [{:type :defmulti :name 'handle-event}
                                          {:type :defmethod :dispatch-val :submit}
                                          {:type :defmethod :dispatch-val :cancel}]
                                  :fill-manifest {:submit/update-db {:args '[db]}
                                                  :cancel/cleanup {:args '[db]}}}
                        new-code {:forms [{:type :defmulti :name 'handle-event}
                                          {:type :defmethod :dispatch-val :submit}
                                          {:type :defmethod :dispatch-val :approve}]
                                  :fill-manifest {:submit/update-db {:args '[db]}
                                                  :approve/check {:args '[db]}}}
                        diff (code/code-diff new-code old-code)]

                       (testing "detects new methods"
                                (is (= [:approve] (:new-methods diff))))

                       (testing "detects removed methods"
                                (is (= [:cancel] (:removed-methods diff))))

                       (testing "detects new fill points"
                                (is (= [:approve/check] (:new-fill-points diff))))

                       (testing "detects removed fill points"
                                (is (= [:cancel/cleanup] (:removed-fill-points diff)))))))

(deftest fill-gaps-integration-test
  ;; Verifies that fill-gaps produces gap entries compatible with
  ;; the gap report structure.
         (testing "fill-gaps integration"
                  (let [r (fills/make-registry)
                        manifest {:a/ok {:args '[x] :returns :int :doc "ok"}
                                  :a/miss {:args '[x] :returns :str :doc "miss"}}]
                       (fills/reg-fill r :a/ok (fn [_x] 1))
                       (let [gaps (code/fill-gaps r manifest)]

                            (testing "all entries have :fill layer"
                                     (is (every? #(= :fill (:layer %)) gaps)))

                            (testing "includes conforming and absent"
                                     (is (some #(= :conforms (:status %)) gaps))
                                     (is (some #(= :absent (:status %)) gaps)))))))
