(ns pneuma.morphism.ordering-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]
              [pneuma.morphism.ordering :as ord]))

;; Tests for ordering morphisms: source ref must precede target ref in chain.
;; Contracts: conforms when source-idx < target-idx, diverges otherwise,
;; diverges when either ref is absent from the chain.

;;; Test formalisms

(defrecord StubFormalism [refs]
           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (get refs ref-kind #{})))

(def chain [:validate :authenticate :authorize :execute :audit])

(def ordering
     (ord/ordering-morphism
      {:id              :auth->exec
       :from            :auth-step
       :to              :exec-step
       :source-ref-kind :step-ref
       :target-ref-kind :step-ref
       :chain           chain}))

(deftest ordering-conforms-test
  ;; Source ref precedes target ref in chain.
         (testing "ordering morphism"
                  (testing "conforms when source index precedes target index"
                           (let [source (->StubFormalism {:step-ref #{:authenticate}})
                                 target (->StubFormalism {:step-ref #{:execute}})
                                 gaps   (p/check ordering source target {})]
                                (is (= 1 (count gaps)) gaps)
                                (is (= :conforms (:status (first gaps))) gaps)))))

(deftest ordering-diverges-wrong-order-test
  ;; Target ref precedes source ref — order violation.
         (testing "ordering morphism"
                  (testing "diverges when source index follows target index"
                           (let [source (->StubFormalism {:step-ref #{:execute}})
                                 target (->StubFormalism {:step-ref #{:authenticate}})
                                 gaps   (p/check ordering source target {})]
                                (is (= 1 (count gaps)) gaps)
                                (is (= :diverges (:status (first gaps))) gaps)
                                (is (= :order-violation (-> gaps first :detail :gap-type)) gaps)))))

(deftest ordering-diverges-source-absent-test
  ;; Source ref not in chain — order violation.
         (testing "ordering morphism"
                  (testing "diverges when source ref is absent from chain"
                           (let [source (->StubFormalism {:step-ref #{:unknown-step}})
                                 target (->StubFormalism {:step-ref #{:execute}})
                                 gaps   (p/check ordering source target {})]
                                (is (= 1 (count gaps)) gaps)
                                (is (= :diverges (:status (first gaps))) gaps)
                                (is (= :order-violation (-> gaps first :detail :gap-type)) gaps)))))

(deftest ordering-diverges-target-absent-test
  ;; Target ref not in chain — order violation.
         (testing "ordering morphism"
                  (testing "diverges when target ref is absent from chain"
                           (let [source (->StubFormalism {:step-ref #{:authenticate}})
                                 target (->StubFormalism {:step-ref #{:missing}})
                                 gaps   (p/check ordering source target {})]
                                (is (= 1 (count gaps)) gaps)
                                (is (= :diverges (:status (first gaps))) gaps)
                                (is (= :order-violation (-> gaps first :detail :gap-type)) gaps)))))

(deftest ordering-diverges-same-position-test
  ;; Same ref for source and target — not strictly less than.
         (testing "ordering morphism"
                  (testing "diverges when source and target refs are the same chain element"
                           (let [source (->StubFormalism {:step-ref #{:authorize}})
                                 target (->StubFormalism {:step-ref #{:authorize}})
                                 gaps   (p/check ordering source target {})]
                                (is (= 1 (count gaps)) gaps)
                                (is (= :diverges (:status (first gaps))) gaps)))))
