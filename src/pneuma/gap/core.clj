(ns pneuma.gap.core
    "Gap report assembly — combines object-level, morphism-level,
  and path-level gaps into the three-layer gap report structure."
    (:require [pneuma.path.core :as path]
              [pneuma.protocol :as p]))

(defn check-object-gaps
      "Checks a single formalism against a refinement map, producing
  object-level gaps. Currently validates that the formalism's gap-type
  descriptor is well-formed."
      [formalism]
      (let [gt (p/->gap-type formalism)]
           (if (and (:formalism gt) (:gap-kinds gt) (:statuses gt))
               [{:layer :object
                 :formalism (:formalism gt)
                 :status :conforms}]
               [{:layer :object
                 :formalism (:formalism gt)
                 :status :diverges
                 :detail {:kind :malformed-gap-type
                          :gap-type gt}}])))

(defn check-morphism-gaps
      "Checks all morphisms in the registry against the provided
  formalisms map, producing morphism-level gaps."
      [registry formalisms-by-kind]
      (into []
            (mapcat
             (fn [[_id morphism]]
                 (let [source (get formalisms-by-kind (:from morphism))
                       target (get formalisms-by-kind (:to morphism))]
                      (cond
                       (nil? source)
                       [{:layer :morphism
                         :id (:id morphism)
                         :status :absent
                         :detail {:reason :source-formalism-missing
                                  :from (:from morphism)}}]

                       (nil? target)
                       [{:layer :morphism
                         :id (:id morphism)
                         :status :absent
                         :detail {:reason :target-formalism-missing
                                  :to (:to morphism)}}]

                       :else
                       (mapv #(assoc % :layer :morphism)
                             (p/check morphism source target {}))))))
            registry))

(defn gap-report
      "Assembles a three-layer gap report from a set of formalisms and
  a morphism registry. Path gaps are discovered automatically via
  Johnson's algorithm on the morphism graph.

  Config map keys:
    :formalisms     - map of kind keyword → formalism record
    :registry       - map of morphism id → morphism record"
      [{:keys [formalisms registry]}]
      (let [object-gaps (into [] (mapcat check-object-gaps) (vals formalisms))
            morphism-gaps (check-morphism-gaps registry formalisms)
            path-gaps (path/check-all-paths registry)]
           {:object-gaps object-gaps
            :morphism-gaps morphism-gaps
            :path-gaps path-gaps}))

(defn failures
      "Returns only non-conforming gaps from a gap report."
      [report]
      (let [non-conforming (fn [gaps]
                               (into [] (remove #(= :conforms (:status %))) gaps))]
           {:object-gaps (non-conforming (:object-gaps report))
            :morphism-gaps (non-conforming (:morphism-gaps report))
            :path-gaps (non-conforming (:path-gaps report))}))

(defn has-failures?
      "Returns true if the gap report contains any non-conforming gaps."
      [report]
      (let [f (failures report)]
           (or (seq (:object-gaps f))
               (seq (:morphism-gaps f))
               (seq (:path-gaps f)))))
