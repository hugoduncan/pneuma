(ns pneuma.morphism.existential-spec
    "Formalism specification for pneuma.morphism.existential.
  Models the namespace's public API (constructor) and its
  IConnection check contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def ex-api-operations
     "Public API of the existential morphism namespace."
     (es/effect-signature
      {:label "Existential API Operations"
       :operations
       {:existential-morphism
        {:input {:id :Keyword
                 :from :Keyword
                 :to :Keyword
                 :source-ref-kind :Keyword
                 :target-ref-kind :Keyword}
         :output :ExistentialMorphism}}}))

(def ex-check-operations
     "The IConnection check contract for ExistentialMorphism.
  Checks that all source refs exist in target refs."
     (es/effect-signature
      {:label "Existential Check Operations"
       :operations
       {:check
        {:input {:morphism :ExistentialMorphism
                 :source :IReferenceable
                 :target :IReferenceable
                 :refinement-map :RefinementMap}
         :output :GapSeq}}}))

(def ex-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:label "Existential API Capabilities"
       :id :ex-api
       :dispatch #{:existential-morphism}}))

(def ex-check-caps
     "The IConnection check capability."
     (cap/capability-set
      {:label "Existential Check Capabilities"
       :id :ex-check
       :dispatch #{:check}}))

(def ex-types
     "Type universe for the existential morphism namespace."
     (ts/type-schema
      {:label "Existential Type Registry"
       :types {:Keyword :keyword
               :ExistentialMorphism :any
               :IReferenceable :any
               :RefinementMap :any
               :GapSeq :any}}))

;;; Registry

(def ex-registry
     "Morphisms connecting the existential spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/ex-api
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :check-caps->check-ops
      (ex/existential-morphism
       {:id :check-caps->check-ops
        :from :capability-set/ex-check
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

(def ex-formalisms
     {:effect-signature/api ex-api-operations
      :effect-signature/check ex-check-operations
      :capability-set/ex-api ex-api-caps
      :capability-set/ex-check ex-check-caps
      :type-schema ex-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms ex-formalisms
      :registry   ex-registry})

;;; Gap report

(defn ex-gap-report
      "Runs the gap report on the existential morphism namespace spec."
      []
      (gap/gap-report spec-system))
