(ns pneuma.doc.html.context-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.doc.html.context :as ctx]
              [pneuma.doc.fragment :as frag]))

;; Tests for semantic rendering context creation and propagation.
;; Contracts: context defaults are correct; priority inferred from
;; gap annotations; frame inferred from fragment id namespace.

(deftest default-ctx-test
         (testing "default-ctx"
                  (testing "returns expected defaults"
                           (let [c (ctx/default-ctx)]
                                (is (= :detail (:intent c)))
                                (is (nil? (:priority c)))
                                (is (= :document (:frame c)))
                                (is (= 0 (:depth c)))
                                (is (= "" (:base-url c)))))
                  (testing "accepts overrides"
                           (let [c (ctx/default-ctx {:intent :hero :depth 3})]
                                (is (= :hero (:intent c)))
                                (is (= 3 (:depth c)))))))

(deftest infer-priority-test
         (testing "infer-priority"
                  (testing "returns nil when no annotations"
                           (let [f (frag/section :s "S" [(frag/prose :p "text")])]
                                (is (nil? (ctx/infer-priority f)))))
                  (testing "returns :high for :diverges"
                           (let [f (frag/section :s "S"
                                                 [(frag/status-annotation :s :diverges nil)])]
                                (is (= :high (ctx/infer-priority f)))))
                  (testing "returns :medium for :absent"
                           (let [f (frag/section :s "S"
                                                 [(frag/status-annotation :s :absent nil)])]
                                (is (= :medium (ctx/infer-priority f)))))
                  (testing "returns :low for :conforms"
                           (let [f (frag/section :s "S"
                                                 [(frag/status-annotation :s :conforms nil)])]
                                (is (= :low (ctx/infer-priority f)))))
                  (testing "returns highest severity when mixed"
                           (let [f (frag/section :s "S"
                                                 [(frag/status-annotation :s :conforms nil)
                                                  (frag/status-annotation :s :diverges nil)])]
                                (is (= :high (ctx/infer-priority f)))))))

(deftest infer-frame-test
         (testing "infer-frame"
                  (testing "returns :morphism for morphism/* ids"
                           (is (= :morphism (ctx/infer-frame :morphism/root :object))))
                  (testing "returns :path for path/* ids"
                           (is (= :path (ctx/infer-frame :path/cycle-1 :object))))
                  (testing "returns :document for gap/* ids"
                           (is (= :document (ctx/infer-frame :gap/summary :object))))
                  (testing "returns fallback for other ids"
                           (is (= :object (ctx/infer-frame :statechart :object))))
                  (testing "returns fallback for nil id"
                           (is (= :object (ctx/infer-frame nil :object))))))

(deftest section-ctx-test
         (testing "section-ctx"
                  (testing "increments depth"
                           (let [f   (frag/section :s "S" [])
                                 c   (ctx/default-ctx)
                                 c'  (ctx/section-ctx f c)]
                                (is (= 1 (:depth c')))))
                  (testing "derives priority from annotations"
                           (let [f   (frag/section :s "S"
                                                   [(frag/status-annotation :s :diverges nil)])
                                 c   (ctx/default-ctx)
                                 c'  (ctx/section-ctx f c)]
                                (is (= :high (:priority c')))))
                  (testing "inherits parent priority when no annotations"
                           (let [f   (frag/section :s "S" [(frag/prose :p "x")])
                                 c   (ctx/default-ctx {:priority :medium})
                                 c'  (ctx/section-ctx f c)]
                                (is (= :medium (:priority c')))))
                  (testing "derives frame from id namespace"
                           (let [f   (frag/section :morphism/root "M" [])
                                 c   (ctx/default-ctx)
                                 c'  (ctx/section-ctx f c)]
                                (is (= :morphism (:frame c')))))))
