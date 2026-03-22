(ns pneuma.doc.html.fragment
    "Multimethod-based rendering of document fragments to hiccup.
  Dispatches on fragment kind; sections vary by intent."
    (:require [clojure.string :as str]
              [hiccup2.core :as h]
              [pneuma.doc.html.context :as ctx]
              [pneuma.doc.html.mermaid :as mermaid]))

(defmulti render-fragment
          "Renders a fragment to hiccup given a semantic context.
  Dispatches on (:kind fragment)."
          (fn [fragment _ctx] (:kind fragment)))

;;; Helpers

(defn full-id
      "Returns the full string id for a keyword, preserving namespace."
      [kw]
      (if-let [ns (namespace kw)]
              (str ns "--" (name kw))
              (name kw)))

(defn- heading-tag
       "Returns the heading keyword for the given depth.
  Depth 0 → :h2, depth 1 → :h3, capped at :h6."
       [depth]
       (keyword (str "h" (min 6 (+ 2 depth)))))

(defn- section-attrs
       "Builds the attribute map for a section element."
       [fragment ctx]
       (cond-> {:id (full-id (:id fragment))}
               (:intent ctx)   (assoc :data-intent (name (:intent ctx)))
               (:priority ctx) (assoc :data-priority (name (:priority ctx)))
               (:frame ctx)    (assoc :data-frame (name (:frame ctx)))))

(defn- render-children
       "Renders a sequence of child fragments with the given context."
       [children ctx]
       (mapv #(render-fragment % ctx) children))

(defn- non-annotation-children
       "Returns children that are not status-annotation or summary fragments."
       [children]
       (filterv #(not (#{:status-annotation :summary} (:kind %))) children))

(defn- summary-child
       "Returns the first summary fragment from children, or nil."
       [children]
       (first (filterv #(= :summary (:kind %)) children)))

(defn- annotation-children
       "Returns children that are status-annotation fragments."
       [children]
       (filterv #(= :status-annotation (:kind %)) children))

(defn- intent-toggle
       "Returns a hiccup toggle button for switching section intent."
       []
       [:button {:class "intent-toggle"
                 :title "Toggle detail/summary"
                 :onclick "toggleIntent(this.parentElement)"}
        "\u25C9"])

;;; Section — all sections use <details> for collapsibility

(defmethod render-fragment :section [fragment ctx]
           (let [child-ctx   (ctx/section-ctx fragment ctx)
                 children    (:children fragment)
                 annotations (annotation-children children)
                 summ        (summary-child children)
                 content     (non-annotation-children children)
                 attrs       (section-attrs fragment child-ctx)
                 badges      (render-children annotations child-ctx)
                 body        (render-children content child-ctx)
                 summary-el  (when summ
                                   [:span {:class "section-summary"} (:text summ)])]
                (cond
                 (= :hero (:intent ctx))
                 (into [:header attrs
                        (into [(heading-tag (:depth ctx)) (:title fragment)] badges)]
                       body)

                 (zero? (:depth ctx))
                 (into [:section attrs
                        (into [(heading-tag (:depth ctx)) (:title fragment)] badges)]
                       body)

                 :else
                 (into [:details (merge attrs {:open true :class "section"})
                        (into [:summary
                               (intent-toggle)
                               [:span {:class "section-title"} (:title fragment)]
                               summary-el]
                              badges)]
                       body))))

;;; Table

(defmethod render-fragment :table [{:keys [id columns rows]} ctx]
           (let [attrs  (cond-> {}
                                id         (assoc :id (full-id id))
                                (:frame ctx) (assoc :data-frame (name (:frame ctx))))
                 header [:thead (into [:tr] (mapv (fn [c] [:th (name c)]) columns))]
                 tbody  (into [:tbody]
                              (mapv (fn [row]
                                        (into [:tr] (mapv (fn [c] [:td (str (get row c ""))]) columns)))
                                    rows))]
                [:table attrs header tbody]))

;;; Prose

(defmethod render-fragment :prose [{:keys [id text]} _ctx]
           (if id
               [:p {:id (full-id id)} text]
               [:p text]))

;;; Diagram spec

(defmethod render-fragment :diagram-spec [{:keys [dialect data]} _ctx]
           [:pre {:class "mermaid"} (h/raw (mermaid/render-mermaid dialect data))])

;;; Cross-ref

(defmethod render-fragment :cross-ref [{:keys [target-id label]} ctx]
           [:a {:href (str (:base-url ctx) "#" (full-id target-id))
                :class "cross-ref"}
            label])

;;; Code block

(defmethod render-fragment :code-block [{:keys [id language code]} _ctx]
           [:pre (cond-> {:class (str "code-block language-" language)}
                         id (assoc :id (full-id id)))
            [:code {:class (str "language-" language)} code]])

;;; Summary — consumed by section renderer, not rendered standalone

(defmethod render-fragment :summary [_ _] nil)

;;; Status annotation

(defmethod render-fragment :status-annotation [{:keys [status detail]} _ctx]
           (let [attrs (cond-> {:class "badge"
                                :data-status (name status)}
                               detail (assoc :title (pr-str detail)))]
                [:span attrs (str/upper-case (name status))]))
