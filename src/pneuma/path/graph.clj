(ns pneuma.path.graph
    "Pure graph algorithms for cycle detection in directed graphs.
  Provides Johnson's algorithm (1975) for finding all elementary
  circuits, and helpers to build directed graphs from morphism
  registries."
    (:require [clojure.set :as set]))

(defn registry->graph
      "Builds a directed adjacency map from a morphism registry.
  Returns {formalism-kind #{successor-formalism-kinds}}.
  All nodes (sources and targets) appear as keys."
      [registry]
      (reduce-kv
       (fn [g _id morphism]
           (-> g
               (update (:from morphism) (fnil conj #{}) (:to morphism))
               (update (:to morphism) #(or % #{}))))
       {}
       registry))

(defn registry->edge-index
      "Builds an index from [from to] pairs to vectors of morphism records.
  Used to resolve node-level circuits to morphism-level paths."
      [registry]
      (reduce-kv
       (fn [idx _id morphism]
           (update idx [(:from morphism) (:to morphism)] (fnil conj []) morphism))
       {}
       registry))

(defn- restrict-graph
       "Returns the subgraph induced by the given node set."
       [graph nodes]
       (reduce-kv
        (fn [g node succs]
            (if (contains? nodes node)
                (assoc g node (set/intersection succs nodes))
                g))
        {}
        graph))

;;; Tarjan's SCC

(defn- tarjan-sccs
       "Finds strongly connected components using Tarjan's algorithm.
  Returns a vector of node sets."
       [graph]
       (let [state (atom {:index 0 :stack [] :on-stack #{}
                          :indices {} :lowlinks {} :sccs []})]
            (letfn [(strongconnect [v]
                                   (let [idx (:index @state)]
                                        (swap! state #(-> %
                                                          (assoc-in [:indices v] idx)
                                                          (assoc-in [:lowlinks v] idx)
                                                          (update :index inc)
                                                          (update :stack conj v)
                                                          (update :on-stack conj v)))
                                        (doseq [w (get graph v #{})]
                                               (if-not (contains? (:indices @state) w)
                                                       (do (strongconnect w)
                                                           (swap! state update-in [:lowlinks v]
                                                                  min (get-in @state [:lowlinks w])))
                                                       (when (contains? (:on-stack @state) w)
                                                             (swap! state update-in [:lowlinks v]
                                                                    min (get-in @state [:indices w])))))
                                        (when (= (get-in @state [:lowlinks v])
                                                 (get-in @state [:indices v]))
                                              (let [stk (:stack @state)
                                                    pos (.indexOf ^java.util.List stk v)
                                                    scc (set (subvec stk pos))]
                                                   (swap! state #(-> %
                                                                     (assoc :stack (subvec stk 0 pos))
                                                                     (update :on-stack set/difference scc)
                                                                     (update :sccs conj scc)))))))]
                   (doseq [v (keys graph)]
                          (when-not (contains? (:indices @state) v)
                                    (strongconnect v)))
                   (:sccs @state))))

;;; Johnson's algorithm

(defn elementary-circuits
      "Finds all elementary circuits using Johnson's algorithm (1975).
  Graph is {node #{successors}}. Returns a vector of vectors, each
  a sequence of distinct nodes forming a simple cycle. The cycle
  implicitly closes from the last node back to the first."
      [graph]
      (let [nodes (vec (sort (keys graph)))
            result (atom [])]
           (doseq [s nodes]
                  (let [remaining (into #{} (drop-while #(not= % s)) nodes)
                        sub (restrict-graph graph remaining)
                        sccs (tarjan-sccs sub)
                        scc (first (filter #(contains? % s) sccs))]
                       (when (and scc
                                  (or (> (count scc) 1)
                                      (contains? (get sub s #{}) s)))
                             (let [scc-graph (restrict-graph sub scc)
                                   blocked (atom #{})
                                   b-sets (atom {})
                                   stack (atom [])]
                                  (letfn [(unblock [u]
                                                   (swap! blocked disj u)
                                                   (doseq [w (get @b-sets u #{})]
                                                          (swap! b-sets update u disj w)
                                                          (when (contains? @blocked w)
                                                                (unblock w))))
                                          (circuit [v]
                                                   (let [found (atom false)]
                                                        (swap! stack conj v)
                                                        (swap! blocked conj v)
                                                        (doseq [w (get scc-graph v #{})]
                                                               (if (= w s)
                                                                   (do (swap! result conj (vec @stack))
                                                                       (reset! found true))
                                                                   (when-not (contains? @blocked w)
                                                                             (when (circuit w)
                                                                                   (reset! found true)))))
                                                        (if @found
                                                            (unblock v)
                                                            (doseq [w (get scc-graph v #{})]
                                                                   (swap! b-sets update w (fnil conj #{}) v)))
                                                        (swap! stack pop)
                                                        @found))]
                                         (circuit s))))))
           @result))
