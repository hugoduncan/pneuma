(ns pneuma.lean.path-semantic-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.existential :as ex]
              [pneuma.path.core :as path]
              [pneuma.lean.path-semantic :as ps]))

(def cycle-ops
     (es/effect-signature
      {:label "test ES"
       :operations
       {:dispatch {:input {:event :Keyword} :output :Boolean}}}))

(def cycle-caps
     (cap/capability-set
      {:label "test caps"
       :id :cycle-caps
       :dispatch #{:dispatch}}))

(def cycle-formalisms
     {:capability-set cycle-caps
      :effect-signature cycle-ops})

(def cyclic-registry
     {:caps->ops (ex/existential-morphism
                  {:id :caps->ops
                   :from :capability-set
                   :to :effect-signature
                   :source-ref-kind :dispatch-refs
                   :target-ref-kind :operation-ids})
      :ops->caps (ex/existential-morphism
                  {:id :ops->caps
                   :from :effect-signature
                   :to :capability-set
                   :source-ref-kind :operation-ids
                   :target-ref-kind :dispatch-refs})})

(deftest path-semantic-test
  ;; emit-path-semantic-composition emits Lean 4 semantic composition
  ;; theorems for each cycle.
         (testing "emit-path-semantic-composition"

                  (testing "with cyclic paths"
                           (let [paths (path/find-paths cyclic-registry)
                                 src (ps/emit-path-semantic-composition paths cycle-formalisms)]

                                (testing "emits semantic composition theorem"
                                         (is (str/includes? src "semantic_composition")))

                                (testing "references step hypotheses"
                                         (is (str/includes? src "h1"))
                                         (is (str/includes? src "h2")))

                                (testing "emits ref preservation"
                                         (is (str/includes? src "ref_preservation")))))

                  (testing "with no paths"
                           (let [src (ps/emit-path-semantic-composition [] cycle-formalisms)]
                                (is (str/includes? src "vacuously true"))))))
