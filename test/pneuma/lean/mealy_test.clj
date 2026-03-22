(ns pneuma.lean.mealy-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.mealy :as mealy]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.mealy]))

(deftest mealy-lean-emission-test
  ;; ->lean emits HandlerId inductive and completeness.
         (testing "MealyHandlerSet ->lean"
                  (let [handlers (mealy/mealy-handler-set
                                  {:label "test mealy"
                                   :declarations
                                   [{:id :submit
                                     :params [{:name :sid :type :String}]
                                     :guards [{:check :in-state? :args [:sid :idle]}]
                                     :effects [{:op :ai/generate
                                                :fields {:on-complete
                                                         [:event-ref :done]}}]}
                                    {:id :cancel}]})
                        lean-src (lp/->lean handlers)]

                       (testing "contains HandlerId inductive"
                                (is (str/includes? lean-src "inductive HandlerId where"))
                                (is (str/includes? lean-src "| submit"))
                                (is (str/includes? lean-src "| cancel")))

                       (testing "contains completeness"
                                (is (str/includes? lean-src "allHandlers_complete")))

                       (testing "contains count"
                                (is (str/includes? lean-src "= 2")))

                       (testing "contains guard state refs"
                                (is (str/includes? lean-src "guardStateRefs")))

                       (testing "contains emission op refs"
                                (is (str/includes? lean-src "emissionOpRefs"))))))
