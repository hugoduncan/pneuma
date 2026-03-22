(ns pneuma.formalism.effect-signature-spec
    "Formalism specification for pneuma.formalism.effect-signature.
  Models the namespace's public API (constructor, type registry
  operations), its IReferenceable ref-kinds, and the type universe
  linking them."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def es-api-operations
     "Public API of the effect-signature namespace as an effect
  signature. Models the constructor and type registry functions."
     (es/effect-signature
      {:label "ES API Operations"
       :operations
       {:effect-signature
        {:input {:operations-map :OperationsMap}
         :output :EffectSignature}

        :register-type!
        {:input {:type-kw :Keyword
                 :malli-schema :MalliSchema}
         :output :Nil}

        :resolve-type
        {:input {:type-kw :Keyword}
         :output :MalliSchema}}}))

(def es-ref-operations
     "The extract-refs dispatch table modeled as an effect signature.
  Each ref-kind is an operation returning a KeywordSet."
     (es/effect-signature
      {:label "ES Ref Operations"
       :operations
       {:operation-ids
        {:input {:formalism :EffectSignature}
         :output :KeywordSet}

        :callback-refs
        {:input {:formalism :EffectSignature}
         :output :KeywordSet}

        :operation-outputs
        {:input {:formalism :EffectSignature}
         :output :KeywordSet}}}))

(def es-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:label "ES API Capabilities"
       :id :es-api
       :dispatch #{:effect-signature :register-type! :resolve-type}}))

(def es-ref-kind-caps
     "The ref-kinds that EffectSignature's IReferenceable supports."
     (cap/capability-set
      {:label "ES Ref Kind Capabilities"
       :id :es-ref-kinds
       :dispatch #{:operation-ids :callback-refs :operation-outputs}}))

(def es-types
     "Type universe for the effect-signature namespace."
     (ts/type-schema
      {:label "ES Type Registry"
       :types {:OperationsMap :any
               :EffectSignature :any
               :Keyword :keyword
               :MalliSchema :any
               :Nil :any
               :KeywordSet [:set :keyword]}}))

;;; Registry

(def es-registry
     "Morphisms connecting the effect-signature spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/es-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :ref-kind-caps->ref-ops
      (ex/existential-morphism
       {:id :ref-kind-caps->ref-ops
        :from :capability-set/es-ref-kinds
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

(def es-formalisms
     "All formalisms in the effect-signature spec."
     {:effect-signature/api es-api-operations
      :effect-signature/refs es-ref-operations
      :capability-set/es-api es-api-caps
      :capability-set/es-ref-kinds es-ref-kind-caps
      :type-schema es-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms es-formalisms
      :registry   es-registry})

;;; Gap report

(defn es-gap-report
      "Runs the gap report on the effect-signature namespace spec.
  Returns the three-layer gap report map."
      []
      (gap/gap-report spec-system))
