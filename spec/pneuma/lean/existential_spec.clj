(ns pneuma.lean.existential-spec
    "Formalism specification for pneuma.lean.existential.
  Models the ILeanConnection ->lean-conn emission contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-ex-operations
     "The ILeanConnection ->lean-conn contract for ExistentialMorphism."
     (es/effect-signature
      {:operations
       {:->lean-conn
        {:input {:morphism :ExistentialMorphism
                 :source :IReferenceable
                 :target :IReferenceable}
         :output :LeanSource}}}))

(def lean-ex-caps
     (cap/capability-set
      {:id :lean-ex
       :dispatch #{:->lean-conn}}))

(def lean-ex-types
     (ts/type-schema
      {:ExistentialMorphism :any
       :IReferenceable :any
       :LeanSource :string}))

;;; Registry

(def lean-ex-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-ex
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :ops->types
      (st/structural-morphism
       {:id :ops->types
        :from :effect-signature/api
        :to :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})})

;;; Formalisms map

(def lean-ex-formalisms
     {:effect-signature/api lean-ex-operations
      :capability-set/lean-ex lean-ex-caps
      :type-schema lean-ex-types})

;;; Gap report

(defn lean-ex-gap-report
      "Runs the gap report on the lean.existential spec."
      []
      (gap/gap-report
       {:formalisms lean-ex-formalisms
        :registry lean-ex-registry}))
