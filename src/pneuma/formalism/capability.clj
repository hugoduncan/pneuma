(ns pneuma.formalism.capability
    "CapabilitySet formalism — bounded permission sets as data.
  A capability set declares which events an agent may dispatch, which
  subscriptions it may observe, and which queries it may issue.
  Capabilities are substructural resources: explicitly granted, not
  ambient. Implements IProjectable and IReferenceable."
    (:require [clojure.test.check.generators :as gen]
              [malli.core :as m]
              [pneuma.protocol :as p]))

;;; Constructor validation schema

(def capability-set-input-schema
     "Malli schema for the capability-set constructor input."
     [:map
      [:id :keyword]
      [:dispatch [:set :keyword]]
      [:subscribe {:optional true} [:set :keyword]]
      [:query {:optional true} [:set :keyword]]])

;;; Schema projection helpers

(defn- sets->malli-schema
       "Builds a Malli schema validating that an operation keyword is a
  member of one of the capability sets."
       [{:keys [dispatch subscribe query]}]
       (let [all-ops (into #{} cat [(or dispatch #{})
                                    (or subscribe #{})
                                    (or query #{})])]
            (if (seq all-ops)
                (into [:enum] (sort all-ops))
                [:enum])))

;;; Monitor helpers

(defn- check-operation
       "Checks whether an operation is within the capability bounds.
  Returns nil if within bounds, or a gap map if not."
       [caps-id kind allowed-set op]
       (when-not (contains? allowed-set op)
                 {:status :diverges
                  :detail {:kind :unauthorized
                           :caps-id caps-id
                           :operation-kind kind
                           :op op
                           :allowed allowed-set}}))

;;; Record

(defrecord CapabilitySet [id dispatch subscribe query]
           p/IProjectable
           (->schema [_]
                     (sets->malli-schema {:dispatch dispatch
                                          :subscribe subscribe
                                          :query query}))

           (->monitor [_]
                      (fn monitor [entry]
                          (let [violations
                                (into []
                                      (comp
                                       (mapcat
                                        (fn [{:keys [kind op]}]
                                            (case kind
                                                  :dispatch [(check-operation id :dispatch
                                                                              dispatch op)]
                                                  :subscribe [(check-operation id :subscribe
                                                                               (or subscribe #{})
                                                                               op)]
                                                  :query [(check-operation id :query
                                                                           (or query #{})
                                                                           op)]
                                                  [nil])))
                                       (keep identity))
                                      (:capability-checks entry))]
                               (if (seq violations)
                                   {:verdict :violation :violations violations}
                                   {:verdict :ok}))))

           (->gen [_]
                  (let [all-ops (into [] cat [(or dispatch [])
                                              (or subscribe [])
                                              (or query [])])]
                       (if (seq all-ops)
                           (gen/elements all-ops)
                           (gen/return nil))))

           (->gap-type [_]
                       {:formalism :capability-set
                        :gap-kinds #{:unauthorized :empty-set}
                        :statuses #{:conforms :absent :diverges}})

           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (case ref-kind
                               :dispatch-refs (or dispatch #{})
                               :subscribe-refs (or subscribe #{})
                               :query-refs (or query #{})
                               :all-refs (into #{}
                                               cat
                                               [(or dispatch #{})
                                                (or subscribe #{})
                                                (or query #{})])
                               #{})))

;;; Public constructor

(defn capability-set
      "Creates a validated CapabilitySet from a map with :id, :dispatch,
  and optionally :subscribe and :query. Each is a set of keywords.
  Throws on invalid input."
      [m]
      (when-not (m/validate capability-set-input-schema m)
                (throw (ex-info "Invalid capability set"
                                {:explanation (m/explain capability-set-input-schema m)})))
      (->CapabilitySet (:id m)
                       (:dispatch m)
                       (or (:subscribe m) #{})
                       (or (:query m) #{})))
