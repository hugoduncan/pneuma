(ns pneuma.gap.core-spec
    "Formalism specification for pneuma.gap.core.
  Models the three-layer gap report structure: check-object-gaps,
  check-morphism-gaps, gap-report, failures, has-failures?."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def gap-api-operations
     "Public API of the gap.core namespace."
     (es/effect-signature
      {:label "Gap Core API Operations"
       :operations
       {:check-object-gaps
        {:input {:formalism :IProjectable}
         :output :GapVec}

        :check-morphism-gaps
        {:input {:registry :Registry
                 :formalisms-by-kind :FormalismMap}
         :output :GapVec}

        :gap-report
        {:input {:config :GapReportConfig}
         :output :GapReport}

        :failures
        {:input {:report :GapReport}
         :output :GapReport}

        :has-failures?
        {:input {:report :GapReport}
         :output :Boolean}}}))

(def gap-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:label "Gap Core API Capabilities"
       :id :gap-api
       :dispatch #{:check-object-gaps :check-morphism-gaps
                   :gap-report :failures :has-failures?}}))

(def gap-types
     "Type universe for the gap.core namespace."
     (ts/type-schema
      {:label "Gap Core Type Registry"
       :types {:IProjectable :any
               :GapVec :any
               :Registry :any
               :FormalismMap :any
               :GapReportConfig :any
               :GapReport :any
               :Boolean :boolean}}))

;;; Registry

(def gap-registry
     "Morphisms connecting the gap.core spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/gap-api
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

(def gap-formalisms
     {:effect-signature/api gap-api-operations
      :capability-set/gap-api gap-api-caps
      :type-schema gap-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms gap-formalisms
      :registry   gap-registry})

;;; Gap report

(defn gap-gap-report
      "Runs the gap report on the gap.core namespace spec."
      []
      (gap/gap-report spec-system))
