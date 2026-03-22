(ns pneuma.lean.system-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.protocol-spec :as spec]
              [pneuma.lean.system :as sys]))

;; Tests for system-level Lean emission driven by the gap report.

(deftest system-lean-conforming-test
  ;; A conforming spec emits `decide` proofs, not `sorry`.
         (testing "conforming spec"
                  (let [lean-src (sys/emit-system-lean
                                  "pneuma.protocol-spec"
                                  {:formalisms spec/protocol-formalisms
                                   :registry spec/protocol-registry})]

                       (testing "reports all conforming"
                                (is (str/includes? lean-src "ALL CONFORMING")))

                       (testing "contains shared Op type"
                                (is (str/includes? lean-src "inductive Op where")))

                       (testing "contains six constructors"
                                (is (= 6 (count (re-seq #"  \| \w+" lean-src)))))

                       (testing "contains dispatch sets"
                                (is (str/includes? lean-src "formalism_record_dispatch"))
                                (is (str/includes? lean-src "morphism_record_dispatch")))

                       (testing "conforming morphisms use decide, not sorry"
                                (is (str/includes? lean-src "[conforms]"))
                                (is (not (str/includes? lean-src "sorry --"))))

                       (testing "contains system_conformance theorem"
                                (is (str/includes? lean-src "system_conformance"))))))

(deftest system-lean-failing-test
  ;; A failing spec emits `sorry` for broken morphisms.
         (testing "failing spec"
                  (let [bad-caps (cap/capability-set
                                  {:label "test caps"
                                   :id :formalism-record
                                   :dispatch #{:->schema :->monitor :->gen
                                               :->gap-type :extract-refs
                                               :bogus-method}})
                        lean-src (sys/emit-system-lean
                                  "broken-spec"
                                  {:formalisms (assoc spec/protocol-formalisms
                                                      :capability-set/formalism bad-caps)
                                   :registry spec/protocol-registry})]

                       (testing "reports failures"
                                (is (str/includes? lean-src "HAS FAILURES")))

                       (testing "failing morphism emits sorry"
                                (is (str/includes? lean-src "[diverges]"))
                                (is (str/includes? lean-src "sorry")))

                       (testing "conforming morphism still uses decide"
                                (is (str/includes? lean-src "[conforms]")))

                       (testing "system theorem uses sorry"
                                (is (str/includes? lean-src
                                                   "sorry -- some morphisms have failures"))))))
