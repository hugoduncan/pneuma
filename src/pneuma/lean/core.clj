(ns pneuma.lean.core
    "Public API for Lean 4 proof emission.
  Provides unified entry points for emitting Lean source from
  formalisms, morphisms, and complete specifications. Requires all
  lean extension namespaces to ensure extend-protocol registrations
  are loaded."
    (:require [clojure.string :as str]
              [pneuma.gap.core :as gap]
              [pneuma.lean.blueprint :as bp]
              [pneuma.lean.circuit :as circuit]
              [pneuma.lean.composition-transitivity :as ct]
              [pneuma.lean.doc :as doc]
              [pneuma.lean.gap-completeness :as gc]
              [pneuma.lean.monitor-schema :as ms]
              [pneuma.lean.morphism-algebra :as ma]
              [pneuma.lean.path-semantic :as ps]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.ref-exhaustive :as re]
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
                    (let [have-steps
                          (map-indexed
                           (fn [i {:keys [morphism theorem-name]}]
                               (str "  -- Step " (inc i) ": "
                                    (morphism-kind-name morphism) " boundary for "
                                    (name (:id morphism)) "\n"
                                    "  have h" (inc i) " := " theorem-name "\n"))
                           step-emissions)
                          h-names (map-indexed (fn [i _] (str "h" (inc i)))
                                               step-emissions)]
                         (str (str/join "" have-steps)
                              "  exact ⟨" (str/join ", " h-names) "⟩\n"))
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

;;; Blueprint emission

(defn emit-lean-blueprint
      "Emits a LaTeX blueprint for a specification.
  Returns a string suitable for writing to blueprint/src/content.tex.
  The Blueprint tool (Patrick Massot) renders this to a browsable HTML
  dependency graph with color-coded proof status."
      [spec-name config]
      (bp/emit-blueprint spec-name config))

;;; New emission layers

(defn emit-lean-algebra
      "Emits morphism algebra proofs for a registry.
  Returns a string of Lean 4 source code."
      [registry formalisms]
      (ma/emit-morphism-algebra registry formalisms))

(defn emit-lean-circuits
      "Emits circuit verification proofs.
  Returns a string of Lean 4 source code."
      [registry]
      (circuit/emit-circuit-verification registry))

(defn emit-lean-gap-completeness
      "Emits gap report completeness proof.
  Returns a string of Lean 4 source code."
      [registry formalisms report]
      (gc/emit-gap-completeness registry formalisms report))

(defn emit-lean-monitor-consistency
      "Emits monitor-schema consistency proofs.
  Returns a string of Lean 4 source code."
      [formalisms]
      (ms/emit-monitor-schema-consistency formalisms))

(defn emit-lean-ref-exhaustive
      "Emits ref-extraction exhaustiveness proofs.
  Returns a string of Lean 4 source code."
      [formalisms registry]
      (re/emit-ref-exhaustiveness formalisms registry))

(defn emit-lean-composition-transitivity
      "Emits composition transitivity proofs per path.
  Returns a string of Lean 4 source code."
      [formalisms registry]
      (let [paths (path/find-paths registry)]
           (ct/emit-composition-transitivity paths formalisms)))

(defn emit-lean-path-semantic
      "Emits full path semantic composition proofs.
  Returns a string of Lean 4 source code."
      [formalisms registry]
      (let [paths (path/find-paths registry)]
           (ps/emit-path-semantic-composition paths formalisms)))

;;; Full emission

(defn emit-lean-all
      "Emits Lean 4 source for all formalisms, morphisms, paths,
  and verification layers.
  Returns a map with keys:
    :formalisms               {kind lean-src}
    :morphisms                {id lean-src}
    :paths                    [{:id path-id :lean-src string}]
    :system                   lean-src
    :blueprint                latex-src
    :algebra                  lean-src
    :circuits                 lean-src
    :gap-completeness         lean-src
    :monitor-consistency      lean-src
    :ref-exhaustive           lean-src
    :composition-transitivity lean-src
    :path-semantic            lean-src"
      [spec-name {:keys [formalisms registry] :as config}]
      (let [report (gap/gap-report config)
            paths (path/find-paths registry)]
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
            (sys/emit-system-lean spec-name config)
            :blueprint
            (bp/emit-blueprint spec-name config)
            :algebra
            (ma/emit-morphism-algebra registry formalisms)
            :circuits
            (circuit/emit-circuit-verification registry)
            :gap-completeness
            (gc/emit-gap-completeness registry formalisms report)
            :monitor-consistency
            (ms/emit-monitor-schema-consistency formalisms)
            :ref-exhaustive
            (re/emit-ref-exhaustiveness formalisms registry)
            :composition-transitivity
            (ct/emit-composition-transitivity paths formalisms)
            :path-semantic
            (ps/emit-path-semantic-composition paths formalisms)}))

(defn emit-lean-file
      "Composes multiple Lean emission strings into a single file
  with a header and imports."
      [spec-name sections]
      (str "-- Generated by Pneuma Lean emission\n"
           "-- Source: " spec-name "\n\n"
           (str/join "\n\n" (remove nil? sections))
           "\n"))
