(ns pneuma.code.ci
    "CI integration for Pneuma code generation.
  Provides validation functions for fill-point completeness,
  morphism-derived test suite generation, and fill contract checking.
  Designed to be called from CI pipelines or bb tasks."
    (:require [clojure.string :as str]
              [malli.core :as m]
              [pneuma.code.path :as code-path]
              [pneuma.code.protocol :as cp]
              [pneuma.fills :as fills]))

;;; Fill-point validation

(defn validate-fills
      "Validate that all fill points in a manifest have registered
  implementations. Returns a map with :ok?, :missing, :orphaned,
  and :arity-mismatch.
  Missing fills are errors; orphaned fills are warnings."
      ([manifest]
       (validate-fills fills/global-registry manifest))
      ([registry manifest]
       (let [status (fills/fill-status registry manifest)]
            {:ok? (and (empty? (:missing status))
                       (empty? (:arity-mismatch status)))
             :missing (:missing status)
             :orphaned (:orphaned status)
             :arity-mismatch (:arity-mismatch status)
             :ok (:ok status)})))

(defn format-fill-report
      "Formats a fill validation result as a human-readable string."
      [result manifest]
      (let [lines (atom [])]
           (when (seq (:missing result))
                 (swap! lines conj "ERRORS — Missing fill points:")
                 (doseq [k (:missing result)]
                        (let [entry (get manifest k)]
                             (swap! lines conj (str "  " k))
                             (swap! lines conj (str "    Args:    " (:args entry)))
                             (swap! lines conj (str "    Returns: " (:returns entry)))
                             (when (:doc entry)
                                   (swap! lines conj (str "    Doc:     " (:doc entry)))))))
           (when (seq (:arity-mismatch result))
                 (swap! lines conj "ERRORS — Arity mismatches:")
                 (doseq [{:keys [key registered-arity expected-arity expected-args]}
                         (:arity-mismatch result)]
                        (swap! lines conj (str "  " key
                                               " registered=" registered-arity
                                               " expected=" expected-arity
                                               " args=" expected-args))))
           (when (seq (:orphaned result))
                 (swap! lines conj "WARNINGS — Orphaned fills (registered but not in manifest):")
                 (doseq [k (:orphaned result)]
                        (swap! lines conj (str "  " k))))
           (swap! lines conj (str "\nSummary: "
                                  (count (:ok result)) " ok, "
                                  (count (:missing result)) " missing, "
                                  (count (:orphaned result)) " orphaned, "
                                  (count (:arity-mismatch result)) " arity mismatches"))
           (str/join "\n" @lines)))

;;; Morphism-derived test suite generation

(defn generate-morphism-tests
      "Generates morphism test data including per-morphism boundary assertions
  and composed path cycle tests.
  Returns a map with :test-ns, :morphism-tests, :path-tests, and counts."
      [{:keys [registry formalisms test-ns opts]}]
      (let [morphism-tests
            (into []
                  (keep
                   (fn [[_id morphism]]
                       (let [source (get formalisms (:from morphism))
                             target (get formalisms (:to morphism))]
                            (when (and source target
                                       (satisfies? cp/ICodeConnection morphism))
                                  (cp/->code-conn morphism source target (or opts {}))))))
                  registry)
            path-test-data (code-path/path-tests registry formalisms (or opts {}))]
           {:test-ns (or test-ns 'generated.morphisms-test)
            :morphism-tests morphism-tests
            :path-tests path-test-data
            :morphism-count (count morphism-tests)
            :path-count (count path-test-data)}))

;;; Fill contract tests

(defn check-fill-contracts
      "Validates that registered fills match their manifest contracts.
  Checks arity (via metadata) and optionally return schema validation.
  Returns a vector of {:fill-point :status :detail} maps."
      ([manifest]
       (check-fill-contracts fills/global-registry manifest))
      ([registry manifest]
       (let [registered @registry]
            (into []
                  (keep
                   (fn [[k entry]]
                       (when-let [f (get registered k)]
                                 (let [expected-arity (count (:args entry))
                                       reg-arity (:pneuma/arity (meta f))
                                       return-schema (:returns entry)]
                                      (cond
                                       (and reg-arity (not= reg-arity expected-arity))
                                       {:fill-point k
                                        :status :arity-mismatch
                                        :detail {:registered reg-arity
                                                 :expected expected-arity}}

                                       (and return-schema
                                            (:pneuma/return-schema (meta f))
                                            (not (m/validate
                                                  (:pneuma/return-schema (meta f))
                                                  return-schema)))
                                       {:fill-point k
                                        :status :schema-mismatch
                                        :detail {:expected return-schema
                                                 :declared (:pneuma/return-schema (meta f))}}

                                       :else
                                       {:fill-point k :status :ok})))))
                  manifest))))
