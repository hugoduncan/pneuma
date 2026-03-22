(ns pneuma.core
    "Public API for the Pneuma conformance checking system.
  Provides constructors for all formalisms and morphisms,
  gap report assembly, per-formalism checking convenience
  functions, and report diffing utilities."
    (:require [malli.core :as m]
              [malli.error :as me]
              [pneuma.formalism.capability :as cap]
              [pneuma.formalism.effect-signature :as es]
              [pneuma.formalism.mealy :as mealy]
              [pneuma.formalism.optic :as optic]
              [pneuma.formalism.resolver :as resolver]
              [pneuma.formalism.statechart :as chart]
              [pneuma.formalism.type-schema :as ts]
              [pneuma.gap.core :as gap]
              [pneuma.gap.diff :as diff]
              [pneuma.morphism.containment :as ct]
              [pneuma.morphism.existential :as ex]
              [pneuma.morphism.ordering :as ord]
              [pneuma.morphism.structural :as st]
              [pneuma.path.core :as path]
              [pneuma.protocol :as p]
              [pneuma.refinement :as rm]))

;;; Formalism constructors

(def effect-signature
     "Creates an EffectSignature formalism."
     es/effect-signature)

(def capability-set
     "Creates a CapabilitySet formalism."
     cap/capability-set)

(def statechart
     "Creates a Statechart formalism."
     chart/statechart)

(def mealy-handler-set
     "Creates a MealyHandlerSet formalism."
     mealy/mealy-handler-set)

(def optic-declaration
     "Creates an OpticDeclaration formalism."
     optic/optic-declaration)

(def resolver-graph
     "Creates a ResolverGraph formalism."
     resolver/resolver-graph)

(def type-schema
     "Creates a TypeSchema formalism."
     ts/type-schema)

;;; Morphism constructors

(def existential-morphism
     "Creates an ExistentialMorphism."
     ex/existential-morphism)

(def structural-morphism
     "Creates a StructuralMorphism."
     st/structural-morphism)

(def containment-morphism
     "Creates a ContainmentMorphism."
     ct/containment-morphism)

(def ordering-morphism
     "Creates an OrderingMorphism."
     ord/ordering-morphism)

;;; Refinement map

(def refinement-map
     "Creates a RefinementMap bridging formalisms to implementation state."
     rm/refinement-map)

;;; Gap report

(def gap-report
     "Assembles a three-layer gap report from formalisms and registry."
     gap/gap-report)

(def failures
     "Returns only non-conforming gaps from a gap report."
     gap/failures)

(def has-failures?
     "Returns true if the gap report has any non-conforming gaps."
     gap/has-failures?)

;;; Report diffing

(def diff-reports
     "Compares two gap reports, returning introduced/resolved/changed per layer."
     diff/diff-reports)

(def has-changes?
     "Returns true if a diff contains any changes."
     diff/has-changes?)

(def gaps-involving
     "Filters a gap report to gaps involving a given formalism kind."
     diff/gaps-involving)

;;; Path discovery

(def find-paths
     "Discovers all composed paths (cycles) in a morphism registry."
     path/find-paths)

;;; Per-formalism convenience functions

(defn check-schema
      "Validates state against a formalism's schema projection.
  Returns a verdict map with :status (:conforms or :diverges)."
      [formalism state]
      (let [schema (p/->schema formalism)
            result (m/explain schema state)]
           (if result
               {:status :diverges
                :detail {:errors (me/humanize result)}}
               {:status :conforms})))

(defn check-trace
      "Replays an event log through a formalism's monitor projection.
  Returns a verdict map with :status and :entries-checked count."
      [formalism event-log]
      (let [monitor (p/->monitor formalism)
            verdicts (mapv monitor event-log)
            violations (filterv #(= :violation (:verdict %)) verdicts)]
           (if (seq violations)
               {:status :diverges
                :entries-checked (count verdicts)
                :violations violations}
               {:status :conforms
                :entries-checked (count verdicts)})))

(defn check-gen
      "Runs property-based tests using a formalism's generator and schema.
  Returns a verdict map with :status and :tests-run count.

  Options:
    :num-tests - number of test iterations (default 100)"
      [formalism {:keys [num-tests] :or {num-tests 100}}]
      (let [schema (p/->schema formalism)
            gen (p/->gen formalism)]
           (require '[clojure.test.check :as tc]
                    '[clojure.test.check.properties :as prop])
           (let [quick-check (resolve 'tc/quick-check)
                 for-all* (resolve 'prop/for-all*)
                 prop (for-all* [gen]
                                (fn [v] (m/validate schema v)))
                 result (quick-check num-tests prop)]
                (if (:pass? result)
                    {:status :conforms
                     :tests-run (:num-tests result)}
                    {:status :diverges
                     :tests-run (:num-tests result)
                     :detail {:shrunk (:shrunk result)
                              :fail (:fail result)}}))))

(defn check-morphism
      "Checks a single morphism against source and target formalisms.
  Returns a vector of gap maps."
      ([morphism source target]
       (check-morphism morphism source target {}))
      ([morphism source target refinement-map]
       (p/check morphism source target refinement-map)))
