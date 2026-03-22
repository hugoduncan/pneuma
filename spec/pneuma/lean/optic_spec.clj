(ns pneuma.lean.optic-spec
    "Formalism specification for pneuma.lean.optic.
  Models the ILeanProjectable ->lean emission contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-optic-operations
     "The ILeanProjectable ->lean contract for OpticDeclaration."
     (es/effect-signature
      {:operations
       {:->lean
        {:input {:formalism :OpticDeclaration}
         :output :LeanSource}}}))

(def lean-optic-caps
     (cap/capability-set
      {:id :lean-optic
       :dispatch #{:->lean}}))

(def lean-optic-types
     (ts/type-schema
      {:OpticDeclaration :any
       :LeanSource :string}))

;;; Registry

(def lean-optic-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-optic
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

(def lean-optic-formalisms
     {:effect-signature/api lean-optic-operations
      :capability-set/lean-optic lean-optic-caps
      :type-schema lean-optic-types})

;;; Gap report

(defn lean-optic-gap-report
      "Runs the gap report on the lean.optic spec."
      []
      (gap/gap-report
       {:formalisms lean-optic-formalisms
        :registry lean-optic-registry}))
