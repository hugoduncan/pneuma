(ns pneuma.path.core
    "Composed paths through the morphism graph.
  Discovers elementary circuits via Johnson's algorithm, resolves
  them to ComposedPath records, and checks cycle-level axioms.

  Axiom A13 (cycle closure) is verified structurally: the last
  step's target must equal the first step's source. Axiom A14
  (precondition chaining) is verified structurally: each step's
  target must equal the next step's source. Semantic precondition
  chaining belongs in the Lean projection layer."
    (:require [clojure.string :as str]
              [pneuma.path.graph :as graph]))

(defrecord ComposedPath [id steps])

(defn- derive-path-id
       "Derives a keyword id from the ordered morphism steps."
       [morphisms]
       (keyword (str/join "->" (map #(name (:id %)) morphisms))))

(defn circuit->paths
      "Resolves a node-level circuit to ComposedPath records.
  Returns a vector of ComposedPaths — one per combination of
  morphisms when multiple morphisms exist on the same edge.
  Circuit is a vector of nodes where the last connects back
  to the first."
      [edge-index circuit]
      (let [edges (mapv (fn [[from to]]
                            (get edge-index [from to]))
                        (partition 2 1 (conj circuit (first circuit))))]
           (if (some nil? edges)
               []
               (let [combos (reduce (fn [acc morphisms]
                                        (for [prefix acc
                                              m morphisms]
                                             (conj prefix m)))
                                    [[]]
                                    edges)]
                    (mapv (fn [steps]
                              (->ComposedPath (derive-path-id steps) steps))
                          combos)))))

(defn check-closure
      "Checks axiom A13: the cycle closes back to its starting formalism.
  Returns a gap map."
      [path]
      (let [steps (:steps path)
            first-from (:from (first steps))
            last-to (:to (peek steps))]
           (if (= first-from last-to)
               {:layer :path
                :id (:id path)
                :status :conforms
                :detail {:axiom :A13-cycle-closure}}
               {:layer :path
                :id (:id path)
                :status :diverges
                :detail {:axiom :A13-cycle-closure
                         :first-from first-from
                         :last-to last-to}})))

(defn check-adjacency
      "Checks axiom A14 (structural form): each step's target equals
  the next step's source. Returns a vector of gap maps, one per
  adjacent pair."
      [path]
      (let [steps (:steps path)]
           (into []
                 (map-indexed
                  (fn [i [step-i step-j]]
                      (let [target (:to step-i)
                            source (:from step-j)]
                           (if (= target source)
                               {:layer :path
                                :id (:id path)
                                :status :conforms
                                :detail {:axiom :A14-adjacency
                                         :step-index i}}
                               {:layer :path
                                :id (:id path)
                                :status :diverges
                                :detail {:axiom :A14-adjacency
                                         :step-index i
                                         :expected-source target
                                         :actual-source source}}))))
                 (partition 2 1 steps))))

(defn check-path
      "Checks all axioms for a single ComposedPath.
  Returns a vector of gap maps."
      [path]
      (into [(check-closure path)]
            (check-adjacency path)))

(defn find-paths
      "Discovers all composed paths from a morphism registry.
  Returns a vector of ComposedPath records."
      [registry]
      (let [g (graph/registry->graph registry)
            edge-index (graph/registry->edge-index registry)
            circuits (graph/elementary-circuits g)]
           (into [] (mapcat #(circuit->paths edge-index %)) circuits)))

(defn check-all-paths
      "Discovers all composed paths and checks them.
  Returns a vector of gap maps tagged :layer :path."
      [registry]
      (into [] (mapcat check-path) (find-paths registry)))
