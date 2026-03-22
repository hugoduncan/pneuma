(ns pneuma.path.core-spec
    "Formalism specification for pneuma.path.core.
  Models ComposedPath construction and the cycle-checking
  axioms A13 (closure) and A14 (adjacency)."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def path-api-operations
     "Public API of the path.core namespace."
     (es/effect-signature
      {:operations
       {:circuit->paths
        {:input {:edge-index :EdgeIndex
                 :circuit :NodeVec}
         :output :ComposedPathVec}

        :check-closure
        {:input {:path :ComposedPath}
         :output :GapMap}

        :check-adjacency
        {:input {:path :ComposedPath}
         :output :GapVec}

        :check-path
        {:input {:path :ComposedPath}
         :output :GapVec}

        :find-paths
        {:input {:registry :Registry}
         :output :ComposedPathVec}

        :check-all-paths
        {:input {:registry :Registry}
         :output :GapVec}}}))

(def path-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:id :path-api
       :dispatch #{:circuit->paths :check-closure :check-adjacency
                   :check-path :find-paths :check-all-paths}}))

(def path-types
     "Type universe for the path.core namespace."
     (ts/type-schema
      {:EdgeIndex :any
       :NodeVec :any
       :ComposedPathVec :any
       :ComposedPath :any
       :GapMap :any
       :GapVec :any
       :Registry :any}))

;;; Registry

(def path-registry
     "Morphisms connecting the path.core spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/path-api
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

(def path-formalisms
     {:effect-signature/api path-api-operations
      :capability-set/path-api path-api-caps
      :type-schema path-types})

;;; Gap report

(defn path-gap-report
      "Runs the gap report on the path.core namespace spec."
      []
      (gap/gap-report
       {:formalisms path-formalisms
        :registry path-registry}))
