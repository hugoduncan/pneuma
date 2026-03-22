(ns pneuma.lean.mealy-spec
    "Formalism specification for pneuma.lean.mealy.
  Models the ILeanProjectable ->lean emission contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-mealy-operations
     "The ILeanProjectable ->lean contract for MealyHandlerSet."
     (es/effect-signature
      {:label "Lean Mealy Operations"
       :operations
       {:->lean
        {:input {:formalism :MealyHandlerSet}
         :output :LeanSource}}}))

(def lean-mealy-caps
     (cap/capability-set
      {:label "Lean Mealy Capabilities"
       :id :lean-mealy
       :dispatch #{:->lean}}))

(def lean-mealy-types
     (ts/type-schema
      {:label "Lean Mealy Type Registry"
       :types {:MealyHandlerSet :any
               :LeanSource :string}}))

;;; Registry

(def lean-mealy-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-mealy
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

(def lean-mealy-formalisms
     {:effect-signature/api lean-mealy-operations
      :capability-set/lean-mealy lean-mealy-caps
      :type-schema lean-mealy-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms lean-mealy-formalisms
      :registry   lean-mealy-registry})

;;; Gap report

(defn lean-mealy-gap-report
      "Runs the gap report on the lean.mealy spec."
      []
      (gap/gap-report spec-system))
