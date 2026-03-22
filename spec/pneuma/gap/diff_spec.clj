(ns pneuma.gap.diff-spec
    "Formalism specification for pneuma.gap.diff.
  Models the diff-reports, has-changes?, and gaps-involving
  operations."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def diff-api-operations
     "Public API of the gap.diff namespace."
     (es/effect-signature
      {:operations
       {:diff-reports
        {:input {:old-report :GapReport
                 :new-report :GapReport}
         :output :DiffReport}

        :has-changes?
        {:input {:diff :DiffReport}
         :output :Boolean}

        :gaps-involving
        {:input {:report :GapReport
                 :formalism-kind :Keyword}
         :output :GapReport}}}))

(def diff-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:id :diff-api
       :dispatch #{:diff-reports :has-changes? :gaps-involving}}))

(def diff-types
     "Type universe for the gap.diff namespace."
     (ts/type-schema
      {:GapReport :any
       :DiffReport :any
       :Boolean :boolean
       :Keyword :keyword}))

;;; Registry

(def diff-registry
     "Morphisms connecting the gap.diff spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/diff-api
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

(def diff-formalisms
     {:effect-signature/api diff-api-operations
      :capability-set/diff-api diff-api-caps
      :type-schema diff-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms diff-formalisms
      :registry   diff-registry})

;;; Gap report

(defn diff-gap-report
      "Runs the gap report on the gap.diff namespace spec."
      []
      (gap/gap-report spec-system))
