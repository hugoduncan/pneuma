(ns pneuma.code.resolver
    "Code generation for ResolverGraph formalisms.
  Extends ResolverGraph with ICodeProjectable via extend-protocol.
  Emits resolver skeletons with input/output attribute declarations
  and fill points for resolver body logic."
    (:require [pneuma.code.protocol :as cp]
              [pneuma.formalism.resolver])
    (:import [pneuma.formalism.resolver ResolverGraph]))

(defn- resolver-fill-points
       "Extracts fill-point declarations for a single resolver."
       [resolver-id {:keys [input output]}]
       [{:key (keyword (name resolver-id) "resolve")
         :args (into '[env] (mapv (comp symbol name) input))
         :returns :map
         :doc (str "Resolve " (name resolver-id)
                   " → " output)
         :handler resolver-id}])

(defn- resolver->form
       "Generates a resolver form for a single declaration."
       [resolver-id declaration]
       (let [{:keys [input output source]} declaration
             fills (resolver-fill-points resolver-id declaration)]
            {:type :resolver
             :resolver-id resolver-id
             :input input
             :output output
             :source source
             :fills fills
             :comment (str "Resolver: " (name resolver-id)
                           " " input " → " output
                           (when source (str " source: " source)))}))

(defn- emit-code-resolver
       "Generates a code fragment map for a ResolverGraph."
       [{:keys [label declarations]} opts]
       (let [target-ns (:target-ns opts)
             resolvers (mapv (fn [[rid decl]] (resolver->form rid decl))
                             (sort-by key declarations))
             all-fills (into [] (mapcat :fills) resolvers)
             manifest (into {} (map (fn [fp] [(:key fp) (dissoc fp :key)])) all-fills)]
            {:namespace target-ns
             :label label
             :requires [['pneuma.fills :refer ['fill 'fill-or]]]
             :forms resolvers
             :fill-manifest manifest
             :metadata {:formalism :resolver
                        :resolver-count (count declarations)}}))

(extend-protocol cp/ICodeProjectable
                 ResolverGraph
                 (->code [this opts]
                         (emit-code-resolver this opts)))
