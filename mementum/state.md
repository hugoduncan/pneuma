# Pneuma — Working Memory

## Status
Dogfood checkpoint complete. Pneuma checks its own protocol layer
with zero failures. Specs live in a separate spec/ source tree using
the -spec naming convention.

## Active Intent
Build pneuma bottom-up, sequenced for earliest dogfooding.

## Key Decisions
- 🎯 Dogfood: pneuma checks itself. Protocol layer is first target.
- 🎯 Option A architecture: Records + Protocols + Data Registry.
- 🎯 Build order revised for dogfooding: EffectSignature →
  CapabilitySet → dogfood checkpoint (done), then Statechart and
  remaining formalisms.
- 🎯 Specs go in spec/ source tree with -spec suffix. Not packaged
  in production jar.
- 🎯 TypeSchema formalism added to fix structural morphism wiring.
- 🎯 Defer specs for implementation namespaces until after Phase 2a —
  more value with richer cross-references.

## Completed
- pneuma.protocol — IProjectable, IConnection, IReferenceable
- pneuma.formalism.effect-signature — EffectSignature record
- pneuma.formalism.capability — CapabilitySet record
- pneuma.formalism.type-schema — TypeSchema record
- pneuma.morphism.existential — ExistentialMorphism + IConnection
- pneuma.morphism.structural — StructuralMorphism + IConnection
- pneuma.morphism.registry — connection registry (2 entries)
- pneuma.gap.core — two-layer gap report assembly
- pneuma.protocol-spec — formal spec of pneuma.protocol (in spec/)
- 35 tests, 207 assertions, 0 failures

## Next
Phase 2a: implement pneuma.formalism.statechart

## Key Files
- PLAN.md — full implementation plan and build order
- spec/pneuma/protocol_spec.clj — protocol layer specification

## Related
- memories/dogfood-intent.md
- memories/two-level-modeling.md
- memories/structural-morphism-target.md
