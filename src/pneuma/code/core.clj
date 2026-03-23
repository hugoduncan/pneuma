(ns pneuma.code.core
    "Public API for Pneuma code generation.
  Orchestrates per-formalism and per-morphism code emission, project
  structure generation, fill-status checking, and gap report
  integration."
    (:require [clojure.set :as set]
              [pneuma.code.path :as code-path]
              [pneuma.code.protocol :as cp]
              [pneuma.code.render :as render]
              [pneuma.fills :as fills]
              [pneuma.code.capability]
              [pneuma.code.containment]
              [pneuma.code.effect-signature]
              [pneuma.code.existential]
              [pneuma.code.mealy]
              [pneuma.code.optic]
              [pneuma.code.ordering]
              [pneuma.code.resolver]
              [pneuma.code.statechart]
              [pneuma.code.structural]))

;;; Per-formalism code generation

(defn emit-code
      "Generate code fragments for a single formalism.
  Returns a code fragment map with :namespace, :forms, :fill-manifest, etc."
      [formalism opts]
      (cp/->code formalism opts))

(defn emit-code-conn
      "Generate test assertion fragments for a single morphism.
  Returns a morphism code fragment with :assertions."
      [morphism source target opts]
      (cp/->code-conn morphism source target opts))

;;; Composed path test generation

(defn path-tests
      "Discover all cycles in the morphism graph and generate test
  assertion data for each. Delegates to pneuma.code.path/path-tests."
      [registry formalisms opts]
      (code-path/path-tests registry formalisms opts))

;;; Project-level emission

(defn emit-project
      "Compose code fragments from all formalisms and morphisms into a
  project-level code generation plan.
  Returns a map of {:sources, :tests, :manifest, :fills-skeleton}
  where each value is a map of namespace symbol → rendered string."
      [{:keys [formalisms morphisms registry opts]}]
      (let [formalism-codes (mapv (fn [[kind formalism]]
                                      (let [target-ns (get-in opts [:target-ns kind]
                                                              (symbol (str "generated." (name kind))))]
                                           [kind (emit-code formalism {:target-ns target-ns})]))
                                  formalisms)
            morphism-codes (mapv (fn [[_id morphism]]
                                     (let [source (get formalisms (:from morphism))
                                           target (get formalisms (:to morphism))]
                                          (when (and source target)
                                                [(:id morphism)
                                                 (emit-code-conn morphism source target opts)])))
                                 (or registry morphisms))
            all-manifests (into {} (mapcat (fn [[_kind code]] (:fill-manifest code)))
                                formalism-codes)
            sources (into {} (map (fn [[_kind code]]
                                      [(:namespace code) (render/render-source code)]))
                          formalism-codes)
            manifest-str (render/render-manifest all-manifests)
            fills-skeleton (into {} (map (fn [[_kind code]]
                                             (when (seq (:fill-manifest code))
                                                   [(:namespace code)
                                                    (render/render-fills-skeleton
                                                     (:namespace code)
                                                     (:fill-manifest code))])))
                                 formalism-codes)]
           {:sources sources
            :tests (into {} (keep identity) morphism-codes)
            :manifest all-manifests
            :manifest-str manifest-str
            :fills-skeleton (into {} (keep identity) fills-skeleton)}))

;;; Fill-status integration

(defn fill-status
      "Compare a fill manifest against registered fills.
  Delegates to pneuma.fills/fill-status."
      ([manifest]
       (fills/fill-status manifest))
      ([registry manifest]
       (fills/fill-status registry manifest)))

(defn fill-gaps
      "Convert fill-status into gap entries for the :fill-gaps layer.
  Delegates to pneuma.fills/fill-gaps."
      ([manifest]
       (fills/fill-gaps manifest))
      ([registry manifest]
       (fills/fill-gaps registry manifest)))

;;; Code diffing

(defn code-diff
      "Compare a newly generated code fragment against an existing namespace.
  Returns a map describing structural differences:
  :new-methods, :removed-methods, :new-fill-points,
  :removed-fill-points, :guard-changes."
      [new-code existing-code]
      (let [new-methods (set (keep #(when (= :defmethod (:type %))
                                          (:dispatch-val %))
                                   (:forms new-code)))
            old-methods (set (keep #(when (= :defmethod (:type %))
                                          (:dispatch-val %))
                                   (:forms existing-code)))
            new-fills (set (keys (:fill-manifest new-code)))
            old-fills (set (keys (:fill-manifest existing-code)))]
           {:new-methods (into [] (sort (set/difference new-methods old-methods)))
            :removed-methods (into [] (sort (set/difference old-methods new-methods)))
            :new-fill-points (into [] (sort (set/difference new-fills old-fills)))
            :removed-fill-points (into [] (sort (set/difference old-fills new-fills)))}))
