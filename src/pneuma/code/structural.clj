(ns pneuma.code.structural
    "Code generation for StructuralMorphism boundaries.
  Extends StructuralMorphism with ICodeConnection via extend-protocol.
  Emits test assertions verifying that source outputs conform to
  the target's schema."
    (:require [pneuma.code.protocol :as cp]
              [pneuma.morphism.structural]
              [pneuma.protocol :as p])
    (:import [pneuma.morphism.structural StructuralMorphism]))

(defn- emit-code-structural
       "Generates a code fragment map with schema conformance test assertions."
       [{:keys [id from to source-ref-kind]} source target _opts]
       (let [source-outputs (p/extract-refs source source-ref-kind)
             target-schema (p/->schema target)]
            {:morphism-id id
             :from from
             :to to
             :type :structural-test
             :assertions
             (mapv (fn [output]
                       {:assertion :validates
                        :schema target-schema
                        :value output
                        :message (str "Output " output " from " (name from)
                                      " must conform to " (name to) " schema")})
                   (sort source-outputs))
             :metadata {:source-ref-kind source-ref-kind
                        :source-count (count source-outputs)}}))

(extend-protocol cp/ICodeConnection
                 StructuralMorphism
                 (->code-conn [this source target opts]
                              (emit-code-structural this source target opts)))
