(ns pneuma.formalism.statechart-spec
    "Formalism specification for pneuma.formalism.statechart.
  Models the namespace's public API (constructor) and its
  IReferenceable ref-kinds."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def sc-api-operations
     "Public API of the statechart namespace."
     (es/effect-signature
      {:label "SC API Operations"
       :operations
       {:statechart
        {:input {:states :KeywordSet
                 :hierarchy :HierarchyMap
                 :parallel :KeywordSet
                 :initial :InitialMap
                 :transitions :TransitionVec}
         :output :Statechart}}}))

(def sc-ref-operations
     "The extract-refs dispatch table for Statechart."
     (es/effect-signature
      {:label "SC Ref Operations"
       :operations
       {:state-ids
        {:input {:formalism :Statechart}
         :output :KeywordSet}

        :event-ids
        {:input {:formalism :Statechart}
         :output :KeywordSet}

        :raised-events
        {:input {:formalism :Statechart}
         :output :KeywordSet}}}))

(def sc-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:label "SC API Capabilities"
       :id :sc-api
       :dispatch #{:statechart}}))

(def sc-ref-kind-caps
     "The ref-kinds that Statechart's IReferenceable supports."
     (cap/capability-set
      {:label "SC Ref Kind Capabilities"
       :id :sc-ref-kinds
       :dispatch #{:state-ids :event-ids :raised-events}}))

(def sc-types
     "Type universe for the statechart namespace."
     (ts/type-schema
      {:label "SC Type Registry"
       :types {:Statechart :any
               :KeywordSet [:set :keyword]
               :HierarchyMap :any
               :InitialMap :any
               :TransitionVec :any}}))

;;; Registry

(def sc-registry
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/sc-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :ref-kind-caps->ref-ops
      (ex/existential-morphism
       {:id :ref-kind-caps->ref-ops
        :from :capability-set/sc-ref-kinds
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

(def sc-formalisms
     {:effect-signature/api sc-api-operations
      :effect-signature/refs sc-ref-operations
      :capability-set/sc-api sc-api-caps
      :capability-set/sc-ref-kinds sc-ref-kind-caps
      :type-schema sc-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms sc-formalisms
      :registry   sc-registry})

;;; Gap report

(defn sc-gap-report
      "Runs the gap report on the statechart namespace spec."
      []
      (gap/gap-report spec-system))
