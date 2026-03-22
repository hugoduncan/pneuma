(ns pneuma.path.core-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.path.core :as path]
              [pneuma.morphism.existential :as ex]))

;; Tests for composed path discovery and axiom checking.
;; Uses fabricated cyclic registries to exercise cycle detection
;; and gap production independently of real formalisms.

(defn- make-morphism
       "Helper: creates an existential morphism with the given id, from, and to."
       [id from to]
       (ex/existential-morphism
        {:id id :from from :to to
         :source-ref-kind :refs :target-ref-kind :refs}))

(def cyclic-registry
     "A three-morphism cyclic registry: :a → :b → :c → :a."
     {:a->b (make-morphism :a->b :a :b)
      :b->c (make-morphism :b->c :b :c)
      :c->a (make-morphism :c->a :c :a)})

(def acyclic-registry
     "A two-morphism acyclic registry: :a → :b → :c."
     {:a->b (make-morphism :a->b :a :b)
      :b->c (make-morphism :b->c :b :c)})

(def two-cycle-registry
     "A registry with two overlapping cycles sharing node :a."
     {:a->b (make-morphism :a->b :a :b)
      :b->a (make-morphism :b->a :b :a)
      :a->c (make-morphism :a->c :a :c)
      :c->a (make-morphism :c->a :c :a)})

(deftest find-paths-test
  ;; Contracts: find-paths discovers ComposedPath records from
  ;; the morphism graph. Acyclic registries produce no paths.
         (testing "find-paths"
                  (testing "finds one cycle in triangular registry"
                           (let [paths (path/find-paths cyclic-registry)]
                                (is (= 1 (count paths)))
                                (is (= 3 (count (:steps (first paths)))))
                                (is (instance? pneuma.path.core.ComposedPath (first paths)))))

                  (testing "returns empty for acyclic registry"
                           (is (empty? (path/find-paths acyclic-registry))))

                  (testing "finds two cycles in overlapping registry"
                           (let [paths (path/find-paths two-cycle-registry)]
                                (is (= 2 (count paths)))))))

(deftest check-closure-test
  ;; Contracts: A13 cycle closure — the last step's :to must
  ;; equal the first step's :from.
         (testing "check-closure"
                  (testing "conforms for valid cycle"
                           (let [paths (path/find-paths cyclic-registry)
                                 gap (path/check-closure (first paths))]
                                (is (= :conforms (:status gap)))
                                (is (= :path (:layer gap)))
                                (is (= :A13-cycle-closure (-> gap :detail :axiom)))))

                  (testing "diverges for broken cycle"
                           (let [bad-path (path/->ComposedPath
                                           :broken
                                           [(make-morphism :a->b :a :b)
                                            (make-morphism :b->c :b :c)])
                                 gap (path/check-closure bad-path)]
                                (is (= :diverges (:status gap)))
                                (is (= :a (-> gap :detail :first-from)))
                                (is (= :c (-> gap :detail :last-to)))))))

(deftest check-adjacency-test
  ;; Contracts: A14 adjacency — each step's :to equals the
  ;; next step's :from.
         (testing "check-adjacency"
                  (testing "all conform for valid cycle"
                           (let [paths (path/find-paths cyclic-registry)
                                 gaps (path/check-adjacency (first paths))]
                                (is (every? #(= :conforms (:status %)) gaps))
                                (is (every? #(= :path (:layer %)) gaps))))

                  (testing "diverges for mismatched adjacency"
                           (let [bad-path (path/->ComposedPath
                                           :broken
                                           [(make-morphism :a->b :a :b)
                                            (make-morphism :x->a :x :a)])
                                 gaps (path/check-adjacency bad-path)]
                                (is (some #(= :diverges (:status %)) gaps))
                                (is (= 0 (-> (first (filter #(= :diverges (:status %)) gaps))
                                             :detail :step-index)))))))

(deftest check-path-test
  ;; Contracts: check-path combines closure and adjacency checks.
         (testing "check-path"
                  (testing "all conform for valid cycle"
                           (let [paths (path/find-paths cyclic-registry)
                                 gaps (path/check-path (first paths))]
                                (is (pos? (count gaps)))
                                (is (every? #(= :conforms (:status %)) gaps))))))

(deftest check-all-paths-test
  ;; Contracts: check-all-paths discovers and checks all paths,
  ;; returning gap maps tagged :layer :path.
         (testing "check-all-paths"
                  (testing "returns conforming gaps for valid cycles"
                           (let [gaps (path/check-all-paths cyclic-registry)]
                                (is (pos? (count gaps)))
                                (is (every? #(= :path (:layer %)) gaps))
                                (is (every? #(= :conforms (:status %)) gaps))))

                  (testing "returns empty for acyclic registry"
                           (is (empty? (path/check-all-paths acyclic-registry))))

                  (testing "checks all cycles in multi-cycle registry"
                           (let [gaps (path/check-all-paths two-cycle-registry)]
                                (is (pos? (count gaps)))
                                (is (every? #(= :path (:layer %)) gaps))))))

(deftest path-id-test
  ;; Contracts: path ids are derived from morphism ids.
         (testing "path id"
                  (testing "is derived from morphism step ids"
                           (let [paths (path/find-paths cyclic-registry)]
                                (is (keyword? (:id (first paths))))))))

(deftest multi-edge-resolution-test
  ;; Contracts: when multiple morphisms exist on the same edge,
  ;; each combination produces a distinct ComposedPath.
         (testing "circuit->paths"
                  (testing "enumerates combinations for multi-edge"
                           (let [registry {:m1 (make-morphism :m1 :a :b)
                                           :m2 (make-morphism :m2 :a :b)
                                           :m3 (make-morphism :m3 :b :a)}
                                 paths (path/find-paths registry)]
                                (is (= 2 (count paths))
                                    "two morphisms on a→b × one on b→a = two paths")
                                (is (= 2 (count (into #{} (map :id) paths)))
                                    "each path has a distinct id")))))
