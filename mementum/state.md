# Pneuma — Working Memory

## Status
All implementation phases (0–5) complete except CI integration.
139 unit tests, 767 assertions, 0 failures + 8 lean compilation tests.

## Active Intent
Core system complete. Remaining work is CI integration and
application to real targets beyond the dogfood spec.

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
- Phase 4: gap.diff, refinement, pneuma.core public API
  (check-schema, check-trace, check-gen, check-morphism, diff-reports)
- Phase 5: Lean projections for all formalisms + morphisms + lean.core
  - pneuma.lean.protocol, lean.system, proofs/Pneuma/System.lean
  - pneuma.lean.core: emit-lean, emit-lean-conn, emit-lean-system, emit-lean-all
  - Path-level Lean emission: emit-lean-path, emit-lean-paths
    (per-step boundaries + composition theorem along cycles)
  - Lean compilation tests (separate :lean kaocha suite)
- 139 unit tests, 767 assertions + 8 lean compilation tests

## Next
- CI integration: `lake build` alongside `clojure -M:test`
- Application to real targets beyond the dogfood spec

## Key Files
- PLAN.md — full implementation plan and build order
- src/pneuma/core.clj — public API entry point
- src/pneuma/lean/core.clj — Lean emission public API
- spec/pneuma/protocol_spec.clj — protocol layer specification
- spec/pneuma/lean_spec.clj — lean projection layer specification
- doc/pneuma-lean4-extension.md — Lean integration design
- proofs/Pneuma/System.lean — generated system proof

## Related
- memories/dogfood-intent.md
- memories/two-level-modeling.md
- memories/structural-morphism-target.md
- memories/lean-isolation-pattern.md
- memories/proof-as-projection.md
