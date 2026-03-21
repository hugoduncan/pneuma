(ns pneuma.dogfood.protocol
    "Dogfood: pneuma.protocol described using pneuma's own formalisms.
  Defines the EffectSignature, CapabilitySets, and TypeSchema instances
  that model the three protocols (IProjectable, IConnection,
  IReferenceable), their method signatures, and the required
  implementations. Wires them into a gap report via the connection
  registry."
    (:require [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.structural :as st]
              [pneuma.gap.core :as gap]))

;;; Formalisms

(def protocol-operations
     "The six protocol methods as an algebraic effect signature.
  Each method is an operation with typed input fields and a typed
  output."
     (es/effect-signature
      {:operations
       {;; IProjectable
        :->schema
        {:input {:formalism :Formalism}
         :output :MalliSchema}

        :->monitor
        {:input {:formalism :Formalism}
         :output :MonitorFn}

        :->gen
        {:input {:formalism :Formalism}
         :output :Generator}

        :->gap-type
        {:input {:formalism :Formalism}
         :output :GapTypeDesc}

     ;; IConnection
        :check
        {:input {:morphism :Morphism
                 :source :Formalism
                 :target :Formalism
                 :rm :RefinementMap}
         :output :GapSequence}

     ;; IReferenceable
        :extract-refs
        {:input {:formalism :Formalism
                 :ref-kind :Keyword}
         :output :KeywordSet}}}))

(def formalism-record-caps
     "Every formalism record must implement these operations."
     (cap/capability-set
      {:id :formalism-record
       :dispatch #{:->schema :->monitor :->gen :->gap-type
                   :extract-refs}}))

(def morphism-record-caps
     "Every morphism kind record must implement these operations."
     (cap/capability-set
      {:id :morphism-record
       :dispatch #{:check}}))

(def protocol-types
     "The type universe for protocol method signatures."
     (ts/type-schema
      {:Formalism :any
       :Morphism :any
       :RefinementMap :any
       :MalliSchema :any
       :MonitorFn :any
       :Generator :any
       :GapTypeDesc :any
       :GapSequence :any
       :Keyword :keyword
       :KeywordSet [:set :keyword]}))

;;; Registry

(def protocol-registry
     "Morphisms connecting the protocol-layer formalisms."
     {:formalism-caps->ops
      (ex/existential-morphism
       {:id :formalism-caps->ops
        :from :capability-set/formalism
        :to :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :morphism-caps->ops
      (ex/existential-morphism
       {:id :morphism-caps->ops
        :from :capability-set/morphism
        :to :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})

      :ops->types
      (st/structural-morphism
       {:id :ops->types
        :from :effect-signature
        :to :type-schema
        :source-ref-kind :operation-outputs
        :target-ref-kind :type-ids})})

;;; Formalisms map (keyed by the :from/:to keywords in the registry)

(def protocol-formalisms
     "All formalisms in the protocol-layer dogfood graph."
     {:effect-signature protocol-operations
      :capability-set/formalism formalism-record-caps
      :capability-set/morphism morphism-record-caps
      :type-schema protocol-types})

;;; Gap report

(defn protocol-gap-report
      "Runs the full gap report on pneuma.protocol's formalism description.
  Returns the three-layer gap report map."
      []
      (gap/gap-report
       {:formalisms protocol-formalisms
        :registry protocol-registry}))
