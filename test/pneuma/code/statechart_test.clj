(ns pneuma.code.statechart-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.statechart :as sc]
              [pneuma.code.protocol :as cp]
              [pneuma.code.statechart]))

(deftest statechart-code-emission-test
  ;; ->code emits a code fragment with defmulti, defmethod per transition,
  ;; fill points for business logic, and metadata about the formalism.
         (testing "Statechart ->code"
                  (let [chart (sc/statechart
                               {:label "session"
                                :states #{:idle :generating :error}
                                :initial {}
                                :transitions [{:source :idle :event :submit :target :generating
                                               :raise :start-gen}
                                              {:source :generating :event :complete :target :idle}
                                              {:source :generating :event :fail :target :error}]})
                        code (cp/->code chart {:target-ns 'agent.handlers})]

                       (testing "contains the target namespace"
                                (is (= 'agent.handlers (:namespace code))))

                       (testing "contains a defmulti form"
                                (let [multi (first (:forms code))]
                                     (is (= :defmulti (:type multi)))
                                     (is (= 'handle-event (:name multi)))))

                       (testing "contains one defmethod per transition"
                                (let [methods (filterv #(= :defmethod (:type %)) (:forms code))]
                                     (is (= 3 (count methods)))
                                     (is (= #{:submit :complete :fail}
                                            (set (map :dispatch-val methods))))))

                       (testing "defmethod has source and target states"
                                (let [submit (first (filterv #(= :submit (:dispatch-val %))
                                                             (:forms code)))]
                                     (is (= :idle (:source submit)))
                                     (is (= :generating (:target submit)))))

                       (testing "produces fill manifest with update-db entries"
                                (let [manifest (:fill-manifest code)]
                                     (is (contains? manifest :submit/update-db))
                                     (is (contains? manifest :complete/update-db))
                                     (is (= '[db session-id event]
                                            (:args (get manifest :submit/update-db))))))

                       (testing "includes formalism metadata"
                                (is (= :statechart (get-in code [:metadata :formalism])))
                                (is (= 3 (get-in code [:metadata :transition-count])))))))
