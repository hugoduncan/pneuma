(ns pneuma.formalism.type-schema-spec
    "Formalism specification for pneuma.formalism.type-schema.
  Models the namespace's public API (constructor) and its
  IReferenceable ref-kinds."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def ts-api-operations
     "Public API of the type-schema namespace."
     (es/effect-signature
      {:operations
       {:type-schema
        {:input {:types-map :TypesMap}
         :output :TypeSchema}}}))

(def ts-ref-operations
     "The extract-refs dispatch table for TypeSchema."
     (es/effect-signature
      {:operations
       {:type-ids
        {:input {:formalism :TypeSchema}
         :output :KeywordSet}}}))

(def ts-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:id :ts-api
       :dispatch #{:type-schema}}))

(def ts-ref-kind-caps
     "The ref-kinds that TypeSchema's IReferenceable supports."
     (cap/capability-set
      {:id :ts-ref-kinds
       :dispatch #{:type-ids}}))

(def ts-types
     "Type universe for the type-schema namespace."
     (ts/type-schema
      {:TypeSchema :any
       :TypesMap :any
       :KeywordSet [:set :keyword]}))

;;; Registry

(def ts-registry
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/ts-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :ref-kind-caps->ref-ops
      (ex/existential-morphism
       {:id :ref-kind-caps->ref-ops
        :from :capability-set/ts-ref-kinds
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

(def ts-formalisms
     {:effect-signature/api ts-api-operations
      :effect-signature/refs ts-ref-operations
      :capability-set/ts-api ts-api-caps
      :capability-set/ts-ref-kinds ts-ref-kind-caps
      :type-schema ts-types})

;;; Gap report

(defn ts-gap-report
      "Runs the gap report on the type-schema namespace spec."
      []
      (gap/gap-report
       {:formalisms ts-formalisms
        :registry ts-registry}))
