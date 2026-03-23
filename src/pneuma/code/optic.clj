(ns pneuma.code.optic
    "Code generation for OpticDeclaration formalisms.
  Extends OpticDeclaration with ICodeProjectable via extend-protocol.
  Emits subscription declarations for path-based optics and fill
  points for derived subscription computation logic."
    (:require [pneuma.code.protocol :as cp]
              [pneuma.formalism.optic])
    (:import [pneuma.formalism.optic OpticDeclaration]))

(defn- optic-fill-points
       "Extracts fill-point declarations for a single optic.
  Path-based optics (Lens, Traversal, Fold) are fully generated.
  Derived optics need a fill point for the computation."
       [optic-id {:keys [optic-type params sources]}]
       (when (= :Derived optic-type)
             (let [prefix (name optic-id)
                   param-args (mapv (comp symbol name :name) (or params []))
                   source-syms (mapv (comp symbol name) (keys (or sources {})))]
                  [{:key (keyword prefix "compute")
                    :args (into '[db] (into param-args source-syms))
                    :returns :any
                    :doc (str "Compute derived value for " prefix)
                    :handler optic-id}])))

(defn- optic->form
       "Generates a subscription form for a single optic declaration."
       [optic-id declaration]
       (let [{:keys [optic-type path sources]} declaration
             fills (optic-fill-points optic-id declaration)]
            {:type :subscription
             :optic-id optic-id
             :optic-type optic-type
             :path path
             :sources sources
             :fills (or fills [])
             :comment (str (name optic-type) " subscription: "
                           (name optic-id)
                           (when path (str " path: " path))
                           (when sources (str " sources: " sources)))}))

(defn- emit-code-optic
       "Generates a code fragment map for an OpticDeclaration."
       [{:keys [label declarations]} opts]
       (let [target-ns (:target-ns opts)
             subs (mapv (fn [[oid decl]] (optic->form oid decl))
                        (sort-by key declarations))
             all-fills (into [] (mapcat :fills) subs)
             manifest (into {} (map (fn [fp] [(:key fp) (dissoc fp :key)])) all-fills)]
            {:namespace target-ns
             :label label
             :requires [['pneuma.fills :refer ['fill 'fill-or]]]
             :forms subs
             :fill-manifest manifest
             :metadata {:formalism :optic
                        :subscription-count (count declarations)
                        :derived-count (count (filterv #(= :Derived (:optic-type (val %)))
                                                       declarations))}}))

(extend-protocol cp/ICodeProjectable
                 OpticDeclaration
                 (->code [this opts]
                         (emit-code-optic this opts)))
