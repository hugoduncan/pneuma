(ns pneuma.lean.capability-spec
    "Formalism specification for pneuma.lean.capability.
  Models the ILeanProjectable ->lean emission contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-cap-operations
     "The ILeanProjectable ->lean contract for CapabilitySet."
     (es/effect-signature
      {:operations
       {:->lean
        {:input {:formalism :CapabilitySet}
         :output :LeanSource}}}))

(def lean-cap-caps
     (cap/capability-set
      {:id :lean-cap
       :dispatch #{:->lean}}))

(def lean-cap-types
     (ts/type-schema
      {:CapabilitySet :any
       :LeanSource :string}))

;;; Registry

(def lean-cap-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-cap
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

(def lean-cap-formalisms
     {:effect-signature/api lean-cap-operations
      :capability-set/lean-cap lean-cap-caps
      :type-schema lean-cap-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms lean-cap-formalisms
      :registry   lean-cap-registry})

;;; Gap report

(defn lean-cap-gap-report
      "Runs the gap report on the lean.capability spec."
      []
      (gap/gap-report spec-system))
