(ns pneuma.core-spec
    "Formalism specification for pneuma.core.
  Models the public API surface: formalism constructors,
  morphism constructors, gap report, diffing, path discovery,
  and per-formalism checking convenience functions."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def core-constructor-operations
     "Formalism and morphism constructor re-exports."
     (es/effect-signature
      {:label "Constructor Operations"
       :operations
       {:effect-signature
        {:input {:config :FormalismConfig}
         :output :EffectSignature}

        :capability-set
        {:input {:config :FormalismConfig}
         :output :CapabilitySet}

        :statechart
        {:input {:config :FormalismConfig}
         :output :Statechart}

        :mealy-handler-set
        {:input {:config :FormalismConfig}
         :output :MealyHandlerSet}

        :optic-declaration
        {:input {:config :FormalismConfig}
         :output :OpticDeclaration}

        :resolver-graph
        {:input {:config :FormalismConfig}
         :output :ResolverGraph}

        :type-schema
        {:input {:config :FormalismConfig}
         :output :TypeSchema}

        :existential-morphism
        {:input {:config :MorphismConfig}
         :output :ExistentialMorphism}

        :structural-morphism
        {:input {:config :MorphismConfig}
         :output :StructuralMorphism}

        :containment-morphism
        {:input {:config :MorphismConfig}
         :output :ContainmentMorphism}

        :ordering-morphism
        {:input {:config :MorphismConfig}
         :output :OrderingMorphism}

        :refinement-map
        {:input {:config :RefinementConfig}
         :output :RefinementMap}}}))

(def core-gap-operations
     "Gap report and diffing operations."
     (es/effect-signature
      {:label "Gap Operations"
       :operations
       {:gap-report
        {:input {:config :GapReportConfig}
         :output :GapReport}

        :failures
        {:input {:report :GapReport}
         :output :GapReport}

        :has-failures?
        {:input {:report :GapReport}
         :output :Boolean}

        :diff-reports
        {:input {:old-report :GapReport
                 :new-report :GapReport}
         :output :DiffReport}

        :has-changes?
        {:input {:diff :DiffReport}
         :output :Boolean}

        :gaps-involving
        {:input {:report :GapReport
                 :formalism-kind :Keyword}
         :output :GapReport}

        :find-paths
        {:input {:registry :Registry}
         :output :ComposedPathVec}}}))

(def core-check-operations
     "Per-formalism checking convenience functions."
     (es/effect-signature
      {:label "Check Operations"
       :operations
       {:check-schema
        {:input {:formalism :IProjectable
                 :state :Any}
         :output :Verdict}

        :check-trace
        {:input {:formalism :IProjectable
                 :event-log :EventLog}
         :output :Verdict}

        :check-gen
        {:input {:formalism :IProjectable
                 :opts :GenOpts}
         :output :Verdict}

        :check-morphism
        {:input {:morphism :IConnection
                 :source :Formalism
                 :target :Formalism}
         :output :GapVec}}}))

(def core-constructor-caps
     (cap/capability-set
      {:label "Core Constructor Capabilities"
       :id :core-constructors
       :dispatch #{:effect-signature :capability-set :statechart
                   :mealy-handler-set :optic-declaration :resolver-graph
                   :type-schema :existential-morphism :structural-morphism
                   :containment-morphism :ordering-morphism :refinement-map}}))

(def core-gap-caps
     (cap/capability-set
      {:label "Core Gap Capabilities"
       :id :core-gap
       :dispatch #{:gap-report :failures :has-failures?
                   :diff-reports :has-changes? :gaps-involving
                   :find-paths}}))

(def core-check-caps
     (cap/capability-set
      {:label "Core Check Capabilities"
       :id :core-check
       :dispatch #{:check-schema :check-trace :check-gen :check-morphism}}))

(def core-types
     "Type universe for the core namespace."
     (ts/type-schema
      {:label "Core Type Registry"
       :types {:FormalismConfig :any
               :MorphismConfig :any
               :RefinementConfig :any
               :EffectSignature :any
               :CapabilitySet :any
               :Statechart :any
               :MealyHandlerSet :any
               :OpticDeclaration :any
               :ResolverGraph :any
               :TypeSchema :any
               :ExistentialMorphism :any
               :StructuralMorphism :any
               :ContainmentMorphism :any
               :OrderingMorphism :any
               :RefinementMap :any
               :GapReportConfig :any
               :GapReport :any
               :DiffReport :any
               :Boolean :boolean
               :Keyword :keyword
               :Registry :any
               :ComposedPathVec :any
               :IProjectable :any
               :IConnection :any
               :Formalism :any
               :Any :any
               :EventLog :any
               :GenOpts :any
               :Verdict :any
               :GapVec :any}}))

;;; Registry

(def core-registry
     "Morphisms connecting the core spec formalisms."
     {:constructor-caps->constructor-ops
      (ex/existential-morphism
       {:id :constructor-caps->constructor-ops
        :from :capability-set/core-constructors
        :to :effect-signature/constructors
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :gap-caps->gap-ops
      (ex/existential-morphism
       {:id :gap-caps->gap-ops
        :from :capability-set/core-gap
        :to :effect-signature/gap
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :check-caps->check-ops
      (ex/existential-morphism
       {:id :check-caps->check-ops
        :from :capability-set/core-check
        :to :effect-signature/check
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :constructor-ops->types
      (st/structural-morphism
       {:id :constructor-ops->types
        :from :effect-signature/constructors
        :to :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})

      :gap-ops->types
      (st/structural-morphism
       {:id :gap-ops->types
        :from :effect-signature/gap
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

(def core-formalisms
     {:effect-signature/constructors core-constructor-operations
      :effect-signature/gap core-gap-operations
      :effect-signature/check core-check-operations
      :capability-set/core-constructors core-constructor-caps
      :capability-set/core-gap core-gap-caps
      :capability-set/core-check core-check-caps
      :type-schema core-types})

;;; Spec system

(def spec-system
     "Complete specification system for use with gap-report and render-doc."
     {:formalisms core-formalisms
      :registry   core-registry})

;;; Gap report

(defn core-gap-report
      "Runs the gap report on the core namespace spec."
      []
      (gap/gap-report spec-system))
