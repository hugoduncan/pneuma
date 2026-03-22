(ns pneuma.lean.ordering-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.protocol :as p]
              [pneuma.morphism.ordering :as ord]
              [pneuma.lean.protocol :as lp]
              [pneuma.lean.ordering]))

;; Tests for Lean 4 emission from OrderingMorphism.
;; Contracts: ->lean-conn emits a chain inductive, an index function,
;; and an ordering proposition proved by decide.

;;; Test formalisms

(defrecord StubFormalism [refs]
           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (get refs ref-kind #{})))

(def chain [:validate :authenticate :authorize :execute :audit])

(def morphism
     (ord/ordering-morphism
      {:id              :auth->exec
       :from            :auth-step
       :to              :exec-step
       :source-ref-kind :step-ref
       :target-ref-kind :step-ref
       :chain           chain}))

(deftest ordering-lean-emission-test
  ;; ->lean-conn emits the chain inductive, index function, and ordering
  ;; proposition for an OrderingMorphism.
         (testing "OrderingMorphism ->lean-conn"
                  (let [source   (->StubFormalism {:step-ref #{:authenticate}})
                        target   (->StubFormalism {:step-ref #{:execute}})
                        lean-src (lp/->lean-conn morphism source target)]

                       (testing "contains chain inductive type"
                                (is (str/includes? lean-src "inductive AuthExecChain where"))
                                (is (str/includes? lean-src "| validate"))
                                (is (str/includes? lean-src "| authenticate"))
                                (is (str/includes? lean-src "| authorize"))
                                (is (str/includes? lean-src "| execute"))
                                (is (str/includes? lean-src "| audit")))

                       (testing "contains DecidableEq derivation"
                                (is (str/includes? lean-src "deriving DecidableEq")))

                       (testing "contains index function"
                                (is (str/includes? lean-src "AuthExecChainIndex"))
                                (is (str/includes? lean-src "Nat")))

                       (testing "contains ordering proposition"
                                (is (str/includes? lean-src "AuthExec_ordering_boundary"))
                                (is (str/includes? lean-src "< AuthExecChainIndex"))
                                (is (str/includes? lean-src "decide")))

                       (testing "source and target refs appear in proposition"
                                (is (str/includes? lean-src ".authenticate"))
                                (is (str/includes? lean-src ".execute")))

                       (testing "contains header comment"
                                (is (str/includes? lean-src "OrderingMorphism"))
                                (is (str/includes? lean-src "auth-step"))
                                (is (str/includes? lean-src "exec-step"))))))
