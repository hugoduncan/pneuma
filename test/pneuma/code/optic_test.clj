(ns pneuma.code.optic-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.formalism.optic :as optic]
              [pneuma.code.protocol :as cp]
              [pneuma.code.optic]))

(deftest optic-code-emission-test
  ;; ->code emits subscription declarations for path-based optics
  ;; and fill points for derived subscriptions.
         (testing "OpticDeclaration ->code"
                  (let [optics (optic/optic-declaration
                                {:label "session subs"
                                 :declarations
                                 [{:id :messages
                                   :optic-type :Lens
                                   :path [:sessions :current :messages]}
                                  {:id :msg-count
                                   :optic-type :Derived
                                   :sources {:messages [:sessions :current :messages]}
                                   :derivations {:count 'count}}]})
                        code (cp/->code optics {:target-ns 'agent.subs})]

                       (testing "contains one form per optic"
                                (is (= 2 (count (:forms code)))))

                       (testing "lens optic has no fill points"
                                (let [lens-form (first (filterv #(= :messages (:optic-id %))
                                                                (:forms code)))]
                                     (is (= :Lens (:optic-type lens-form)))
                                     (is (empty? (:fills lens-form)))))

                       (testing "derived optic has a compute fill"
                                (let [derived-form (first (filterv #(= :msg-count (:optic-id %))
                                                                   (:forms code)))]
                                     (is (= :Derived (:optic-type derived-form)))
                                     (is (seq (:fills derived-form)))))

                       (testing "fill manifest has derived compute entry"
                                (let [manifest (:fill-manifest code)]
                                     (is (contains? manifest :msg-count/compute))
                                     (is (not (contains? manifest :messages/compute)))))

                       (testing "includes formalism metadata"
                                (is (= :optic (get-in code [:metadata :formalism])))
                                (is (= 2 (get-in code [:metadata :subscription-count])))
                                (is (= 1 (get-in code [:metadata :derived-count])))))))
