(ns pneuma.path.graph-spec
    "Formalism specification for pneuma.path.graph.
  Models the pure graph algorithm contracts: registry->graph,
  registry->edge-index, and elementary-circuits."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def graph-api-operations
     "Public API of the path.graph namespace."
     (es/effect-signature
      {:label "Graph API Operations"
       :operations
       {:registry->graph
        {:input {:registry :Registry}
         :output :AdjacencyMap}

        :registry->edge-index
        {:input {:registry :Registry}
         :output :EdgeIndex}

        :elementary-circuits
        {:input {:graph :AdjacencyMap}
         :output :CircuitVec}}}))

(def graph-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:label "Graph API Capabilities"
       :id :graph-api
       :dispatch #{:registry->graph :registry->edge-index
                   :elementary-circuits}}))

(def graph-types
     "Type universe for the path.graph namespace."
     (ts/type-schema
      {:label "Graph Type Registry"
       :types {:Registry :any
               :AdjacencyMap :any
               :EdgeIndex :any
               :CircuitVec :any}}))

;;; Registry

(def graph-registry
     "Morphisms connecting the path.graph spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/graph-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :api-ops->types
      (st/structural-morphism
       {:id :api-ops->types
        :from :effect-signature/api
        :to :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})})

;;; Formalisms map

(def graph-formalisms
     {:effect-signature/api graph-api-operations
      :capability-set/graph-api graph-api-caps
      :type-schema graph-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms graph-formalisms
      :registry   graph-registry})

;;; Gap report

(defn graph-gap-report
      "Runs the gap report on the path.graph namespace spec."
      []
      (gap/gap-report spec-system))
