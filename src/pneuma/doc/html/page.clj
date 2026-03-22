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

table { border-collapse: collapse; width: 100%; margin: 1em 0; }
th, td { padding: 0.4em 0.8em; border: 1px solid #e5e7eb; text-align: left; }
th { background: #f9fafb; font-weight: 600; }

#toc { position: sticky; top: 0; background: white; padding: 0.5em 1em;
       border-bottom: 1px solid #e5e7eb; z-index: 10; }
#toc ul { list-style: none; display: flex; gap: 1em; padding: 0; margin: 0;
          flex-wrap: wrap; }

a.cross-ref { text-decoration: none; border-bottom: 1px dashed currentColor; }

details { margin: 0.5em 0; padding-left: 1em; }
summary { cursor: pointer; font-weight: 600; }

main { max-width: 60em; margin: 0 auto; padding: 1em;
       font-family: system-ui, sans-serif; }

h2, h3, h4, h5, h6 { margin-top: 1.5em; margin-bottom: 0.5em; }
p { margin: 0.5em 0; line-height: 1.5; }
section { margin-bottom: 1em; padding-left: 0.5em; }")

(def ^:private mermaid-cdn-url
     "https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js")

(defn- build-toc
       "Builds a table-of-contents nav from the root fragment's children."
       [fragment]
       (let [sections (filterv #(= :section (:kind %)) (:children fragment))]
            (when (seq sections)
                  [:nav {:id "toc"}
                   (into [:ul]
                         (mapv (fn [s]
                                   [:li [:a {:href (str "#" (name (:id s)))}
                                         (:title s)]])
                               sections))])))

(defn render-page
      "Renders a fragment tree to a complete self-contained HTML page string.
  Returns an HTML string. Options: :title (page title), :base-url."
      [fragment opts]
      (let [title   (or (:title opts) (:title fragment) "Architecture Document")
            render-ctx (ctx/default-ctx (select-keys opts [:base-url]))
            toc     (build-toc fragment)
            body    (frag/render-fragment fragment render-ctx)
            page    [:html {:lang "en"}
                     [:head
                      [:meta {:charset "utf-8"}]
                      [:meta {:name "viewport"
                              :content "width=device-width, initial-scale=1"}]
                      [:title title]
                      [:style (h/raw css-text)]
                      [:script {:src mermaid-cdn-url}]
                      [:script (h/raw "mermaid.initialize({startOnLoad:true});")]]
                     [:body
                      toc
                      [:main body]]]]
           (str "<!DOCTYPE html>\n" (str (h/html page)))))
