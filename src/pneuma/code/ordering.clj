(ns pneuma.code.ordering
    "Code generation for OrderingMorphism boundaries.
  Extends OrderingMorphism with ICodeConnection via extend-protocol.
  Emits test assertions verifying that source refs precede target refs
  in the declared ordering chain."
    (:require [pneuma.code.protocol :as cp]
              [pneuma.morphism.ordering]
              [pneuma.protocol :as p])
    (:import [pneuma.morphism.ordering OrderingMorphism]))

(defn- emit-code-ordering
       "Generates a code fragment map with ordering invariant test assertions."
       [{:keys [id from to source-ref-kind target-ref-kind chain]} source target _opts]
       (let [source-ref (first (p/extract-refs source source-ref-kind))
             target-ref (first (p/extract-refs target target-ref-kind))]
            {:morphism-id id
             :from from
             :to to
             :type :ordering-test
             :assertions
             (if (and source-ref target-ref)
                 [{:assertion :precedes
                   :chain chain
                   :source-ref source-ref
                   :target-ref target-ref
                   :message (str (name source-ref) " must precede "
                                 (name target-ref) " in chain")}]
                 [{:assertion :ref-missing
                   :source-ref source-ref
                   :target-ref target-ref
                   :message "Cannot check ordering: missing ref"}])
             :metadata {:source-ref-kind source-ref-kind
                        :target-ref-kind target-ref-kind
                        :chain chain}}))

(extend-protocol cp/ICodeConnection
                 OrderingMorphism
                 (->code-conn [this source target opts]
                              (emit-code-ordering this source target opts)))
