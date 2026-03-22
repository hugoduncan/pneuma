(ns pneuma.lean.optic
    "Lean 4 emission for OpticDeclaration formalisms.
  Extends OpticDeclaration with ILeanProjectable via extend-protocol.
  Emits inductive OpticId type, optic type classification function,
  path declarations, and completeness theorem."
    (:require [clojure.string :as str]
              [pneuma.lean.doc :as doc]
              [pneuma.lean.protocol :as lp])
    (:import [pneuma.formalism.optic OpticDeclaration]))

(defn- kw->lean-name
       [kw]
       (-> (name kw)
           (str/replace "-" "_")
           (str/replace "/" "_")))

(defn- emit-optic-id-inductive
       "Emits inductive type for all optic identifiers."
       [optic-ids]
       (let [sorted (sort (mapv kw->lean-name optic-ids))
             ctors (str/join "\n"
                             (mapv #(str "  | " %) sorted))]
            (str "inductive OpticId where\n"
                 ctors "\n"
                 "  deriving DecidableEq, Repr\n")))

(defn- emit-optic-type-inductive
       "Emits the OpticType enumeration."
       []
       (str "inductive OpticType where\n"
            "  | Lens\n"
            "  | Traversal\n"
            "  | Fold\n"
            "  | Derived\n"
            "  deriving DecidableEq, Repr\n"))

(defn- emit-classify-function
       "Emits opticType : OpticId → OpticType classification function."
       [declarations]
       (let [clauses (str/join "\n"
                               (mapv (fn [[_ decl]]
                                         (str "  | ." (kw->lean-name (:id decl))
                                              " => ." (name (:optic-type decl))))
                                     (sort-by (comp name :id val) declarations)))]
            (str "def opticType (o : OpticId) : OpticType :=\n"
                 "  match o with\n"
                 clauses "\n")))

(defn- path->lean-list
       "Converts a keyword path vector to a Lean list of strings."
       [path]
       (let [members (str/join ", "
                               (mapv #(str "\"" (kw->lean-name %) "\"") path))]
            (str "[" members "]")))

(defn- emit-path-declarations
       "Emits path definitions for lens/traversal/fold optics."
       [declarations]
       (let [path-decls (into []
                              (keep (fn [[_ decl]]
                                        (when (#{:Lens :Traversal :Fold} (:optic-type decl))
                                              decl)))
                              (sort-by (comp name :id val) declarations))]
            (when (seq path-decls)
                  (str/join "\n"
                            (mapv (fn [decl]
                                      (str "def " (kw->lean-name (:id decl)) "_path"
                                           " : List String :=\n"
                                           "  " (path->lean-list (:path decl)) "\n"))
                                  path-decls)))))

(defn- emit-source-declarations
       "Emits source path definitions for derived optics."
       [declarations]
       (let [derived-decls (into []
                                 (keep (fn [[_ decl]]
                                           (when (= :Derived (:optic-type decl))
                                                 decl)))
                                 (sort-by (comp name :id val) declarations))]
            (when (seq derived-decls)
                  (str/join "\n"
                            (mapv (fn [decl]
                                      (let [sources (:sources decl)
                                            src-defs
                                            (str/join "\n"
                                                      (mapv (fn [[sk sp]]
                                                                (str "def " (kw->lean-name (:id decl))
                                                                     "_source_" (kw->lean-name sk)
                                                                     " : List String :=\n"
                                                                     "  " (path->lean-list sp) "\n"))
                                                            (sort-by (comp name key) sources)))]
                                           src-defs))
                                  derived-decls)))))

(defn- emit-completeness
       "Emits allOptics list and completeness theorem."
       [id optic-ids]
       (let [sorted (sort (mapv kw->lean-name optic-ids))
             members (str/join ", " (mapv #(str "." %) sorted))
             n (count optic-ids)]
            (str (doc/lean-doc (str "Exhaustive list of all optics in OpticDeclaration"
                                    (when id (str " " (doc/id-str id))) "."))
                 "def allOptics : List OpticId :=\n"
                 "  [" members "]\n"
                 "\n"
                 (doc/theorem-doc "Every member of OpticId appears in allOptics. Proved by case analysis.")
                 "theorem allOptics_complete :\n"
                 "    ∀ o : OpticId, o ∈ allOptics := by\n"
                 "  intro o\n"
                 "  cases o <;> simp [allOptics]\n"
                 "\n"
                 (doc/theorem-doc (str "allOptics contains exactly " n " members."))
                 "theorem allOptics_count :\n"
                 "    allOptics.length = " n " := by\n"
                 "  rfl\n")))

(defn- emit-optic-lean
       [id declarations]
       (let [optic-ids (keys declarations)
             id-label (if id (str " " (doc/id-str id)) "")]
            (str "-- Generated by Pneuma from OpticDeclaration" id-label "\n\n"
                 (doc/type-doc "OpticDeclaration" id
                               (str "Optic identifiers declared in OpticDeclaration" id-label "."))
                 (emit-optic-id-inductive optic-ids)
                 "\n"
                 (doc/lean-doc "Classification of optic kinds: Lens, Traversal, Fold, or Derived.")
                 (emit-optic-type-inductive)
                 "\n"
                 (doc/lean-doc "Maps each optic to its kind.")
                 (emit-classify-function declarations)
                 "\n"
                 (or (emit-path-declarations declarations) "")
                 "\n"
                 (or (emit-source-declarations declarations) "")
                 "\n"
                 (emit-completeness id optic-ids))))

(extend-protocol lp/ILeanProjectable
                 OpticDeclaration
                 (->lean [this]
                         (emit-optic-lean (:id this) (:declarations this))))
