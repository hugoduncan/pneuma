(ns pneuma.code.capability-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.capability :as cap]
              [pneuma.code.protocol :as cp]
              [pneuma.code.capability]))

(deftest capability-code-emission-test
  ;; ->code emits capability guard checks — often 100% generated
  ;; with no fill points.
         (testing "CapabilitySet ->code"
                  (let [caps (cap/capability-set
                              {:label "agent caps"
                               :id :agent
                               :dispatch #{:submit :approve}
                               :subscribe #{:messages :status}
                               :query #{:user-info}})
                        code (cp/->code caps {:target-ns 'agent.caps})]

                       (testing "contains guard check forms"
                                (let [guards (:forms code)]
                                     (is (= 3 (count guards)))
                                     (is (= #{:dispatch :subscribe :query}
                                            (set (map :kind guards))))))

                       (testing "dispatch guard lists allowed operations"
                                (let [dg (first (filterv #(= :dispatch (:kind %))
                                                         (:forms code)))]
                                     (is (= #{:submit :approve} (:allowed dg)))))

                       (testing "has empty fill manifest"
                                (is (empty? (:fill-manifest code))))

                       (testing "includes formalism metadata"
                                (is (= :capability-set (get-in code [:metadata :formalism])))
                                (is (= 3 (get-in code [:metadata :guard-count])))))))
