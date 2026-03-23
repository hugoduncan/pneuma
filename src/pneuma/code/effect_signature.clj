(ns pneuma.code.effect-signature
    "Code generation for EffectSignature formalisms.
  Extends EffectSignature with ICodeProjectable via extend-protocol.
  Emits a defmulti dispatching on effect type, one defmethod per
  operation with destructured arguments and schema validation, plus
  a fill point for the actual execution logic."
    (:require [pneuma.code.protocol :as cp])
    (:import [pneuma.formalism.effect_signature EffectSignature]))

(defn- operation-fill-points
       "Extracts fill-point declarations for a single operation."
       [op-kw {:keys [input output]}]
       [{:key (keyword (name op-kw) "execute")
         :args (into '[session-id] (mapv (comp symbol name) (keys input)))
         :returns output
         :doc (str "Execute " (name op-kw) " effect")
         :handler op-kw}])

(defn- operation->defmethod
       "Generates a defmethod form for a single operation."
       [op-kw {:keys [input output] :as op-def}]
       (let [fills (operation-fill-points op-kw op-def)
             field-syms (mapv (comp symbol name) (keys input))]
            {:type :defmethod
             :dispatch-val op-kw
             :destructure field-syms
             :fills fills
             :comment (str "Executor for " (name op-kw)
                           " → " (name output))}))

(defn- emit-code-effect-signature
       "Generates a code fragment map for an EffectSignature."
       [{:keys [label operations]} opts]
       (let [target-ns (:target-ns opts)
             methods (mapv (fn [[op-kw op-def]]
                               (operation->defmethod op-kw op-def))
                           (sort-by key operations))
             all-fills (into [] (mapcat :fills) methods)
             manifest (into {} (map (fn [fp] [(:key fp) (dissoc fp :key)])) all-fills)]
            {:namespace target-ns
             :label label
             :requires [['pneuma.fills :refer ['fill 'fill-or]]]
             :forms (into [{:type :defmulti
                            :name 'execute-effect
                            :dispatch '(fn [effect] (:op effect))
                            :doc (str "Effect executor for " label)}]
                          methods)
             :fill-manifest manifest
             :metadata {:formalism :effect-signature
                        :operation-count (count operations)}}))

(extend-protocol cp/ICodeProjectable
                 EffectSignature
                 (->code [this opts]
                         (emit-code-effect-signature this opts)))
