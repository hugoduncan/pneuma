(ns pneuma.code.containment
    "Code generation for ContainmentMorphism boundaries.
  Extends ContainmentMorphism with ICodeConnection via extend-protocol.
  Emits test assertions verifying that source references are contained
  within the target set."
    (:require [pneuma.code.protocol :as cp]
              [pneuma.protocol :as p])
    (:import [pneuma.morphism.containment ContainmentMorphism]))

(defn- emit-code-containment
       "Generates a code fragment map with bounds-checking test assertions."
       [{:keys [id from to source-ref-kind target-ref-kind]} source target _opts]
       (let [source-refs (p/extract-refs source source-ref-kind)
             target-refs (p/extract-refs target target-ref-kind)]
            {:morphism-id id
             :from from
             :to to
             :type :containment-test
             :assertions
             (mapv (fn [ref]
                       {:assertion :subset
                        :target-set target-refs
                        :value ref
                        :message (str "Reference " ref " from " (name from)
                                      " must be within bounds of " (name to))})
                   (sort source-refs))
             :metadata {:source-ref-kind source-ref-kind
                        :target-ref-kind target-ref-kind
                        :source-count (count source-refs)
                        :target-count (count target-refs)}}))

(extend-protocol cp/ICodeConnection
                 ContainmentMorphism
                 (->code-conn [this source target opts]
                              (emit-code-containment this source target opts)))
