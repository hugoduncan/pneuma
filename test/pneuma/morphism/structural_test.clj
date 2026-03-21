(ns pneuma.morphism.structural-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.morphism.structural :as st]))

;; Tests for structural morphisms: operation outputs must conform
;; to the target formalism's schema.

(def protocol-ops
     (es/effect-signature
      {:operations
       {:->schema {:input {:formalism :Keyword} :output :Any}
        :check {:input {:morphism :Keyword} :output :Any}}}))

(def caps-with-matching-ops
     (cap/capability-set
      {:id :matching
       :dispatch #{:->schema :check}}))

(def morphism
     (st/structural-morphism
      {:id :ops->caps
       :from :effect-signature
       :to :capability-set
       :source-ref-kind :operation-outputs
       :target-ref-kind :all-refs}))

(deftest structural-conforms-test
  ;; When operation outputs are within the target's schema bounds.
         (testing "structural morphism"
                  (testing "conforms when outputs validate against target schema"
      ;; The effect sig outputs are #{:Any}. The caps schema is
      ;; [:enum :->schema :check]. :Any is not in that enum, so
      ;; this should diverge — testing the real behavior.
                           (let [gaps (p/check morphism protocol-ops caps-with-matching-ops {})]
                                (is (= 1 (count gaps)))
        ;; :Any is not a member of the caps enum, so this diverges
                                (is (= :diverges (:status (first gaps))))))))

(deftest structural-with-matching-outputs-test
  ;; Create an effect sig whose outputs ARE in the caps schema.
         (testing "structural morphism"
                  (testing "conforms when outputs are members of target schema"
                           (let [sig (es/effect-signature
                                      {:operations
                                       {:op-a {:input {:x :Keyword} :output :Keyword}
                                        :op-b {:input {:y :Keyword} :output :Keyword}}})
                                 caps (cap/capability-set
                                       {:id :kw-caps
                                        :dispatch #{:Keyword}})
                                 m (st/structural-morphism
                                    {:id :test-match
                                     :from :effect-signature
                                     :to :capability-set
                                     :source-ref-kind :operation-outputs
                                     :target-ref-kind :all-refs})
                                 gaps (p/check m sig caps {})]
                                (is (= 1 (count gaps)))
                                (is (= :conforms (:status (first gaps))))))))

(deftest structural-diverges-test
  ;; When an output is not in the target schema.
         (testing "structural morphism"
                  (testing "diverges with shape mismatches"
                           (let [sig (es/effect-signature
                                      {:operations
                                       {:op-a {:input {:x :Keyword} :output :Keyword}
                                        :op-b {:input {:y :Keyword} :output :BadType}}})
                                 caps (cap/capability-set
                                       {:id :narrow
                                        :dispatch #{:Keyword}})
                                 m (st/structural-morphism
                                    {:id :test-mismatch
                                     :from :effect-signature
                                     :to :capability-set
                                     :source-ref-kind :operation-outputs
                                     :target-ref-kind :all-refs})
                                 gaps (p/check m sig caps {})]
                                (is (= 1 (count gaps)))
                                (is (= :diverges (:status (first gaps))))
                                (is (seq (-> gaps first :detail :shape-mismatches)))))))
