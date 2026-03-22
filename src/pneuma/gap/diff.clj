(ns pneuma.gap.diff
    "Gap report diffing and filtering.
  Compares two gap reports to produce a delta showing introduced,
  resolved, and changed gaps. Provides filtering by formalism or
  morphism involvement."
    (:require [clojure.set :as set]))

(defn- gap-key
       "Returns a stable identity key for a gap, used for diffing."
       [gap]
       (case (:layer gap)
             :object   [:object (:formalism gap)]
             :morphism [:morphism (:id gap)]
             :path     [:path (:id gap)]
             [(:layer gap) (:id gap) (:formalism gap)]))

(defn- diff-layer
       "Diffs two gap vectors by stable identity. Returns a map of
  :introduced, :resolved, and :changed gaps."
       [old-gaps new-gaps]
       (let [old-by-key (into {} (map (juxt gap-key identity)) old-gaps)
             new-by-key (into {} (map (juxt gap-key identity)) new-gaps)
             old-keys (set (keys old-by-key))
             new-keys (set (keys new-by-key))
             introduced-keys (set/difference new-keys old-keys)
             resolved-keys (set/difference old-keys new-keys)
             common-keys (set/intersection old-keys new-keys)
             changed (into []
                           (keep (fn [k]
                                     (let [og (get old-by-key k)
                                           ng (get new-by-key k)]
                                          (when (not= (:status og) (:status ng))
                                                {:gap ng
                                                 :previous-status (:status og)}))))
                           common-keys)]
            {:introduced (mapv new-by-key introduced-keys)
             :resolved (mapv old-by-key resolved-keys)
             :changed changed}))

(defn diff-reports
      "Compares two gap reports and returns a delta per layer.
  Each layer contains :introduced, :resolved, and :changed entries."
      [old-report new-report]
      {:object-gaps (diff-layer (:object-gaps old-report)
                                (:object-gaps new-report))
       :morphism-gaps (diff-layer (:morphism-gaps old-report)
                                  (:morphism-gaps new-report))
       :path-gaps (diff-layer (:path-gaps old-report)
                              (:path-gaps new-report))})

(defn has-changes?
      "Returns true if the diff contains any introduced, resolved, or
  changed gaps."
      [diff]
      (some (fn [[_layer delta]]
                (or (seq (:introduced delta))
                    (seq (:resolved delta))
                    (seq (:changed delta))))
            diff))

(defn gaps-involving
      "Filters a gap report to only gaps that involve the given
  formalism kind keyword. Checks :formalism (object gaps), :from/:to
  on morphism steps, and path step formalisms."
      [report formalism-kind]
      (let [involves? (fn [gap]
                          (case (:layer gap)
                                :object (= formalism-kind (:formalism gap))
                                :morphism (or (= formalism-kind
                                                 (-> gap :detail :from))
                                              (= formalism-kind
                                                 (-> gap :detail :to))
                                              (= formalism-kind (:from gap))
                                              (= formalism-kind (:to gap)))
                                :path true
                                false))]
           {:object-gaps (into [] (filter involves?) (:object-gaps report))
            :morphism-gaps (into [] (filter involves?) (:morphism-gaps report))
            :path-gaps (:path-gaps report)}))
