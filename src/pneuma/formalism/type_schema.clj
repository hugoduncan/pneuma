(ns pneuma.formalism.type-schema
    "TypeSchema formalism — a registry of type keywords mapped to Malli
  schemas. Serves as the target for structural morphisms that check
  whether output type keywords in other formalisms resolve to known
  types. Implements IProjectable and IReferenceable."
    (:require [malli.core :as m]
              [malli.generator :as mg]
              [pneuma.doc.fragment :as doc]
              [pneuma.protocol :as p]))

(defrecord TypeSchema [types]
           p/IProjectable
           (->schema [_]
                     (into [:enum] (sort (keys types))))

           (->monitor [_]
                      (let [known (set (keys types))]
                           (fn monitor [entry]
                               (let [violations
                                     (into []
                                           (keep (fn [type-kw]
                                                     (when-not (contains? known type-kw)
                                                               {:status :diverges
                                                                :detail {:kind :unknown-type
                                                                         :type-kw type-kw}})))
                                           (:type-refs entry))]
                                    (if (seq violations)
                                        {:verdict :violation :violations violations}
                                        {:verdict :ok})))))

           (->gen [_]
                  (if (seq types)
                      (mg/generator (into [:enum] (sort (keys types))))
                      (mg/generator :keyword)))

           (->gap-type [_]
                       {:formalism :type-schema
                        :gap-kinds #{:unknown-type}
                        :statuses #{:conforms :absent :diverges}})

           (->doc [_]
                  (let [type-rows (mapv (fn [[type-kw schema]]
                                            {:type   (name type-kw)
                                             :schema (str schema)})
                                        types)]
                       (doc/section
                        :type-schema/root "Type Schema"
                        [(doc/table :type-schema/types
                                    [:type :schema]
                                    type-rows)])))

           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (case ref-kind
                               :type-ids (set (keys types))
                               #{})))

(defn type-schema
      "Creates a TypeSchema from a map of type-keyword → Malli schema.
  Validates that every value is a valid Malli schema."
      [types-map]
      (doseq [[kw schema] types-map]
             (when-not (try (m/schema schema) true (catch Exception _ false))
                       (throw (ex-info "Invalid Malli schema for type"
                                       {:type-kw kw :schema schema}))))
      (->TypeSchema types-map))
