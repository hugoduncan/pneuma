(ns pneuma.lean.containment-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.statechart :as sc]
              [pneuma.morphism.containment :as ct]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.containment]))

;; ->lean-conn emits boundary propositions for a containment morphism,
;; checking that source refs are members of the target set.

(deftest containment-lean-emission-test
         (testing "ContainmentMorphism ->lean-conn"
                  (let [caps (cap/capability-set
                              {:id :agent
                               :dispatch #{:start :stop}})
                        chart (sc/statechart
                               {:states #{:idle :active}
                                :hierarchy {:root #{:idle :active}}
                                :initial {:root :idle}
                                :transitions [{:source :idle :event :start :target :active}
                                              {:source :active :event :stop :target :idle}
                                              {:source :active :event :pause :target :idle}]})
                        morph (ct/containment-morphism
                               {:id              :caps->events
                                :from            :capability-set
                                :to              :statechart
                                :source-ref-kind :dispatch-refs
                                :target-ref-kind :event-ids})
                        lean-src (lp/->lean-conn morph caps chart)]

                       (testing "contains source inductive"
                                (is (str/includes? lean-src "inductive CapsEventsSource where"))
                                (is (str/includes? lean-src "| start"))
                                (is (str/includes? lean-src "| stop")))

                       (testing "contains target inductive"
                                (is (str/includes? lean-src "inductive CapsEventsTarget where"))
                                (is (str/includes? lean-src "| pause"))
                                (is (str/includes? lean-src "| start"))
                                (is (str/includes? lean-src "| stop")))

                       (testing "contains membership function"
                                (is (str/includes? lean-src "CapsEventsInTarget"))
                                (is (str/includes? lean-src "| .start => true"))
                                (is (str/includes? lean-src "| .stop => true")))

                       (testing "contains completeness theorems"
                                (is (str/includes? lean-src "allCapsEventsSource_complete"))
                                (is (str/includes? lean-src "allCapsEventsTarget_complete")))

                       (testing "contains boundary proposition"
                                (is (str/includes? lean-src "CapsEvents_containment_boundary"))
                                (is (str/includes? lean-src "InTarget s = true")))

                       (testing "contains header comment"
                                (is (str/includes? lean-src "ContainmentMorphism"))
                                (is (str/includes? lean-src "capability-set"))
                                (is (str/includes? lean-src "statechart"))))))
