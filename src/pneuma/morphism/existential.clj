(ns pneuma.morphism.existential
    "Existential morphism — checks that identifiers referenced by one
  formalism exist in another. Gap type: dangling-ref.
  Implements IConnection."
    (:require [pneuma.protocol :as p]))

(defrecord ExistentialMorphism [id from to source-ref-kind target-ref-kind]
           p/IConnection
           (check [_ source target _rm]
                  (let [source-refs (p/extract-refs source source-ref-kind)
                        target-refs (p/extract-refs target target-ref-kind)
                        dangling (into #{} (remove target-refs) source-refs)]
                       (if (seq dangling)
                           [{:id id
                             :kind :existential
                             :status :diverges
                             :detail {:dangling-refs dangling
                                      :source-ref-kind source-ref-kind
                                      :target-ref-kind target-ref-kind}}]
                           [{:id id
                             :kind :existential
                             :status :conforms}]))))

(defn existential-morphism
      "Creates an ExistentialMorphism. Every identifier extracted from the
  source formalism via source-ref-kind must exist in the set extracted
  from the target formalism via target-ref-kind."
      [{:keys [id from to source-ref-kind target-ref-kind]}]
      (->ExistentialMorphism id from to source-ref-kind target-ref-kind))
