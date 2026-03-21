# Pneuma — Working Memory

## Status
Phase 0 complete. Protocols implemented and tested. Design documents
and implementation plan in place.

## Active Intent
Build pneuma bottom-up, sequenced for earliest dogfooding on
pneuma.protocol itself.

## Key Decisions
- 🎯 Dogfood: pneuma checks itself. Protocol layer is first target.
- 🎯 Option A architecture: Records + Protocols + Data Registry.
- 🎯 Revised build order: EffectSignature → CapabilitySet → dogfood
  checkpoint, then Statechart and remaining formalisms.
- 🎯 Protocol layer maps to EffectSignature (typed operations) +
  CapabilitySet (required implementations) + existential/structural
  morphisms.

## Completed
- pneuma.protocol — IProjectable, IConnection, IReferenceable
- Structural domain model (19 sorts, 28 axioms, 13 morphisms)
- System architecture design (Option A recommended)
- Option A formalism (8 sorts, 17 axioms, 6 morphisms)
- Projection contract axioms (A21–A28)
- Dogfood protocol design

## Next
Phase 1a: implement pneuma.formalism.effect-signature

## Key Files
- PLAN.md — full implementation plan and build order
- doc/formalism-first-conformance.md — mathematical foundations
- doc/structural-domain-model.md — domain model
- doc/system-architecture-prose.md — architecture design
- doc/option-a-formalism.md — architecture formalism
- doc/dogfood-protocol.md — protocol dogfooding strategy

## Related
- memories/dogfood-intent.md
