(ns pneuma.lean.resolver-spec
    "Formalism specification for pneuma.lean.resolver.
  Models the ILeanProjectable ->lean emission contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-resolver-operations
     "The ILeanProjectable ->lean contract for ResolverGraph."
     (es/effect-signature
      {:operations
       {:->lean
        {:input {:formalism :ResolverGraph}
         :output :LeanSource}}}))

(def lean-resolver-caps
     (cap/capability-set
      {:id :lean-resolver
       :dispatch #{:->lean}}))

(def lean-resolver-types
     (ts/type-schema
      {:ResolverGraph :any
       :LeanSource :string}))

;;; Registry

(def lean-resolver-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-resolver
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

(def lean-resolver-formalisms
     {:effect-signature/api lean-resolver-operations
      :capability-set/lean-resolver lean-resolver-caps
      :type-schema lean-resolver-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms lean-resolver-formalisms
      :registry   lean-resolver-registry})

;;; Gap report

(defn lean-resolver-gap-report
      "Runs the gap report on the lean.resolver spec."
      []
      (gap/gap-report spec-system))
