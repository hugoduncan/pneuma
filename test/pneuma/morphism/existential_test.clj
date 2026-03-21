(ns pneuma.morphism.existential-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.morphism.existential :as ex]))

;; Tests for existential morphisms using the dogfood instances:
;; capability sets referencing effect signature operations.

(def protocol-ops
     (es/effect-signature
      {:operations
       {:->schema {:input {:formalism :Keyword} :output :Any}
        :->monitor {:input {:formalism :Keyword} :output :Any}
        :->gen {:input {:formalism :Keyword} :output :Any}
        :->gap-type {:input {:formalism :Keyword} :output :Any}
        :check {:input {:morphism :Keyword} :output :Any}
        :extract-refs {:input {:formalism :Keyword} :output :Any}}}))

(def formalism-caps
     (cap/capability-set
      {:id :formalism-record
       :dispatch #{:->schema :->monitor :->gen :->gap-type
                   :extract-refs}}))

(def bad-caps
     (cap/capability-set
      {:id :bad-record
       :dispatch #{:->schema :->monitor :bogus-method}}))

(def morphism
     (ex/existential-morphism
      {:id :caps->ops
       :from :capability-set
       :to :effect-signature
       :source-ref-kind :dispatch-refs
       :target-ref-kind :operation-ids}))

(deftest existential-conforms-test
  ;; All capability dispatch refs exist in the effect signature.
         (testing "existential morphism"
                  (testing "conforms when all refs resolve"
                           (let [gaps (p/check morphism formalism-caps protocol-ops {})]
                                (is (= 1 (count gaps)))
                                (is (= :conforms (:status (first gaps))))))))

(deftest existential-diverges-test
  ;; A capability set with a ref not in the effect signature.
         (testing "existential morphism"
                  (testing "diverges with dangling refs"
                           (let [gaps (p/check morphism bad-caps protocol-ops {})]
                                (is (= 1 (count gaps)))
                                (is (= :diverges (:status (first gaps))))
                                (is (contains? (-> gaps first :detail :dangling-refs)
                                               :bogus-method))))))
