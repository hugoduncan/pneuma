# Pneuma — Working Memory

## Status
Phase 3 complete — generic cycle detection via Johnson's algorithm.
ComposedPath discovery and axiom checking (A13, A14) wired into
gap.core. All phases through 3 done.

## Active Intent
Build pneuma bottom-up, sequenced for earliest dogfooding. Lean
proof emission is a parallel track alongside core implementation.

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
- Phase 0: pneuma.protocol — IProjectable, IConnection, IReferenceable
- Phase 1a: EffectSignature, CapabilitySet, TypeSchema formalisms
- Phase 1b: ExistentialMorphism, StructuralMorphism, morphism registry
- Phase 1c: gap.core — two-layer gap report assembly
- Dogfood checkpoint: pneuma.protocol-spec, pneuma.lean-spec
- Phase 2a: Statechart, MealyHandlerSet, OpticDeclaration, ResolverGraph
- Phase 2b: ContainmentMorphism, OrderingMorphism
- Phase 3: pneuma.path.graph (Johnson's algorithm), pneuma.path.core
  (ComposedPath record, A13/A14 axiom checking, wired into gap.core)
- Phase 5 (partial): Lean projections for all formalisms + morphisms
  - pneuma.lean.protocol, lean.system, proofs/Pneuma/System.lean
  - Lean compilation tests (separate :lean kaocha suite)
- 118 unit tests, 692 assertions + 8 lean compilation tests

## Next
- Phase 4: full gap report + public API
- Phase 5 (remaining): lean.core, proofs/ project structure, CI

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
