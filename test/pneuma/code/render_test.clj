(ns pneuma.code.render-test
    (:require [clojure.string :as str]
              [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.statechart :as sc]
              [pneuma.formalism.capability :as cap]
              [pneuma.code.protocol :as cp]
              [pneuma.code.render :as render]
              [pneuma.code.statechart]
              [pneuma.code.capability]))

(deftest render-source-test
  ;; Verifies that render-source produces valid-looking Clojure source
  ;; from a code fragment, including ns form, defmulti, and defmethods.
         (testing "render-source"
                  (let [chart (sc/statechart
                               {:label "session"
                                :states #{:idle :active}
                                :initial {}
                                :transitions [{:source :idle :event :start :target :active}]})
                        code (cp/->code chart {:target-ns 'agent.handlers})
                        source (render/render-source code)]

                       (testing "contains ns declaration"
                                (is (str/includes? source "(ns agent.handlers")))

                       (testing "contains DO NOT EDIT warning"
                                (is (str/includes? source "DO NOT EDIT")))

                       (testing "contains defmulti"
                                (is (str/includes? source "defmulti handle-event")))

                       (testing "contains defmethod"
                                (is (str/includes? source "defmethod handle-event :start"))))))

(deftest render-source-capability-test
  ;; Verifies render-source for capability guard checks (no fill points).
         (testing "render-source for capability"
                  (let [caps (cap/capability-set
                              {:label "test caps"
                               :id :agent
                               :dispatch #{:op-a}})
                        code (cp/->code caps {:target-ns 'agent.caps})
                        source (render/render-source code)]

                       (testing "contains guard definition"
                                (is (str/includes? source "agent-dispatch-guard"))))))

(deftest render-manifest-test
  ;; Verifies render-manifest produces EDN string.
         (testing "render-manifest"
                  (let [manifest {:submit/update-db {:args '[db sid event]
                                                     :returns :db
                                                     :doc "Update db"}}
                        edn-str (render/render-manifest manifest)]

                       (testing "contains the fill key"
                                (is (str/includes? edn-str "submit/update-db")))

                       (testing "is non-empty"
                                (is (pos? (count edn-str)))))))

(deftest render-fills-skeleton-test
  ;; Verifies render-fills-skeleton produces a fills ns with TODOs.
         (testing "render-fills-skeleton"
                  (let [manifest {:submit/messages {:args '[db sid]
                                                    :returns :messages
                                                    :doc "Extract messages"}}
                        skeleton (render/render-fills-skeleton
                                  'agent.handlers manifest)]

                       (testing "contains fills namespace"
                                (is (str/includes? skeleton "agent.handlers.fills")))

                       (testing "contains NEVER regenerated warning"
                                (is (str/includes? skeleton "NEVER regenerated")))

                       (testing "contains reg-fill call"
                                (is (str/includes? skeleton "reg-fill")))

                       (testing "contains TODO"
                                (is (str/includes? skeleton "TODO"))))))
