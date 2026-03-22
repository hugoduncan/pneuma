(ns pneuma.doc.fragment
    "Format-agnostic document fragment constructors, predicates,
  and gap annotation for the pneuma documentation projection.")

;;; Fragment constructors

(defn section
      "Creates a section fragment containing child fragments."
      [id title children]
      {:kind :section :id id :title title :children (vec children)})

(defn table
      "Creates a tabular fragment with named columns and row maps."
      [id columns rows]
      {:kind :table :id id :columns (vec columns) :rows (vec rows)})

(defn prose
      "Creates a prose text fragment."
      [id text]
      {:kind :prose :id id :text text})

(defn diagram-spec
      "Creates a diagram specification fragment.
  Dialect is one of :mermaid-state, :mermaid-sequence, :mermaid-graph.
  Data is dialect-specific structure, not pre-rendered text."
      [id dialect data]
      {:kind :diagram-spec :id id :dialect dialect :data data})

(defn cross-ref
      "Creates a cross-reference to another fragment by id."
      [target-id label]
      {:kind :cross-ref :target-id target-id :label label})

(defn status-annotation
      "Creates a conformance status annotation for a fragment.
  Status is one of :conforms, :absent, :diverges."
      [target-id status detail]
      {:kind :status-annotation :target-id target-id :status status :detail detail})

(defn code-block
      "Creates a syntax-highlighted code block fragment.
  Language is a string like \"lean\", \"clojure\", etc."
      [id language code]
      {:kind :code-block :id id :language language :code code})

(defn summary
      "Creates a compact one-line summary fragment for a section.
  Displayed in the section header when the section is collapsed or
  in summary intent."
      [id text]
      {:kind :summary :id id :text text})

;;; Predicates

(def ^:private known-kinds
     #{:section :table :prose :diagram-spec :cross-ref :status-annotation :summary :code-block})

(defn fragment?
      "Returns true if x is a known fragment map."
      [x]
      (and (map? x) (contains? known-kinds (:kind x))))

(defn section?
      "Returns true if x is a section fragment."
      [x]
      (= (:kind x) :section))

(defn table?
      "Returns true if x is a table fragment."
      [x]
      (= (:kind x) :table))

(defn prose?
      "Returns true if x is a prose fragment."
      [x]
      (= (:kind x) :prose))

(defn diagram-spec?
      "Returns true if x is a diagram-spec fragment."
      [x]
      (= (:kind x) :diagram-spec))

(defn cross-ref?
      "Returns true if x is a cross-ref fragment."
      [x]
      (= (:kind x) :cross-ref))

(defn status-annotation?
      "Returns true if x is a status-annotation fragment."
      [x]
      (= (:kind x) :status-annotation))

(defn summary?
      "Returns true if x is a summary fragment."
      [x]
      (= (:kind x) :summary))

;;; Gap annotation

(defn- all-gaps
       "Extracts all gap entries from the three layers of a gap report."
       [gap-report]
       (into []
             (concat (:object-gaps gap-report)
                     (:morphism-gaps gap-report)
                     (:path-gaps gap-report))))

(defn- gap->annotation
       "Converts a gap entry to a status-annotation fragment targeting fragment-id."
       [fragment-id gap]
       (status-annotation fragment-id (:status gap) (:detail gap)))

(defn- gaps-for-fragment
       "Returns all gaps whose :formalism matches the given fragment id."
       [gaps fragment-id]
       (filterv #(= (:formalism %) fragment-id) gaps))

(defn- annotate-fragment
       "Adds status-annotation children to a fragment if any gaps match its :id."
       [fragment gaps]
       (if-let [id (:id fragment)]
               (let [matching (gaps-for-fragment gaps id)]
                    (if (seq matching)
                        (let [annotations (mapv #(gap->annotation id %) matching)]
                             (if (contains? fragment :children)
                                 (update fragment :children into annotations)
                                 (assoc fragment :children annotations)))
                        fragment))
               fragment))

(defn annotate-with-gaps
      "Walks a document fragment tree and overlays gap status annotations.
  For each fragment with an :id, finds gaps whose :formalism matches
  that id and appends status-annotation children."
      [doc-tree gap-report]
      (let [gaps (all-gaps gap-report)]
           ((fn walk [fragment]
                (let [annotated (annotate-fragment fragment gaps)]
                     (if (contains? annotated :children)
                         (update annotated :children #(mapv walk %))
                         annotated)))
            doc-tree)))
