(ns pneuma.fills
    "Fill-point registry connecting generated code frames to human-written
  business logic. The registry maps qualified keywords to implementation
  functions. Supports both an explicit registry argument (for testing
  via nullable pattern) and a convenience global default."
    (:require [clojure.set :as set]))

;;; Registry operations

(defonce ^{:doc "Global fill-point registry. Use for convenience;
  pass an explicit registry atom for testing."}
 global-registry (atom {}))

(defn reg-fill
      "Register a fill-point implementation.
  Returns the registry atom."
      ([k f]
       (reg-fill global-registry k f))
      ([registry k f]
       (swap! registry assoc k f)
       registry))

(defn fill
      "Look up and invoke a fill point. Throws if unregistered."
      [registry k & args]
      (if-let [f (get @registry k)]
              (apply f args)
              (throw (ex-info (str "Unregistered fill point: " k
                                   "\nDeclare it in your fills namespace.")
                              {:fill-point k :args args}))))

(defn fill-or
      "Look up a fill point, returning default-val if unregistered."
      [registry k default-val & args]
      (if-let [f (get @registry k)]
              (apply f args)
              default-val))

;;; Fill-status checking

(defn fill-status
      "Compare a fill manifest against a registry's registered fills.
  Returns a map with :ok, :missing, :orphaned, and :arity-mismatch vectors."
      ([manifest]
       (fill-status global-registry manifest))
      ([registry manifest]
       (let [registered @registry
             manifest-keys (set (keys manifest))
             registered-keys (set (keys registered))
             common (set/intersection manifest-keys registered-keys)
             missing (into [] (sort (set/difference manifest-keys registered-keys)))
             orphaned (into [] (sort (set/difference registered-keys manifest-keys)))
             arity-mismatches
             (into []
                   (keep
                    (fn [k]
                        (let [expected-arity (count (:args (get manifest k)))
                              f (get registered k)
                        ;; Clojure fns don't expose arity directly,
                        ;; but we can check via metadata if provided
                              registered-arity (:pneuma/arity (meta f))]
                             (when (and registered-arity
                                        (not= registered-arity expected-arity))
                                   {:key k
                                    :registered-arity registered-arity
                                    :expected-arity expected-arity
                                    :expected-args (:args (get manifest k))}))))
                   common)
             ok (into [] (sort (set/difference common (set (map :key arity-mismatches)))))]
            {:ok ok
             :missing missing
             :orphaned orphaned
             :arity-mismatch arity-mismatches})))

(defn fill-gaps
      "Convert fill-status into gap entries for the :fill-gaps layer of
  the gap report."
      ([manifest]
       (fill-gaps global-registry manifest))
      ([registry manifest]
       (let [{:keys [ok missing orphaned arity-mismatch]} (fill-status registry manifest)]
            (vec
             (concat
              (mapv (fn [k] {:layer :fill :fill-point k :status :conforms})
                    ok)
              (mapv (fn [k] {:layer :fill :fill-point k :status :absent
                             :detail (select-keys (get manifest k) [:args :returns :doc])})
                    missing)
              (mapv (fn [k] {:layer :fill :fill-point k :status :orphaned})
                    orphaned)
              (mapv (fn [{:keys [key] :as m}]
                        {:layer :fill :fill-point key :status :diverges
                         :detail (select-keys m [:registered-arity :expected-arity :expected-args])})
                    arity-mismatch))))))

(defn make-registry
      "Create a fresh empty registry atom. Use for test isolation."
      []
      (atom {}))
