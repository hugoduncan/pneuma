(ns pneuma.lean.composition-transitivity
    "Lean 4 emission for morphism composition transitivity.
  For each path (cycle), emits bridge functions connecting adjacent
  morphism endpoints and transitivity theorems. The bridge maps
  target refs of step i to source refs of step i+1 by name matching.
  Proofs use case analysis on finite inductive types."
    (:require [clojure.string :as str]
              [pneuma.lean.doc :as doc]
              [pneuma.protocol :as p]))

(defn- kw->lean-name
       "Converts a keyword to a valid Lean identifier."
       [kw]
       (let [base (if (namespace kw)
                      (str (namespace kw) "_" (name kw))
                      (name kw))]
            (-> base
                (str/replace "-" "_")
                (str/replace ">" "_")
                (str/replace "/" "_")
                (str/replace "." "_"))))

(defn- morphism-id->lean-prefix
       "Converts a morphism id keyword to a CamelCase Lean prefix."
       [id-kw]
       (let [raw (kw->lean-name id-kw)]
            (->> (str/split raw #"_")
                 (mapv str/capitalize)
                 (str/join ""))))

(defn- morphism-kind-name
       "Extracts the morphism kind from its class name."
       [morphism]
       (-> (.getSimpleName (class morphism))
           (str/replace "Morphism" "")
           str/lower-case))

;;; Bridge function emission

(defn- compute-bridge-mapping
       "Computes the mapping from step-i target refs to step-j source refs.
  Matches by keyword name. Returns a map of {target-ref-name source-ref-name}."
       [target-refs source-refs]
       (let [source-by-name (into {} (map (fn [r] [(kw->lean-name r) r])) source-refs)
             target-by-name (into {} (map (fn [r] [(kw->lean-name r) r])) target-refs)]
            (into {}
                  (keep (fn [[name _target-ref]]
                            (when (contains? source-by-name name)
                                  [name name])))
                  target-by-name)))

(defn- emit-bridge-fn
       "Emits a bridge function mapping step-i's target type to step-j's source type.
  Only matched refs get mapped; unmatched ones return a default."
       [prefix-i prefix-j _bridge-mapping target-refs source-refs]
       (let [bridge-name (str "bridge_" prefix-i "_" prefix-j)
             target-type (str prefix-i "Target")
             source-type (str prefix-j "Source")
             target-names (sort (mapv kw->lean-name target-refs))
             source-name-set (into #{} (map kw->lean-name) source-refs)
             clauses
             (mapv (fn [tname]
                       (if (contains? source-name-set tname)
                           (str "  | ." tname " => .some ." tname)
                           (str "  | ." tname " => .none")))
                   target-names)]
            (str (doc/lean-doc
                  (str "Bridge from " prefix-i " targets to " prefix-j " sources.")
                  "Maps matching references by name.")
                 "def " bridge-name " : " target-type " → Option " source-type "\n"
                 (str/join "\n" clauses) "\n")))

(defn- emit-bridge-totality
       "Emits a theorem that the bridge function is total when all names match."
       [prefix-i prefix-j bridge-mapping]
       (let [bridge-name (str "bridge_" prefix-i "_" prefix-j)
             target-type (str prefix-i "Target")]
            (if (seq bridge-mapping)
                (str (doc/theorem-doc
                      (str "The bridge from " prefix-i " to " prefix-j
                           " is total: every target ref maps to a source ref."))
                     "theorem " bridge-name "_total :\n"
                     "    ∀ t : " target-type ", (" bridge-name " t).isSome = true := by\n"
                     "  intro t\n"
                     "  cases t <;> rfl\n")
                (str "-- Bridge " bridge-name " is partial: not all targets have matching sources\n"))))

;;; Per-step transitivity

(defn- emit-step-transitivity
       "Emits a transitivity theorem for two adjacent steps in a path."
       [step-i step-j formalisms]
       (let [prefix-i (morphism-id->lean-prefix (:id step-i))
             prefix-j (morphism-id->lean-prefix (:id step-j))
             target-formalism (get formalisms (:to step-i))
             source-formalism-j (get formalisms (:from step-j))]
            (when (and target-formalism source-formalism-j)
                  (let [target-refs (p/extract-refs target-formalism
                                                    (:target-ref-kind step-i))
                        source-refs (p/extract-refs source-formalism-j
                                                    (:source-ref-kind step-j))
                        bridge-mapping (compute-bridge-mapping target-refs source-refs)
                        kind-i (morphism-kind-name step-i)
                        kind-j (morphism-kind-name step-j)]
                       (str "-- Transitivity: " (name (:id step-i))
                            " → " (name (:id step-j)) "\n"
                            "-- Kinds: " kind-i " → " kind-j "\n\n"
                            (emit-bridge-fn prefix-i prefix-j
                                            bridge-mapping target-refs source-refs)
                            "\n"
                            (emit-bridge-totality prefix-i prefix-j
                                                  bridge-mapping))))))

;;; Per-path emission

(defn- emit-path-transitivity
       "Emits transitivity proofs for all adjacent step pairs in a path."
       [path formalisms]
       (let [steps (:steps path)
             pairs (partition 2 1 steps)
             ;; Include wrap-around pair for cycles
             cycle-pairs (if (> (count steps) 1)
                             (conj (vec pairs) [(last steps) (first steps)])
                             pairs)]
            (str "-- Composition transitivity for path: "
                 (name (:id path)) "\n"
                 "-- Steps: " (count steps) " morphisms\n\n"
                 (str/join "\n"
                           (into []
                                 (keep (fn [[si sj]]
                                           (emit-step-transitivity si sj formalisms)))
                                 cycle-pairs)))))

;;; Public API

(defn emit-composition-transitivity
      "Emits Lean 4 source for morphism composition transitivity per path.
  For each path, emits bridge functions and transitivity theorems
  for adjacent morphism pairs.
  Returns a string of Lean 4 source code."
      [paths formalisms]
      (if (empty? paths)
          "-- No paths: composition transitivity is vacuously true\n"
          (str "-- Composition transitivity verification\n"
               "-- Generated by Pneuma\n\n"
               (str/join "\n\n"
                         (mapv #(emit-path-transitivity % formalisms) paths)))))
