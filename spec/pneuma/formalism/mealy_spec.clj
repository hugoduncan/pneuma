(ns pneuma.formalism.mealy-spec
    "Formalism specification for pneuma.formalism.mealy.
  Models the namespace's public API (constructor) and its
  IReferenceable ref-kinds."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def mealy-api-operations
     "Public API of the mealy namespace."
     (es/effect-signature
      {:operations
       {:mealy-handler-set
        {:input {:declarations :DeclarationVec}
         :output :MealyHandlerSet}}}))

(def mealy-ref-operations
     "The extract-refs dispatch table for MealyHandlerSet."
     (es/effect-signature
      {:operations
       {:handler-ids
        {:input {:formalism :MealyHandlerSet}
         :output :KeywordSet}

        :guard-state-refs
        {:input {:formalism :MealyHandlerSet}
         :output :KeywordSet}

        :emission-op-refs
        {:input {:formalism :MealyHandlerSet}
         :output :KeywordSet}

        :callback-refs
        {:input {:formalism :MealyHandlerSet}
         :output :KeywordSet}

        :update-path-refs
        {:input {:formalism :MealyHandlerSet}
         :output :PathSet}}}))

(def mealy-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:id :mealy-api
       :dispatch #{:mealy-handler-set}}))

(def mealy-ref-kind-caps
     "The ref-kinds that MealyHandlerSet's IReferenceable supports."
     (cap/capability-set
      {:id :mealy-ref-kinds
       :dispatch #{:handler-ids :guard-state-refs :emission-op-refs
                   :callback-refs :update-path-refs}}))

(def mealy-types
     "Type universe for the mealy namespace."
     (ts/type-schema
      {:MealyHandlerSet :any
       :DeclarationVec :any
       :KeywordSet [:set :keyword]
       :PathSet :any}))

;;; Registry

(def mealy-registry
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/mealy-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :ref-kind-caps->ref-ops
      (ex/existential-morphism
       {:id :ref-kind-caps->ref-ops
        :from :capability-set/mealy-ref-kinds
        :to :effect-signature/refs
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :api-ops->types
      (st/structural-morphism
       {:id :api-ops->types
        :from :effect-signature/api
        :to :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})

      :ref-ops->types
      (st/structural-morphism
       {:id :ref-ops->types
        :from :effect-signature/refs
        :to :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})})

;;; Formalisms map

(def mealy-formalisms
     {:effect-signature/api mealy-api-operations
      :effect-signature/refs mealy-ref-operations
      :capability-set/mealy-api mealy-api-caps
      :capability-set/mealy-ref-kinds mealy-ref-kind-caps
      :type-schema mealy-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms mealy-formalisms
      :registry   mealy-registry})

;;; Gap report

(defn mealy-gap-report
      "Runs the gap report on the mealy namespace spec."
      []
      (gap/gap-report spec-system))
