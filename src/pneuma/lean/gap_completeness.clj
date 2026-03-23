(ns pneuma.lean.gap-completeness
    "Lean 4 emission for gap report completeness verification.
  Proves that every morphism in the registry was checked in the
  gap report, every formalism has an object-layer entry, and every
  discovered path has a path-layer entry. All proofs use case
  analysis on finite inductive types."
    (:require [clojure.string :as str]
              [pneuma.lean.doc :as doc]))

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

;;; Morphism completeness

(defn- emit-morphism-id-inductive
       "Emits an inductive type from all morphism ids in the registry."
       [morphism-ids]
       (let [sorted (sort (mapv kw->lean-name morphism-ids))
             ctors (str/join "\n"
                             (mapv #(str "  | " %) sorted))]
            (str (doc/lean-doc "All morphism identifiers in the registry.")
                 "inductive MorphismId where\n"
                 ctors "\n"
                 "  deriving DecidableEq, Repr\n")))

(defn- emit-checked-morphisms
       "Emits the list of morphism ids that appear in the gap report."
       [report]
       (let [checked-ids (into #{}
                               (keep :id)
                               (:morphism-gaps report))
             sorted (sort (mapv kw->lean-name checked-ids))
             members (str/join ", " (mapv #(str "." %) sorted))]
            (str (doc/lean-doc "Morphism ids that were checked in the gap report.")
                 "def checkedMorphisms : List MorphismId :=\n"
                 "  [" members "]\n")))

(defn- emit-morphism-completeness
       "Emits a theorem that every registered morphism was checked."
       []
       (str (doc/theorem-doc "Every registered morphism was checked in the gap report.")
            "theorem morphism_gap_complete :\n"
            "    ∀ m : MorphismId, m ∈ checkedMorphisms := by\n"
            "  intro m\n"
            "  cases m <;> simp [checkedMorphisms]\n"))

;;; Formalism completeness

(defn- emit-formalism-kind-inductive
       "Emits an inductive type from all formalism kinds."
       [formalism-kinds]
       (let [sorted (sort (mapv kw->lean-name formalism-kinds))
             ctors (str/join "\n"
                             (mapv #(str "  | " %) sorted))]
            (str (doc/lean-doc "All formalism kinds in the specification.")
                 "inductive FormalismKind where\n"
                 ctors "\n"
                 "  deriving DecidableEq, Repr\n")))

(defn- emit-checked-formalisms
       "Emits the list of formalism kinds that appear in object gaps."
       [report]
       (let [checked-kinds (into #{}
                                 (keep :formalism)
                                 (:object-gaps report))
             sorted (sort (mapv kw->lean-name checked-kinds))
             members (str/join ", " (mapv #(str "." %) sorted))]
            (str (doc/lean-doc "Formalism kinds that were checked at the object layer.")
                 "def checkedFormalisms : List FormalismKind :=\n"
                 "  [" members "]\n")))

(defn- emit-formalism-completeness
       "Emits a theorem that every formalism was checked."
       []
       (str (doc/theorem-doc "Every formalism kind was checked at the object layer.")
            "theorem object_gap_complete :\n"
            "    ∀ f : FormalismKind, f ∈ checkedFormalisms := by\n"
            "  intro f\n"
            "  cases f <;> simp [checkedFormalisms]\n"))

;;; Path completeness

(defn- emit-path-id-inductive
       "Emits an inductive type from all path ids."
       [path-ids]
       (if (empty? path-ids)
           ""
           (let [sorted (sort (mapv kw->lean-name path-ids))
                 ctors (str/join "\n"
                                 (mapv #(str "  | " %) sorted))]
                (str (doc/lean-doc "All discovered path identifiers.")
                     "inductive PathId where\n"
                     ctors "\n"
                     "  deriving DecidableEq, Repr\n"))))

(defn- emit-checked-paths
       "Emits the list of path ids that appear in path gaps."
       [report]
       (let [checked-ids (into #{} (keep :id) (:path-gaps report))]
            (if (empty? checked-ids)
                ""
                (let [sorted (sort (mapv kw->lean-name checked-ids))
                      members (str/join ", " (mapv #(str "." %) sorted))]
                     (str (doc/lean-doc "Path ids that were checked at the path layer.")
                          "def checkedPaths : List PathId :=\n"
                          "  [" members "]\n")))))

(defn- emit-path-completeness
       "Emits a theorem that every discovered path was checked."
       [path-ids]
       (if (empty? path-ids)
           ""
           (str (doc/theorem-doc "Every discovered path was checked at the path layer.")
                "theorem path_gap_complete :\n"
                "    ∀ p : PathId, p ∈ checkedPaths := by\n"
                "  intro p\n"
                "  cases p <;> simp [checkedPaths]\n")))

;;; System theorem

(defn- emit-system-completeness
       "Emits a conjunction theorem for all three layers."
       [has-paths?]
       (let [conjuncts (cond-> ["(∀ m : MorphismId, m ∈ checkedMorphisms)"
                                "(∀ f : FormalismKind, f ∈ checkedFormalisms)"]
                               has-paths? (conj "(∀ p : PathId, p ∈ checkedPaths)"))
             proofs (cond-> ["morphism_gap_complete"
                             "object_gap_complete"]
                            has-paths? (conj "path_gap_complete"))]
            (str (doc/theorem-doc "The gap report checked every registered component at every layer.")
                 "theorem gap_report_complete :\n"
                 "    " (str/join " ∧\n    " conjuncts) " := by\n"
                 "  exact ⟨" (str/join ", " proofs) "⟩\n")))

;;; Public API

(defn emit-gap-completeness
      "Emits Lean 4 source proving every morphism, formalism, and path
  in the registry was checked in the gap report.
  Returns a string of Lean 4 source code."
      [registry formalisms report]
      (let [morphism-ids (keys registry)
            formalism-kinds (keys formalisms)
            path-ids (into #{} (keep :id) (:path-gaps report))
            has-paths? (seq path-ids)]
           (str "-- Gap report completeness verification\n"
                "-- Generated by Pneuma\n\n"
                (emit-morphism-id-inductive morphism-ids)
                "\n"
                (emit-checked-morphisms report)
                "\n"
                (emit-morphism-completeness)
                "\n"
                (emit-formalism-kind-inductive formalism-kinds)
                "\n"
                (emit-checked-formalisms report)
                "\n"
                (emit-formalism-completeness)
                "\n"
                (when has-paths?
                      (str (emit-path-id-inductive path-ids)
                           "\n"
                           (emit-checked-paths report)
                           "\n"
                           (emit-path-completeness path-ids)
                           "\n"))
                (emit-system-completeness has-paths?))))
