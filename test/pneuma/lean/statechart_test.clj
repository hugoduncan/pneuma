(ns pneuma.lean.statechart-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.statechart :as sc]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.statechart]))

(deftest statechart-lean-emission-test
  ;; ->lean emits State/Event inductives, step function, and safety theorem.
         (testing "Statechart ->lean"
                  (let [chart (sc/statechart
                               {:states #{:idle :running :done}
                                :initial {:root :idle}
                                :hierarchy {:root #{:idle :running :done}}
                                :transitions
                                [{:source :idle :event :start :target :running}
                                 {:source :running :event :finish :target :done}
                                 {:source :done :event :reset :target :idle}]})
                        lean-src (lp/->lean chart)]

                       (testing "contains State inductive"
                                (is (str/includes? lean-src "inductive State where"))
                                (is (str/includes? lean-src "| idle"))
                                (is (str/includes? lean-src "| running"))
                                (is (str/includes? lean-src "| done")))

                       (testing "contains Event inductive"
                                (is (str/includes? lean-src "inductive Event where"))
                                (is (str/includes? lean-src "| start"))
                                (is (str/includes? lean-src "| finish"))
                                (is (str/includes? lean-src "| reset")))

                       (testing "contains step function"
                                (is (str/includes? lean-src "def step"))
                                (is (str/includes? lean-src ".idle, .start => .running")))

                       (testing "contains initial state"
                                (is (str/includes? lean-src "initialState")))

                       (testing "contains reachability definition"
                                (is (str/includes? lean-src "reachable")))

                       (testing "contains chart safety theorem"
                                (is (str/includes? lean-src "chart_safety")))

                       (testing "contains determinism theorem"
                                (is (str/includes? lean-src "step_deterministic")))

                       (testing "contains docstrings"
                                (is (str/includes? lean-src "/-- "))
                                (is (str/includes? lean-src "States of the"))
                                (is (str/includes? lean-src "Transition function"))
                                (is (str/includes? lean-src "all reachable states satisfy it"))))))
