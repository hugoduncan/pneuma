(ns pneuma.formalism.effect-signature
    "EffectSignature formalism — algebraic effect signature as data.
  An effect signature is a set of named operations, each with typed
  input fields and a typed output. Implements IProjectable to project
  into Malli schemas, trace monitors, test.check generators, and gap
  type descriptors."
    (:require [clojure.string :as str]
              [malli.core :as m]
              [malli.generator :as mg]
              [pneuma.doc.fragment :as doc]
              [pneuma.protocol :as p]))

;;; Type registry

(def default-type-registry
     "Default mapping from type keywords to Malli schemas.
  Extended at runtime via register-type!."
     {:String :string
      :Keyword :keyword
      :Bool :boolean
      :Nat nat-int?
      :Int :int
      :Any :any
      :KeywordSet [:set :keyword]
      :EventRef :keyword})

(defonce ^{:doc "Mutable type registry. Maps type keywords to Malli schemas."}
 type-registry
         (atom default-type-registry))

(defn register-type!
      "Registers a type keyword to a Malli schema in the global registry."
      [type-kw malli-schema]
      (swap! type-registry assoc type-kw malli-schema))

(defn resolve-type
      "Resolves a type keyword to a Malli schema. Falls back to :any."
      [type-kw]
      (get @type-registry type-kw :any))

;;; Constructor validation schema

(def operation-schema
     "Malli schema for a single operation declaration."
     [:map
      [:input [:map-of :keyword :keyword]]
      [:output :keyword]])

(def effect-signature-input-schema
     "Malli schema for the effect-signature constructor input."
     [:map
      [:label :string]
      [:operations [:map-of :keyword operation-schema]]])

;;; Schema projection helpers

(defn- operation->malli-fields
       "Converts an operation's :input map to Malli map entries,
  resolving type keywords through the registry."
       [input-map]
       (into [] (map (fn [[field-kw type-kw]]
                         [field-kw (resolve-type type-kw)]))
             input-map))

(defn- build-multi-schema
       "Builds a Malli :multi schema dispatching on :op, with one branch
  per operation."
       [operations]
       (into [:multi {:dispatch :op}]
             (map (fn [[op-kw {:keys [input]}]]
                      [op-kw
                       (into [:map [:op [:= op-kw]]]
                             (operation->malli-fields input))]))
             operations))

;;; Monitor helpers

(defn- check-effect
       "Checks a single effect map against the signature's operations.
  Returns nil if conforming, or a gap map if not."
       [operations schema effect]
       (let [op (:op effect)]
            (cond
             (nil? op)
             {:status :diverges
              :detail {:kind :missing-op-key
                       :effect effect}}

             (not (contains? operations op))
             {:status :diverges
              :detail {:kind :missing-operation
                       :op op}}

             (not (m/validate schema effect))
             {:status :diverges
              :detail {:kind :malformed-fields
                       :op op
                       :explanation (m/explain schema effect)}})))

;;; Record

(defrecord EffectSignature [label operations]
           p/IProjectable
           (->schema [_]
                     (build-multi-schema operations))

           (->monitor [this]
                      (let [schema (p/->schema this)]
                           (fn monitor [entry]
                               (let [effects (:effects entry)
                                     violations (into []
                                                      (keep (fn [effect]
                                                                (check-effect operations
                                                                              schema
                                                                              effect)))
                                                      effects)]
                                    (if (seq violations)
                                        {:verdict :violation :violations violations}
                                        {:verdict :ok})))))

           (->gen [this]
                  (mg/generator (p/->schema this)))

           (->gap-type [_]
                       {:label label
                        :formalism :effect-signature
                        :gap-kinds #{:missing-operation :malformed-fields
                                     :missing-op-key :unknown-type}
                        :statuses #{:conforms :absent :diverges}})

           (->doc [_]
                  (let [op-names (str/join ", " (mapv (comp name key) operations))
                        op-rows  (mapv (fn [[op-kw {:keys [input output]}]]
                                           {:operation (name op-kw)
                                            :fields    (str input)
                                            :output    (name output)})
                                       operations)]
                       (doc/section
                        :effect-signature/root label
                        [(doc/summary :effect-signature/summary
                                      (str (count operations) " ops: " op-names))
                         (doc/table :effect-signature/operations
                                    [:operation :fields :output]
                                    op-rows)])))

           p/IReferenceable
           (extract-refs [_ ref-kind]
                         (case ref-kind
                               :operation-ids (set (keys operations))
                               :callback-refs (into #{} (map (comp :output val)) operations)
                               :operation-outputs (into #{} (map (comp :output val)) operations)
                               #{})))

;;; Public constructor

(defn effect-signature
      "Creates a validated EffectSignature from a map with :label and :operations.
  :label is a display string. Each operation is a map of {:input {field type-kw}, :output type-kw}.
  Throws on invalid input."
      [m]
      (when-not (m/validate effect-signature-input-schema m)
                (throw (ex-info "Invalid effect signature"
                                {:explanation (m/explain effect-signature-input-schema m)})))
      (->EffectSignature (:label m) (:operations m)))
