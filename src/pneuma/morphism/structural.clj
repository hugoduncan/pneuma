(ns pneuma.morphism.structural
    "Structural morphism — checks that output of one formalism conforms
  to the input schema of another. Gap type: shape-mismatch.
  Implements IConnection."
    (:require [malli.core :as m]
              [pneuma.protocol :as p]))

(defrecord StructuralMorphism [id from to source-ref-kind target-ref-kind]
           p/IConnection
           (check [_ source target _rm]
                  (let [source-outputs (p/extract-refs source source-ref-kind)
                        target-schema (p/->schema target)
                        mismatches (into []
                                         (keep (fn [output]
                                                   (when-not (m/validate target-schema output)
                                                             {:output output
                                                              :explanation (m/explain target-schema output)})))
                                         source-outputs)]
                       (if (seq mismatches)
                           [{:id id
                             :kind :structural
                             :status :diverges
                             :detail {:shape-mismatches mismatches
                                      :source-ref-kind source-ref-kind}}]
                           [{:id id
                             :kind :structural
                             :status :conforms}]))))

(defn structural-morphism
      "Creates a StructuralMorphism. Every value extracted from the source
  formalism via source-ref-kind must validate against the target
  formalism's schema."
      [{:keys [id from to source-ref-kind target-ref-kind]}]
      (->StructuralMorphism id from to source-ref-kind target-ref-kind))
