(ns pneuma.lean.core-spec
    "Formalism specification for pneuma.lean.core.
  Models the public lean orchestration API: emit-lean,
  emit-lean-conn, emit-lean-system, emit-lean-path,
  emit-lean-paths, emit-lean-all, emit-lean-file."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def lean-core-operations
     "Public API of the lean.core namespace."
     (es/effect-signature
      {:label "Lean Core Operations"
       :operations
       {:emit-lean
        {:input {:formalism :ILeanProjectable}
         :output :LeanSource}

        :emit-lean-conn
        {:input {:morphism :ILeanConnection
                 :source :Formalism
                 :target :Formalism}
         :output :LeanSource}

        :emit-lean-system
        {:input {:spec-name :String
                 :config :SystemConfig}
         :output :LeanSource}

        :emit-lean-path
        {:input {:composed-path :ComposedPath
                 :formalisms :FormalismMap}
         :output :LeanSource}

        :emit-lean-paths
        {:input {:formalisms :FormalismMap
                 :registry :Registry}
         :output :PathResultVec}

        :emit-lean-all
        {:input {:spec-name :String
                 :config :SystemConfig}
         :output :EmissionMap}

        :emit-lean-file
        {:input {:spec-name :String
                 :sections :SectionVec}
         :output :LeanSource}}}))

(def lean-core-caps
     "The namespace's public API functions."
     (cap/capability-set
      {:label "Lean Core Capabilities"
       :id :lean-core
       :dispatch #{:emit-lean :emit-lean-conn :emit-lean-system
                   :emit-lean-path :emit-lean-paths :emit-lean-all
                   :emit-lean-file}}))

(def lean-core-types
     "Type universe for the lean.core namespace."
     (ts/type-schema
      {:label "Lean Core Type Registry"
       :types {:ILeanProjectable :any
               :ILeanConnection :any
               :Formalism :any
               :LeanSource :string
               :String :string
               :SystemConfig :any
               :ComposedPath :any
               :FormalismMap :any
               :Registry :any
               :PathResultVec :any
               :EmissionMap :any
               :SectionVec :any}}))

;;; Registry

(def lean-core-registry
     "Morphisms connecting the lean.core spec formalisms."
     {:caps->ops
      (ex/existential-morphism
       {:id :caps->ops
        :from :capability-set/lean-core
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

(def lean-core-formalisms
     {:effect-signature/api lean-core-operations
      :capability-set/lean-core lean-core-caps
      :type-schema lean-core-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms lean-core-formalisms
      :registry   lean-core-registry})

;;; Gap report

(defn lean-core-gap-report
      "Runs the gap report on the lean.core spec."
      []
      (gap/gap-report spec-system))
