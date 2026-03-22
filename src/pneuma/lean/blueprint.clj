(ns pneuma.lean.blueprint
    "Lean Blueprint LaTeX emission for Pneuma specifications.
  Produces a content.tex file with \\lean{}, \\leanok, and \\uses{}
  macros linking human-readable mathematical prose to machine-checked
  Lean declarations. The Blueprint tool (Patrick Massot) renders this
  to a browsable HTML dependency graph with color-coded proof status."
    (:require [clojure.string :as str])
    (:import [pneuma.formalism.capability CapabilitySet]
             [pneuma.formalism.effect_signature EffectSignature]
             [pneuma.formalism.mealy MealyHandlerSet]
             [pneuma.formalism.optic OpticDeclaration]
             [pneuma.formalism.resolver ResolverGraph]
             [pneuma.formalism.statechart Statechart]
             [pneuma.formalism.type_schema TypeSchema]
             [pneuma.morphism.containment ContainmentMorphism]
             [pneuma.morphism.existential ExistentialMorphism]
             [pneuma.morphism.ordering OrderingMorphism]
             [pneuma.morphism.structural StructuralMorphism]))

;;; Name derivation (matches the lean emitters)

(defn- kw->lean-name
       [kw]
       (-> (if (namespace kw)
               (str (namespace kw) "_" (name kw))
               (name kw))
           (str/replace #"^->" "")
           (str/replace "-" "_")
           (str/replace ">" "_")
           (str/replace "/" "_")
           (str/replace "." "_")))

(defn- morphism-id->lean-prefix
       [id-kw]
       (let [raw (kw->lean-name id-kw)]
            (->> (str/split raw #"_")
                 (mapv str/capitalize)
                 (str/join ""))))

;;; Blueprint entry construction

(defn- entry
       "Creates a blueprint entry map."
       [kind lean-name label-prefix prose & {:keys [proved? uses]
                                             :or {proved? true uses []}}]
       {:kind kind
        :name lean-name
        :label (str label-prefix ":" lean-name)
        :proved? proved?
        :uses uses
        :prose prose})

;;; Per-formalism blueprint entries

(defmulti blueprint-entries
          "Returns a vector of blueprint entry maps for a formalism."
          (fn [formalism] (class formalism)))

(defmethod blueprint-entries CapabilitySet
           [{:keys [id dispatch]}]
           (let [id-str (kw->lean-name id)
                 type-name (str (str/capitalize id-str) "Op")
                 n (count (into (sorted-set) cat [(or dispatch #{})]))]
                [(entry :definition type-name "def"
                        (str "Enumerates all " n " operation kinds declared by capability set "
                             "\\texttt{:" (name id) "}."))
                 (when (seq dispatch)
                       (entry :definition (str id-str "_dispatch") "def"
                              (str "The set of operations that \\texttt{:" (name id)
                                   "} is permitted to dispatch.")
                              :uses [(str "def:" type-name)]))
                 (entry :theorem (str id-str "_dispatch_bounded") "thm"
                        (str "Every operation in \\texttt{" id-str "\\_dispatch} is a member of "
                             "\\texttt{" type-name "}.")
                        :uses [(str "def:" type-name)
                               (str "def:" id-str "_dispatch")])]))

(defmethod blueprint-entries EffectSignature
           [{:keys [operations]}]
           (let [op-names (sort (map (comp kw->lean-name key) operations))
                 n (count operations)]
                (into
                 [(entry :definition "Op" "def"
                         (str "Operation alphabet with " n " operations."))
                  (entry :definition "allOps" "def"
                         (str "Exhaustive list of all " n " operations.")
                         :uses ["def:Op"])
                  (entry :theorem "allOps_complete" "thm"
                         "Every member of \\texttt{Op} appears in \\texttt{allOps}."
                         :uses ["def:Op" "def:allOps"])
                  (entry :theorem "Op_count" "thm"
                         (str "\\texttt{allOps} contains exactly " n " members.")
                         :uses ["def:allOps"])]
                 (mapv (fn [op-name]
                           (entry :definition (str (str/capitalize op-name) "Args") "def"
                                  (str "Input fields for operation \\texttt{" op-name "}.")
                                  :uses ["def:Op"]))
                       op-names))))

(defmethod blueprint-entries Statechart
           [{:keys [states transitions initial]}]
           (let [n-states (count states)
                 n-events (count (into #{} (map :event) transitions))
                 init-name (kw->lean-name (first (vals initial)))]
                [(entry :definition "State" "def"
                        (str "Enumerates all " n-states " states of the statechart."))
                 (entry :definition "Event" "def"
                        (str "Enumerates all " n-events " events that drive transitions."))
                 (entry :definition "step" "def"
                        "Transition function $\\delta$: given a state and event, returns the successor state."
                        :uses ["def:State" "def:Event"])
                 (entry :definition "initialState" "def"
                        (str "The starting state: \\texttt{" init-name "}.")
                        :uses ["def:State"])
                 (entry :definition "reachable" "def"
                        "A state is reachable if some event sequence from \\texttt{initialState} leads to it."
                        :uses ["def:State" "def:Event" "def:step" "def:initialState"])
                 (entry :lemma "chart_safety_aux" "thm"
                        "An invariant preserved by one step is preserved by any event sequence."
                        :uses ["def:State" "def:Event" "def:step"])
                 (entry :theorem "chart_safety" "thm"
                        "For any invariant that holds on \\texttt{initialState} and is preserved by \\texttt{step}, all reachable states satisfy it."
                        :uses ["def:State" "def:reachable" "def:initialState" "thm:chart_safety_aux"])
                 (entry :theorem "step_deterministic" "thm"
                        "The step function is deterministic: equal inputs produce equal outputs."
                        :uses ["def:step"])]))

(defmethod blueprint-entries MealyHandlerSet
           [{:keys [declarations]}]
           (let [n (count declarations)]
                [(entry :definition "HandlerId" "def"
                        (str "Enumerates all " n " handler identifiers."))
                 (entry :definition "allHandlers" "def"
                        (str "Exhaustive list of all " n " handlers.")
                        :uses ["def:HandlerId"])
                 (entry :theorem "allHandlers_complete" "thm"
                        "Every member of \\texttt{HandlerId} appears in \\texttt{allHandlers}."
                        :uses ["def:HandlerId" "def:allHandlers"])
                 (entry :theorem "allHandlers_count" "thm"
                        (str "\\texttt{allHandlers} contains exactly " n " members.")
                        :uses ["def:allHandlers"])]))

(defmethod blueprint-entries OpticDeclaration
           [{:keys [declarations]}]
           (let [n (count declarations)]
                [(entry :definition "OpticId" "def"
                        (str "Enumerates all " n " optic identifiers."))
                 (entry :definition "OpticType" "def"
                        "Classification of optic kinds: Lens, Traversal, Fold, or Derived.")
                 (entry :definition "opticType" "def"
                        "Maps each optic to its kind."
                        :uses ["def:OpticId" "def:OpticType"])
                 (entry :definition "allOptics" "def"
                        (str "Exhaustive list of all " n " optics.")
                        :uses ["def:OpticId"])
                 (entry :theorem "allOptics_complete" "thm"
                        "Every member of \\texttt{OpticId} appears in \\texttt{allOptics}."
                        :uses ["def:OpticId" "def:allOptics"])
                 (entry :theorem "allOptics_count" "thm"
                        (str "\\texttt{allOptics} contains exactly " n " members.")
                        :uses ["def:allOptics"])]))

(defmethod blueprint-entries ResolverGraph
           [{:keys [declarations]}]
           (let [n (count declarations)]
                [(entry :definition "ResolverId" "def"
                        (str "Enumerates all " n " resolver identifiers."))
                 (entry :definition "Attribute" "def"
                        "Enumerates all attributes reachable by resolvers.")
                 (entry :definition "allResolvers" "def"
                        (str "Exhaustive list of all " n " resolvers.")
                        :uses ["def:ResolverId"])
                 (entry :theorem "allResolvers_complete" "thm"
                        "Every member of \\texttt{ResolverId} appears in \\texttt{allResolvers}."
                        :uses ["def:ResolverId" "def:allResolvers"])
                 (entry :theorem "allResolvers_count" "thm"
                        (str "\\texttt{allResolvers} contains exactly " n " members.")
                        :uses ["def:allResolvers"])
                 (entry :definition "closed" "def"
                        "A set of attributes is closed if every resolver whose inputs are present also has its outputs present."
                        :uses ["def:ResolverId" "def:Attribute"]
                        :proved? false)
                 (entry :theorem "chase_terminates" "thm"
                        "The chase algorithm terminates and computes the closure of the initial attribute set."
                        :uses ["def:closed"]
                        :proved? false)]))

(defmethod blueprint-entries TypeSchema
           [{:keys [types]}]
           (let [n (count types)]
                [(entry :definition "TypeId" "def"
                        (str "Enumerates all " n " type identifiers."))
                 (entry :definition "allTypeIds" "def"
                        (str "Exhaustive list of all " n " types.")
                        :uses ["def:TypeId"])
                 (entry :theorem "allTypeIds_complete" "thm"
                        "Every member of \\texttt{TypeId} appears in \\texttt{allTypeIds}."
                        :uses ["def:TypeId" "def:allTypeIds"])
                 (entry :theorem "allTypeIds_count" "thm"
                        (str "\\texttt{allTypeIds} contains exactly " n " members.")
                        :uses ["def:allTypeIds"])]))

;;; Per-morphism blueprint entries

(defmulti blueprint-conn-entries
          "Returns a vector of blueprint entry maps for a morphism boundary."
          (fn [morphism _source _target] (class morphism)))

(defmethod blueprint-conn-entries ContainmentMorphism
           [{:keys [id from to]} _source _target]
           (let [prefix (morphism-id->lean-prefix id)]
                [(entry :definition (str prefix "Source") "def"
                        (str "Source references for containment check \\texttt{" (name from)
                             "} $\\to$ \\texttt{" (name to) "}.")
                        :uses [])
                 (entry :definition (str prefix "Target") "def"
                        (str "Target references for containment check \\texttt{" (name from)
                             "} $\\to$ \\texttt{" (name to) "}.")
                        :uses [])
                 (entry :definition (str prefix "InTarget") "def"
                        "Checks whether each source reference exists in the target set."
                        :uses [(str "def:" prefix "Source") (str "def:" prefix "Target")])
                 (entry :theorem (str prefix "_containment_boundary") "thm"
                        "Every source reference is contained in the target set."
                        :uses [(str "def:" prefix "Source") (str "def:" prefix "InTarget")])]))

(defmethod blueprint-conn-entries ExistentialMorphism
           [{:keys [id from to]} _source _target]
           (let [prefix (morphism-id->lean-prefix id)]
                [(entry :definition (str prefix "Source") "def"
                        (str "Source references for existential check \\texttt{" (name from)
                             "} $\\to$ \\texttt{" (name to) "}.")
                        :uses [])
                 (entry :definition (str prefix "Target") "def"
                        (str "Target references for existential check \\texttt{" (name from)
                             "} $\\to$ \\texttt{" (name to) "}.")
                        :uses [])
                 (entry :definition (str prefix "Embed") "def"
                        "Maps each source reference to its target counterpart."
                        :uses [(str "def:" prefix "Source") (str "def:" prefix "Target")])
                 (entry :theorem (str prefix "_existential_boundary") "thm"
                        "Every source reference embeds into the target set."
                        :uses [(str "def:" prefix "Source") (str "def:" prefix "Embed")])]))

(defmethod blueprint-conn-entries OrderingMorphism
           [{:keys [id from to]} _source _target]
           (let [prefix (morphism-id->lean-prefix id)]
                [(entry :definition (str prefix "Chain") "def"
                        (str "Chain elements for ordering check \\texttt{" (name from)
                             "} $\\to$ \\texttt{" (name to) "}.")
                        :uses [])
                 (entry :definition (str prefix "ChainIndex") "def"
                        "Maps each chain element to its positional index."
                        :uses [(str "def:" prefix "Chain")])
                 (entry :theorem (str prefix "_ordering_boundary") "thm"
                        "Source precedes target in the interceptor chain."
                        :uses [(str "def:" prefix "ChainIndex")])]))

(defmethod blueprint-conn-entries StructuralMorphism
           [{:keys [id from to]} _source _target]
           (let [prefix (morphism-id->lean-prefix id)]
                [(entry :definition (str prefix "Output") "def"
                        (str "Source outputs for structural check \\texttt{" (name from)
                             "} $\\to$ \\texttt{" (name to) "}.")
                        :uses [])
                 (entry :definition (str prefix "Valid") "def"
                        "Opaque predicate: source output conforms to the target schema."
                        :uses [(str "def:" prefix "Output")]
                        :proved? false)
                 (entry :theorem (str prefix "_structural_boundary") "thm"
                        "Every source output validates against the target schema."
                        :uses [(str "def:" prefix "Output") (str "def:" prefix "Valid")]
                        :proved? false)]))

;;; LaTeX rendering

(defn- latex-env-name
       "Maps entry kind to LaTeX environment name."
       [kind]
       (case kind
             :definition "definition"
             :theorem "theorem"
             :lemma "lemma"))

(defn- human-name
       "Converts a Lean name to a readable title."
       [lean-name]
       (-> lean-name
           (str/replace "_" " ")))

(defn render-entry
      "Renders a single blueprint entry to a LaTeX string."
      [{:keys [kind name label proved? uses prose]}]
      (let [env (latex-env-name kind)
            uses-str (when (seq uses)
                           (str "\\uses{" (str/join ", " uses) "}\n"))
            leanok (when proved? "\\leanok\n")]
           (str "\\begin{" env "}[" (human-name name) "]"
                "\\label{" label "}\n"
                "\\lean{" name "}\n"
                leanok
                uses-str
                prose "\n"
                "\\end{" env "}\n")))

(defn- render-proof
       "Renders a proof block for a theorem/lemma entry."
       [{:keys [kind proved? uses]}]
       (when (#{:theorem :lemma} kind)
             (let [uses-str (when (seq uses)
                                  (str "\\uses{" (str/join ", " uses) "}\n"))
                   leanok (when proved? "\\leanok\n")]
                  (str "\\begin{proof}\n"
                       leanok
                       uses-str
                       (if proved?
                           "Proved by case analysis or decision procedure."
                           "Proof target: not yet formalized.")
                       "\n\\end{proof}\n"))))

(def ^:private preamble
     "LaTeX preamble for the blueprint document."
     (str "% Generated by Pneuma Lean Blueprint emission\n"
          "% Do not edit manually — regenerate with emit-lean-blueprint\n"
          "\\input{preamble/preamble}\n"
          "\n"
          "\\title{Pneuma Proof Blueprint}\n"
          "\\begin{document}\n"
          "\\maketitle\n\n"))

(def ^:private postamble
     "\\end{document}\n")

(defn render-section
      "Renders a titled section with its entries."
      [title entries]
      (let [valid-entries (remove nil? entries)]
           (str "\\section{" title "}\n\n"
                (str/join "\n"
                          (mapv (fn [e]
                                    (str (render-entry e)
                                         (render-proof e)))
                                valid-entries))
                "\n")))

(defn render-blueprint
      "Renders a complete blueprint document from sections.
  sections is a vector of [title entries] pairs."
      [spec-name sections]
      (str preamble
           "% Source: " spec-name "\n\n"
           (str/join "\n" (mapv (fn [[title entries]]
                                    (render-section title entries))
                                sections))
           postamble))

;;; Assembly

(defn- formalism-section-title
       "Returns a section title for a formalism."
       [formalism]
       (let [class-name (.getSimpleName (class formalism))
             id (or (:id formalism) class-name)]
            (str class-name (when (keyword? id) (str " — " (name id))))))

(defn assemble-sections
      "Assembles blueprint sections from a config map.
  Returns a vector of [title entries] pairs."
      [{:keys [formalisms registry]}]
      (let [form-sections
            (into []
                  (keep (fn [[_k f]]
                            (let [entries (blueprint-entries f)]
                                 (when (seq entries)
                                       [(formalism-section-title f) entries]))))
                  (sort-by (comp str key) formalisms))

            morph-sections
            (into []
                  (keep (fn [[_id morphism]]
                            (let [source (get formalisms (:from morphism))
                                  target (get formalisms (:to morphism))]
                                 (when (and source target)
                                       (let [entries (blueprint-conn-entries morphism source target)]
                                            (when (seq entries)
                                                  [(str "Morphism " (name (:id morphism))
                                                        ": " (name (:from morphism))
                                                        " → " (name (:to morphism)))
                                                   entries]))))))
                  (sort-by (comp name key) registry))]
           (into form-sections morph-sections)))

(defn emit-blueprint
      "Emits a complete LaTeX blueprint for a specification.
  Returns a string suitable for writing to blueprint/src/content.tex."
      [spec-name config]
      (render-blueprint spec-name (assemble-sections config)))
