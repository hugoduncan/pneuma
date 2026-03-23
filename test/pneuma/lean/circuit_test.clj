(ns pneuma.lean.circuit-test
    (:require [clojure.test :refer [deftest testing is]]
              [clojure.string :as str]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.morphism.existential :as ex]
              [pneuma.lean.circuit :as circuit]))

(def cycle-ops
     (es/effect-signature
      {:label "test ES"
       :operations
       {:dispatch {:input {:event :Keyword} :output :Boolean}}}))

(def cycle-caps
     (cap/capability-set
      {:label "test caps"
       :id :cycle-caps
       :dispatch #{:dispatch}}))

(def cyclic-registry
     {:caps->ops (ex/existential-morphism
                  {:id :caps->ops
                   :from :capability-set
                   :to :effect-signature
                   :source-ref-kind :dispatch-refs
                   :target-ref-kind :operation-ids})
      :ops->caps (ex/existential-morphism
                  {:id :ops->caps
                   :from :effect-signature
                   :to :capability-set
                   :source-ref-kind :operation-ids
                   :target-ref-kind :dispatch-refs})})

(def acyclic-registry
     {:caps->ops (ex/existential-morphism
                  {:id :caps->ops
                   :from :capability-set
                   :to :effect-signature
                   :source-ref-kind :dispatch-refs
                   :target-ref-kind :operation-ids})})

(deftest circuit-verification-test
  ;; emit-circuit-verification emits Lean 4 proofs that each
  ;; discovered circuit is a valid cycle in the graph.
         (testing "emit-circuit-verification"

                  (testing "with cyclic registry"
                           (let [src (circuit/emit-circuit-verification cyclic-registry)]

                                (testing "emits adjacency function"
                                         (is (str/includes? src "def adj")))

                                (testing "emits circuit validity theorem"
                                         (is (str/includes? src "circuit_0_valid"))
                                         (is (str/includes? src "decide")))

                                (testing "emits all-circuits-valid conjunction"
                                         (is (str/includes? src "all_circuits_valid")))

                                (testing "emits circuit count"
                                         (is (str/includes? src "circuitCount")))))

                  (testing "with acyclic registry"
                           (let [src (circuit/emit-circuit-verification acyclic-registry)]

                                (testing "reports no circuits"
                                         (is (str/includes? src "No circuits discovered")))

                                (testing "still emits adjacency function"
                                         (is (str/includes? src "def adj")))))))
