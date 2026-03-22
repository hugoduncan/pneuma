(ns pneuma.formalism.optic-spec
    "Formalism specification for pneuma.formalism.optic.
  Models the namespace's public API (constructor) and its
  IReferenceable ref-kinds."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def optic-api-operations
     "Public API of the optic namespace."
     (es/effect-signature
      {:operations
       {:optic-declaration
        {:input {:declarations :DeclarationVec}
         :output :OpticDeclaration}}}))

(def optic-ref-operations
     "The extract-refs dispatch table for OpticDeclaration."
     (es/effect-signature
      {:operations
       {:optic-ids
        {:input {:formalism :OpticDeclaration}
         :output :KeywordSet}

        :paths
        {:input {:formalism :OpticDeclaration}
         :output :PathSet}

        :source-optic-refs
        {:input {:formalism :OpticDeclaration}
         :output :PathSet}}}))

(def optic-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:id :optic-api
       :dispatch #{:optic-declaration}}))

(def optic-ref-kind-caps
     "The ref-kinds that OpticDeclaration's IReferenceable supports."
     (cap/capability-set
      {:id :optic-ref-kinds
       :dispatch #{:optic-ids :paths :source-optic-refs}}))

(def optic-types
     "Type universe for the optic namespace."
     (ts/type-schema
      {:OpticDeclaration :any
       :DeclarationVec :any
       :KeywordSet [:set :keyword]
       :PathSet :any}))

;;; Registry

(def optic-registry
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/optic-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :ref-kind-caps->ref-ops
      (ex/existential-morphism
       {:id :ref-kind-caps->ref-ops
        :from :capability-set/optic-ref-kinds
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

(def optic-formalisms
     {:effect-signature/api optic-api-operations
      :effect-signature/refs optic-ref-operations
      :capability-set/optic-api optic-api-caps
      :capability-set/optic-ref-kinds optic-ref-kind-caps
      :type-schema optic-types})

;;; Gap report

(defn optic-gap-report
      "Runs the gap report on the optic namespace spec."
      []
      (gap/gap-report
       {:formalisms optic-formalisms
        :registry optic-registry}))
