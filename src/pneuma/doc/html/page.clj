(ns pneuma.doc.html.page
    "HTML page shell assembly with TOC, CSS, and mermaid.js script."
    (:require [hiccup2.core :as h]
              [pneuma.doc.html.context :as ctx]
              [pneuma.doc.html.fragment :as frag]))

(def ^:private css-text
     ":root {
  --color-conforms:    #16a34a;
  --color-conforms-bg: #f0fdf4;
  --color-absent:      #d97706;
  --color-absent-bg:   #fffbeb;
  --color-diverges:    #dc2626;
  --color-diverges-bg: #fef2f2;
}

[data-priority=\"high\"]   { border-left: 3px solid var(--color-diverges); }
[data-priority=\"medium\"] { border-left: 3px solid var(--color-absent); }
[data-priority=\"low\"]    { border-left: 3px solid var(--color-conforms); }

.badge { padding: 2px 6px; border-radius: 3px; font-size: 0.8em;
         font-weight: 600; margin-left: 0.5em; }
.badge[data-status=\"conforms\"] { background: var(--color-conforms-bg); color: var(--color-conforms); }
.badge[data-status=\"absent\"]   { background: var(--color-absent-bg);   color: var(--color-absent); }
.badge[data-status=\"diverges\"] { background: var(--color-diverges-bg); color: var(--color-diverges); }

[data-intent=\"hero\"] { padding: 1em; margin-bottom: 1em;
                         border: 1px solid #e5e7eb; border-radius: 6px; }

/* Summary intent hides content children */
[data-intent=\"summary\"] > table,
[data-intent=\"summary\"] > p,
[data-intent=\"summary\"] > pre,
[data-intent=\"summary\"] > details,
[data-intent=\"summary\"] > a.cross-ref { display: none; }

table { border-collapse: collapse; width: 100%; margin: 1em 0; }
th, td { padding: 0.4em 0.8em; border: 1px solid #e5e7eb; text-align: left; }
th { background: #f9fafb; font-weight: 600; }

#toc { position: sticky; top: 0; background: white; padding: 0.5em 1em;
       border-bottom: 1px solid #e5e7eb; z-index: 10; }
#toc ul { list-style: none; display: flex; gap: 1em; padding: 0; margin: 0;
          flex-wrap: wrap; }
#toc .controls { margin-left: auto; display: flex; gap: 0.5em; }
#toc .controls button { font-size: 0.8em; cursor: pointer; padding: 2px 8px;
                        border: 1px solid #d1d5db; border-radius: 3px;
                        background: white; }
#toc .controls button:hover { background: #f3f4f6; }

a.cross-ref { text-decoration: none; border-bottom: 1px dashed currentColor; }
a.index-link { font-weight: 600; text-decoration: none; margin-right: 1em;
               color: #374151; }
a.index-link:hover { color: #111827; }

details.section { margin: 0.5em 0; padding-left: 0.5em; }
details.section > summary { cursor: pointer; font-weight: 600;
                            list-style: none; display: flex; align-items: center;
                            gap: 0.3em; }
details.section > summary::-webkit-details-marker { display: none; }
details.section > summary::before { content: '\\25B6'; font-size: 0.6em;
                                    transition: transform 0.15s; }
details.section[open] > summary::before { transform: rotate(90deg); }

.intent-toggle { background: none; border: 1px solid #d1d5db; border-radius: 3px;
                 cursor: pointer; font-size: 0.7em; padding: 1px 4px;
                 color: #6b7280; line-height: 1; }
.intent-toggle:hover { background: #f3f4f6; color: #111827; }
[data-intent=\"summary\"] > summary > .intent-toggle { color: #3b82f6; }

.section-summary { color: #6b7280; font-size: 0.85em; font-weight: 400;
                   margin-left: 0.5em; }
[data-intent=\"detail\"] > summary > .section-summary { display: none; }

main { max-width: 60em; margin: 0 auto; padding: 1em;
       font-family: system-ui, sans-serif; }

h2, h3, h4, h5, h6 { margin-top: 1.5em; margin-bottom: 0.5em; }
p { margin: 0.5em 0; line-height: 1.5; }")

(def ^:private js-text
     "function toggleIntent(summaryEl) {
  var el = summaryEl.parentElement;
  var current = el.getAttribute('data-intent') || 'detail';
  var next = current === 'detail' ? 'summary' : 'detail';
  el.setAttribute('data-intent', next);
  // Re-init mermaid for any diagrams that become visible
  if (next === 'detail' && typeof mermaid !== 'undefined') {
    mermaid.run();
  }
}
function expandAll() {
  document.querySelectorAll('details.section').forEach(function(d) { d.open = true; });
}
function collapseAll() {
  document.querySelectorAll('details.section').forEach(function(d) { d.open = false; });
}
function setAllIntent(intent) {
  document.querySelectorAll('details.section[data-intent]').forEach(function(el) {
    el.setAttribute('data-intent', intent);
  });
  if (intent === 'detail' && typeof mermaid !== 'undefined') {
    mermaid.run();
  }
}")

(def ^:private mermaid-cdn-url
     "https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js")

(defn- build-toc
       "Builds a table-of-contents nav from the root fragment's children.
  When :index-url is set in opts, includes a back-link to the index page."
       [fragment opts]
       (let [sections (filterv #(= :section (:kind %)) (:children fragment))]
            (when (seq sections)
                  [:nav {:id "toc"}
                   (when-let [url (:index-url opts)]
                             [:a {:href url :class "index-link"} "\u2190 Index"])
                   (into [:ul]
                         (mapv (fn [s]
                                   [:li [:a {:href (str "#" (frag/full-id (:id s)))}
                                         (:title s)]])
                               sections))
                   [:span {:class "controls"}
                    [:button {:onclick "expandAll()"} "Expand"]
                    [:button {:onclick "collapseAll()"} "Collapse"]
                    [:button {:onclick "setAllIntent('summary')"} "Summary"]
                    [:button {:onclick "setAllIntent('detail')"} "Detail"]]])))

(defn render-page
      "Renders a fragment tree to a complete self-contained HTML page string.
  Returns an HTML string. Options: :title (page title), :base-url."
      [fragment opts]
      (let [title   (or (:title opts) (:title fragment) "Architecture Document")
            render-ctx (ctx/default-ctx (select-keys opts [:base-url]))
            toc     (build-toc fragment opts)
            body    (frag/render-fragment fragment render-ctx)
            page    [:html {:lang "en"}
                     [:head
                      [:meta {:charset "utf-8"}]
                      [:meta {:name "viewport"
                              :content "width=device-width, initial-scale=1"}]
                      [:title title]
                      [:style (h/raw css-text)]
                      [:script {:src mermaid-cdn-url}]
                      [:script (h/raw "mermaid.initialize({startOnLoad:true});")]
                      [:script (h/raw js-text)]]
                     [:body
                      toc
                      [:main body]]]]
           (str "<!DOCTYPE html>\n" (h/html page))))

(def ^:private index-css
     "body { font-family: system-ui, sans-serif; max-width: 60em;
             margin: 0 auto; padding: 2em; }
h1 { margin-bottom: 0.5em; }
.spec-group { margin-bottom: 1.5em; }
.spec-group h2 { font-size: 1.1em; color: #374151; margin-bottom: 0.3em;
                  border-bottom: 1px solid #e5e7eb; padding-bottom: 0.3em; }
.spec-group ul { list-style: none; padding: 0; margin: 0; display: flex;
                 flex-wrap: wrap; gap: 0.5em; }
.spec-group li a { display: inline-block; padding: 0.3em 0.8em;
                   border: 1px solid #d1d5db; border-radius: 4px;
                   text-decoration: none; color: #1f2937; font-size: 0.9em; }
.spec-group li a:hover { background: #f3f4f6; }
.summary { color: #6b7280; margin-bottom: 2em; }")

(defn render-index
      "Renders an index page linking to all spec HTML files.
  entries is a seq of {:name \"spec-name\" :file \"spec-name.html\"
  :gap-summary {:total N :failures N}}. Returns an HTML string."
      [title entries]
      (let [groups {"Formalisms"  #{"capability" "effect-sig" "mealy" "optic"
                                    "resolver" "statechart" "type-schema"}
                    "Morphisms"   #{"containment" "existential" "ordering"
                                    "structural" "registry"}
                    "Paths & Gaps" #{"path-core" "path-graph" "gap-core" "gap-diff"}
                    "Lean"        #{"lean" "lean-capability" "lean-containment"
                                    "lean-core" "lean-effect-sig" "lean-existential"
                                    "lean-mealy" "lean-optic" "lean-ordering"
                                    "lean-resolver" "lean-statechart" "lean-structural"}
                    "System"      #{"core" "protocol" "refinement"}}
            group-order ["System" "Formalisms" "Morphisms" "Paths & Gaps" "Lean"]
            entry-map (into {} (map (fn [e] [(:name e) e])) entries)
            ungrouped (into #{}
                            (remove (fn [n] (some #(contains? (val %) n) groups)))
                            (map :name entries))
            all-groups (if (seq ungrouped)
                           (conj group-order "Other")
                           group-order)
            groups (if (seq ungrouped)
                       (assoc groups "Other" ungrouped)
                       groups)
            total-specs (count entries)
            total-failures (reduce + 0 (map #(get-in % [:gap-summary :failures] 0) entries))
            body (into [:div]
                       (mapv (fn [group-name]
                                 (let [names (sort (get groups group-name))
                                       items (filterv some? (mapv #(get entry-map %) names))]
                                      (when (seq items)
                                            [:div {:class "spec-group"}
                                             [:h2 group-name]
                                             (into [:ul]
                                                   (mapv (fn [e]
                                                             (let [failures (get-in e [:gap-summary :failures] 0)
                                                                   style (when (pos? failures)
                                                                               "border-color: var(--color-diverges, #dc2626);")]
                                                                  [:li [:a (cond-> {:href (:file e)}
                                                                                   style (assoc :style style))
                                                                        (:name e)
                                                                        (when (pos? failures)
                                                                              [:span {:style "color:#dc2626; margin-left:0.3em; font-size:0.8em;"}
                                                                               (str " (" failures " gaps)")])]]))
                                                         items))])))
                             all-groups))
            page [:html {:lang "en"}
                  [:head
                   [:meta {:charset "utf-8"}]
                   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                   [:title title]
                   [:style (h/raw index-css)]]
                  [:body
                   [:h1 title]
                   [:p {:class "summary"}
                    (str total-specs " specs, " total-failures " total gap failures")]
                   body]]]
           (str "<!DOCTYPE html>\n" (h/html page))))
