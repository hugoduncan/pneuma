(ns pneuma.code.statechart
    "Code generation for Statechart formalisms.
  Extends Statechart with ICodeProjectable via extend-protocol.
  Emits a defmulti dispatching on event type, one defmethod per
  transition with guard preconditions, state transition logic, effect
  emission slots, and named fill points for business logic."
    (:require [clojure.set :as set]
              [pneuma.code.protocol :as cp])
    (:import [pneuma.formalism.statechart Statechart]))

(defn- kw->sym
       "Converts a keyword to a symbol, preserving namespace."
       [kw]
       (if (namespace kw)
           (symbol (namespace kw) (name kw))
           (symbol (name kw))))

(defn- transition-fill-points
       "Extracts fill-point declarations for a single transition."
       [t]
       (let [evt-name (name (:event t))]
            [{:key (keyword evt-name "update-db")
              :args '[db session-id event]
              :returns :db
              :doc (str "Additional db updates on " evt-name)
              :handler (:event t)}]))

(defn- transition->defmethod
       "Generates a defmethod form for a single transition."
       [{:keys [source event target guard raise] :as t} _opts]
       (let [evt-name (name event)
             fills (transition-fill-points t)
             guard-form (cond-> [(list '= (keyword source)
                                       (list 'state/conv-state 'db 'session-id))]
                                guard (conj (list 'fill (keyword evt-name "guard")
                                                  'db 'session-id)))
             effect-forms (when raise
                                (let [raises (if (keyword? raise) [raise] raise)]
                                     (mapv (fn [r]
                                               (list 'fx (kw->sym r)
                                                     {:session-id 'session-id}))
                                           raises)))]
            {:type :defmethod
             :dispatch-val event
             :source source
             :target target
             :guard guard-form
             :fills fills
             :effects effect-forms
             :comment (str "Transition: " (name source)
                           " → " (name target)
                           " on " (name event)
                           (when raise (str " raises: " raise)))}))

(defn- leaf-states
       "Returns states not appearing as hierarchy keys."
       [states hierarchy]
       (set/difference states (set (keys hierarchy))))

(defn- emit-code-statechart
       "Generates a code fragment map for a Statechart."
       [{:keys [label states hierarchy transitions]} opts]
       (let [target-ns (:target-ns opts)
             leaves (leaf-states states (or hierarchy {}))
             methods (mapv #(transition->defmethod % opts) transitions)
             all-fills (into [] (mapcat :fills) methods)
             manifest (into {} (map (fn [fp] [(:key fp) (dissoc fp :key)])) all-fills)]
            {:namespace target-ns
             :label label
             :requires [['state :as 'state]
                        ['effects :as 'fx]
                        ['pneuma.fills :refer ['fill 'fill-or]]]
             :forms (into [{:type :defmulti
                            :name 'handle-event
                            :dispatch '(fn [db event] (:type event))
                            :doc (str "Event handler for " label)}]
                          methods)
             :fill-manifest manifest
             :metadata {:formalism :statechart
                        :states (vec (sort leaves))
                        :transition-count (count transitions)}}))

(extend-protocol cp/ICodeProjectable
                 Statechart
                 (->code [this opts]
                         (emit-code-statechart this opts)))
