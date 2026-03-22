(ns pneuma.morphism.containment-spec
    "Formalism specification for pneuma.morphism.containment.
  Models the namespace's public API (constructor) and its
  IConnection check contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def ct-api-operations
     "Public API of the containment morphism namespace."
     (es/effect-signature
      {:label "Containment API Operations"
       :operations
       {:containment-morphism
        {:input {:id :Keyword
                 :from :Keyword
                 :to :Keyword
                 :source-ref-kind :Keyword
                 :target-ref-kind :Keyword}
         :output :ContainmentMorphism}}}))

(def ct-check-operations
     "The IConnection check contract for ContainmentMorphism.
  Checks that all source refs are within target bounds."
     (es/effect-signature
      {:label "Containment Check Operations"
       :operations
       {:check
        {:input {:morphism :ContainmentMorphism
                 :source :IReferenceable
                 :target :IReferenceable
                 :refinement-map :RefinementMap}
         :output :GapSeq}}}))

(def ct-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:label "Containment API Capabilities"
       :id :ct-api
       :dispatch #{:containment-morphism}}))

(def ct-check-caps
     "The IConnection check capability."
     (cap/capability-set
      {:label "Containment Check Capabilities"
       :id :ct-check
       :dispatch #{:check}}))

(def ct-types
     "Type universe for the containment morphism namespace."
     (ts/type-schema
      {:label "Containment Type Registry"
       :types {:Keyword :keyword
               :ContainmentMorphism :any
               :IReferenceable :any
               :RefinementMap :any
               :GapSeq :any}}))

;;; Registry

(def ct-registry
     "Morphisms connecting the containment spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/ct-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :check-caps->check-ops
      (ex/existential-morphism
       {:id :check-caps->check-ops
        :from :capability-set/ct-check
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

(def ct-formalisms
     {:effect-signature/api ct-api-operations
      :effect-signature/check ct-check-operations
      :capability-set/ct-api ct-api-caps
      :capability-set/ct-check ct-check-caps
      :type-schema ct-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms ct-formalisms
      :registry   ct-registry})

;;; Gap report

(defn ct-gap-report
      "Runs the gap report on the containment morphism namespace spec."
      []
      (gap/gap-report spec-system))
