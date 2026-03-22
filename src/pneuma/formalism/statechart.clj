(ns pneuma.formalism.statechart
    "Statechart formalism — Harel statechart as data.
  A statechart is a set of states with hierarchy (nesting), optional
  parallel composition, initial-child mappings, and guarded transitions.
  Implements IProjectable to project into Malli schemas, trace monitors,
  test.check generators, and gap type descriptors."
    (:require [clojure.set :as set]
              [clojure.string :as str]
              [clojure.test.check.generators :as gen]
              [malli.core :as m]
              [pneuma.doc.fragment :as doc]
              [pneuma.protocol :as p]))

;;; Constructor validation schema

(def transition-schema
     "Malli schema for a single transition declaration."
     [:map
      [:source :keyword]
      [:event  :keyword]
      [:target :keyword]
      [:raise  {:optional true} [:or :keyword [:vector :keyword]]]
      [:guard  {:optional true} :keyword]])

(def statechart-input-schema
     "Malli schema for the statechart constructor input."
     [:map
      [:label       :string]
      [:states      [:set :keyword]]
      [:hierarchy   {:optional true} [:map-of :keyword [:set :keyword]]]
      [:parallel    {:optional true} [:set :keyword]]
      [:initial     [:map-of :keyword :keyword]]
      [:transitions [:vector transition-schema]]])

;;; Configuration helpers

(defn- leaf-states
       "Returns states that are not parents in the hierarchy.
  These are the atomic states a configuration consists of."
       [states hierarchy]
       (set/difference states (set (keys hierarchy))))

(defn- build-parent-index
       "Inverts the hierarchy map to a child→parent lookup."
       [hierarchy]
       (into {}
             (mapcat (fn [[parent children]]
                         (map (fn [child] [child parent]) children)))
             hierarchy))

(defn- expand-to-config
       "Expands a state down to its leaf states using the initial map.
  For parallel composites, expands ALL children. For non-parallel
  composites, follows the initial map to pick one child. Leaf states
  expand to themselves."
       [state hierarchy parallel initial]
       (if-not (contains? hierarchy state)
               ;; leaf state
               #{state}
               (if (contains? parallel state)
                 ;; parallel: expand all children concurrently
                   (into #{}
                         (mapcat (fn [child]
                                     (expand-to-config child hierarchy parallel initial)))
                         (get hierarchy state))
                 ;; non-parallel composite: follow initial map
                   (let [initial-child (get initial state)]
                        (expand-to-config initial-child hierarchy parallel initial)))))

(defn- initial-config
       "Computes the initial configuration from the hierarchy roots.
  Roots are hierarchy keys that are not children of any other state."
       [hierarchy parallel initial]
       (let [parent-index (build-parent-index hierarchy)
             roots        (into #{}
                                (filter (fn [k] (not (contains? parent-index k))))
                                (keys hierarchy))]
            (into #{}
                  (mapcat (fn [root]
                              (expand-to-config root hierarchy parallel initial)))
                  roots)))

;;; Step function

(defn- step
       "Computes δ(config, event) → new-config.
  Finds all transitions matching the current config and event,
  then updates the config. Returns config unchanged if no transitions fire.
  TODO: does not process :raise events (internal event queuing)."
       [config event transitions hierarchy parallel initial]
       (let [matching (into []
                            (filter (fn [t]
                                        (and (contains? config (:source t))
                                             (= event (:event t)))))
                            transitions)]
            (if (empty? matching)
                config
                (reduce (fn [cfg {:keys [source target]}]
                            (-> cfg
                                (disj source)
                                (set/union (expand-to-config target hierarchy parallel initial))))
                        config
                        matching))))

;;; Reachability

(defn- reachable-configs
       "BFS from initial-config over all possible events.
  Returns the set of all reachable configurations."
       [init-config all-events transitions hierarchy parallel initial]
       (loop [visited  #{init-config}
              frontier #{init-config}]
             (if (empty? frontier)
                 visited
                 (let [next-configs
                       (into #{}
                             (mapcat (fn [cfg]
                                         (map (fn [ev]
                                                  (step cfg ev transitions hierarchy parallel initial))
                                              all-events)))
                             frontier)
                       new-configs (set/difference next-configs visited)]
                      (recur (set/union visited new-configs)
                             new-configs)))))

;;; Monitor helpers

(defn- check-config-valid
       "Verifies a config contains only known leaf states.
  Returns a violation map or nil."
       [config known-leaves label]
       (let [unknown (set/difference config known-leaves)]
            (when (seq unknown)
                  {:status :diverges
                   :detail {:kind    :invalid-config
                            :label   label
                            :unknown unknown}})))

(defn- check-step
       "Verifies config-after matches δ(config-before, event).
  Returns a violation map or nil."
       [config-before event config-after transitions hierarchy parallel initial]
       (let [expected (step config-before event transitions hierarchy parallel initial)]
            (when (not= expected config-after)
                  {:status :diverges
                   :detail {:kind          :missing-transition
                            :config-before config-before
                            :event         event
                            :expected      expected
                            :actual        config-after}})))

;;; Record

(defrecord Statechart [label states hierarchy parallel initial transitions]
           p/IProjectable
           (->schema [_]
                     (let [leaves (leaf-states states hierarchy)]
                          [:set (into [:enum] (sort leaves))]))

           (->monitor [_]
                      (let [leaves (leaf-states states hierarchy)]
                           (fn monitor [entry]
                               (let [{:keys [config-before event config-after]} entry
                                     violations
                                     (into []
                                           (keep identity)
                                           [(check-config-valid config-before leaves :config-before)
                                            (check-config-valid config-after leaves :config-after)
                                            (when (and (empty? (check-config-valid config-before leaves :config-before))
                                                       (empty? (check-config-valid config-after leaves :config-after)))
                                                  (check-step config-before event config-after
                                                              transitions hierarchy parallel initial))])]
                                    (if (seq violations)
                                        {:verdict :violation :violations violations}
                                        {:verdict :ok})))))

           (->gen [_]
                  (let [all-events  (into #{} (map :event) transitions)
                        init-config (initial-config hierarchy parallel initial)
                        all-configs (reachable-configs init-config all-events transitions
                                                       hierarchy parallel initial)]
                       (if (empty? all-configs)
                           (gen/return #{})
                           (gen/elements (vec all-configs)))))

           (->gap-type [_]
                       {:label label
                        :formalism :statechart
                        :gap-kinds #{:missing-state :missing-transition
                                     :unreachable-state :invalid-config}
                        :statuses  #{:conforms :absent :diverges}})

           (->doc [_]
                  (let [leaves    (leaf-states states hierarchy)
                        all-evts  (into #{} (map :event) transitions)
                        init-cfg  (initial-config hierarchy parallel initial)
                        all-cfgs  (reachable-configs init-cfg all-evts transitions
                                                     hierarchy parallel initial)
                        diag-data {:states      (vec leaves)
                                   :transitions (mapv (fn [t]
                                                          [(:source t)
                                                           (:target t)
                                                           (name (:event t))])
                                                      transitions)}
                        t-rows    (mapv (fn [t]
                                            {:source (name (:source t))
                                             :event  (name (:event t))
                                             :target (name (:target t))
                                             :guard  (if-let [g (:guard t)] (name g) "")
                                             :raise  (if-let [r (:raise t)] (str r) "")})
                                        transitions)
                        cfg-text  (str/join
                                   ", "
                                   (mapv str (sort-by str all-cfgs)))]
                       (doc/section
                        :statechart/root label
                        (filterv some?
                                 [(doc/summary :statechart/summary
                                               (str (count leaves) " states, "
                                                    (count transitions) " transitions, "
                                                    (count all-cfgs) " configs"))
                                  (doc/diagram-spec :statechart/diagram :mermaid-state diag-data)
                                  (doc/table :statechart/transitions
                                             [:source :event :target :guard :raise]
                                             t-rows)
                                  (doc/prose :statechart/configs
                                             (str "Reachable configurations: " cfg-text))
                                  (when (seq hierarchy)
                                        (doc/prose :statechart/hierarchy
                                                   (str "Hierarchy: " hierarchy)))]))))

           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (case ref-kind
                               :state-ids states
                               :event-ids (into #{} (map :event) transitions)
                               :raised-events
                               (into #{}
                                     (mapcat (fn [t]
                                                 (let [r (:raise t)]
                                                      (cond
                                                       (nil? r)     []
                                                       (keyword? r) [r]
                                                       :else        r))))
                                     transitions)
                               #{})))

;;; Public constructor

(defn statechart
      "Creates a validated Statechart from a map.
  Required keys: :label (display string), :states (set of keywords),
  :initial (map of composite→child), :transitions (vector of transition maps).
  Optional keys: :hierarchy (map of parent→#{children}), :parallel (set of parallel states).
  Throws ex-info on invalid input or domain invariant violations."
      [m]
      (when-not (m/validate statechart-input-schema m)
                (throw (ex-info "Invalid statechart input"
                                {:explanation (m/explain statechart-input-schema m)})))
      (let [{:keys [label states transitions initial]
             hierarchy :hierarchy
             parallel  :parallel
             :or       {hierarchy {} parallel #{}}} m]
           ;; Domain invariants
           ;; Children can be either leaf states or other composite states
           (let [all-children  (into #{} (mapcat val) hierarchy)
                 known-nodes   (set/union states (set (keys hierarchy)))]
                (when-let [bad (seq (set/difference all-children known-nodes))]
                          (throw (ex-info "Hierarchy children not in states or hierarchy"
                                          {:unknown-children (vec bad)}))))
           (when-let [bad (seq (set/difference parallel (set (keys hierarchy))))]
                     (throw (ex-info "Parallel members must be hierarchy keys"
                                     {:unknown-parallel (vec bad)})))
           (let [hierarchy-keys (set (keys hierarchy))
                 known-nodes    (set/union states hierarchy-keys)]
                (doseq [[k v] initial]
                       (when-not (contains? hierarchy-keys k)
                                 (throw (ex-info "Initial key must be a hierarchy key"
                                                 {:key k})))
                       (when-not (contains? known-nodes v)
                                 (throw (ex-info "Initial value must be a known state or composite"
                                                 {:key k :value v})))))
           (let [known-nodes (set/union states (set (keys hierarchy)))]
                (doseq [{:keys [source target]} transitions]
                       (when-not (contains? known-nodes source)
                                 (throw (ex-info "Transition source not in states"
                                                 {:source source})))
                       (when-not (contains? known-nodes target)
                                 (throw (ex-info "Transition target not in states"
                                                 {:target target})))))
           (->Statechart label states hierarchy parallel initial transitions)))
