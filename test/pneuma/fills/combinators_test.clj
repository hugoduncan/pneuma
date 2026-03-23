(ns pneuma.fills.combinators-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.fills :as fills]
              [pneuma.fills.combinators :as c]))

(deftest from-path-test
  ;; Verifies from-path produces a fill that reads from a nested path.
         (testing "from-path"
                  (let [r (fills/make-registry)
                        db {:config {:ai {:model "opus"}}}]
                       (fills/reg-fill r :test/model (c/from-path [:config :ai :model]))

                       (testing "reads the value at the path"
                                (is (= "opus" (fills/fill r :test/model db))))

                       (testing "returns nil for missing path"
                                (is (nil? (fills/fill r :test/model {})))))))

(deftest from-session-test
  ;; Verifies from-session produces a fill that reads a session field.
         (testing "from-session"
                  (let [r (fills/make-registry)
                        db {:sessions {"s1" {:name "alice"}}}]
                       (fills/reg-fill r :test/name (c/from-session :name))

                       (testing "reads the session field"
                                (is (= "alice" (fills/fill r :test/name db "s1")))))))

(deftest const-val-test
  ;; Verifies const-val returns the same value regardless of args.
         (testing "const-val"
                  (let [r (fills/make-registry)]
                       (fills/reg-fill r :test/default (c/const-val 42))

                       (testing "returns the constant"
                                (is (= 42 (fills/fill r :test/default)))
                                (is (= 42 (fills/fill r :test/default :ignored :args)))))))
