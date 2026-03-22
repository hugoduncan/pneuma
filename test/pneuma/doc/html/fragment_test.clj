(ns pneuma.doc.html.fragment-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.doc.fragment :as frag]
              [pneuma.doc.html.context :as ctx]
              [pneuma.doc.html.fragment :as hfrag]))

;; Tests for HTML fragment rendering.
;; Contracts: each fragment kind produces correct hiccup structure;
;; sections are collapsible details elements with intent toggles;
;; semantic attributes propagate correctly.

(defn- hiccup->str
       "Converts hiccup to a string for content assertions."
       [hiccup]
       (pr-str hiccup))

(deftest full-id-test
         (testing "full-id"
                  (testing "preserves namespace with -- separator"
                           (is (= "morphism--root" (hfrag/full-id :morphism/root))))
                  (testing "returns name for unnamespaced keywords"
                           (is (= "statechart" (hfrag/full-id :statechart))))))

(deftest render-prose-test
         (testing "render-fragment"
                  (testing "prose produces a :p element"
                           (let [f (frag/prose :p1 "hello")
                                 h (hfrag/render-fragment f (ctx/default-ctx))]
                                (is (= :p (first h)))
                                (is (= "hello" (last h)))))))

(deftest render-table-test
         (testing "render-fragment"
                  (testing "table produces a :table element"
                           (let [f (frag/table :t1 [:name :age]
                                               [{:name "Alice" :age "30"}])
                                 h (hfrag/render-fragment f (ctx/default-ctx))]
                                (is (= :table (first h)))
                                (is (some #(= :thead (first %))
                                          (rest h)))))
                  (testing "table includes data-frame attribute"
                           (let [f (frag/table :t1 [:col] [{:col "v"}])
                                 h (hfrag/render-fragment f (ctx/default-ctx {:frame :morphism}))]
                                (is (= "morphism" (get-in h [1 :data-frame])))))))

(deftest render-section-collapsible-test
         (testing "render-fragment"
                  (testing "sections render as :details at all depths"
                           (let [f (frag/section :s1 "Title" [(frag/prose :p "x")])]
                                (doseq [depth [0 1 2 3]]
                                       (let [h (hfrag/render-fragment f (ctx/default-ctx {:depth depth}))]
                                            (is (= :details (first h))
                                                (str "depth " depth " should produce :details"))))))
                  (testing "section has open attribute"
                           (let [f (frag/section :s1 "Title" [(frag/prose :p "x")])
                                 h (hfrag/render-fragment f (ctx/default-ctx))]
                                (is (true? (:open (second h))))))
                  (testing "section has intent toggle button in summary"
                           (let [f (frag/section :s1 "Title" [(frag/prose :p "x")])
                                 h (hfrag/render-fragment f (ctx/default-ctx))
                                 summary (nth h 2)]
                                (is (= :summary (first summary)))
                                (is (some #(and (vector? %) (= :button (first %)))
                                          (rest summary)))))))

(deftest render-section-priority-test
         (testing "render-fragment"
                  (testing "section with diverges annotation gets data-priority high"
                           (let [f (frag/section :s1 "Title"
                                                 [(frag/prose :p "x")
                                                  (frag/status-annotation :s1 :diverges {:reason :x})])
                                 h (hfrag/render-fragment f (ctx/default-ctx))
                                 attrs (second h)]
                                (is (= "high" (:data-priority attrs)))))))

(deftest render-section-hero-test
         (testing "render-fragment"
                  (testing "section with hero intent produces :header"
                           (let [f (frag/section :gap/root "Gap Report"
                                                 [(frag/prose :p "summary")])
                                 h (hfrag/render-fragment f (ctx/default-ctx {:intent :hero}))]
                                (is (= :header (first h)))
                                (is (= "hero" (get-in h [1 :data-intent])))))))

(deftest render-section-namespaced-id-test
         (testing "render-fragment"
                  (testing "namespaced section id includes namespace"
                           (let [f (frag/section :morphism/root "Morphisms" [])
                                 h (hfrag/render-fragment f (ctx/default-ctx))
                                 attrs (second h)]
                                (is (= "morphism--root" (:id attrs)))))))

(deftest render-diagram-spec-test
         (testing "render-fragment"
                  (testing "diagram-spec produces :pre with mermaid class"
                           (let [f (frag/diagram-spec :d1 :mermaid-state
                                                      {:states [:idle] :transitions []})
                                 h (hfrag/render-fragment f (ctx/default-ctx))]
                                (is (= :pre (first h)))
                                (is (= "mermaid" (get-in h [1 :class])))
                                (is (str/includes? (last h) "stateDiagram-v2"))))))

(deftest render-cross-ref-test
         (testing "render-fragment"
                  (testing "cross-ref produces :a with href anchor"
                           (let [f (frag/cross-ref :target "See Target")
                                 h (hfrag/render-fragment f (ctx/default-ctx))]
                                (is (= :a (first h)))
                                (is (= "#target" (get-in h [1 :href])))
                                (is (= "See Target" (last h)))))
                  (testing "cross-ref with namespaced target uses full id"
                           (let [f (frag/cross-ref :morphism/root "Morphisms")
                                 h (hfrag/render-fragment f (ctx/default-ctx))]
                                (is (= "#morphism--root" (get-in h [1 :href])))))))

(deftest render-status-annotation-test
         (testing "render-fragment"
                  (testing "status-annotation produces :span badge"
                           (let [f (frag/status-annotation :s1 :conforms nil)
                                 h (hfrag/render-fragment f (ctx/default-ctx))]
                                (is (= :span (first h)))
                                (is (= "conforms" (get-in h [1 :data-status])))
                                (is (= "CONFORMS" (last h)))))
                  (testing "status-annotation with detail includes title"
                           (let [f (frag/status-annotation :s1 :diverges {:reason :x})
                                 h (hfrag/render-fragment f (ctx/default-ctx))]
                                (is (some? (get-in h [1 :title])))))))

(deftest render-composed-tree-test
         (testing "render-fragment"
                  (testing "composed tree renders all children"
                           (let [f (frag/section :root "Report"
                                                 [(frag/prose :p1 "intro")
                                                  (frag/table :t1 [:col] [{:col "v"}])
                                                  (frag/cross-ref :appendix "See App")
                                                  (frag/status-annotation :root :conforms nil)])
                                 h (hfrag/render-fragment f (ctx/default-ctx))
                                 s (hiccup->str h)]
                                (is (str/includes? s "intro"))
                                (is (str/includes? s ":table"))
                                (is (str/includes? s "See App"))
                                (is (str/includes? s "CONFORMS"))))))
