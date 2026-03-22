(ns pneuma.formalism.mealy-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.test.check.generators :as gen]
              [malli.core :as m]
              [pneuma.protocol :as p]
              [pneuma.formalism.mealy :as mealy]))

;; Three handler declarations: one minimal, one with params only,
;; one with guards/updates/effects like the submit-prompt example.
(def sample-declarations
     {:declarations
      [{:id :ping}

       {:id :set-name
        :params [{:name :session-id :type :String}
                 {:name :name :type :String}]}

       {:id :submit-prompt
        :params  [{:name :sid :type :String}
                  {:name :prompt :type :String}]
        :guards  [{:check :member :args [:sid [:keys-of :sessions]]}
                  {:check :in-state? :args [:sid :idle]}]
        :updates [{:path [:sessions :sid :messages]
                   :op   :append
                   :value {:role :user :content :prompt}}]
        :effects [{:op     :ai/generate
                   :fields {:session-id :sid
                            :messages   [:get [:sessions :sid :messages]]
                            :on-complete [:event-ref :generation-complete :sid]
                            :on-error    [:event-ref :generation-error :sid]}}]}]})

(deftest constructor-test
  ;; mealy-handler-set validates input and rejects duplicates or invalid input.
         (testing "mealy-handler-set"
                  (testing "accepts valid declarations"
                           (is (some? (mealy/mealy-handler-set sample-declarations))))

                  (testing "accepts single minimal handler"
                           (is (some? (mealy/mealy-handler-set
                                       {:declarations [{:id :ping}]}))))

                  (testing "rejects missing :declarations key"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (mealy/mealy-handler-set {}))))

                  (testing "rejects non-vector :declarations"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (mealy/mealy-handler-set {:declarations "bad"}))))

                  (testing "rejects declaration missing :id"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (mealy/mealy-handler-set
                                         {:declarations [{:params []}]}))))

                  (testing "rejects duplicate handler ids"
                           (is (thrown? clojure.lang.ExceptionInfo
                                        (mealy/mealy-handler-set
                                         {:declarations [{:id :ping}
                                                         {:id :ping}]}))))))

(deftest schema-projection-test
  ;; ->schema produces a Malli :multi schema dispatching on :handler-id.
         (let [hs     (mealy/mealy-handler-set sample-declarations)
               schema (p/->schema hs)]

              (testing "->schema"
                       (testing "produces a valid Malli schema"
                                (is (m/schema? (m/schema schema))))

                       (testing "validates conforming :ping entry (no params)"
                                (is (m/validate schema {:handler-id :ping})))

                       (testing "validates conforming :set-name entry"
                                (is (m/validate schema
                                                {:handler-id :set-name
                                                 :params     {:session-id "s1"
                                                              :name       "alice"}})))

                       (testing "validates conforming :submit-prompt entry"
                                (is (m/validate schema
                                                {:handler-id :submit-prompt
                                                 :params     {:sid    "s1"
                                                              :prompt "hello"}})))

                       (testing "rejects unknown handler-id"
                                (is (not (m/validate schema {:handler-id :bogus}))))

                       (testing "rejects :set-name with wrong param type"
                                (is (not (m/validate schema
                                                     {:handler-id :set-name
                                                      :params     {:session-id 999
                                                                   :name       "alice"}})))))))

(deftest monitor-projection-test
  ;; ->monitor checks trace entries for absent handlers, wrong emissions, and wrong updates.
         (let [hs      (mealy/mealy-handler-set sample-declarations)
               monitor (p/->monitor hs)]

              (testing "->monitor"
                       (testing "returns :ok for conforming :ping entry"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:handler-id :ping
                                                  :db-before  {}
                                                  :db-after   {}
                                                  :effects    []
                                                  :params     {}})))))

                       (testing "returns :ok when all declared effects are present"
                                (is (= :ok
                                       (:verdict
                                        (monitor {:handler-id :submit-prompt
                                                  :db-before  {:sessions {"s1" {:messages []}}}
                                                  :db-after   {:sessions {"s1" {:messages [{:role :user :content "hi"}]}}}
                                                  :effects    [{:op :ai/generate :fields {}}]
                                                  :params     {:sid "s1" :prompt "hi"}})))))

                       (testing "returns :violation for absent handler"
                                (let [result (monitor {:handler-id :no-such
                                                       :db-before  {}
                                                       :db-after   {}
                                                       :effects    []
                                                       :params     {}})]
                                     (is (= :violation (:verdict result)))
                                     (is (= :absent-handler
                                            (-> result :violations first :detail :kind)))))

                       (testing "returns :violation for missing declared effect"
                                (let [result (monitor {:handler-id :submit-prompt
                                                       :db-before  {:sessions {"s1" {:messages []}}}
                                                       :db-after   {:sessions {"s1" {:messages [{:role :user :content "hi"}]}}}
                                                       :effects    []
                                                       :params     {:sid "s1" :prompt "hi"}})]
                                     (is (= :violation (:verdict result)))
                                     (is (some #(= :wrong-emission (-> % :detail :kind))
                                               (:violations result)))))

                       (testing "returns :violation when db path unchanged"
                                (let [result (monitor {:handler-id :submit-prompt
                                                       :db-before  {:sessions {"s1" {:messages []}}}
                                                       :db-after   {:sessions {"s1" {:messages []}}}
                                                       :effects    [{:op :ai/generate :fields {}}]
                                                       :params     {:sid "s1" :prompt "hi"}})]
                                     (is (= :violation (:verdict result)))
                                     (is (some #(= :wrong-update (-> % :detail :kind))
                                               (:violations result))))))))

(deftest generator-projection-test
  ;; ->gen produces entries conforming to ->schema (axiom A24).
         (let [hs      (mealy/mealy-handler-set sample-declarations)
               g       (p/->gen hs)
               schema  (p/->schema hs)
               samples (gen/sample g 20)]

              (testing "->gen"
                       (testing "produces a generator"
                                (is (some? g)))

                       (testing "generated values conform to schema (A24)"
                                (doseq [sample samples]
                                       (is (m/validate schema sample)
                                           (str "sample failed: " (pr-str sample))))))))

(deftest gap-type-projection-test
  ;; ->gap-type returns the failure taxonomy for mealy handler sets.
         (let [hs (mealy/mealy-handler-set sample-declarations)
               gt (p/->gap-type hs)]

              (testing "->gap-type"
                       (testing "has :mealy formalism key"
                                (is (= :mealy (:formalism gt))))

                       (testing "has expected gap-kinds"
                                (is (contains? (:gap-kinds gt) :absent-handler))
                                (is (contains? (:gap-kinds gt) :missing-guard))
                                (is (contains? (:gap-kinds gt) :wrong-update))
                                (is (contains? (:gap-kinds gt) :wrong-emission)))

                       (testing "has expected statuses"
                                (is (= #{:conforms :absent :diverges} (:statuses gt)))))))

(deftest referenceable-test
  ;; extract-refs returns cross-formalism reference sets keyed by ref-kind.
         (let [hs (mealy/mealy-handler-set sample-declarations)]

              (testing "extract-refs"
                       (testing "returns handler ids for :handler-ids"
                                (is (= #{:ping :set-name :submit-prompt}
                                       (p/extract-refs hs :handler-ids))))

                       (testing "returns in-state? second arg for :guard-state-refs"
                                (is (= #{:idle}
                                       (p/extract-refs hs :guard-state-refs))))

                       (testing "returns effect :op keywords for :emission-op-refs"
                                (is (= #{:ai/generate}
                                       (p/extract-refs hs :emission-op-refs))))

                       (testing "returns event-ref keywords for :callback-refs"
                                (is (= #{:generation-complete :generation-error}
                                       (p/extract-refs hs :callback-refs))))

                       (testing "returns update paths for :update-path-refs"
                                (is (= #{[:sessions :sid :messages]}
                                       (p/extract-refs hs :update-path-refs))))

                       (testing "returns empty set for unknown ref-kind"
                                (is (= #{} (p/extract-refs hs :unknown)))))))
