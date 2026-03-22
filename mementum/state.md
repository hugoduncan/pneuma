# Pneuma — Working Memory

## Status
All implementation phases (0–12c) complete. PLAN.md fully checked off.
203 unit tests, 1079 assertions, 0 failures + lean compilation tests.
CI: `bb ci` runs lint + fmt + test-all + lake build.

## Active Intent
System feature-complete. All planned phases delivered including
dogfood specs, living documentation, and Lean human-readable proofs.
Next is application to real targets beyond the dogfood spec, or
writing real Lean proofs to replace `sorry` scaffolding.

## Key Decisions
- 🎯 Dogfood: pneuma checks itself. Protocol layer is first target.
- 🎯 Option A architecture: Records + Protocols + Data Registry.
- 🎯 Build order revised for dogfooding: EffectSignature →
  CapabilitySet → dogfood checkpoint (done), then Statechart and
  remaining formalisms.
- 🎯 Specs go in spec/ source tree with -spec suffix. Not packaged
  in production jar.
- 🎯 TypeSchema formalism added to fix structural morphism wiring.
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
- 🎯 spec-system added to all 31 spec files for consistent spec
  registration and discovery.
- 🎯 ->doc projection is format-agnostic data (fragments) rendered
  to markdown/HTML/docx. Gap status overlaid as second pass.
- 🎯 Lean blueprint emits LaTeX with \lean{}, \leanok, \uses{}
  macros for browsable HTML proof status.
- 🎯 Structured proof style: calc chains, have steps, suffices,
  decide for finite-state properties.

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
- Phase 5: Lean projections for all formalisms + morphisms + lean.core
  - Path-level Lean emission, lean compilation tests
- Phase 6: Dogfood specs for all 7 formalism namespaces
- Phase 7: Dogfood specs for all 5 morphism namespaces
- Phase 8: Dogfood specs for gap and path layers
- Phase 9: Dogfood specs for all 11 lean emitters
- Phase 10: Dogfood specs for core API and refinement
- Phase 11: Living documentation (->doc projection)
  - 11a: DocFragment data model and rendering (markdown/HTML/docx)
  - 11b: Per-formalism ->doc implementations
  - 11c: Cross-formalism docs (morphism map, path guide, status dashboard)
  - 11d: Document assembly and REPL API (render-doc, explain)
- Phase 12: Lean human-readable proofs
  - 12a: Lean 4 docstrings on all emitters
  - 12b: Lean Blueprint LaTeX emission
  - 12c: Structured proof style guidelines and scaffolding
- 203 unit tests, 1079 assertions, 0 failures

## Next
- Write real Lean proofs to replace `sorry` scaffolding
- Application to real targets beyond the dogfood spec
- Runtime monitoring using ->monitor projections

## Key Files
- PLAN.md — full implementation plan and build order
- src/pneuma/core.clj — public API entry point
- src/pneuma/lean/core.clj — Lean emission public API
- src/pneuma/lean/blueprint.clj — LaTeX blueprint emission
- src/pneuma/doc/core.clj — living documentation API
- spec/ — 31 dogfood spec files
- proofs/Pneuma/System.lean — generated system proof
- proofs/blueprint/ — generated LaTeX blueprint
- doc/proof-style-guidelines.md — structured proof style

## Related
- memories/dogfood-intent.md
- memories/two-level-modeling.md
- memories/structural-morphism-target.md
- memories/lean-isolation-pattern.md
- memories/proof-as-projection.md
