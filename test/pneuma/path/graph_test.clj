(ns pneuma.path.graph-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.path.graph :as graph]
              [pneuma.morphism.existential :as ex]))

;; Tests for pure graph algorithms: adjacency map construction
;; from morphism registries, and Johnson's algorithm for
;; elementary circuit detection.

(deftest registry->graph-test
  ;; Contracts: registry->graph builds an adjacency map where
  ;; every source appears with its targets, and every target
  ;; appears as a key (possibly with empty successors).
         (testing "registry->graph"
                  (testing "builds adjacency from morphism registry"
                           (let [registry {:m1 (ex/existential-morphism
                                                {:id :m1 :from :a :to :b
                                                 :source-ref-kind :r1 :target-ref-kind :r2})
                                           :m2 (ex/existential-morphism
                                                {:id :m2 :from :b :to :c
                                                 :source-ref-kind :r1 :target-ref-kind :r2})}
                                 g (graph/registry->graph registry)]
                                (is (= #{:b} (get g :a)))
                                (is (= #{:c} (get g :b)))
                                (is (= #{} (get g :c))
                                    "target-only nodes have empty successor sets")))

                  (testing "merges multiple targets from same source"
                           (let [registry {:m1 (ex/existential-morphism
                                                {:id :m1 :from :a :to :b
                                                 :source-ref-kind :r1 :target-ref-kind :r2})
                                           :m2 (ex/existential-morphism
                                                {:id :m2 :from :a :to :c
                                                 :source-ref-kind :r1 :target-ref-kind :r2})}
                                 g (graph/registry->graph registry)]
                                (is (= #{:b :c} (get g :a)))))

                  (testing "returns empty map for empty registry"
                           (is (= {} (graph/registry->graph {}))))))

(deftest registry->edge-index-test
  ;; Contracts: registry->edge-index groups morphisms by
  ;; their [from to] pair, preserving all morphisms per edge.
         (testing "registry->edge-index"
                  (testing "indexes morphisms by edge pair"
                           (let [m1 (ex/existential-morphism
                                     {:id :m1 :from :a :to :b
                                      :source-ref-kind :r1 :target-ref-kind :r2})
                                 m2 (ex/existential-morphism
                                     {:id :m2 :from :a :to :b
                                      :source-ref-kind :r3 :target-ref-kind :r4})
                                 idx (graph/registry->edge-index {:m1 m1 :m2 m2})]
                                (is (= 2 (count (get idx [:a :b]))))
                                (is (= #{:m1 :m2}
                                       (into #{} (map :id) (get idx [:a :b]))))))))

(deftest elementary-circuits-empty-test
  ;; Contracts: empty and acyclic graphs produce no circuits.
         (testing "elementary-circuits"
                  (testing "returns empty for empty graph"
                           (is (= [] (graph/elementary-circuits {}))))

                  (testing "returns empty for acyclic graph"
                           (is (= [] (graph/elementary-circuits
                                      {:a #{:b} :b #{:c} :c #{}}))))))

(deftest elementary-circuits-self-loop-test
  ;; Contracts: a self-loop is the simplest elementary circuit.
         (testing "elementary-circuits"
                  (testing "finds self-loop"
                           (let [circuits (graph/elementary-circuits {:a #{:a}})]
                                (is (= 1 (count circuits)))
                                (is (= #{[:a]} (set circuits)))))))

(deftest elementary-circuits-triangle-test
  ;; Contracts: a directed triangle has exactly one circuit.
         (testing "elementary-circuits"
                  (testing "finds simple triangle cycle"
                           (let [circuits (graph/elementary-circuits
                                           {:a #{:b} :b #{:c} :c #{:a}})]
                                (is (= 1 (count circuits)))
                                (is (= 3 (count (first circuits))))
                                (is (= #{:a :b :c} (set (first circuits))))))))

(deftest elementary-circuits-two-overlapping-test
  ;; Contracts: two cycles sharing a node are found independently.
         (testing "elementary-circuits"
                  (testing "finds two cycles sharing a node"
                           (let [circuits (graph/elementary-circuits
                                           {:a #{:b :c} :b #{:a} :c #{:a}})]
                                (is (= 2 (count circuits)))
                                (is (= #{#{:a :b} #{:a :c}}
                                       (into #{} (map set) circuits)))))))

(deftest elementary-circuits-complex-test
  ;; Contracts: overlapping cycles of different lengths
  ;; are all enumerated.
         (testing "elementary-circuits"
                  (testing "finds all cycles in graph with shared edges"
                           (let [circuits (graph/elementary-circuits
                                           {:a #{:b} :b #{:c :a} :c #{:a}})]
                                (is (= 2 (count circuits)))
                                (is (= #{#{:a :b} #{:a :b :c}}
                                       (into #{} (map set) circuits)))))))

(deftest elementary-circuits-disconnected-test
  ;; Contracts: disconnected cycles are found independently.
         (testing "elementary-circuits"
                  (testing "finds cycles in disconnected components"
                           (let [circuits (graph/elementary-circuits
                                           {:a #{:b} :b #{:a}
                                            :c #{:d} :d #{:c}
                                            :e #{}})]
                                (is (= 2 (count circuits)))
                                (is (= #{#{:a :b} #{:c :d}}
                                       (into #{} (map set) circuits)))))))
