(ns pneuma.protocol
    "Protocols for the Pneuma conformance checking system.
  Defines the three core dispatch interfaces: IProjectable for
  formalism-to-artifact projection, IConnection for morphism
  boundary checking, and IReferenceable for cross-formalism
  reference extraction.")

(defprotocol IProjectable
             "Projects a mathematical formalism into five checking and documentation artifacts.
  Every formalism record must implement all five methods."
             (->schema [this]
                       "Returns a Malli schema for structural validation of the
    formalism's relevant state.")
             (->monitor [this]
                        "Returns a trace monitor function (EventLogEntry → Verdict)
    for behavioral checking against the event log.")
             (->gen [this]
                    "Returns a test.check generator producing valid inputs for
    property-based testing.")
             (->gap-type [this]
                         "Returns a gap type descriptor map classifying how conformance
    failures are reported for this formalism.")
             (->doc [this]
                    "Returns a format-agnostic document fragment tree describing this formalism."))

(defprotocol IConnection
             "Checks a typed boundary contract between two formalisms.
  Every morphism kind record must implement this protocol."
             (check [this source target refinement-map]
                    "Returns a sequence of Gap maps describing any boundary
    violations between the source and target formalisms.
    The refinement-map bridges mathematical objects to the
    running system's state."))

(defprotocol IReferenceable
             "Extracts cross-formalism references from a formalism record.
  Every formalism record must implement this protocol."
             (extract-refs [this ref-kind]
                           "Returns a set of identifiers for the given ref-kind keyword.
    The ref-kind determines which cross-formalism references to
    extract (e.g. :guard-state-refs, :callback-refs)."))
