(ns pneuma.code.path
    "Code generation for composed path cycle tests.
  Discovers elementary circuits in the morphism graph and generates
  test assertion data for cycle closure (A13) and adjacency (A14)
  properties, plus per-step boundary test assertions via ICodeConnection."
    (:require [pneuma.code.protocol :as cp]
              [pneuma.path.core :as path]))

(defn- step-test-assertions
       "Generates test assertions for a single morphism step within a cycle,
  using the morphism's ICodeConnection if available."
       [step source target opts]
       (when (satisfies? cp/ICodeConnection step)
             (cp/->code-conn step source target opts)))

(defn path-test
      "Generates test assertion data for a single ComposedPath.
  Returns a map with closure, adjacency, and per-step boundary assertions."
      [composed-path formalisms opts]
      (let [steps (:steps composed-path)
            step-tests (into []
                             (keep
                              (fn [step]
                                  (let [source (get formalisms (:from step))
                                        target (get formalisms (:to step))]
                                       (when (and source target)
                                             (step-test-assertions step source target opts)))))
                             steps)]
           {:path-id (:id composed-path)
            :step-count (count steps)
            :assertions
            {:closure {:assertion :cycle-closure
                       :first-from (:from (first steps))
                       :last-to (:to (peek steps))
                       :message (str "Cycle " (:id composed-path)
                                     " must close: first source = last target")}
             :adjacency (mapv (fn [[step-i step-j]]
                                  {:assertion :adjacency
                                   :step-target (:to step-i)
                                   :next-source (:from step-j)
                                   :message (str (:id step-i) " target must equal "
                                                 (:id step-j) " source")})
                              (partition 2 1 steps))
             :boundary-tests step-tests}}))

(defn path-tests
      "Discovers all composed paths from a morphism registry and generates
  test assertion data for each. Returns a vector of path test maps."
      [registry formalisms opts]
      (let [paths (path/find-paths registry)]
           (mapv #(path-test % formalisms opts) paths)))
