(ns pneuma.lean.optic-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.optic :as optic]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.optic]))

(deftest optic-lean-emission-test
  ;; ->lean emits OpticId inductive, classification, paths, and completeness.
         (testing "OpticDeclaration ->lean"
                  (let [od (optic/optic-declaration
                            {:label "test optics"
                             :declarations
                             [{:id :session-msgs
                               :optic-type :Lens
                               :params [{:name :sid :type :String}]
                               :path [:sessions :sid :messages]}
                              {:id :all-ids
                               :optic-type :Fold
                               :path [:session-ids]}
                              {:id :msg-count
                               :optic-type :Derived
                               :params [{:name :sid :type :String}]
                               :sources {:msgs [:sessions :sid :messages]}
                               :derivations {:count [:length :msgs]}}]})
                        lean-src (lp/->lean od)]

                       (testing "contains OpticId inductive"
                                (is (str/includes? lean-src "inductive OpticId where"))
                                (is (str/includes? lean-src "| session_msgs"))
                                (is (str/includes? lean-src "| all_ids"))
                                (is (str/includes? lean-src "| msg_count")))

                       (testing "contains OpticType inductive"
                                (is (str/includes? lean-src "inductive OpticType where"))
                                (is (str/includes? lean-src "| Lens"))
                                (is (str/includes? lean-src "| Derived")))

                       (testing "contains classification function"
                                (is (str/includes? lean-src "def opticType"))
                                (is (str/includes? lean-src ".session_msgs => .Lens"))
                                (is (str/includes? lean-src ".all_ids => .Fold"))
                                (is (str/includes? lean-src ".msg_count => .Derived")))

                       (testing "contains path declarations"
                                (is (str/includes? lean-src "session_msgs_path"))
                                (is (str/includes? lean-src "all_ids_path")))

                       (testing "contains source declarations for derived"
                                (is (str/includes? lean-src "msg_count_source_msgs")))

                       (testing "contains completeness"
                                (is (str/includes? lean-src "allOptics_complete"))
                                (is (str/includes? lean-src "= 3"))))))
