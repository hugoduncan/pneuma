(ns pneuma.morphism.registry-spec
    "Formalism specification for pneuma.morphism.registry.
  Models the namespace's public API (default-registry,
  morphisms-involving, morphisms-of-kind) and the registry
  data structure."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def reg-api-operations
     "Public API of the registry namespace."
     (es/effect-signature
      {:operations
       {:default-registry
        {:input {}
         :output :Registry}

        :morphisms-involving
        {:input {:registry :Registry
                 :formalism-kind :Keyword}
         :output :Registry}

        :morphisms-of-kind
        {:input {:registry :Registry
                 :kind :Keyword}
         :output :Registry}}}))

(def reg-api-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:id :reg-api
       :dispatch #{:default-registry :morphisms-involving :morphisms-of-kind}}))

(def reg-types
     "Type universe for the registry namespace."
     (ts/type-schema
      {:Registry :any
       :Keyword :keyword}))

;;; Registry

(def reg-registry
     "Morphisms connecting the registry spec formalisms."
     {:api-caps->api-ops
      (ex/existential-morphism
       {:id :api-caps->api-ops
        :from :capability-set/reg-api
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

(def reg-formalisms
     {:effect-signature/api reg-api-operations
      :capability-set/reg-api reg-api-caps
      :type-schema reg-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms reg-formalisms
      :registry   reg-registry})

;;; Gap report

(defn reg-gap-report
      "Runs the gap report on the registry namespace spec."
      []
      (gap/gap-report spec-system))
