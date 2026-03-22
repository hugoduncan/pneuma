(ns pneuma.formalism.capability-spec
    "Formalism specification for pneuma.formalism.capability.
  Models the namespace's public API (constructor) and its
  IReferenceable ref-kinds."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def cap-api-operations
     "Public API of the capability namespace."
     (es/effect-signature
      {:label "Capability API Operations"
       :operations
       {:capability-set
        {:input {:id :Keyword
                 :dispatch :KeywordSet
                 :subscribe :KeywordSet
                 :query :KeywordSet}
         :output :CapabilitySet}}}))

(def cap-ref-operations
     "The extract-refs dispatch table for CapabilitySet."
     (es/effect-signature
      {:label "Capability Ref Operations"
       :operations
       {:dispatch-refs
        {:input {:formalism :CapabilitySet}
         :output :KeywordSet}

        :subscribe-refs
        {:input {:formalism :CapabilitySet}
         :output :KeywordSet}

        :query-refs
        {:input {:formalism :CapabilitySet}
         :output :KeywordSet}

        :all-refs
        {:input {:formalism :CapabilitySet}
         :output :KeywordSet}}}))

(def cap-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:label "Capability API Capabilities"
       :id :cap-api
       :dispatch #{:capability-set}}))

(def cap-ref-kind-caps
     "The ref-kinds that CapabilitySet's IReferenceable supports."
     (cap/capability-set
      {:label "Capability Ref Kind Capabilities"
       :id :cap-ref-kinds
       :dispatch #{:dispatch-refs :subscribe-refs :query-refs :all-refs}}))

(def cap-types
     "Type universe for the capability namespace."
     (ts/type-schema
      {:label "Capability Type Registry"
       :types {:CapabilitySet :any
               :Keyword :keyword
               :KeywordSet [:set :keyword]}}))

;;; Registry

(def cap-registry
     "Morphisms connecting the capability spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/cap-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :ref-kind-caps->ref-ops
      (ex/existential-morphism
       {:id :ref-kind-caps->ref-ops
        :from :capability-set/cap-ref-kinds
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

(def cap-formalisms
     {:effect-signature/api cap-api-operations
      :effect-signature/refs cap-ref-operations
      :capability-set/cap-api cap-api-caps
      :capability-set/cap-ref-kinds cap-ref-kind-caps
      :type-schema cap-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms cap-formalisms
      :registry   cap-registry})

;;; Gap report

(defn cap-gap-report
      "Runs the gap report on the capability namespace spec."
      []
      (gap/gap-report spec-system))
