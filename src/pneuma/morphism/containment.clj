(ns pneuma.morphism.containment
    "Containment morphism — checks that declared references fit within a
  target set. Gap type: out-of-bounds.
  Implements IConnection."
    (:require [pneuma.protocol :as p]))

(defrecord ContainmentMorphism [id from to source-ref-kind target-ref-kind]
           p/IConnection
           (check [_ source target _rm]
                  (let [source-refs (p/extract-refs source source-ref-kind)
                        target-refs (p/extract-refs target target-ref-kind)
                        out-of-bounds (into #{} (remove target-refs) source-refs)]
                       (if (seq out-of-bounds)
                           [{:id id
                             :kind :containment
                             :status :diverges
                             :detail {:out-of-bounds out-of-bounds
                                      :source-ref-kind source-ref-kind
                                      :target-ref-kind target-ref-kind}}]
                           [{:id id
                             :kind :containment
                             :status :conforms}]))))

(defn containment-morphism
      "Creates a ContainmentMorphism. Every identifier extracted from the
  source formalism via source-ref-kind must be contained in the set extracted
  from the target formalism via target-ref-kind."
      [{:keys [id from to source-ref-kind target-ref-kind]}]
      (->ContainmentMorphism id from to source-ref-kind target-ref-kind))
