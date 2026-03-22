(ns pneuma.lean.containment-spec
    "Formalism specification for pneuma.lean.containment.
  Models the ILeanConnection ->lean-conn emission contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-ct-operations
     "The ILeanConnection ->lean-conn contract for ContainmentMorphism."
     (es/effect-signature
      {:operations
       {:->lean-conn
        {:input {:morphism :ContainmentMorphism
                 :source :IReferenceable
                 :target :IReferenceable}
         :output :LeanSource}}}))

(def lean-ct-caps
     (cap/capability-set
      {:id :lean-ct
       :dispatch #{:->lean-conn}}))

(def lean-ct-types
     (ts/type-schema
      {:ContainmentMorphism :any
       :IReferenceable :any
       :LeanSource :string}))

;;; Registry

(def lean-ct-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-ct
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

(def lean-ct-formalisms
     {:effect-signature/api lean-ct-operations
      :capability-set/lean-ct lean-ct-caps
      :type-schema lean-ct-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms lean-ct-formalisms
      :registry   lean-ct-registry})

;;; Gap report

(defn lean-ct-gap-report
      "Runs the gap report on the lean.containment spec."
      []
      (gap/gap-report spec-system))
