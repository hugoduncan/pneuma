(ns pneuma.lean.structural-spec
    "Formalism specification for pneuma.lean.structural.
  Models the ILeanConnection ->lean-conn emission contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-st-operations
     "The ILeanConnection ->lean-conn contract for StructuralMorphism."
     (es/effect-signature
      {:operations
       {:->lean-conn
        {:input {:morphism :StructuralMorphism
                 :source :IReferenceable
                 :target :IProjectable}
         :output :LeanSource}}}))

(def lean-st-caps
     (cap/capability-set
      {:id :lean-st
       :dispatch #{:->lean-conn}}))

(def lean-st-types
     (ts/type-schema
      {:StructuralMorphism :any
       :IReferenceable :any
       :IProjectable :any
       :LeanSource :string}))

;;; Registry

(def lean-st-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-st
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

(def lean-st-formalisms
     {:effect-signature/api lean-st-operations
      :capability-set/lean-st lean-st-caps
      :type-schema lean-st-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms lean-st-formalisms
      :registry   lean-st-registry})

;;; Gap report

(defn lean-st-gap-report
      "Runs the gap report on the lean.structural spec."
      []
      (gap/gap-report spec-system))
