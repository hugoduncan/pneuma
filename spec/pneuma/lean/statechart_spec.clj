(ns pneuma.lean.statechart-spec
    "Formalism specification for pneuma.lean.statechart.
  Models the ILeanProjectable ->lean emission contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-sc-operations
     "The ILeanProjectable ->lean contract for Statechart."
     (es/effect-signature
      {:label "Lean Statechart Operations"
       :operations
       {:->lean
        {:input {:formalism :Statechart}
         :output :LeanSource}}}))

(def lean-sc-caps
     (cap/capability-set
      {:label "Lean Statechart Capabilities"
       :id :lean-sc
       :dispatch #{:->lean}}))

(def lean-sc-types
     (ts/type-schema
      {:label "Lean Statechart Type Registry"
       :types {:Statechart :any
               :LeanSource :string}}))

;;; Registry

(def lean-sc-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-sc
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

(def lean-sc-formalisms
     {:effect-signature/api lean-sc-operations
      :capability-set/lean-sc lean-sc-caps
      :type-schema lean-sc-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms lean-sc-formalisms
      :registry   lean-sc-registry})

;;; Gap report

(defn lean-sc-gap-report
      "Runs the gap report on the lean.statechart spec."
      []
      (gap/gap-report spec-system))
