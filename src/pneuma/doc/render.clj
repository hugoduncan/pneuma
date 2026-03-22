(ns pneuma.doc.render
    "Rendering of format-agnostic document fragment trees to concrete
  output formats. Markdown is the primary format; HTML produces a
  self-contained static page."
    (:require [clojure.string :as str]
              [pneuma.doc.html.mermaid :as mermaid]
              [pneuma.doc.html.page :as html.page]))

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
            (mermaid/render-mermaid dialect data)
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
             :code-block        (str "```" (:language fragment) "\n" (:code fragment) "\n```\n\n")
             :summary           (str "*" (:text fragment) "*\n\n")
             (throw (ex-info "Unknown fragment kind" {:fragment fragment}))))

;;; Public API

(defn render-markdown
      "Renders a fragment tree to a markdown string."
      [fragment]
      (render-fragment fragment 0))

(defn render-html
      "Renders a fragment tree to a self-contained HTML string.
  Opts are passed through to render-page (e.g. :index-url, :title)."
      ([fragment] (html.page/render-page fragment {}))
      ([fragment opts] (html.page/render-page fragment opts)))

(defn render-docx
      "Renders a fragment tree to docx bytes."
      [_fragment]
      (throw (ex-info "Docx rendering not yet implemented" {})))
