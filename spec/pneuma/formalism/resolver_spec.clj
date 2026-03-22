(ns pneuma.formalism.resolver-spec
    "Formalism specification for pneuma.formalism.resolver.
  Models the namespace's public API (constructor, reachable-attributes)
  and its IReferenceable ref-kinds."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def resolver-api-operations
     "Public API of the resolver namespace."
     (es/effect-signature
      {:operations
       {:resolver-graph
        {:input {:declarations :DeclarationVec}
         :output :ResolverGraph}

        :reachable-attributes
        {:input {:declarations :DeclarationMap
                 :known-attrs :KeywordSet}
         :output :KeywordSet}}}))

(def resolver-ref-operations
     "The extract-refs dispatch table for ResolverGraph."
     (es/effect-signature
      {:operations
       {:resolver-ids
        {:input {:formalism :ResolverGraph}
         :output :KeywordSet}

        :input-attributes
        {:input {:formalism :ResolverGraph}
         :output :KeywordSet}

        :output-attributes
        {:input {:formalism :ResolverGraph}
         :output :KeywordSet}

        :external-sources
        {:input {:formalism :ResolverGraph}
         :output :KeywordSet}}}))

(def resolver-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:id :resolver-api
       :dispatch #{:resolver-graph :reachable-attributes}}))

(def resolver-ref-kind-caps
     "The ref-kinds that ResolverGraph's IReferenceable supports."
     (cap/capability-set
      {:id :resolver-ref-kinds
       :dispatch #{:resolver-ids :input-attributes
                   :output-attributes :external-sources}}))

(def resolver-types
     "Type universe for the resolver namespace."
     (ts/type-schema
      {:ResolverGraph :any
       :DeclarationVec :any
       :DeclarationMap :any
       :KeywordSet [:set :keyword]}))

;;; Registry

(def resolver-registry
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/resolver-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :ref-kind-caps->ref-ops
      (ex/existential-morphism
       {:id :ref-kind-caps->ref-ops
        :from :capability-set/resolver-ref-kinds
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

(def resolver-formalisms
     {:effect-signature/api resolver-api-operations
      :effect-signature/refs resolver-ref-operations
      :capability-set/resolver-api resolver-api-caps
      :capability-set/resolver-ref-kinds resolver-ref-kind-caps
      :type-schema resolver-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms resolver-formalisms
      :registry   resolver-registry})

;;; Gap report

(defn resolver-gap-report
      "Runs the gap report on the resolver namespace spec."
      []
      (gap/gap-report spec-system))
