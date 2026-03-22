(ns pneuma.integrant.integrant-test
    "Regression test for pneuma's formal model of integrant.
  Validates internal consistency of the spec: all morphisms
  conform, all capability sets reference declared operations,
  and all output types resolve in the type universe."
    (:require [clojure.test :refer [deftest is testing]]
              [pneuma.gap.core :as gap]
              [pneuma.integrant.integrant-spec :as spec]))

(deftest ^:regression integrant-spec-test
  ;; Verify the integrant formal model is internally consistent:
  ;; all capability dispatch sets reference declared operations,
  ;; all output types resolve in the type universe, and the
  ;; lifecycle statechart is well-formed.
         (testing "integrant-spec"
                  (testing "produces a conforming gap report"
                           (let [report (spec/integrant-gap-report)
                                 fails  (gap/failures report)]
                                (is (not (gap/has-failures? report))
                                    (str "Unexpected failures in integrant spec: "
                                         (pr-str fails)))
                                (is (seq (:object-gaps report))
                                    "Expected object-level gaps to be present")
                                (is (seq (:morphism-gaps report))
                                    "Expected morphism-level gaps to be present")))

                  (testing "has the expected number of formalisms"
                           (is (= 8 (count (:formalisms spec/spec-system)))))

                  (testing "has the expected number of morphisms"
                           (is (= 6 (count (:registry spec/spec-system)))))

                  (testing "lifecycle statechart has expected states"
                           (is (= #{:uninitialized :expanded :running :suspended :halted}
                                  (:states spec/lifecycle))))

                  (testing "lifecycle statechart has expected transitions"
                           (is (= 7 (count (:transitions spec/lifecycle)))))))
