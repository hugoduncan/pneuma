(ns pneuma.fills.combinators
    "Declarative fill-point helpers for common patterns.
  Produces functions suitable for reg-fill from configuration data.")

(defn from-path
      "Fill that reads from a nested map at the given path."
      [path]
      (fn [db & _args]
          (get-in db path)))

(defn from-session
      "Fill that reads a session field from db."
      [field-key]
      (fn [db session-id & _args]
          (get-in db [:sessions session-id field-key])))

(defn const-val
      "Fill that always returns the same value."
      [v]
      (fn [& _args] v))
