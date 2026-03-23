(ns pneuma.lean.composition-transitivity-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.existential :as ex]
              [pneuma.path.core :as path]
              [pneuma.lean.composition-transitivity :as ct]))

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

(deftest composition-transitivity-test
  ;; emit-composition-transitivity emits Lean 4 bridge functions
  ;; and transitivity theorems for adjacent morphism pairs in paths.
         (testing "emit-composition-transitivity"

                  (testing "with cyclic registry"
                           (let [paths (path/find-paths cyclic-registry)
                                 src (ct/emit-composition-transitivity paths cycle-formalisms)]

                                (testing "emits bridge functions"
                                         (is (str/includes? src "bridge_")))

                                (testing "emits transitivity header"
                                         (is (str/includes? src "Transitivity")))

                                (testing "references morphism ids"
                                         (is (str/includes? src "CapsOps"))
                                         (is (str/includes? src "OpsCaps")))))

                  (testing "with no paths"
                           (let [src (ct/emit-composition-transitivity [] cycle-formalisms)]
                                (is (str/includes? src "vacuously true"))))))
