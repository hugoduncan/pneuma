(ns pneuma.formalism.resolver
    "ResolverGraph formalism — functional dependency hypergraph as data.
  A resolver graph is a set of resolvers, each declaring input and output
  attribute sets. Resolvers are hyperedges in an attribute dependency graph.
  Reachability is computed via the chase algorithm (fixpoint iteration).
  Implements IProjectable to project into Malli schemas, trace monitors,
  test.check generators, and gap type descriptors."
    (:require [clojure.set :as set]
              [clojure.test.check.generators :as gen]
              [malli.core :as m]
              [pneuma.protocol :as p]))

;;; Constructor validation schema

(def ^:private resolver-source-schema
     [:or
      [:= :local]
      [:tuple [:= :external] :keyword]])

(def ^:private resolver-declaration-schema
     [:map
      [:id :keyword]
      [:input [:set :keyword]]
      [:output [:set :keyword]]
      [:source {:optional true} resolver-source-schema]])

(def resolver-graph-input-schema
     "Malli schema for the resolver-graph constructor input."
     [:map [:declarations [:vector resolver-declaration-schema]]])

;;; Chase algorithm

(defn reachable-attributes
      "Computes the set of attributes reachable from known-attrs via chase.
  Iteratively fires resolvers whose input sets are subsets of the known set,
  adding their outputs. Returns the fixpoint set."
      [declarations known-attrs]
      (loop [known known-attrs]
            (let [new-attrs (reduce (fn [acc decl]
                                        (if (set/subset? (:input decl) known)
                                            (set/union acc (:output decl))
                                            acc))
                                    known
                                    (vals declarations))]
                 (if (= new-attrs known)
                     known
                     (recur new-attrs)))))

;;; Schema projection helpers

(defn- resolver->malli-branch
       "Builds one :multi branch for a resolver declaration.
  Validates that a trace entry has the resolver-id and input attributes."
       [declaration]
       (let [{:keys [id]} declaration]
            [id
             [:map
              [:resolver-id [:= id]]
              [:input-attrs [:map-of :keyword :any]]
              [:output-attrs {:optional true} [:map-of :keyword :any]]]]))

(defn- build-resolver-multi-schema
       "Builds a Malli :multi schema dispatching on :resolver-id."
       [declarations]
       (into [:multi {:dispatch :resolver-id}]
             (map resolver->malli-branch)
             (vals declarations)))

;;; Monitor helpers

(defn- check-missing-resolver
       "Returns a gap if resolver-id is not in declarations, else nil."
       [declarations resolver-id]
       (when-not (contains? declarations resolver-id)
                 {:status :absent
                  :detail {:kind :missing-resolver :resolver-id resolver-id}}))

(defn- check-wrong-output
       "Returns gaps for declared output attributes missing from actual output."
       [declaration output-attrs]
       (when output-attrs
             (let [declared-outputs (:output declaration)
                   actual-keys (set (keys output-attrs))
                   missing (set/difference declared-outputs actual-keys)]
                  (when (seq missing)
                        [{:status :diverges
                          :detail {:kind        :wrong-output
                                   :resolver-id (:id declaration)
                                   :missing     missing}}]))))

;;; Record

(defrecord ResolverGraph [declarations]
           p/IProjectable
           (->schema [_]
                     (build-resolver-multi-schema declarations))

           (->monitor [_]
                      (fn monitor [entry]
                          (let [{:keys [resolver-id output-attrs]} entry
                                absent (check-missing-resolver declarations resolver-id)]
                               (if absent
                                   {:verdict :violation :violations [absent]}
                                   (let [decl       (get declarations resolver-id)
                                         violations (into []
                                                          cat
                                                          [(check-wrong-output decl output-attrs)])]
                                        (if (seq violations)
                                            {:verdict :violation :violations violations}
                                            {:verdict :ok}))))))

           (->gen [_]
                  (let [resolver-ids (vec (keys declarations))]
                       (gen/bind
                        (gen/elements resolver-ids)
                        (fn [resolver-id]
                            (let [decl        (get declarations resolver-id)
                                  input-attrs (:input decl)]
                                 (if (empty? input-attrs)
                                     (gen/return {:resolver-id resolver-id
                                                  :input-attrs {}})
                                     (let [attr-gens (into {}
                                                           (map (fn [attr]
                                                                    [attr (gen/fmap str gen/string-alphanumeric)]))
                                                           input-attrs)]
                                          (gen/fmap (fn [attrs]
                                                        {:resolver-id resolver-id
                                                         :input-attrs attrs})
                                                    (apply gen/hash-map
                                                           (into []
                                                                 (mapcat identity)
                                                                 attr-gens))))))))))

           (->gap-type [_]
                       {:formalism :resolver
                        :gap-kinds #{:missing-resolver :unreachable-attribute :wrong-output}
                        :statuses  #{:conforms :absent :diverges}})

           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (case ref-kind
                               :resolver-ids
                               (into #{} (map :id) (vals declarations))

                               :input-attributes
                               (into #{} (mapcat :input) (vals declarations))

                               :output-attributes
                               (into #{} (mapcat :output) (vals declarations))

                               :external-sources
                               (into #{}
                                     (keep (fn [decl]
                                               (when (vector? (:source decl))
                                                     (second (:source decl)))))
                                     (vals declarations))

                               #{})))

;;; Public constructor

(defn resolver-graph
      "Creates a validated ResolverGraph from a map with :declarations.
  Each declaration has :id, :input (set of attribute keywords),
  :output (set of attribute keywords), and optional :source.
  Throws ex-info on invalid input or duplicate ids."
      [m]
      (when-not (m/validate resolver-graph-input-schema m)
                (throw (ex-info "Invalid resolver-graph input"
                                {:explanation (m/explain resolver-graph-input-schema m)})))
      (let [declarations (:declarations m)
            ids          (mapv :id declarations)
            id-counts    (frequencies ids)
            duplicates   (into []
                               (keep (fn [[id cnt]] (when (> cnt 1) id)))
                               id-counts)]
           (when (seq duplicates)
                 (throw (ex-info "Duplicate resolver ids"
                                 {:duplicate-ids duplicates})))
           (let [indexed (into {} (map (fn [decl] [(:id decl) decl])) declarations)]
                (->ResolverGraph indexed))))
