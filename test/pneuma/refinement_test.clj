(ns pneuma.refinement-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.refinement :as rm]))

;; Tests for RefinementMap construction and state access.
;; atom-ref holds a var pointing to an atom (double deref).

(def ^:dynamic *test-state* (atom {:sessions {:s1 {:name "test"}}}))

(def ^:dynamic *test-users* (atom {:users {:u1 {:name "alice"}}}))

(deftest refinement-map-test
  ;; Contracts: refinement-map creates a record with atom-ref,
  ;; event-log-ref, accessors, and source-nss.
         (testing "refinement-map"
                  (testing "creates a RefinementMap with all fields"
                           (let [r (rm/refinement-map
                                    {:atom-ref #'*test-state*
                                     :accessors {:session (fn [db sid] (get-in db [:sessions sid]))}
                                     :source-nss '[my.ns]})]
                                (is (instance? pneuma.refinement.RefinementMap r))
                                (is (= '[my.ns] (:source-nss r)))))

                  (testing "defaults optional fields"
                           (let [r (rm/refinement-map {:atom-ref nil})]
                                (is (= {} (:accessors r)))
                                (is (= [] (:source-nss r)))))))

(deftest deref-state-test
  ;; Contracts: deref-state dereferences the var, then the atom.
         (testing "deref-state"
                  (testing "returns current atom value"
                           (let [r (rm/refinement-map {:atom-ref #'*test-state*})]
                                (is (= {:sessions {:s1 {:name "test"}}}
                                       (rm/deref-state r)))))

                  (testing "returns nil when atom-ref is nil"
                           (let [r (rm/refinement-map {:atom-ref nil})]
                                (is (nil? (rm/deref-state r)))))))

(deftest access-test
  ;; Contracts: access applies a named accessor to current state.
         (testing "access"
                  (testing "extracts value via accessor"
                           (let [r (rm/refinement-map
                                    {:atom-ref #'*test-users*
                                     :accessors {:user (fn [db uid] (get-in db [:users uid]))}})]
                                (is (= {:name "alice"} (rm/access r :user :u1)))))

                  (testing "returns nil for missing accessor"
                           (let [r (rm/refinement-map {:atom-ref nil})]
                                (is (nil? (rm/access r :nonexistent)))))))
