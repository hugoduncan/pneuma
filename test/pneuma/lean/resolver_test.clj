(ns pneuma.lean.resolver-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.resolver :as resolver]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.resolver]))

(deftest resolver-lean-emission-test
  ;; ->lean emits ResolverId/Attribute inductives, resolver defs, and completeness.
         (testing "ResolverGraph ->lean"
                  (let [rg (resolver/resolver-graph
                            {:declarations
                             [{:id     :session-msgs
                               :input  #{:session/id}
                               :output #{:session/messages}
                               :source :local}
                              {:id     :git-status
                               :input  #{:session/dir}
                               :output #{:git/status}
                               :source [:external :git]}]})
                        lean-src (lp/->lean rg)]

                       (testing "contains ResolverId inductive"
                                (is (str/includes? lean-src "inductive ResolverId where"))
                                (is (str/includes? lean-src "| session_msgs"))
                                (is (str/includes? lean-src "| git_status")))

                       (testing "contains Attribute inductive"
                                (is (str/includes? lean-src "inductive Attribute where"))
                                (is (str/includes? lean-src "| session_id"))
                                (is (str/includes? lean-src "| session_messages"))
                                (is (str/includes? lean-src "| git_status")))

                       (testing "contains resolver input/output defs"
                                (is (str/includes? lean-src "session_msgs_input"))
                                (is (str/includes? lean-src "session_msgs_output"))
                                (is (str/includes? lean-src "git_status_input"))
                                (is (str/includes? lean-src "git_status_output")))

                       (testing "contains completeness"
                                (is (str/includes? lean-src "allResolvers_complete"))
                                (is (str/includes? lean-src "= 2")))

                       (testing "contains reachability scaffold"
                                (is (str/includes? lean-src "chase_terminates"))))))
