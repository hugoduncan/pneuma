(ns pneuma.morphism.ordering-spec
    "Formalism specification for pneuma.morphism.ordering.
  Models the namespace's public API (constructor) and its
  IConnection check contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def ord-api-operations
     "Public API of the ordering morphism namespace."
     (es/effect-signature
      {:label "Ordering API Operations"
       :operations
       {:ordering-morphism
        {:input {:id :Keyword
                 :from :Keyword
                 :to :Keyword
                 :source-ref-kind :Keyword
                 :target-ref-kind :Keyword
                 :chain :OrderedSeq}
         :output :OrderingMorphism}}}))

(def ord-check-operations
     "The IConnection check contract for OrderingMorphism.
  Checks that source ref precedes target ref in the chain."
     (es/effect-signature
      {:label "Ordering Check Operations"
       :operations
       {:check
        {:input {:morphism :OrderingMorphism
                 :source :IReferenceable
                 :target :IReferenceable
                 :refinement-map :RefinementMap}
         :output :GapSeq}}}))

(def ord-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:label "Ordering API Capabilities"
       :id :ord-api
       :dispatch #{:ordering-morphism}}))

(def ord-check-caps
     "The IConnection check capability."
     (cap/capability-set
      {:label "Ordering Check Capabilities"
       :id :ord-check
       :dispatch #{:check}}))

(def ord-types
     "Type universe for the ordering morphism namespace."
     (ts/type-schema
      {:label "Ordering Type Registry"
       :types {:Keyword :keyword
               :OrderedSeq :any
               :OrderingMorphism :any
               :IReferenceable :any
               :RefinementMap :any
               :GapSeq :any}}))

;;; Registry

(def ord-registry
     "Morphisms connecting the ordering spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/ord-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :check-caps->check-ops
      (ex/existential-morphism
       {:id :check-caps->check-ops
        :from :capability-set/ord-check
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

(def ord-formalisms
     {:effect-signature/api ord-api-operations
      :effect-signature/check ord-check-operations
      :capability-set/ord-api ord-api-caps
      :capability-set/ord-check ord-check-caps
      :type-schema ord-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms ord-formalisms
      :registry   ord-registry})

;;; Gap report

(defn ord-gap-report
      "Runs the gap report on the ordering morphism namespace spec."
      []
      (gap/gap-report spec-system))
