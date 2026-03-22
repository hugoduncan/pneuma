# Pneuma — Working Memory

## Status
Phase 2a complete — all six formalisms done (optic, resolver added).
All implement ILeanProjectable. Phase 2b (remaining morphisms) is next.

## Active Intent
Build pneuma bottom-up, sequenced for earliest dogfooding. Lean
proof emission is a parallel track alongside remaining formalisms.

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
- 🎯 Lean protocols (ILeanProjectable, ILeanConnection) isolated in
  pneuma.lean namespace layer — separate from core protocols. Extended
  onto existing records via extend-protocol.
- 🎯 System-level Lean emission driven by gap report: conforming →
  decide, failing → sorry. Proofs are a projection of Pneuma's own
  conformance check, not manually written.
- 🎯 Lean compilation tests in separate :lean kaocha suite. Default
  :unit suite skips them via :lean metadata. CI runs both.
- 🎯 Skip opaque declarations for Lean builtins (Bool, Nat, String,
  etc.) to avoid redeclaration errors.

## Completed
- pneuma.protocol — IProjectable, IConnection, IReferenceable
- pneuma.formalism.effect-signature — EffectSignature record
- pneuma.formalism.capability — CapabilitySet record
- pneuma.formalism.type-schema — TypeSchema record
- pneuma.formalism.statechart — Statechart record (Phase 2a)
- pneuma.formalism.mealy — MealyHandlerSet record (Phase 2a)
- pneuma.morphism.existential — ExistentialMorphism + IConnection
- pneuma.morphism.structural — StructuralMorphism + IConnection
- pneuma.morphism.registry — connection registry (2 entries)
- pneuma.gap.core — two-layer gap report assembly
- pneuma.protocol-spec — formal spec of pneuma.protocol (in spec/)
- pneuma.lean-spec — formal spec of lean projection layer (in spec/)
- pneuma.lean.protocol — ILeanProjectable, ILeanConnection
- pneuma.lean.capability — ->lean for CapabilitySet
- pneuma.lean.effect-signature — ->lean for EffectSignature
- pneuma.lean.statechart — ->lean for Statechart (State/Event/step/safety)
- pneuma.lean.mealy — ->lean for MealyHandlerSet
- pneuma.lean.type-schema — ->lean for TypeSchema
- pneuma.formalism.optic — OpticDeclaration record (Phase 2a)
- pneuma.formalism.resolver — ResolverGraph record (Phase 2a)
- pneuma.lean.optic — ->lean for OpticDeclaration
- pneuma.lean.resolver — ->lean for ResolverGraph
- pneuma.lean.system — gap-report-driven system-level Lean emission
- proofs/Pneuma/System.lean — verified by lake build (Lean 4.28.0)
- Lean compilation tests for all 7 formalisms + system
- 92 unit tests, 565 assertions + 8 lean compilation tests

## Next
- Phase 2b: containment and ordering morphisms
- Phase 3: composed paths (cycles)
- Phase 5: ->lean-conn for morphisms (cross-formalism proofs)

## Key Files
- PLAN.md — full implementation plan and build order
- spec/pneuma/protocol_spec.clj — protocol layer specification
- spec/pneuma/lean_spec.clj — lean projection layer specification
- doc/pneuma-lean4-extension.md — Lean integration design
- doc/dogfood-lean.md — lean layer dogfood document
- proofs/Pneuma/System.lean — generated system proof
- test/pneuma/lean/lean_compile_test.clj — Lean compilation tests

## Related
- memories/dogfood-intent.md
- memories/two-level-modeling.md
- memories/structural-morphism-target.md
- memories/lean-isolation-pattern.md
- memories/proof-as-projection.md
