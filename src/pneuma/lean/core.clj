(ns pneuma.lean.core
    "Public API for Lean 4 proof emission.
  Provides unified entry points for emitting Lean source from
  formalisms, morphisms, and complete specifications. Requires all
  lean extension namespaces to ensure extend-protocol registrations
  are loaded."
    (:require [clojure.string :as str]
              [pneuma.lean.doc :as doc]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.system :as sys]
              [pneuma.path.core :as path]
              ;; Require all extension namespaces for side effects
              ;; (extend-protocol registrations)
              pneuma.lean.capability
              pneuma.lean.containment
              pneuma.lean.effect-signature
              pneuma.lean.existential
              pneuma.lean.mealy
              pneuma.lean.optic
              pneuma.lean.ordering
              pneuma.lean.resolver
              pneuma.lean.statechart
              pneuma.lean.structural
              pneuma.lean.type-schema))

(defn emit-lean
      "Emits Lean 4 source for a single formalism.
  Returns a string of Lean 4 source code with type definitions,
  property statements, and proof scaffolding."
      [formalism]
      (lp/->lean formalism))

(defn emit-lean-conn
      "Emits Lean 4 boundary propositions for a single morphism.
  Takes the morphism record and its source and target formalisms.
  Returns a string of Lean 4 source code."
      [morphism source target]
      (lp/->lean-conn morphism source target))

(defn emit-lean-system
      "Emits a unified Lean 4 file for a complete specification.
  Runs the gap report internally and uses results to generate proofs:
  conforming morphisms get `decide` proofs, failing ones get `sorry`.
  Takes a spec name string and a config map {:formalisms {...}
  :registry {...}}. Returns a string of Lean 4 source code."
      [spec-name config]
      (sys/emit-system-lean spec-name config))

;;; Path-level emission

(defn- morphism-kind-name
       "Extracts the morphism kind from its class name."
       [morphism]
       (-> (.getSimpleName (class morphism))
           (str/replace "Morphism" "")
           str/lower-case))

(defn- morphism-id->lean-prefix
       "Converts a morphism id keyword to a CamelCase Lean prefix."
       [id-kw]
       (let [raw (-> (if (namespace id-kw)
                         (str (namespace id-kw) "_" (name id-kw))
                         (name id-kw))
                     (str/replace "-" "_")
                     (str/replace ">" "_")
                     (str/replace "/" "_")
                     (str/replace "." "_"))]
            (->> (str/split raw #"_")
                 (mapv str/capitalize)
                 (str/join ""))))

(defn- step-theorem-name
       "Returns the Lean theorem name for a morphism step."
       [morphism]
       (let [prefix (morphism-id->lean-prefix (:id morphism))
             kind (morphism-kind-name morphism)]
            (str prefix "_" kind "_boundary")))

(defn emit-lean-path
      "Emits Lean 4 source for a composed path (cycle).
  Emits each step's boundary propositions, then a composition
  theorem conjoining all step boundaries. If any step contains
  `sorry`, the composition also uses `sorry`."
      [composed-path formalisms]
      (let [steps (:steps composed-path)
            step-emissions
            (into []
                  (keep (fn [morphism]
                            (let [source (get formalisms (:from morphism))
                                  target (get formalisms (:to morphism))]
                                 (when (and source target)
                                       {:morphism morphism
                                        :lean-src (lp/->lean-conn morphism source target)
                                        :theorem-name (step-theorem-name morphism)}))))
                  steps)
            all-proved? (not-any? #(str/includes? (:lean-src %) "sorry")
                                  step-emissions)
            path-name (name (:id composed-path))
            lean-path-name (-> path-name
                               (str/replace "-" "_")
                               (str/replace ">" "_"))
            formalism-chain (mapv #(name (:from %)) steps)
            chain-desc (str/join " → "
                                 (conj formalism-chain
                                       (name (:from (first steps)))))]
           (str "-- Composed path: " path-name "\n"
                "-- Cycle: " chain-desc "\n"
                "-- Steps: " (count steps) " morphisms\n\n"
                (str/join "\n" (mapv :lean-src step-emissions))
                "\n\n-- Composition theorem: all boundaries hold along the cycle\n"
                (doc/theorem-doc
                 (str "All boundaries hold along the cycle " chain-desc "."))
                "theorem " lean-path-name "_composition :\n"
                "    " (str/join " ∧\n    "
                                 (mapv (fn [{:keys [morphism]}]
                                           (let [prefix (morphism-id->lean-prefix
                                                         (:id morphism))
                                                 kind (morphism-kind-name morphism)]
                                                (case kind
                                                      "existential"
                                                      (str "(∀ s : " prefix "Source, ("
                                                           prefix "Embed s).isSome = true)")
                                                      "containment"
                                                      (str "(∀ s : " prefix "Source, "
                                                           prefix "InTarget s = true)")
                                                      "ordering"
                                                      (str "(" prefix "ChainIndex .source < "
                                                           prefix "ChainIndex .target)")
                                                      "structural"
                                                      (str "(" prefix "Validation)")
                                                  ;; fallback
                                                      (str "(" (step-theorem-name morphism)
                                                           ")"))))
                                       step-emissions))
                " := by\n"
                (if all-proved?
                    (str "  exact ⟨"
                         (str/join ", " (mapv :theorem-name step-emissions))
                         "⟩\n")
                    "  sorry -- some step boundaries are unproved\n"))))

(defn emit-lean-paths
      "Discovers all composed paths in the registry and emits Lean 4
  source for each. Returns a vector of {:id path-id :lean-src string}."
      [formalisms registry]
      (let [paths (path/find-paths registry)]
           (into []
                 (map (fn [p]
                          {:id (:id p)
                           :lean-src (emit-lean-path p formalisms)}))
                 paths)))

;;; Full emission

(defn emit-lean-all
      "Emits Lean 4 source for all formalisms, morphisms, and paths.
  Returns a map of {:formalisms {kind lean-src}
                    :morphisms  {id lean-src}
                    :paths      [{:id path-id :lean-src string}]
                    :system     lean-src}."
      [spec-name {:keys [formalisms registry] :as config}]
      {:formalisms
       (into {}
             (map (fn [[kind f]]
                      [kind (lp/->lean f)]))
             formalisms)
       :morphisms
       (into {}
             (map (fn [[id morphism]]
                      (let [source (get formalisms (:from morphism))
                            target (get formalisms (:to morphism))]
                           [id (when (and source target)
                                     (lp/->lean-conn morphism source target))])))
             registry)
       :paths
       (emit-lean-paths formalisms registry)
       :system
       (sys/emit-system-lean spec-name config)})

(defn emit-lean-file
      "Composes multiple Lean emission strings into a single file
  with a header and imports."
      [spec-name sections]
      (str "-- Generated by Pneuma Lean emission\n"
           "-- Source: " spec-name "\n\n"
           (str/join "\n\n" (remove nil? sections))
           "\n"))
