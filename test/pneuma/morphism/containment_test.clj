(ns pneuma.morphism.containment-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.statechart :as sc]
              [pneuma.morphism.containment :as ct]))

;; Tests for containment morphisms using capability dispatch refs
;; checked against statechart event ids.
;; Contract: source refs must be a subset of target refs.

(def sc-with-events
     (sc/statechart
      {:states #{:idle :active}
       :hierarchy {:root #{:idle :active}}
       :initial {:root :idle}
       :transitions [{:source :idle :event :start :target :active}
                     {:source :active :event :stop :target :idle}
                     {:source :active :event :pause :target :idle}]}))

(def caps-within-bounds
     (cap/capability-set
      {:id :agent
       :dispatch #{:start :stop}}))

(def caps-out-of-bounds
     (cap/capability-set
      {:id :bad-agent
       :dispatch #{:start :stop :fly}}))

(def morphism
     (ct/containment-morphism
      {:id :caps->events
       :from :capability-set
       :to :statechart
       :source-ref-kind :dispatch-refs
       :target-ref-kind :event-ids}))

(deftest containment-conforms-test
  ;; All capability dispatch refs are contained in the statechart event ids.
         (testing "containment morphism"
                  (testing "conforms when all source refs are in target"
                           (let [gaps (p/check morphism caps-within-bounds sc-with-events {})]
                                (is (= 1 (count gaps)))
                                (is (= :conforms (:status (first gaps))))))))

(deftest containment-diverges-test
  ;; A capability set with a dispatch ref not in the statechart event ids.
         (testing "containment morphism"
                  (testing "diverges with out-of-bounds refs"
                           (let [gaps (p/check morphism caps-out-of-bounds sc-with-events {})]
                                (is (= 1 (count gaps)))
                                (is (= :diverges (:status (first gaps))))
                                (is (= :containment (:kind (first gaps))))
                                (is (contains? (-> gaps first :detail :out-of-bounds)
                                               :fly))))))
