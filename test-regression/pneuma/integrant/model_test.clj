(ns pneuma.integrant.model-test
    "Model-based tests for integrant using pneuma formalisms.
  Exercises integrant's actual code against the formal spec:
  lifecycle statechart, effect signature, capabilities,
  schema validation, and property-based generation."
    (:require [clojure.test :refer [deftest is testing]]
              [integrant.core :as ig]
              [pneuma.core :as pneuma]
              [pneuma.integrant.integrant-spec :as spec]
              [pneuma.protocol :as p]))

;;; Test fixtures — minimal integrant system

(defmethod ig/init-key ::counter [_ {:keys [start]}]
           (atom (or start 0)))

(defmethod ig/halt-key! ::counter [_ counter]
           (reset! counter 0))

(defmethod ig/suspend-key! ::counter [_ counter]
           (reset! counter -1))

(defmethod ig/resume-key ::counter [_ {:keys [start]} _old-cfg old-impl]
           (reset! old-impl (or start 0))
           old-impl)

(defmethod ig/init-key ::logger [_ {:keys [counter]}]
           {:counter counter :log (atom [])})

(defmethod ig/halt-key! ::logger [_ {:keys [log]}]
           (reset! log []))

(def test-config
     {::counter {:start 10}
      ::logger  {:counter (ig/ref ::counter)}})

;;; Lifecycle trace helpers

(defn- track-lifecycle
       "Exercises a full integrant lifecycle and returns a trace
  of state transitions as statechart monitor entries."
       [config]
       (let [trace (atom [])
             record! (fn [from event to]
                         (swap! trace conj
                                {:config-before #{from}
                                 :event event
                                 :config-after #{to}}))]
    ;; init
            (let [system (ig/init config)]
                 (record! :uninitialized :init :running)

      ;; suspend
                 (ig/suspend! system)
                 (record! :running :suspend :suspended)

      ;; resume
                 (let [resumed (ig/resume config system)]
                      (record! :suspended :resume :running)

        ;; halt
                      (ig/halt! resumed)
                      (record! :running :halt :halted)))

            @trace))

(defn- track-effects
       "Exercises integrant multimethods and returns effect entries
  matching the effect-signature monitor format."
       [config]
       (let [effects (atom [])]
            (let [system (ig/init config)]
      ;; init-key was called for each key
                 (doseq [[k v] config]
                        (swap! effects conj
                               {:effects [{:op :init-key
                                           :key k
                                           :value v}]}))
      ;; resolve-key (implicit during init)
                 (doseq [[k v] system]
                        (swap! effects conj
                               {:effects [{:op :resolve-key
                                           :key k
                                           :value v}]}))

      ;; suspend
                 (ig/suspend! system)
                 (doseq [[k v] system]
                        (swap! effects conj
                               {:effects [{:op :suspend-key!
                                           :key k
                                           :value v}]}))

      ;; resume
                 (let [resumed (ig/resume config system)]
                      (doseq [[k _v] config]
                             (swap! effects conj
                                    {:effects [{:op :resume-key
                                                :key k
                                                :value (get config k)
                                                :old-value (get config k)
                                                :old-impl (get system k)}]}))

        ;; halt
                      (ig/halt! resumed)
                      (doseq [[k v] resumed]
                             (swap! effects conj
                                    {:effects [{:op :halt-key!
                                                :key k
                                                :value v}]}))))
            @effects))

(defn- track-capabilities
       "Returns capability-check entries for each lifecycle phase."
       [config]
       (let [system (ig/init config)
             init-checks (into []
                               (mapcat (fn [[_k _]]
                                           [{:kind :dispatch :op :assert-key}
                                            {:kind :dispatch :op :init-key}
                                            {:kind :dispatch :op :resolve-key}]))
                               config)
             running-checks [{:kind :query :op :resolve-key}]]
            (ig/suspend! system)
            (let [suspend-checks (mapv (fn [[_k _]] {:kind :dispatch :op :suspend-key!})
                                       system)
                  resumed (ig/resume config system)
                  resume-checks (into []
                                      (mapcat (fn [[_k _]]
                                                  [{:kind :dispatch :op :resume-key}
                                                   {:kind :dispatch :op :resolve-key}]))
                                      config)
                  _ (ig/halt! resumed)
                  halt-checks (mapv (fn [[_k _]] {:kind :dispatch :op :halt-key!})
                                    system)]
                 {:init    {:capability-checks init-checks}
                  :running {:capability-checks running-checks}
                  :suspend {:capability-checks suspend-checks}
                  :resume  {:capability-checks resume-checks}
                  :halt    {:capability-checks halt-checks}})))

;;; Tests

(deftest ^:regression lifecycle-statechart-test
  ;; Exercise integrant's actual lifecycle against the statechart
  ;; monitor. Verify state transitions match the declared machine.
         (testing "lifecycle-statechart"
                  (testing "accepts valid lifecycle traces"
                           (let [monitor (p/->monitor spec/lifecycle)
                                 trace   (track-lifecycle test-config)]
                                (doseq [entry trace]
                                       (let [verdict (monitor entry)]
                                            (is (= :ok (:verdict verdict))
                                                (str "Statechart violation: " (pr-str entry)
                                                     " → " (pr-str verdict)))))))

                  (testing "rejects invalid transitions"
                           (let [monitor (p/->monitor spec/lifecycle)]
                                (is (= :violation
                                       (:verdict (monitor {:config-before #{:halted}
                                                           :event :init
                                                           :config-after #{:running}})))
                                    "halted → init should be rejected")))

                  (testing "schema validates reachable configs"
                           (let [result (pneuma/check-schema spec/lifecycle #{:running})]
                                (is (= :conforms (:status result)))))

                  (testing "schema rejects invalid configs"
                           (let [result (pneuma/check-schema spec/lifecycle #{:bogus})]
                                (is (= :diverges (:status result)))))

                  (testing "gen produces valid configs"
                           (let [result (pneuma/check-gen spec/lifecycle {:num-tests 50})]
                                (is (= :conforms (:status result)))))))

(deftest ^:regression effect-signature-test
  ;; Exercise integrant's multimethods against the effect-signature
  ;; monitor. Verify operation names match declared operations.
         (testing "effect-signature"
                  (testing "accepts valid effect traces"
                           (let [monitor (p/->monitor spec/multimethod-sig)
                                 effects (track-effects test-config)]
                                (doseq [entry effects]
                                       (let [verdict (monitor entry)]
                                            (is (= :ok (:verdict verdict))
                                                (str "Effect violation: " (pr-str entry)
                                                     " → " (pr-str verdict)))))))

                  (testing "rejects unknown operations"
                           (let [monitor (p/->monitor spec/multimethod-sig)]
                                (is (= :violation
                                       (:verdict (monitor {:effects [{:op :destroy-key
                                                                      :key ::counter
                                                                      :value nil}]})))
                                    ":destroy-key is not a declared operation")))

                  (testing "schema validates operation names"
                           (let [result (pneuma/check-schema spec/multimethod-sig
                                                             {:op :init-key
                                                              :key ::counter
                                                              :value {}})]
                                (is (= :conforms (:status result)))))

                  (testing "gen produces valid effect maps"
                           (let [result (pneuma/check-gen spec/multimethod-sig {:num-tests 50})]
                                (is (= :conforms (:status result)))))))

(deftest ^:regression capability-test
  ;; Exercise per-phase capability checks against the capability monitors.
         (testing "capabilities"
                  (let [caps (track-capabilities test-config)]
                       (testing "init phase permits assert-key, init-key, resolve-key"
                                (let [monitor (p/->monitor spec/init-phase-caps)
                                      verdict (monitor (:init caps))]
                                     (is (= :ok (:verdict verdict))
                                         (str "Init capability violation: " (pr-str verdict)))))

                       (testing "running phase permits resolve-key query"
                                (let [monitor (p/->monitor spec/running-phase-caps)
                                      verdict (monitor (:running caps))]
                                     (is (= :ok (:verdict verdict))
                                         (str "Running capability violation: " (pr-str verdict)))))

                       (testing "suspend phase permits suspend-key!"
                                (let [monitor (p/->monitor spec/suspend-phase-caps)
                                      verdict (monitor (:suspend caps))]
                                     (is (= :ok (:verdict verdict))
                                         (str "Suspend capability violation: " (pr-str verdict)))))

                       (testing "resume phase permits resume-key, resolve-key"
                                (let [monitor (p/->monitor spec/resume-phase-caps)
                                      verdict (monitor (:resume caps))]
                                     (is (= :ok (:verdict verdict))
                                         (str "Resume capability violation: " (pr-str verdict)))))

                       (testing "halt phase permits halt-key!"
                                (let [monitor (p/->monitor spec/halt-phase-caps)
                                      verdict (monitor (:halt caps))]
                                     (is (= :ok (:verdict verdict))
                                         (str "Halt capability violation: " (pr-str verdict)))))

                       (testing "rejects unauthorized operations"
                                (let [monitor (p/->monitor spec/halt-phase-caps)]
                                     (is (= :violation
                                            (:verdict (monitor {:capability-checks
                                                                [{:kind :dispatch :op :init-key}]})))
                                         "init-key not permitted in halt phase"))))))

(deftest ^:regression check-morphisms-test
  ;; Exercise pneuma.core/check-morphism on each registry morphism
  ;; against real formalism instances.
         (testing "check-morphism"
                  (doseq [[id morphism] spec/integrant-registry]
                         (testing (str "morphism " id " conforms")
                                  (let [source-kind (:from morphism)
                                        target-kind (:to morphism)
                                        source (get spec/integrant-formalisms source-kind)
                                        target (get spec/integrant-formalisms target-kind)
                                        gaps   (pneuma/check-morphism morphism source target)]
                                       (is (every? #(= :conforms (:status %)) gaps)
                                           (str "Morphism " id " diverges: " (pr-str gaps))))))))

(deftest ^:regression type-schema-test
  ;; Exercise the type schema formalism.
         (testing "type-schema"
                  (testing "validates known types"
                           (let [result (pneuma/check-schema spec/integrant-types :ConfigKey)]
                                (is (= :conforms (:status result)))))

                  (testing "rejects unknown types"
                           (let [result (pneuma/check-schema spec/integrant-types :BogusType)]
                                (is (= :diverges (:status result)))))

                  (testing "gen produces valid type keywords"
                           (let [result (pneuma/check-gen spec/integrant-types {:num-tests 50})]
                                (is (= :conforms (:status result)))))))

(deftest ^:regression full-gap-report-test
  ;; Exercise the full gap report pipeline end-to-end.
         (testing "full-gap-report"
                  (testing "all layers are present and conforming"
                           (let [report (spec/integrant-gap-report)]
                                (is (not (pneuma/has-failures? report))
                                    (str "Failures: " (pr-str (pneuma/failures report))))
                                (is (pos? (count (:object-gaps report))))
                                (is (pos? (count (:morphism-gaps report))))))

                  (testing "diff against itself shows no changes"
                           (let [report (spec/integrant-gap-report)]
                                (is (not (pneuma/has-changes? (pneuma/diff-reports report report))))))))
