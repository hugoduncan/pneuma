(ns pneuma.lean.effect-signature-spec
    "Formalism specification for pneuma.lean.effect-signature.
  Models the ILeanProjectable ->lean emission contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-es-operations
     "The ILeanProjectable ->lean contract for EffectSignature."
     (es/effect-signature
      {:label "Lean ES Operations"
       :operations
       {:->lean
        {:input {:formalism :EffectSignature}
         :output :LeanSource}}}))

(def lean-es-caps
     (cap/capability-set
      {:label "Lean ES Capabilities"
       :id :lean-es
       :dispatch #{:->lean}}))

(def lean-es-types
     (ts/type-schema
      {:label "Lean ES Type Registry"
       :types {:EffectSignature :any
               :LeanSource :string}}))

;;; Registry

(def lean-es-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-es
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

(def lean-es-formalisms
     {:effect-signature/api lean-es-operations
      :capability-set/lean-es lean-es-caps
      :type-schema lean-es-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms lean-es-formalisms
      :registry   lean-es-registry})

;;; Gap report

(defn lean-es-gap-report
      "Runs the gap report on the lean.effect-signature spec."
      []
      (gap/gap-report spec-system))
