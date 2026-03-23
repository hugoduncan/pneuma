(ns pneuma.code.protocol
    "Protocols for emitting Clojure code scaffolding from Pneuma formalisms.
  Separate from pneuma.protocol to keep the core checking system
  independent of the optional code generation toolchain.")

(defprotocol ICodeProjectable
             "Projects a formalism into Clojure code fragments with fill points."
             (->code [this opts]
                     "Returns a code fragment map with :namespace, :requires, :forms,
    and :fill-manifest. The opts map provides target namespace names
    and project-level configuration."))

(defprotocol ICodeConnection
             "Emits Clojure test assertions for a morphism boundary."
             (->code-conn [this source target opts]
                          "Returns a code fragment map containing test assertions that verify
    the boundary contract between source and target formalisms."))
