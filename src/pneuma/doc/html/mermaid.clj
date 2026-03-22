(ns pneuma.doc.html.mermaid
    "Mermaid diagram text rendering for all supported dialects.
  Extracted from pneuma.doc.render for reuse by both markdown
  and HTML renderers."
    (:require [clojure.string :as str]))

(defn render-mermaid-state
      "Renders :mermaid-state dialect data to mermaid stateDiagram-v2 text.
  Returns a string. Data map keys: :states, :transitions."
      [{:keys [states transitions]}]
      (let [state-lines (mapv #(str "  " (name %)) (or states []))
            trans-lines (mapv (fn [[from to label]]
                                  (if label
                                      (str "  " (name from) " --> " (name to) " : " label)
                                      (str "  " (name from) " --> " (name to))))
                              (or transitions []))]
           (str "stateDiagram-v2\n"
                (str/join "\n" (into state-lines trans-lines)))))

(defn render-mermaid-graph
      "Renders :mermaid-graph dialect data to mermaid graph LR text.
  Returns a string. Data map keys: :edges."
      [{:keys [edges]}]
      (let [edge-lines (mapv (fn [[from to label]]
                                 (if label
                                     (str "  " (name from) " -->|" label "| " (name to))
                                     (str "  " (name from) " --> " (name to))))
                             (or edges []))]
           (str "graph LR\n"
                (str/join "\n" edge-lines))))

(defn render-mermaid-sequence
      "Renders :mermaid-sequence dialect data to mermaid sequenceDiagram text.
  Returns a string. Data map keys: :participants, :interactions."
      [{:keys [participants interactions]}]
      (let [part-lines (mapv #(str "  participant " (name %)) (or participants []))
            int-lines  (mapv (fn [[from to msg]]
                                 (str "  " (name from) "->>" (name to) ": " msg))
                             (or interactions []))]
           (str "sequenceDiagram\n"
                (str/join "\n" (into part-lines int-lines)))))

(defn render-mermaid
      "Dispatches mermaid dialect data to the appropriate renderer.
  Returns a mermaid text string."
      [dialect data]
      (case dialect
            :mermaid-state    (render-mermaid-state data)
            :mermaid-graph    (render-mermaid-graph data)
            :mermaid-sequence (render-mermaid-sequence data)
            (throw (ex-info "Unknown mermaid dialect" {:dialect dialect}))))
