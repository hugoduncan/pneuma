(ns pneuma.doc.core
    "Document assembly and REPL integration for pneuma documentation projection."
    (:require [clojure.string :as str]
              [pneuma.doc.fragment :as doc]
              [pneuma.doc.render :as render]
              [pneuma.lean.system :as lean-sys]
              [pneuma.morphism.registry :as mreg]
              [pneuma.path.core :as path]
              [pneuma.protocol :as p]))

;;; Cross-formalism documentation

(defn- morphism-kind
       "Derives a kind string from a morphism record by inspecting its class name.
  Falls back to the record's :kind field if present, then the simple class name."
       [morphism]
       (or (some-> (:kind morphism) name)
           (-> (type morphism) .getSimpleName
               (str/lower-case)
               (str/replace #"morphism$" ""))))

(defn morphism-doc
      "Produces document fragments for the morphism connection graph.
  Returns a section containing a mermaid graph diagram and a table of morphisms."
      [registry]
      (let [morphisms (vals registry)
            edges     (mapv (fn [m] [(:from m) (:to m) (morphism-kind m)]) morphisms)
            diagram   (doc/diagram-spec :morphism/graph :mermaid-graph {:edges edges})
            rows      (mapv (fn [m]
                                {:id   (str (:id m))
                                 :kind (morphism-kind m)
                                 :from (name (:from m))
                                 :to   (name (:to m))})
                            morphisms)
            table     (doc/table :morphism/table [:id :kind :from :to] rows)]
           (doc/section :morphism/root "Morphism Connections" [diagram table])))

(defn- path-step-description
       "Produces a prose string describing one step in a composed path."
       [step]
       (str (name (:from step)) " --[" (morphism-kind step) "]--> " (name (:to step))))

(defn- path->section
       "Produces a section fragment documenting a single ComposedPath."
       [composed-path]
       (let [path-id      (:id composed-path)
             steps        (:steps composed-path)
             step-text    (str/join "\n" (mapv path-step-description steps))
             prose        (doc/prose
                           (keyword (str "path/" (name path-id) "/desc"))
                           (str "Cycle " (name path-id) " steps:\n" step-text))
             nodes        (into [] (distinct) (mapcat (fn [s] [(:from s) (:to s)]) steps))
             interactions (mapv (fn [s] [(:from s) (:to s) (morphism-kind s)]) steps)
             diagram      (doc/diagram-spec
                           (keyword (str "path/" (name path-id) "/seq"))
                           :mermaid-sequence
                           {:participants nodes
                            :interactions interactions})]
            (doc/section (keyword (str "path/" (name path-id))) (name path-id) [prose diagram])))

(defn path-doc
      "Produces document fragments for composed paths (cycles) in the morphism graph.
  Returns a section containing one child section per discovered path."
      [registry]
      (let [paths (path/find-paths registry)
            children (mapv path->section paths)]
           (doc/section :path/root "Composed Paths" children)))

(defn- gap-summary-text
       "Builds a summary prose string from a gap report."
       [gap-report]
       (let [all-gaps  (into [] cat [(:object-gaps gap-report)
                                     (:morphism-gaps gap-report)
                                     (:path-gaps gap-report)])
             total     (count all-gaps)
             conforms  (count (filterv #(= :conforms (:status %)) all-gaps))
             failures  (- total conforms)]
            (str "Total gaps: " total
                 ". Conforming: " conforms
                 ". Failures: " failures ".")))

(defn gap-report-doc
      "Produces document fragments for the gap report status dashboard.
  Returns a section with summary prose and tables for each gap layer."
      [gap-report]
      (let [summary   (doc/prose :gap/summary (gap-summary-text gap-report))
            obj-rows  (mapv (fn [g]
                                {:formalism (str (or (:formalism g) ""))
                                 :status    (name (:status g))
                                 :detail    (str (or (:detail g) ""))})
                            (:object-gaps gap-report))
            obj-table (doc/table :gap/object-table [:formalism :status :detail] obj-rows)
            mor-rows  (mapv (fn [g]
                                {:id     (str (or (:id g) ""))
                                 :status (name (:status g))
                                 :detail (str (or (:detail g) ""))})
                            (:morphism-gaps gap-report))
            mor-table (doc/table :gap/morphism-table [:id :status :detail] mor-rows)
            path-rows (mapv (fn [g]
                                {:id     (str (or (:id g) ""))
                                 :status (name (:status g))
                                 :detail (str (or (:detail g) ""))})
                            (:path-gaps gap-report))
            path-table (doc/table :gap/path-table [:id :status :detail] path-rows)]
           (doc/section :gap/root "Gap Report" [summary obj-table mor-table path-table])))

;;; Document assembly

(defn- render-for-format
       "Dispatches a fragment tree to the renderer for the given format keyword."
       [fragment format opts]
       (case format
             :markdown (render/render-markdown fragment)
             :html     (render/render-html fragment opts)
             :docx     (render/render-docx fragment)
             (throw (ex-info "Unknown format" {:format format}))))

(defn- lean-doc
       "Produces a Lean projection section if the spec has effect signatures.
  Returns a section fragment with the generated Lean 4 code, or nil."
       [spec-name formalisms-map registry]
       (try
        (let [lean-code (lean-sys/emit-system-lean
                         spec-name
                         {:formalisms formalisms-map :registry registry})
              line-count (count (str/split-lines lean-code))]
             (doc/section
              :lean/root "Lean Projection"
              [(doc/summary :lean/summary
                            (str line-count " lines of Lean 4"))
               (doc/code-block :lean/code "lean" lean-code)]))
        (catch Exception _
               nil)))

(defn render-doc
      "Assembles and renders a complete architecture document.
  Config keys: :formalisms (seq of IProjectable records), :registry (morphism
  registry map), :gap-report (optional gap report map), :format (default :markdown),
  :formalisms-map (optional, keyed map for Lean projection),
  :spec-name (optional, for Lean section title).
  Returns a rendered string (for :markdown) or bytes (for :docx)."
      [{:keys [formalisms registry gap-report format render-opts
               formalisms-map spec-name]
        :or {format :markdown}}]
      (let [formalism-sections (mapv p/->doc formalisms)
            morphism-section   (morphism-doc registry)
            path-section       (path-doc registry)
            lean-section       (when formalisms-map
                                     (lean-doc (or spec-name "spec")
                                               formalisms-map registry))
            base-children      (cond-> (into formalism-sections
                                             [morphism-section path-section])
                                       lean-section (conj lean-section))
            all-children       (if gap-report
                                   (conj base-children (gap-report-doc gap-report))
                                   base-children)
            root               (doc/section :doc/root "Architecture Document" all-children)
            annotated          (if gap-report
                                   (doc/annotate-with-gaps root gap-report)
                                   root)]
           (render-for-format annotated format (or render-opts {}))))

;;; REPL integration

(defn- fragment-contains-id?
       "Returns true if the fragment tree rooted at fragment contains a node with the given id."
       [fragment component-id]
       (or (= (:id fragment) component-id)
           (some #(fragment-contains-id? % component-id) (:children fragment))))

(defn explain
      "Returns a prose description of a component for REPL display.
  Searches through formalisms (map of kind → IProjectable record) for the
  formalism whose ->doc tree contains component-id, then renders that subtree
  to compact markdown. Returns nil if not found."
      [component-id formalisms]
      (let [found (some (fn [[_kind record]]
                            (let [doc-tree (p/->doc record)]
                                 (when (fragment-contains-id? doc-tree component-id)
                                       doc-tree)))
                        formalisms)]
           (when found
                 (render/render-markdown found))))

(defn explain-connections
      "Returns a prose description of morphisms involving a formalism kind.
  Returns rendered markdown of all morphisms from/to the given formalism-kind
  keyword in the registry."
      [formalism-kind registry]
      (let [relevant  (mreg/morphisms-involving registry formalism-kind)
            section   (morphism-doc relevant)]
           (render/render-markdown section)))

(defn explain-cycle
      "Returns a prose walk-through of a cycle path.
  Finds the path with the given cycle-id in the registry and renders its
  documentation as markdown. Returns nil if no matching path is found."
      [cycle-id registry]
      (let [paths (path/find-paths registry)
            found (some #(when (= (:id %) cycle-id) %) paths)]
           (when found
                 (render/render-markdown (path->section found)))))
