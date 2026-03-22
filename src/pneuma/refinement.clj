(ns pneuma.refinement
    "RefinementMap — bridges mathematical formalism objects to the
  running implementation's concrete state. Holds var references and
  accessor functions that are dereferenced at check time to get
  current values.")

(defrecord RefinementMap [atom-ref event-log-ref accessors source-nss])

(defn refinement-map
      "Creates a RefinementMap from a config map.

  Keys:
    :atom-ref      - var referencing the application state atom
    :event-log-ref - var referencing the event log (or nil)
    :accessors     - map of keyword → (fn [db & args] ...) for
                     extracting formalism-relevant state
    :source-nss    - vector of namespace symbols containing the
                     implementation code to check"
      [{:keys [atom-ref event-log-ref accessors source-nss]
        :or {accessors {} source-nss []}}]
      (->RefinementMap atom-ref event-log-ref accessors source-nss))

(defn deref-state
      "Dereferences the atom-ref in the refinement map.
  Returns the current application state."
      [rm]
      (some-> (:atom-ref rm) deref deref))

(defn deref-event-log
      "Dereferences the event-log-ref in the refinement map.
  Returns the current event log, or nil."
      [rm]
      (some-> (:event-log-ref rm) deref deref))

(defn access
      "Applies a named accessor from the refinement map to the
  current state. Returns the extracted value."
      [rm accessor-key & args]
      (when-let [f (get (:accessors rm) accessor-key)]
                (apply f (deref-state rm) args)))
