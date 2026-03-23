(ns pneuma.fills-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.fills :as fills]))

(deftest fills-registry-test
  ;; Verifies the fill-point registry CRUD operations and lookup
  ;; semantics using an isolated registry instance.
         (testing "fills registry"
                  (let [r (fills/make-registry)]
                       (testing "with a registered fill"
                                (fills/reg-fill r :test/greet (fn [name] (str "hi " name)))

                                (testing "invokes the fill"
                                         (is (= "hi alice" (fills/fill r :test/greet "alice"))))

                                (testing "returns default for missing key via fill-or"
                                         (is (= :default (fills/fill-or r :test/missing :default)))))

                       (testing "with an unregistered fill"
                                (testing "throws on lookup"
                                         (is (thrown-with-msg? clojure.lang.ExceptionInfo
                                                               #"Unregistered fill point"
                                                               (fills/fill r :test/missing))))))))

(deftest fill-or-test
  ;; Verifies fill-or invokes registered fills and falls back to default.
         (testing "fill-or"
                  (let [r (fills/make-registry)]
                       (fills/reg-fill r :test/add (fn [a b] (+ a b)))

                       (testing "invokes registered fill"
                                (is (= 5 (fills/fill-or r :test/add :unused 2 3))))

                       (testing "returns default for missing fill"
                                (is (= 42 (fills/fill-or r :test/nope 42)))))))

(deftest fill-status-test
  ;; Verifies fill-status classification: ok, missing, orphaned,
  ;; and arity-mismatch detection.
         (testing "fill-status"
                  (let [r (fills/make-registry)
                        manifest {:submit/messages {:args '[db session-id]
                                                    :returns :messages-list
                                                    :doc "Extract messages"}
                                  :submit/model    {:args '[db session-id]
                                                    :returns :model-id
                                                    :doc "Resolve model"}}]

                       (testing "with all fills registered"
                                (fills/reg-fill r :submit/messages (fn [_db _sid] []))
                                (fills/reg-fill r :submit/model (fn [_db _sid] "m"))
                                (let [status (fills/fill-status r manifest)]
                                     (testing "reports all as ok"
                                              (is (= [:submit/messages :submit/model] (:ok status))))
                                     (testing "reports no missing"
                                              (is (empty? (:missing status))))
                                     (testing "reports no orphaned"
                                              (is (empty? (:orphaned status))))))

                       (testing "with a missing fill"
                                (let [r2 (fills/make-registry)]
                                     (fills/reg-fill r2 :submit/messages (fn [_db _sid] []))
                                     (let [status (fills/fill-status r2 manifest)]
                                          (testing "reports the missing key"
                                                   (is (= [:submit/model] (:missing status)))))))

                       (testing "with an orphaned fill"
                                (let [r3 (fills/make-registry)]
                                     (fills/reg-fill r3 :submit/messages (fn [_db _sid] []))
                                     (fills/reg-fill r3 :submit/model (fn [_db _sid] "m"))
                                     (fills/reg-fill r3 :legacy/old (fn [] nil))
                                     (let [status (fills/fill-status r3 manifest)]
                                          (testing "reports the orphaned key"
                                                   (is (= [:legacy/old] (:orphaned status))))))))))

(deftest fill-gaps-test
  ;; Verifies that fill-gaps produces gap entries suitable for the
  ;; :fill-gaps layer of the gap report.
         (testing "fill-gaps"
                  (let [r (fills/make-registry)
                        manifest {:a/ok {:args '[x] :returns :int :doc "ok fill"}
                                  :a/miss {:args '[x y] :returns :str :doc "missing"}}]
                       (fills/reg-fill r :a/ok (fn [_x] 1))
                       (fills/reg-fill r :a/extra (fn [] nil))
                       (let [gaps (fills/fill-gaps r manifest)]

                            (testing "includes conforming fill"
                                     (is (some #(and (= :a/ok (:fill-point %))
                                                     (= :conforms (:status %)))
                                               gaps)))

                            (testing "includes missing fill"
                                     (is (some #(and (= :a/miss (:fill-point %))
                                                     (= :absent (:status %)))
                                               gaps)))

                            (testing "includes orphaned fill"
                                     (is (some #(and (= :a/extra (:fill-point %))
                                                     (= :orphaned (:status %)))
                                               gaps)))

                            (testing "all entries have :fill layer"
                                     (is (every? #(= :fill (:layer %)) gaps)))))))
