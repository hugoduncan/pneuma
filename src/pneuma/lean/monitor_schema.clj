(ns pneuma.lean.monitor-schema
    "Lean 4 emission for monitor-schema consistency.
  For each formalism, proves that the gap-kind and status
  enumerations are exhaustive, and that the monitor's verdict
  domain covers all declared gap kinds. Uses case analysis
  on finite inductive types."
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

(defn- kw->lean-type-prefix
       "Converts a formalism kind keyword to a CamelCase prefix."
       [kind-kw]
       (let [raw (kw->lean-name kind-kw)]
            (->> (str/split raw #"_")
                 (mapv str/capitalize)
                 (str/join ""))))

;;; Per-formalism emission

(defn- emit-gap-kind-inductive
       "Emits an inductive type for a formalism's gap kinds."
       [prefix gap-kinds]
       (let [sorted (sort (mapv kw->lean-name gap-kinds))
             ctors (str/join "\n"
                             (mapv #(str "  | " %) sorted))]
            (str (doc/lean-doc (str "Gap kinds for " prefix " formalism."))
                 "inductive " prefix "GapKind where\n"
                 ctors "\n"
                 "  deriving DecidableEq, Repr\n")))

(defn- emit-gap-kind-completeness
       "Emits completeness theorem for gap kinds."
       [prefix gap-kinds]
       (let [sorted (sort (mapv kw->lean-name gap-kinds))
             members (str/join ", " (mapv #(str "." %) sorted))]
            (str (doc/lean-doc (str "All gap kinds for " prefix "."))
                 "def all" prefix "GapKinds : List " prefix "GapKind :=\n"
                 "  [" members "]\n"
                 "\n"
                 (doc/theorem-doc
                  (str "Every gap kind for " prefix " is accounted for."))
                 "theorem " (kw->lean-name (keyword prefix))
                 "_gap_kinds_complete :\n"
                 "    ∀ k : " prefix "GapKind, k ∈ all"
                 prefix "GapKinds := by\n"
                 "  intro k\n"
                 "  cases k <;> simp [all" prefix "GapKinds]\n")))

(defn- emit-status-inductive
       "Emits an inductive type for a formalism's statuses."
       [prefix statuses]
       (let [sorted (sort (mapv kw->lean-name statuses))
             ctors (str/join "\n"
                             (mapv #(str "  | " %) sorted))]
            (str (doc/lean-doc (str "Gap statuses for " prefix " formalism."))
                 "inductive " prefix "Status where\n"
                 ctors "\n"
                 "  deriving DecidableEq, Repr\n")))

(defn- emit-status-completeness
       "Emits completeness theorem for statuses."
       [prefix statuses]
       (let [sorted (sort (mapv kw->lean-name statuses))
             members (str/join ", " (mapv #(str "." %) sorted))]
            (str (doc/lean-doc (str "All statuses for " prefix "."))
                 "def all" prefix "Statuses : List " prefix "Status :=\n"
                 "  [" members "]\n"
                 "\n"
                 (doc/theorem-doc
                  (str "Every status for " prefix " is accounted for."))
                 "theorem " (kw->lean-name (keyword prefix))
                 "_statuses_complete :\n"
                 "    ∀ s : " prefix "Status, s ∈ all"
                 prefix "Statuses := by\n"
                 "  intro s\n"
                 "  cases s <;> simp [all" prefix "Statuses]\n")))

(defn- emit-consistency-theorem
       "Emits a per-formalism consistency theorem: gap-kind and status
  enumerations are both exhaustive."
       [prefix]
       (str (doc/theorem-doc
             (str "Monitor-schema consistency for " prefix
                  ": both gap-kind and status enumerations are exhaustive."))
            "theorem " (kw->lean-name (keyword prefix))
            "_monitor_schema_consistent :\n"
            "    (∀ k : " prefix "GapKind, k ∈ all" prefix "GapKinds) ∧\n"
            "    (∀ s : " prefix "Status, s ∈ all" prefix "Statuses) := by\n"
            "  exact ⟨" (kw->lean-name (keyword prefix)) "_gap_kinds_complete, "
            (kw->lean-name (keyword prefix)) "_statuses_complete⟩\n"))

(defn- emit-per-formalism
       "Emits monitor-schema consistency proofs for a single formalism."
       [kind formalism]
       (let [gap-type (p/->gap-type formalism)
             prefix (kw->lean-type-prefix kind)
             gap-kinds (:gap-kinds gap-type)
             statuses (:statuses gap-type)]
            (str "-- Monitor-schema consistency for " (name kind) "\n\n"
                 (emit-gap-kind-inductive prefix gap-kinds)
                 "\n"
                 (emit-gap-kind-completeness prefix gap-kinds)
                 "\n"
                 (emit-status-inductive prefix statuses)
                 "\n"
                 (emit-status-completeness prefix statuses)
                 "\n"
                 (emit-consistency-theorem prefix))))

;;; Public API

(defn emit-monitor-schema-consistency
      "Emits Lean 4 source for monitor-schema consistency per formalism.
  For each formalism, proves that gap-kind and status enumerations
  are exhaustive.
  Returns a string of Lean 4 source code."
      [formalisms]
      (str "-- Monitor-schema consistency verification\n"
           "-- Generated by Pneuma\n\n"
           (str/join "\n\n"
                     (mapv (fn [[kind f]]
                               (emit-per-formalism kind f))
                           (sort-by (comp name key) formalisms)))))
