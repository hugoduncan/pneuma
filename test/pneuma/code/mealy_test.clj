(ns pneuma.code.mealy-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.mealy :as mealy]
              [pneuma.code.protocol :as cp]
              [pneuma.code.mealy]))

(deftest mealy-code-emission-test
  ;; ->code emits handler stubs with guards, state updates, effect
  ;; emissions, and fill points for business logic.
         (testing "MealyHandlerSet ->code"
                  (let [handlers (mealy/mealy-handler-set
                                  {:label "session handlers"
                                   :declarations
                                   [{:id :submit
                                     :params [{:name :prompt :type :String}]
                                     :guards [{:check :idle? :args [:session-id]}]
                                     :updates [{:path [:conv-state] :op :set :value :generating}]
                                     :effects [{:op :ai-generate :fields {:model :String}}]}
                                    {:id :complete
                                     :params [{:name :response :type :String}]}]})
                        code (cp/->code handlers {:target-ns 'agent.handlers})]

                       (testing "contains one defmethod per handler"
                                (let [methods (filterv #(= :defmethod (:type %)) (:forms code))]
                                     (is (= 2 (count methods)))
                                     (is (= #{:submit :complete}
                                            (set (map :dispatch-val methods))))))

                       (testing "produces fill manifest entries"
                                (let [manifest (:fill-manifest code)]
                                     (is (contains? manifest :submit/update-db))
                                     (is (contains? manifest :submit/effects))
                                     (is (contains? manifest :complete/update-db))
          ;; complete has no effects, so no effects fill
                                     (is (not (contains? manifest :complete/effects)))))

                       (testing "includes formalism metadata"
                                (is (= :mealy (get-in code [:metadata :formalism])))
                                (is (= 2 (get-in code [:metadata :handler-count])))))))
