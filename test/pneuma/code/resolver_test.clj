(ns pneuma.code.resolver-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.resolver :as resolver]
              [pneuma.code.protocol :as cp]
              [pneuma.code.resolver]))

(deftest resolver-code-emission-test
  ;; ->code emits resolver skeletons with input/output declarations
  ;; and fill points for resolver body logic.
         (testing "ResolverGraph ->code"
                  (let [graph (resolver/resolver-graph
                               {:label "user resolvers"
                                :declarations
                                [{:id :user-by-id
                                  :input #{:user-id}
                                  :output #{:name :email}
                                  :source :local}
                                 {:id :perms-by-user
                                  :input #{:user-id}
                                  :output #{:permissions}
                                  :source [:external :auth-svc]}]})
                        code (cp/->code graph {:target-ns 'agent.resolvers})]

                       (testing "contains one form per resolver"
                                (is (= 2 (count (:forms code)))))

                       (testing "each resolver form has input/output"
                                (let [user-form (first (filterv #(= :user-by-id (:resolver-id %))
                                                                (:forms code)))]
                                     (is (= #{:user-id} (:input user-form)))
                                     (is (= #{:name :email} (:output user-form)))))

                       (testing "produces fill manifest with resolve entries"
                                (let [manifest (:fill-manifest code)]
                                     (is (contains? manifest :user-by-id/resolve))
                                     (is (contains? manifest :perms-by-user/resolve))))

                       (testing "includes formalism metadata"
                                (is (= :resolver (get-in code [:metadata :formalism])))
                                (is (= 2 (get-in code [:metadata :resolver-count])))))))
