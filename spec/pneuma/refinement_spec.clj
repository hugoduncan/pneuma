(ns pneuma.refinement-spec
    "Formalism specification for pneuma.refinement.
  Models the RefinementMap constructor and its accessor
  functions: deref-state, deref-event-log, access."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def rm-api-operations
     "Public API of the refinement namespace."
     (es/effect-signature
      {:label "Refinement Map API Operations"
       :operations
       {:refinement-map
        {:input {:atom-ref :VarRef
                 :event-log-ref :VarRef
                 :accessors :AccessorMap
                 :source-nss :NsVec}
         :output :RefinementMap}

        :deref-state
        {:input {:rm :RefinementMap}
         :output :Any}

        :deref-event-log
        {:input {:rm :RefinementMap}
         :output :Any}

        :access
        {:input {:rm :RefinementMap
                 :accessor-key :Keyword}
         :output :Any}}}))

(def rm-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:label "Refinement Map API Capabilities"
       :id :rm-api
       :dispatch #{:refinement-map :deref-state :deref-event-log :access}}))

(def rm-types
     "Type universe for the refinement namespace."
     (ts/type-schema
      {:label "Refinement Map Type Registry"
       :types {:VarRef :any
               :AccessorMap :any
               :NsVec :any
               :RefinementMap :any
               :Keyword :keyword
               :Any :any}}))

;;; Registry

(def rm-registry
     "Morphisms connecting the refinement spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/rm-api
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

(def rm-formalisms
     {:effect-signature/api rm-api-operations
      :capability-set/rm-api rm-api-caps
      :type-schema rm-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms rm-formalisms
      :registry   rm-registry})

;;; Gap report

(defn rm-gap-report
      "Runs the gap report on the refinement namespace spec."
      []
      (gap/gap-report spec-system))
