(ns pneuma.protocol-test
    (:require [clojure.test :refer [deftest testing is]]
              [pneuma.protocol :as p]))

;; Verify that the three protocols exist and that records implementing
;; them are dispatched correctly. Uses minimal stub records to test
;; the protocol contracts without depending on any formalism.

(defrecord StubFormalism []
           p/IProjectable
           (->schema [_] :stub-schema)
           (->monitor [_] :stub-monitor)
           (->gen [_] :stub-gen)
           (->gap-type [_] :stub-gap-type)

           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (case ref-kind
                               :alpha #{:a :b}
                               :beta  #{:c}
                               #{})))

(defrecord StubMorphism []
           p/IConnection
           (check [_ source target _rm]
                  [{:source source :target target :status :conforms}]))

(deftest protocol-dispatch-test
  ;; IProjectable, IConnection, and IReferenceable dispatch correctly
  ;; on stub records implementing the protocols.
         (testing "IProjectable"
                  (let [f (->StubFormalism)]
                       (testing "dispatches ->schema"
                                (is (= :stub-schema (p/->schema f))))
                       (testing "dispatches ->monitor"
                                (is (= :stub-monitor (p/->monitor f))))
                       (testing "dispatches ->gen"
                                (is (= :stub-gen (p/->gen f))))
                       (testing "dispatches ->gap-type"
                                (is (= :stub-gap-type (p/->gap-type f))))))

         (testing "IConnection"
                  (let [m (->StubMorphism)]
                       (testing "dispatches check with source, target, and refinement-map"
                                (is (= [{:source :s :target :t :status :conforms}]
                                       (p/check m :s :t {}))))))

         (testing "IReferenceable"
                  (let [f (->StubFormalism)]
                       (testing "dispatches extract-refs for known ref-kind"
                                (is (= #{:a :b} (p/extract-refs f :alpha))))
                       (testing "returns empty set for unknown ref-kind"
                                (is (= #{} (p/extract-refs f :unknown)))))))

(deftest satisfies-test
  ;; Protocol satisfaction is testable at runtime.
         (testing "StubFormalism"
                  (let [f (->StubFormalism)]
                       (testing "satisfies IProjectable"
                                (is (satisfies? p/IProjectable f)))
                       (testing "satisfies IReferenceable"
                                (is (satisfies? p/IReferenceable f)))
                       (testing "does not satisfy IConnection"
                                (is (not (satisfies? p/IConnection f))))))

         (testing "StubMorphism"
                  (let [m (->StubMorphism)]
                       (testing "satisfies IConnection"
                                (is (satisfies? p/IConnection m)))
                       (testing "does not satisfy IProjectable"
                                (is (not (satisfies? p/IProjectable m)))))))
