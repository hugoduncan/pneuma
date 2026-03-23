(ns pneuma.code.mealy
    "Code generation for MealyHandlerSet formalisms.
  Extends MealyHandlerSet with ICodeProjectable via extend-protocol.
  Emits handler function stubs with guard preconditions, state update
  declarations, effect emissions, and fill points for business logic."
    (:require [pneuma.code.protocol :as cp])
    (:import [pneuma.formalism.mealy MealyHandlerSet]))

(defn- handler-fill-points
       "Extracts fill-point declarations for a single handler."
       [handler-id {:keys [params effects]}]
       (let [prefix (name handler-id)
             param-args (mapv (comp symbol name :name) (or params []))]
            (into
             [{:key (keyword prefix "update-db")
               :args (into '[db] param-args)
               :returns :db
               :doc (str "State update logic for " prefix)
               :handler handler-id}]
             (when (seq effects)
                   [{:key (keyword prefix "effects")
                     :args (into '[db] param-args)
                     :returns :effects
                     :doc (str "Effect results for " prefix)
                     :handler handler-id}]))))

(defn- handler->form
       "Generates a handler form for a single declaration."
       [handler-id declaration]
       (let [{:keys [guards updates effects params]} declaration
             fills (handler-fill-points handler-id declaration)]
            {:type :defmethod
             :dispatch-val handler-id
             :guards (or guards [])
             :updates (or updates [])
             :effects (or effects [])
             :params (or params [])
             :fills fills
             :comment (str "Handler: " (name handler-id)
                           (when (seq guards)
                                 (str " guards: " (mapv :check guards)))
                           (when (seq effects)
                                 (str " effects: " (mapv :op effects))))}))

(defn- emit-code-mealy
       "Generates a code fragment map for a MealyHandlerSet."
       [{:keys [label declarations]} opts]
       (let [target-ns (:target-ns opts)
             methods (mapv (fn [[hid decl]] (handler->form hid decl))
                           (sort-by key declarations))
             all-fills (into [] (mapcat :fills) methods)
             manifest (into {} (map (fn [fp] [(:key fp) (dissoc fp :key)])) all-fills)]
            {:namespace target-ns
             :label label
             :requires [['pneuma.fills :refer ['fill 'fill-or]]]
             :forms (into [{:type :defmulti
                            :name 'handle-event
                            :dispatch '(fn [db event] (:type event))
                            :doc (str "Mealy handler dispatch for " label)}]
                          methods)
             :fill-manifest manifest
             :metadata {:formalism :mealy
                        :handler-count (count declarations)}}))

(extend-protocol cp/ICodeProjectable
                 MealyHandlerSet
                 (->code [this opts]
                         (emit-code-mealy this opts)))
