(ns pneuma.lean.ordering-spec
    "Formalism specification for pneuma.lean.ordering.
  Models the ILeanConnection ->lean-conn emission contract."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-ord-operations
     "The ILeanConnection ->lean-conn contract for OrderingMorphism."
     (es/effect-signature
      {:label "Lean Ordering Operations"
       :operations
       {:->lean-conn
        {:input {:morphism :OrderingMorphism
                 :source :IReferenceable
                 :target :IReferenceable}
         :output :LeanSource}}}))

(def lean-ord-caps
     (cap/capability-set
      {:label "Lean Ordering Capabilities"
       :id :lean-ord
       :dispatch #{:->lean-conn}}))

(def lean-ord-types
     (ts/type-schema
      {:label "Lean Ordering Type Registry"
       :types {:OrderingMorphism :any
               :IReferenceable :any
               :LeanSource :string}}))

;;; Registry

(def lean-ord-registry
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-ord
        :to :effect-signature/api
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :ops->types
      (st/structural-morphism
       {:id :ops->types
        :from :effect-signature/api
        :to :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})})

;;; Formalisms map

(def lean-ord-formalisms
     {:effect-signature/api lean-ord-operations
      :capability-set/lean-ord lean-ord-caps
      :type-schema lean-ord-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms lean-ord-formalisms
      :registry   lean-ord-registry})

;;; Gap report

(defn lean-ord-gap-report
      "Runs the gap report on the lean.ordering spec."
      []
      (gap/gap-report spec-system))
