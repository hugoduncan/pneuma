(ns pneuma.lean.doc
    "Lean 4 docstring generation for Pneuma emitters.
  Produces /-- ... -/ documentation comments for type definitions
  and theorems in generated Lean files."
    (:require [clojure.string :as str]))

(defn lean-doc
      "Wraps lines in a Lean 4 /-- ... -/ docstring block.
  Returns a string ending with newline, ready to prepend to a declaration."
      [& lines]
      (let [body (str/join "\n    " lines)]
           (str "/-- " body " -/\n")))

(defn id-str
      "Returns \":id-name\" when id is non-nil, empty string otherwise."
      [id]
      (if id (str ":" (name id)) ""))

(defn- id-suffix
       "Returns \" :id-name\" when id is non-nil, empty string otherwise."
       [id]
       (if id (str " " (id-str id)) ""))

(defn type-doc
      "Docstring for a generated type definition.
  Links back to the source Pneuma formalism. id may be nil."
      [formalism-kind id description]
      (lean-doc description
                (str "Derived from Pneuma " formalism-kind (id-suffix id) ".")))

(defn theorem-doc
      "Docstring for a generated theorem.
  States the property in plain English."
      [property]
      (lean-doc property))

(defn morphism-type-doc
      "Docstring for a type in a morphism boundary emission."
      [morphism-kind id description]
      (lean-doc description
                (str "Derived from Pneuma " morphism-kind " :" (name id) ".")))

(defn morphism-theorem-doc
      "Docstring for a morphism boundary theorem."
      [morphism-kind id property]
      (lean-doc property
                (str "Boundary of " morphism-kind " :" (name id) ".")))
