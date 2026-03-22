(ns pneuma.lean.protocol
    "Protocols for emitting Lean 4 source code from Pneuma formalisms.
  Separate from pneuma.protocol to keep the core checking system
  independent of the optional Lean toolchain.")

(defprotocol ILeanProjectable
             "Projects a formalism into Lean 4 source code."
             (->lean [this]
                     "Returns a string of Lean 4 source code containing type
    definitions, property statements (with sorry placeholders),
    and proof scaffolding."))

(defprotocol ILeanConnection
             "Emits Lean 4 boundary propositions for a morphism."
             (->lean-conn [this source target]
                          "Returns a string of Lean 4 source code containing boundary
    propositions and composition theorem scaffolding."))
