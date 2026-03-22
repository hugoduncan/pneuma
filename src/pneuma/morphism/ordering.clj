(ns pneuma.morphism.ordering
    "Ordering morphism — checks that a source ref precedes a target ref
  within an ordered chain of identifiers. Gap type: order-violation.
  Implements IConnection."
    (:require [pneuma.protocol :as p]))

(defn- chain-index
       "Returns the index of item in chain, or nil if absent."
       [chain item]
       (let [idx (.indexOf ^java.util.List chain item)]
            (when (>= idx 0) idx)))

(defrecord OrderingMorphism [id from to source-ref-kind target-ref-kind chain]
           p/IConnection
           (check [_ source target _rm]
                  (let [source-ref (first (p/extract-refs source source-ref-kind))
                        target-ref (first (p/extract-refs target target-ref-kind))
                        source-idx (chain-index chain source-ref)
                        target-idx (chain-index chain target-ref)]
                       (if (and source-idx target-idx (< source-idx target-idx))
                           [{:id id
                             :kind :ordering
                             :status :conforms}]
                           [{:id id
                             :kind :ordering
                             :status :diverges
                             :detail {:gap-type :order-violation
                                      :source-ref source-ref
                                      :target-ref target-ref
                                      :source-idx source-idx
                                      :target-idx target-idx
                                      :chain chain}}]))))

(defn ordering-morphism
      "Creates an OrderingMorphism. The source ref extracted from source-ref-kind
  must appear in chain before the target ref extracted from target-ref-kind.
  If either ref is absent from the chain, the result is an order-violation."
      [{:keys [id from to source-ref-kind target-ref-kind chain]}]
      (->OrderingMorphism id from to source-ref-kind target-ref-kind chain))
