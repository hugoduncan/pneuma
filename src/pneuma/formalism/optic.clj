(ns pneuma.formalism.optic
    "OpticDeclaration formalism — subscription optics as data.
  An optic declaration set describes lenses, traversals, folds, and
  derived subscriptions that focus on paths into shared state.
  Implements IProjectable to project into Malli schemas, trace monitors,
  test.check generators, and gap type descriptors."
    (:require [clojure.test.check.generators :as gen]
              [malli.core :as m]
              [malli.generator :as mg]
              [pneuma.doc.fragment :as doc]
              [pneuma.formalism.effect-signature :as effect-signature]
              [pneuma.protocol :as p]))

;;; Constructor validation schemas

(def ^:private param-schema
     [:map [:name :keyword] [:type :keyword]])

(def ^:private path-optic-declaration-schema
     [:map
      [:id :keyword]
      [:optic-type [:enum :Lens :Traversal :Fold]]
      [:params {:optional true} [:vector param-schema]]
      [:path [:vector :any]]])

(def ^:private derived-optic-declaration-schema
     [:map
      [:id :keyword]
      [:optic-type [:= :Derived]]
      [:params {:optional true} [:vector param-schema]]
      [:sources [:map-of :keyword [:vector :any]]]
      [:derivations [:map-of :keyword :any]]])

(def ^:private optic-declaration-schema
     [:or path-optic-declaration-schema derived-optic-declaration-schema])

(def optic-declaration-input-schema
     "Malli schema for the optic-declaration constructor input."
     [:map
      [:label :string]
      [:declarations [:vector optic-declaration-schema]]])

;;; Path resolution

(defn- resolve-path
       "Substitutes param-name segments in path with runtime values from params-map."
       [path params-map param-names]
       (mapv (fn [seg]
                 (if (and (keyword? seg) (contains? param-names seg))
                     (get params-map seg seg)
                     seg))
             path))

(defn- path-reachable?
       "Returns true if every key in path exists in nested map state.
  Distinguishes missing keys from nil values."
       [state path]
       (if (empty? path)
           true
           (let [head (first path)
                 tail (rest path)]
                (if (and (associative? state) (contains? state head))
                    (recur (get state head) (vec tail))
                    false))))

;;; Schema projection helpers

(defn- optic->malli-branch
       "Builds one :multi branch for an optic declaration."
       [declaration]
       (let [{:keys [id params]} declaration
             param-fields (into []
                                (map (fn [{:keys [name type]}]
                                         [name (effect-signature/resolve-type type)]))
                                params)]
            [id
             (into [:map [:optic-id [:= id]]]
                   (when (seq param-fields)
                         [[:params (into [:map] param-fields)]]))]))

(defn- build-optic-multi-schema
       "Builds a Malli :multi schema dispatching on :optic-id."
       [declarations]
       (into [:multi {:dispatch :optic-id}]
             (map optic->malli-branch)
             (vals declarations)))

;;; Derivation DSL evaluator

(defn- eval-derivation-expr
       "Evaluates a derivation expression against a map of resolved source values.
  Supported forms:
    [:length src-key]                    — count of source collection
    [:first src-key]                     — first element
    [:get-in [src-key & sub-path] field] — nested get from source value"
       [expr source-values]
       (let [[op & args] expr]
            (case op
                  :length (count (get source-values (first args)))
                  :first  (first (get source-values (first args)))
                  :get-in (let [[src-path field] args
                                [src-key & sub-path] src-path
                                base (get source-values src-key)]
                               (get (get-in base sub-path) field))
                  nil)))

;;; Monitor helpers

(defn- check-missing-subscription
       "Returns a gap if optic-id is not in declarations, else nil."
       [declarations optic-id]
       (when-not (contains? declarations optic-id)
                 {:status :absent
                  :detail {:kind :missing-subscription :optic-id optic-id}}))

(defn- check-broken-path
       "Returns a gap if the optic's concrete path cannot be resolved in state."
       [declaration params-map state]
       (let [path        (:path declaration)
             param-names (into #{} (map :name) (:params declaration []))
             concrete    (resolve-path path params-map param-names)]
            (when-not (path-reachable? state concrete)
                      {:status :diverges
                       :detail {:kind     :broken-path
                                :optic-id (:id declaration)
                                :path     concrete}})))

(defn- check-derived-sources
       "Returns gaps for any source paths that cannot be resolved in state."
       [declaration params-map state]
       (let [param-names (into #{} (map :name) (:params declaration []))]
            (into []
                  (keep (fn [[src-key src-path]]
                            (let [concrete (resolve-path src-path params-map param-names)]
                                 (when-not (path-reachable? state concrete)
                                           {:status :diverges
                                            :detail {:kind     :broken-path
                                                     :optic-id (:id declaration)
                                                     :source   src-key
                                                     :path     concrete}}))))
                  (:sources declaration))))

(defn- check-wrong-derivation
       "Returns gaps for derivation expressions that produce values inconsistent
  with the declared-value in the trace entry (when :derived-value is present)."
       [declaration params-map state derived-value]
       (when derived-value
             (let [{:keys [id sources derivations params]} declaration
                   param-names   (into #{} (map :name) (or params []))
                   source-values (into {}
                                       (map (fn [[src-key src-path]]
                                                (let [concrete (resolve-path src-path params-map param-names)]
                                                     [src-key (get-in state concrete)])))
                                       sources)]
                  (into []
                        (keep (fn [[deriv-key expr]]
                                  (let [expected (eval-derivation-expr expr source-values)
                                        actual   (get derived-value deriv-key)]
                                       (when (not= expected actual)
                                             {:status :diverges
                                              :detail {:kind      :wrong-derivation
                                                       :optic-id  id
                                                       :deriv-key deriv-key
                                                       :expected  expected
                                                       :actual    actual}}))))
                        derivations))))

;;; Record

(defrecord OpticDeclaration [label declarations]
           p/IProjectable
           (->schema [_]
                     (build-optic-multi-schema declarations))

           (->monitor [_]
                      (fn monitor [entry]
                          (let [{:keys [optic-id params state-before derived-value]} entry
                                absent (check-missing-subscription declarations optic-id)]
                               (if absent
                                   {:verdict :violation :violations [absent]}
                                   (let [decl       (get declarations optic-id)
                                         violations (case (:optic-type decl)
                                                          (:Lens :Traversal :Fold)
                                                          (if-let [v (check-broken-path
                                                                      decl (or params {}) (or state-before {}))]
                                                                  [v] [])

                                                          :Derived
                                                          (into (check-derived-sources
                                                                 decl (or params {}) (or state-before {}))
                                                                (check-wrong-derivation
                                                                 decl (or params {}) (or state-before {})
                                                                 derived-value)))]
                                        (if (seq violations)
                                            {:verdict :violation :violations violations}
                                            {:verdict :ok}))))))

           (->gen [_]
                  (let [optic-ids (vec (keys declarations))]
                       (gen/bind
                        (gen/elements optic-ids)
                        (fn [optic-id]
                            (let [decl   (get declarations optic-id)
                                  params (:params decl [])]
                                 (if (empty? params)
                                     (gen/return {:optic-id optic-id :params {}})
                                     (let [param-gens
                                           (into {}
                                                 (map (fn [{:keys [name type]}]
                                                          [name (mg/generator
                                                                 (effect-signature/resolve-type type))]))
                                                 params)]
                                          (apply gen/hash-map
                                                 :optic-id (gen/return optic-id)
                                                 :params (apply gen/hash-map
                                                                (into []
                                                                      (mapcat identity)
                                                                      param-gens))
                                                 []))))))))

           (->gap-type [_]
                       {:label label
                        :formalism :optic
                        :gap-kinds #{:broken-path :wrong-derivation :missing-subscription}
                        :statuses  #{:conforms :absent :diverges}})

           (->doc [_]
                  (let [optic-rows
                        (mapv (fn [[_ decl]]
                                  {:id         (name (:id decl))
                                   :optic-type (name (:optic-type decl))
                                   :path       (if (= :Derived (:optic-type decl))
                                                   (str (keys (:sources decl {})))
                                                   (str (:path decl)))})
                              declarations)]
                       (doc/section
                        :optic/root label
                        [(doc/table :optic/catalog
                                    [:id :optic-type :path]
                                    optic-rows)])))

           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (case ref-kind
                               :optic-ids
                               (into #{} (map :id) (vals declarations))

                               :paths
                               (into #{}
                                     (keep (fn [decl]
                                               (when (#{:Lens :Traversal :Fold} (:optic-type decl))
                                                     (:path decl))))
                                     (vals declarations))

                               :source-optic-refs
                               (into #{}
                                     (mapcat (fn [decl]
                                                 (when (= :Derived (:optic-type decl))
                                                       (vals (:sources decl {})))))
                                     (vals declarations))

                               #{})))

;;; Public constructor

(defn optic-declaration
      "Creates a validated OpticDeclaration from a map with :label and :declarations.
  :label is a display string. Each declaration has :id, :optic-type
  (:Lens/:Traversal/:Fold/:Derived), optional :params, and type-specific fields
  (:path or :sources/:derivations). Throws ex-info on invalid input or duplicate ids."
      [m]
      (when-not (m/validate optic-declaration-input-schema m)
                (throw (ex-info "Invalid optic-declaration input"
                                {:explanation (m/explain optic-declaration-input-schema m)})))
      (let [declarations (:declarations m)
            ids          (mapv :id declarations)
            id-counts    (frequencies ids)
            duplicates   (into []
                               (keep (fn [[id cnt]] (when (> cnt 1) id)))
                               id-counts)]
           (when (seq duplicates)
                 (throw (ex-info "Duplicate optic ids"
                                 {:duplicate-ids duplicates})))
           (let [indexed (into {} (map (fn [decl] [(:id decl) decl])) declarations)]
                (->OpticDeclaration (:label m) indexed))))
