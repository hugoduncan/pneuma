(ns pneuma.morphism.structural-spec
    "Formalism specification for pneuma.morphism.structural.
  Models the namespace's public API (constructor) and its
  IConnection check contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def st-api-operations
     "Public API of the structural morphism namespace."
     (es/effect-signature
      {:operations
       {:structural-morphism
        {:input {:id :Keyword
                 :from :Keyword
                 :to :Keyword
                 :source-ref-kind :Keyword
                 :target-ref-kind :Keyword}
         :output :StructuralMorphism}}}))

(def st-check-operations
     "The IConnection check contract for StructuralMorphism.
  Validates that source outputs conform to target schema."
     (es/effect-signature
      {:operations
       {:check
        {:input {:morphism :StructuralMorphism
                 :source :IReferenceable
                 :target :IProjectable
                 :refinement-map :RefinementMap}
         :output :GapSeq}}}))

(def st-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:id :st-api
       :dispatch #{:structural-morphism}}))

(def st-check-caps
     "The IConnection check capability."
     (cap/capability-set
      {:id :st-check
       :dispatch #{:check}}))

(def st-types
     "Type universe for the structural morphism namespace."
     (ts/type-schema
      {:Keyword :keyword
       :StructuralMorphism :any
       :IReferenceable :any
       :IProjectable :any
       :RefinementMap :any
       :GapSeq :any}))

;;; Registry

(def st-registry
     "Morphisms connecting the structural spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/st-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :check-caps->check-ops
      (ex/existential-morphism
       {:id :check-caps->check-ops
        :from :capability-set/st-check
        :to :effect-signature/check
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :api-ops->types
      (st/structural-morphism
       {:id :api-ops->types
        :from :effect-signature/api
        :to :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})

      :check-ops->types
      (st/structural-morphism
       {:id :check-ops->types
        :from :effect-signature/check
        :to :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})})

;;; Formalisms map

(def st-formalisms
     {:effect-signature/api st-api-operations
      :effect-signature/check st-check-operations
      :capability-set/st-api st-api-caps
      :capability-set/st-check st-check-caps
      :type-schema st-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms st-formalisms
      :registry   st-registry})

;;; Gap report

(defn st-gap-report
      "Runs the gap report on the structural morphism namespace spec."
      []
      (gap/gap-report spec-system))
