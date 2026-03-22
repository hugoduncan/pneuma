(ns pneuma.formalism.type-schema
    "TypeSchema formalism — a registry of type keywords mapped to Malli
  schemas. Serves as the target for structural morphisms that check
  whether output type keywords in other formalisms resolve to known
  types. Implements IProjectable and IReferenceable."
    (:require [malli.core :as m]
              [malli.generator :as mg]
              [pneuma.doc.fragment :as doc]
              [pneuma.protocol :as p]))

(def type-schema-input-schema
     "Malli schema for the type-schema constructor input."
     [:map
      [:label :string]
      [:types [:map-of :keyword :any]]])

(defrecord TypeSchema [label types]
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
                       {:label label
                        :formalism :type-schema
                        :gap-kinds #{:unknown-type}
                        :statuses #{:conforms :absent :diverges}})

           (->doc [_]
                  (let [type-rows (mapv (fn [[type-kw schema]]
                                            {:type   (name type-kw)
                                             :schema (str schema)})
                                        types)]
                       (doc/section
                        :type-schema/root label
                        [(doc/summary :type-schema/summary
                                      (str (count types) " types"))
                         (doc/table :type-schema/types
                                    [:type :schema]
                                    type-rows)])))

           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (case ref-kind
                               :type-ids (set (keys types))
                               #{})))

(defn type-schema
      "Creates a TypeSchema from a map with :label and :types.
  :label is a display string. :types is a map of type-keyword → Malli schema.
  Validates input structure and that every type value is a valid Malli schema."
      [m]
      (when-not (m/validate type-schema-input-schema m)
                (throw (ex-info "Invalid type-schema input"
                                {:explanation (m/explain type-schema-input-schema m)})))
      (doseq [[kw schema] (:types m)]
             (when-not (try (m/schema schema) true (catch Exception _ false))
                       (throw (ex-info "Invalid Malli schema for type"
                                       {:type-kw kw :schema schema}))))
      (->TypeSchema (:label m) (:types m)))
