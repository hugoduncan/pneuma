(ns pneuma.code.existential
    "Code generation for ExistentialMorphism boundaries.
  Extends ExistentialMorphism with ICodeConnection via extend-protocol.
  Emits test assertions verifying that every source reference exists
  in the target set."
    (:require [pneuma.code.protocol :as cp]
              [pneuma.protocol :as p])
    (:import [pneuma.morphism.existential ExistentialMorphism]))

(defn- emit-code-existential
       "Generates a code fragment map with referential integrity test assertions."
       [{:keys [id from to source-ref-kind target-ref-kind]} source target _opts]
       (let [source-refs (p/extract-refs source source-ref-kind)
             target-refs (p/extract-refs target target-ref-kind)]
            {:morphism-id id
             :from from
             :to to
             :type :existential-test
             :assertions
             (mapv (fn [ref]
                       {:assertion :contains
                        :target-set target-refs
                        :value ref
                        :message (str "Reference " ref " from " (name from)
                                      " must exist in " (name to))})
                   (sort source-refs))
             :metadata {:source-ref-kind source-ref-kind
                        :target-ref-kind target-ref-kind
                        :source-count (count source-refs)
                        :target-count (count target-refs)}}))

(extend-protocol cp/ICodeConnection
                 ExistentialMorphism
                 (->code-conn [this source target opts]
                              (emit-code-existential this source target opts)))
