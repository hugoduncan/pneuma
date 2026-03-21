(ns pneuma.morphism.registry
    "Connection registry — the formalism graph as data.
  Maps morphism ids to descriptors. Each descriptor specifies which
  formalisms are connected, what kind of morphism links them, and
  which ref-kinds to use for extraction."
    (:require [pneuma.morphism.existential :as ex]))

(def default-registry
     "The default connection registry. Maps morphism id keywords to
  morphism records.
  Currently contains only the existential morphism for the dogfood
  case. The structural morphism (return-type checking) requires a
  dedicated return-type schema formalism as target — not yet built."
     {:caps->protocol/operations
      (ex/existential-morphism
       {:id :caps->protocol/operations
        :from :capability-set
        :to :effect-signature
        :source-ref-kind :dispatch-refs
        :target-ref-kind :operation-ids})})

(defn morphisms-involving
      "Returns all morphisms in the registry that involve the given
  formalism kind keyword as source or target."
      [registry formalism-kind]
      (into {}
            (filter (fn [[_ m]]
                        (or (= formalism-kind (:from m))
                            (= formalism-kind (:to m)))))
            registry))

(defn morphisms-of-kind
      "Returns all morphisms in the registry of the given kind keyword.
  The kind is stored in the :kind field of each morphism record."
      [registry kind]
      (into {}
            (filter (fn [[_ m]] (= kind (:kind m))))
            registry))
