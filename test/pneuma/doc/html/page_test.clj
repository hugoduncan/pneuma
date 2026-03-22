(ns pneuma.doc.html.page-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.doc.fragment :as frag]
              [pneuma.doc.html.page :as page]
              [pneuma.doc.render :as render]))

;; Tests for HTML page rendering and integration through render-html.
;; Contracts: render-page produces a complete HTML page; render-html
;; delegates correctly; page includes CSS, mermaid script, and TOC.

(deftest render-page-structure-test
         (testing "render-page"
                  (let [tree (frag/section :doc/root "Test Doc"
                                           [(frag/section :sc "Statechart"
                                                          [(frag/prose :p1 "States here")])
                                            (frag/section :es "Effects"
                                                          [(frag/prose :p2 "Operations")])])
                        html (page/render-page tree {})]
                       (testing "produces DOCTYPE declaration"
                                (is (str/starts-with? html "<!DOCTYPE html>")))
                       (testing "includes page title"
                                (is (str/includes? html "<title>Test Doc</title>")))
                       (testing "includes mermaid script"
                                (is (str/includes? html "mermaid.min.js")))
                       (testing "includes CSS custom properties"
                                (is (str/includes? html "--color-conforms")))
                       (testing "includes TOC nav"
                                (is (str/includes? html "<nav"))
                                (is (str/includes? html "#sc"))
                                (is (str/includes? html "Statechart")))
                       (testing "includes rendered content"
                                (is (str/includes? html "States here"))
                                (is (str/includes? html "Operations"))))))

(deftest render-page-with-gaps-test
         (testing "render-page"
                  (testing "renders gap annotations as badges"
                           (let [tree (frag/section :doc/root "Doc"
                                                    [(frag/section :sc "SC"
                                                                   [(frag/prose :p "text")
                                                                    (frag/status-annotation :sc :diverges
                                                                                            {:reason :missing})])])
                                 html (page/render-page tree {})]
                                (is (str/includes? html "DIVERGES"))
                                (is (str/includes? html "data-status"))
                                (is (str/includes? html "data-priority"))))))

(deftest render-page-with-diagram-test
         (testing "render-page"
                  (testing "renders mermaid diagrams"
                           (let [tree (frag/section :doc/root "Doc"
                                                    [(frag/diagram-spec :d1 :mermaid-state
                                                                        {:states [:idle :run]
                                                                         :transitions [[:idle :run "go"]]})])
                                 html (page/render-page tree {})]
                                (is (str/includes? html "class=\"mermaid\""))
                                (is (str/includes? html "stateDiagram-v2"))))))

(deftest render-html-integration-test
         (testing "render-html"
                  (testing "delegates to render-page"
                           (let [tree (frag/section :doc/root "Doc"
                                                    [(frag/prose :p "content")])
                                 html (render/render-html tree)]
                                (is (str/starts-with? html "<!DOCTYPE html>"))
                                (is (str/includes? html "content"))))))

(deftest render-page-title-override-test
         (testing "render-page"
                  (testing "accepts title override"
                           (let [tree (frag/section :doc/root "Doc" [])
                                 html (page/render-page tree {:title "Custom Title"})]
                                (is (str/includes? html "<title>Custom Title</title>"))))))
