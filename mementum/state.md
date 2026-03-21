# Pneuma — Working Memory

## Status
Phases 0–1c complete. Dogfood checkpoint reached. Pneuma can check
its own protocol layer's cross-formalism references and produce a
two-layer gap report.

## Active Intent
Build pneuma bottom-up, sequenced for earliest dogfooding on
pneuma.protocol itself.

## Key Decisions
- 🎯 Dogfood: pneuma checks itself. Protocol layer is first target.
- 🎯 Option A architecture: Records + Protocols + Data Registry.
- 🎯 Revised build order: EffectSignature → CapabilitySet → dogfood
  checkpoint, then Statechart and remaining formalisms.
- 🎯 Protocol layer maps to EffectSignature (typed operations) +
  CapabilitySet (required implementations) + existential morphism.
- 🎯 The structural return-type morphism from dogfood-protocol.md §3.3
  needs a dedicated return-type schema formalism as target, not a
  CapabilitySet. Deferred.

## Completed
- pneuma.protocol — IProjectable, IConnection, IReferenceable
- pneuma.formalism.effect-signature — EffectSignature record
- pneuma.formalism.capability — CapabilitySet record
- pneuma.morphism.existential — ExistentialMorphism + IConnection
- pneuma.morphism.structural — StructuralMorphism + IConnection
- pneuma.morphism.registry — connection registry (1 entry)
- pneuma.gap.core — two-layer gap report assembly
- 27 tests, 165 assertions, 0 failures

## Next
Phase 2a: remaining formalisms (Statechart first) or the dogfood
instance test namespace (pneuma.dogfood.protocol).

## Key Files
- PLAN.md — full implementation plan and build order
- doc/dogfood-protocol.md — protocol dogfooding strategy

## Related
- memories/dogfood-intent.md
- memories/two-level-modeling.md
