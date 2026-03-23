# Pneuma — Working Memory

## Status
All implementation phases (0–14c) complete. PLAN.md fully checked off.
280 unit + 7 regression + 7 regression-lean tests, 0 failures.
248 Lean proof files (31 system + 217 verification), all build with lake.
CI: `bb ci` runs lint + fmt + test-all + lake build.

## Active Intent
System feature-complete. All planned phases delivered including
dogfood specs, living documentation, Lean human-readable proofs,
->code projection (code generation from formalisms), and deep Lean
verification layers (morphism algebra, circuit verification, gap
completeness, monitor-schema consistency, ref exhaustiveness,
composition transitivity, path semantic composition).
First external target (integrant) fully modeled with model-based
tests exercising real integrant code and Lean proof verification.

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
- 🎯 Regression tests in test-regression/ (model-based, fast) and
  test-regression-lean/ (lean compilation, slow). Separate kaocha
  suites: :regression and :regression-lean.
- 🎯 Integrant added as test dependency (1.0.1) for model-based tests.
- 🎯 Avoid Lean builtin names in type schemas (Void → UnitResult).

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
- Integrant regression spec: 8 formalisms, 6 morphisms, all conforming
  - Model-based tests: lifecycle, effects, capabilities, morphisms, types
  - Lean verification: no sorry, all decide, system_conformance proved
- Fixed empty-dispatch capability lean emission (always emit dispatch list)
- Phase 13: ->code projection (code generation from formalisms)
  - 13a: Fill-point infrastructure (pneuma.fills, combinators)
  - 13b: Code protocols + per-formalism ->code (statechart, effect-sig, mealy, optic, resolver, capability)
  - 13c: Morphism ->code test generation (existential, structural, containment, ordering, path)
  - 13d: Code rendering, project emission, fill-status in gap report
  - 13e: CI integration (fill validation, morphism test generation, contract checking)
- Required :label field added to all formalism records
- HTML renderer for architecture documents, collapsible sections, intent toggle
- Phase 14: Deep Lean verification layers
  - 14a: morphism-algebra, circuit, gap-completeness, monitor-schema, ref-exhaustive
  - 14b: composition-transitivity, path-semantic
  - 14c: Integration into lean.core, bin/self-proof, 248 Lean files emitted and verified
- 280 unit + 7 regression + 7 regression-lean tests, 0 failures

## Next
- Expand integrant model (refinement map bridging spec to source)
- More external target specs
- Runtime monitoring using ->monitor projections
- Package/release as a Clojure library

## Notes
- 248 Lean proof files: 31 system-level + 217 verification layers (31
  specs × 7 layers). All use `decide` (no `sorry`). The comment
  mentioning `sorry` on line 5 of system files documents the generation
  strategy, not an open task.
- Verification layers wrap each file in a Lean namespace to avoid type
  collisions between specs.

## Key Files
- PLAN.md — full implementation plan and build order
- src/pneuma/core.clj — public API entry point
- src/pneuma/lean/core.clj — Lean emission public API
- src/pneuma/lean/blueprint.clj — LaTeX blueprint emission
- src/pneuma/doc/core.clj — living documentation API
- src/pneuma/code/core.clj — code generation public API
- src/pneuma/fills.clj — fill registry for code generation
- spec/ — 31 dogfood spec files
- proofs/Pneuma/System.lean — generated system proof
- proofs/blueprint/ — generated LaTeX blueprint
- doc/proof-style-guidelines.md — structured proof style
- doc/pneuma-codegen-extension.md — ->code projection design
- test-regression/pneuma/integrant/ — integrant formal model + model-based tests
- test-regression-lean/pneuma/integrant/ — integrant lean compilation + verification

## Related
- memories/dogfood-intent.md
- memories/two-level-modeling.md
- memories/structural-morphism-target.md
- memories/lean-isolation-pattern.md
- memories/proof-as-projection.md
