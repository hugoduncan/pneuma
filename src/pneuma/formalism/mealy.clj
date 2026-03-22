(ns pneuma.formalism.mealy
    "MealyHandlerSet formalism — Mealy-machine handler set as data.
  A handler set is a map of named handlers, each with typed params,
  guards, state-update declarations, and effect declarations.
  Implements IProjectable to project into Malli schemas, trace monitors,
  test.check generators, and gap type descriptors."
    (:require [clojure.string :as str]
              [clojure.test.check.generators :as gen]
              [malli.core :as m]
              [malli.generator :as mg]
              [pneuma.doc.fragment :as doc]
              [pneuma.formalism.effect-signature :as effect-signature]
              [pneuma.protocol :as p]))

;;; Constructor validation schema

(def handler-declaration-schema
     "Malli schema for a single handler declaration."
     [:map
      [:id :keyword]
      [:params {:optional true}
       [:vector [:map [:name :keyword] [:type :keyword]]]]
      [:guards {:optional true}
       [:vector [:map [:check :keyword] [:args [:vector :any]]]]]
      [:updates {:optional true}
       [:vector [:map [:path [:vector :any]] [:op :keyword] [:value :any]]]]
      [:effects {:optional true}
       [:vector [:map [:op :keyword] [:fields [:map-of :keyword :any]]]]]])

(def mealy-handler-set-input-schema
     "Malli schema for the mealy-handler-set constructor input."
     [:map
      [:declarations
       [:vector handler-declaration-schema]]])

;;; Schema projection helpers

(defn- handler->malli-branch
       "Builds one :multi branch for a handler declaration.
  The branch validates that a trace entry has the right param names."
       [declaration]
       (let [{:keys [id params]} declaration
             param-fields (into []
                                (map (fn [{:keys [name type]}]
                                         [name (effect-signature/resolve-type type)]))
                                params)]
            [id
             (into [:map [:handler-id [:= id]]]
                   (when (seq param-fields)
                         [[:params (into [:map] param-fields)]]))]))

(defn- build-handler-multi-schema
       "Builds a Malli :multi schema dispatching on :handler-id."
       [declarations]
       (into [:multi {:dispatch :handler-id}]
             (map handler->malli-branch)
             (vals declarations)))

;;; Monitor helpers

(defn- check-absent-handler
       "Returns a gap map if handler-id is not in declarations, else nil."
       [declarations handler-id]
       (when-not (contains? declarations handler-id)
                 {:status :absent
                  :detail {:kind       :absent-handler
                           :handler-id handler-id}}))

(defn- check-wrong-emission
       "Returns gap maps for any declared effects whose :op is absent from actual effects."
       [declaration actual-effects]
       (let [actual-ops (into #{} (map :op) actual-effects)
             declared-effects (:effects declaration [])]
            (into []
                  (keep (fn [declared-effect]
                            (let [op (:op declared-effect)]
                                 (when-not (contains? actual-ops op)
                                           {:status :diverges
                                            :detail {:kind       :wrong-emission
                                                     :handler-id (:id declaration)
                                                     :missing-op op}}))))
                  declared-effects)))

(defn- check-wrong-update
       "Returns gap maps for declared updates where db-after does not differ from db-before.
  Resolves symbolic path segments using the runtime params map.
  TODO: This is a path-level approximation — does not validate the new value itself."
       [declaration params-map db-before db-after]
       (let [declared-updates (:updates declaration [])
             param-names      (into #{} (map :name) (:params declaration []))]
            (into []
                  (keep (fn [declared-update]
                            (let [path (:path declared-update)
                                  ;; substitute param-name segments with runtime values
                                  concrete-path (mapv (fn [seg]
                                                          (if (and (keyword? seg)
                                                                   (contains? param-names seg))
                                                              (get params-map seg seg)
                                                              seg))
                                                      path)
                                  val-before    (get-in db-before concrete-path)
                                  val-after     (get-in db-after concrete-path)]
                                 (when (= val-before val-after)
                                       {:status :diverges
                                        :detail {:kind       :wrong-update
                                                 :handler-id (:id declaration)
                                                 :path       path}}))))
                  declared-updates)))

;;; Ref extraction helpers

(defn- collect-callback-refs
       "Recursively walks a value and collects the keyword from any
  [:event-ref kw ...] tagged vector found."
       [v]
       (cond
        (and (vector? v)
             (= :event-ref (first v))
             (keyword? (second v)))
        #{(second v)}

        (map? v)
        (into #{} (mapcat collect-callback-refs) (vals v))

        (vector? v)
        (into #{} (mapcat collect-callback-refs) v)

        (sequential? v)
        (into #{} (mapcat collect-callback-refs) v)

        :else #{}))

;;; Record

(defrecord MealyHandlerSet [declarations]
           p/IProjectable
           (->schema [_]
                     (build-handler-multi-schema declarations))

           (->monitor [_]
                      (fn monitor [entry]
                          (let [{:keys [handler-id db-before db-after effects params]} entry
                                absent-violation (check-absent-handler declarations handler-id)]
                               (if absent-violation
                                   {:verdict :violation :violations [absent-violation]}
                                   (let [declaration  (get declarations handler-id)
                                         emission-violations (check-wrong-emission
                                                              declaration
                                                              (or effects []))
                                         update-violations   (check-wrong-update
                                                              declaration
                                                              (or params {})
                                                              (or db-before {})
                                                              (or db-after {}))
                                         all-violations (into emission-violations update-violations)]
                                        (if (seq all-violations)
                                            {:verdict :violation :violations all-violations}
                                            {:verdict :ok}))))))

           (->gen [_]
                  (let [handler-ids (vec (keys declarations))]
                       (gen/bind
                        (gen/elements handler-ids)
                        (fn [handler-id]
                            (let [declaration (get declarations handler-id)
                                  params      (:params declaration [])]
                                 (if (empty? params)
                                     (gen/return {:handler-id handler-id :params {}})
                                     (let [param-gens
                                           (into {}
                                                 (map (fn [{:keys [name type]}]
                                                          [name (mg/generator
                                                                 (effect-signature/resolve-type type))]))
                                                 params)]
                                          (apply gen/hash-map
                                                 :handler-id (gen/return handler-id)
                                                 :params (apply gen/hash-map
                                                                (into []
                                                                      (mapcat identity)
                                                                      param-gens))
                                                 []))))))))

           (->gap-type [_]
                       {:formalism :mealy
                        :gap-kinds #{:absent-handler :missing-guard
                                     :wrong-update :wrong-emission}
                        :statuses  #{:conforms :absent :diverges}})

           (->doc [_]
                  (let [handler-rows
                        (mapv (fn [[_ decl]]
                                  {:handler (name (:id decl))
                                   :params  (str/join ", " (mapv (comp name :name) (:params decl [])))
                                   :guards  (str/join ", " (mapv (comp name :check) (:guards decl [])))
                                   :updates (str (count (:updates decl [])))
                                   :effects (str/join ", " (mapv (comp name :op) (:effects decl [])))})
                              declarations)]
                       (doc/section
                        :mealy/root "Mealy Handler Set"
                        [(doc/table :mealy/handlers
                                    [:handler :params :guards :updates :effects]
                                    handler-rows)])))

           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (case ref-kind
                               :handler-ids
                               (into #{} (map :id) (vals declarations))

                               :guard-state-refs
                               (into #{}
                                     (mapcat (fn [decl]
                                                 (keep (fn [guard]
                                                           (when (= :in-state? (:check guard))
                                                                 (second (:args guard))))
                                                       (:guards decl []))))
                                     (vals declarations))

                               :emission-op-refs
                               (into #{}
                                     (mapcat (fn [decl]
                                                 (map :op (:effects decl []))))
                                     (vals declarations))

                               :callback-refs
                               (into #{}
                                     (mapcat (fn [decl]
                                                 (mapcat (fn [effect]
                                                             (collect-callback-refs (:fields effect {})))
                                                         (:effects decl []))))
                                     (vals declarations))

                               :update-path-refs
                               (into #{}
                                     (mapcat (fn [decl]
                                                 (map :path (:updates decl []))))
                                     (vals declarations))

                               #{})))

;;; Public constructor

(defn mealy-handler-set
      "Creates a validated MealyHandlerSet from a map with :declarations.
  Each declaration is a map with :id, and optional :params, :guards, :updates, :effects.
  Throws ex-info on invalid input or duplicate handler ids."
      [m]
      (when-not (m/validate mealy-handler-set-input-schema m)
                (throw (ex-info "Invalid mealy-handler-set input"
                                {:explanation (m/explain mealy-handler-set-input-schema m)})))
      (let [declarations (:declarations m)
            ids (mapv :id declarations)
            id-counts (frequencies ids)
            duplicates (into [] (keep (fn [[id cnt]] (when (> cnt 1) id))) id-counts)]
           (when (seq duplicates)
                 (throw (ex-info "Duplicate handler ids"
                                 {:duplicate-ids duplicates})))
           (let [indexed (into {} (map (fn [decl] [(:id decl) decl])) declarations)]
                (->MealyHandlerSet indexed))))
