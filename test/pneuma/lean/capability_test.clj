(ns pneuma.lean.capability-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.capability]))

;; Tests for Lean emission from CapabilitySet formalisms.

(deftest capability-set-lean-emission-test
  ;; ->lean produces syntactically structured Lean 4 source.
         (testing "CapabilitySet ->lean"
                  (let [caps (cap/capability-set
                              {:id :test-caps
                               :dispatch #{:alpha :beta}})
                        lean-src (lp/->lean caps)]

                       (testing "returns a non-empty string"
                                (is (string? lean-src))
                                (is (pos? (count lean-src))))

                       (testing "contains the inductive type"
                                (is (str/includes? lean-src "inductive Test_capsOp where")))

                       (testing "contains constructors for each operation"
                                (is (str/includes? lean-src "| alpha"))
                                (is (str/includes? lean-src "| beta")))

                       (testing "contains DecidableEq deriving"
                                (is (str/includes? lean-src "deriving DecidableEq")))

                       (testing "contains dispatch set definition"
                                (is (str/includes? lean-src "test_caps_dispatch")))

                       (testing "contains proof target"
                                (is (str/includes? lean-src "theorem")))

                       (testing "contains docstrings"
                                (is (str/includes? lean-src "/-- "))
                                (is (str/includes? lean-src " -/"))
                                (is (str/includes? lean-src "Derived from Pneuma CapabilitySet :test-caps."))))))

(deftest protocol-caps-lean-emission-test
  ;; Real dogfood instance: the protocol-spec's formalism-record-caps.
         (testing "protocol formalism-record-caps ->lean"
                  (let [caps (cap/capability-set
                              {:id :formalism-record
                               :dispatch #{:->schema :->monitor :->gen
                                           :->gap-type :extract-refs}})
                        lean-src (lp/->lean caps)]

                       (testing "contains all five operations as constructors"
                                (is (str/includes? lean-src "| schema"))
                                (is (str/includes? lean-src "| monitor"))
                                (is (str/includes? lean-src "| gen"))
                                (is (str/includes? lean-src "| gap_type"))
                                (is (str/includes? lean-src "| extract_refs"))))))
