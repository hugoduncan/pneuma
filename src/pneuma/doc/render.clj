(ns pneuma.doc.render
    "Rendering of format-agnostic document fragment trees to concrete
  output formats. Markdown is the primary format; HTML and docx are stubs."
    (:require [clojure.string :as str]))

;;; Mermaid dialect rendering

(defn- render-mermaid-state
       "Renders :mermaid-state dialect data to mermaid stateDiagram-v2 text.
  Data map keys: :states (seq of state names), :transitions (seq of [from to label])."
       [{:keys [states transitions]}]
       (let [state-lines (mapv #(str "  " (name %)) (or states []))
             trans-lines (mapv (fn [[from to label]]
                                   (if label
                                       (str "  " (name from) " --> " (name to) " : " label)
                                       (str "  " (name from) " --> " (name to))))
                               (or transitions []))]
            (str "stateDiagram-v2\n"
                 (str/join "\n" (into state-lines trans-lines)))))

(defn- render-mermaid-graph
       "Renders :mermaid-graph dialect data to mermaid graph LR text.
  Data map keys: :edges (seq of [from to label])."
       [{:keys [edges]}]
       (let [edge-lines (mapv (fn [[from to label]]
                                  (if label
                                      (str "  " (name from) " -->|" label "| " (name to))
                                      (str "  " (name from) " --> " (name to))))
                              (or edges []))]
            (str "graph LR\n"
                 (str/join "\n" edge-lines))))

(defn- render-mermaid-sequence
       "Renders :mermaid-sequence dialect data to mermaid sequenceDiagram text.
  Data map keys: :participants (seq of names), :interactions (seq of [from to message])."
       [{:keys [participants interactions]}]
       (let [part-lines (mapv #(str "  participant " (name %)) (or participants []))
             int-lines  (mapv (fn [[from to msg]]
                                  (str "  " (name from) "->>" (name to) ": " msg))
                              (or interactions []))]
            (str "sequenceDiagram\n"
                 (str/join "\n" (into part-lines int-lines)))))

(defn- render-mermaid
       "Dispatches mermaid dialect data to the appropriate renderer."
       [dialect data]
       (case dialect
             :mermaid-state    (render-mermaid-state data)
             :mermaid-graph    (render-mermaid-graph data)
             :mermaid-sequence (render-mermaid-sequence data)
             (throw (ex-info "Unknown mermaid dialect" {:dialect dialect}))))

;;; Markdown table helpers

(defn- table-separator-row
       "Produces a markdown table separator row for the given column count."
       [n]
       (str "| " (str/join " | " (repeat n "---")) " |"))

(defn- row-to-md
       "Renders a single table row map to a markdown table row string."
       [columns row]
       (str "| "
            (str/join " | "
                      (mapv #(str (get row % "")) columns))
            " |"))

;;; Fragment rendering

(declare render-fragment)

(defn- heading-prefix
       "Returns the markdown heading prefix for the given nesting depth.
  Depth 0 → ##, depth 1 → ###, etc., capped at ######."
       [depth]
       (apply str (repeat (min 6 (+ 2 depth)) "#")))

(defn- render-section
       "Renders a section fragment to markdown at the given nesting depth."
       [{:keys [title children]} depth]
       (let [heading (str (heading-prefix depth) " " title "\n\n")
             body    (str/join "" (mapv #(render-fragment % (inc depth)) children))]
            (str heading body)))

(defn- render-table
       "Renders a table fragment to a markdown table string."
       [{:keys [columns rows]}]
       (let [header    (str "| " (str/join " | " (mapv name columns)) " |")
             separator (table-separator-row (count columns))
             data-rows (mapv #(row-to-md columns %) rows)]
            (str (str/join "\n" (into [header separator] data-rows)) "\n\n")))

(defn- render-prose
       "Renders a prose fragment to a markdown paragraph."
       [{:keys [text]}]
       (str text "\n\n"))

(defn- render-diagram-spec
       "Renders a diagram-spec fragment to a fenced mermaid code block."
       [{:keys [dialect data]}]
       (str "```mermaid\n"
            (render-mermaid dialect data)
            "\n```\n\n"))

(defn- render-cross-ref
       "Renders a cross-ref fragment to a markdown link."
       [{:keys [target-id label]}]
       (let [anchor (-> (name target-id)
                        (str/lower-case)
                        (str/replace #"[^a-z0-9-]" "-"))]
            (str "[" label "](#" anchor ")")))

(defn- render-status-annotation
       "Renders a status-annotation fragment to a markdown badge."
       [{:keys [status detail]}]
       (let [badge (str/upper-case (name status))]
            (if detail
                (str "**[" badge "]** " (pr-str detail) "\n\n")
                (str "**[" badge "]**\n\n"))))

(defn- render-fragment
       "Renders a single fragment to a markdown string at the given nesting depth."
       [fragment depth]
       (case (:kind fragment)
             :section           (render-section fragment depth)
             :table             (render-table fragment)
             :prose             (render-prose fragment)
             :diagram-spec      (render-diagram-spec fragment)
             :cross-ref         (render-cross-ref fragment)
             :status-annotation (render-status-annotation fragment)
             (throw (ex-info "Unknown fragment kind" {:fragment fragment}))))

;;; Public API

(defn render-markdown
      "Renders a fragment tree to a markdown string."
      [fragment]
      (render-fragment fragment 0))

(defn render-html
      "Renders a fragment tree to an HTML string."
      [_fragment]
      (throw (ex-info "HTML rendering not yet implemented" {})))

(defn render-docx
      "Renders a fragment tree to docx bytes."
      [_fragment]
      (throw (ex-info "Docx rendering not yet implemented" {})))
