# Pneuma — Implementation Plan

Build order for the conformance checking system, sequenced for
earliest possible dogfooding.

## Design Documents

- [doc/formalism-first-conformance.md](doc/formalism-first-conformance.md) — mathematical foundations
- [doc/structural-domain-model.md](doc/structural-domain-model.md) — 19 sorts, 30 axioms, 14 morphisms
- [doc/system-architecture-prose.md](doc/system-architecture-prose.md) — Option A: Records + Protocols + Data Registry
- [doc/option-a-formalism.md](doc/option-a-formalism.md) — structural formalism for the architecture
- [doc/dogfood-protocol.md](doc/dogfood-protocol.md) — how pneuma checks its own protocol layer
- [doc/pneuma-lean4-extension.md](doc/pneuma-lean4-extension.md) — Lean 4 proof extension (->lean projection)
- [doc/dogfood-lean.md](doc/dogfood-lean.md) — how pneuma checks its own lean projection layer

## Phases

### Phase 0 — Protocols

The dependency-free leaf. All other code depends on this.

- [x] `pneuma.protocol` — IProjectable, IConnection, IReferenceable

### Phase 1a — Dogfood formalisms

EffectSignature and CapabilitySet first, because they are sufficient
to describe and check pneuma.protocol (see dogfood-protocol.md).

- [x] `pneuma.formalism.effect-signature` — EffectSignature record + IProjectable
  - Operations as Σ(name : Keyword). Fields(name)
  - →schema: Malli :multi schema keyed on operation name
  - →monitor: check emitted effects match declared field shapes
  - →gen: generate well-typed effect description maps
  - →gap-type: :missing-operation, :missing-field, :wrong-field-type
- [x] `pneuma.formalism.capability` — CapabilitySet record + IProjectable
  - Product of three PowerSets (dispatch, subscribe, query)
  - →schema: set membership bounds
  - →monitor: check dispatched events are within bounds
  - →gen: generate events within the capability bound
  - →gap-type: :unauthorized-dispatch, :unauthorized-subscribe, :missing-enforcement

### Phase 1b — Dogfood morphisms

Two morphism kinds needed to connect EffectSignature ↔ CapabilitySet.

- [x] `pneuma.morphism.existential` — ExistentialMorphism record + IConnection
  - Set membership: identifier in A must exist in B
  - Gap type: dangling-ref
- [x] `pneuma.morphism.structural` — StructuralMorphism record + IConnection
  - Schema validation: output of A conforms to input schema of B
  - Gap type: shape-mismatch
- [x] `pneuma.morphism.registry` — connection registry as data
  - Initial entry: caps→protocol/operations (existential)
  - TODO: structural return-type morphism needs a dedicated target formalism

### Phase 1c — Dogfood gap report

Minimal gap assembly — enough to produce a two-layer report (object
+ morphism gaps, no path gaps yet).

- [x] `pneuma.gap.core` — Gap construction, GapReport assembly
  - gap-report: merge object-gaps and morphism-gaps
  - failures: filter to non-conforming gaps
  - has-failures?: predicate for quick pass/fail

### Dogfood checkpoint

Run `gap-report` on `pneuma.protocol` using the formalism instances
from dogfood-protocol.md. This is the first time pneuma checks
itself. Validates that the object and morphism layers work on a real
target.

- [x] `pneuma.protocol-spec` — formalism spec of pneuma.protocol (in spec/)
  - EffectSignature instance for the six protocol methods
  - CapabilitySet instances for formalism and morphism records
  - Registry entries connecting them
  - Test: gap-report produces expected results

### Phase 2a — Remaining formalisms

Build order within this phase is flexible. Each is independently
testable. Every formalism test must include an A24 property test:
`∀ v ∈ ->gen(f), m/validate(->schema(f), v)` using `tc/quick-check`
with 100 trials and shrinking.

- [x] `pneuma.formalism.statechart` — Statechart record + IProjectable
  - Harel statechart tuple (S, ≤, T, C, H, δ)
  - Step function δ: Configuration × Event → Configuration
  - Reachability analysis (BFS over transition relation)
  - →schema: valid configurations as Malli :enum
  - →monitor: check config transitions match δ
  - →gen: random walks over reachable states
  - →gap-type: :missing-state, :missing-transition, :unreachable-state, :invalid-config
- [x] `pneuma.formalism.mealy` — MealyDeclaration record + IProjectable
  - Handler contracts: guards, updates, effect emissions
  - →schema: input/output schemas per handler
  - →monitor: check db-before/db-after diffs match declared updates
  - →gen: (db, event) pairs satisfying guards
  - →gap-type: :absent-handler, :missing-guard, :wrong-update, :wrong-emission
- [x] `pneuma.formalism.optic` — OpticDeclaration record + IProjectable
  - Lens/traversal/fold/derived declarations
  - Path resolution checking via Specter
  - →schema: return types of optic application
  - →monitor: subscription change detection
  - →gen: state maps where optics resolve
  - →gap-type: :broken-path, :wrong-derivation, :missing-subscription
- [ ] `pneuma.formalism.resolver` — ResolverGraph record + IProjectable
  - Functional dependency hypergraph
  - Reachability computation (chase algorithm)
  - →schema: attribute reachability graph
  - →monitor: resolver output validation
  - →gen: reachable query inputs
  - →gap-type: :missing-resolver, :unreachable-attribute, :wrong-output

### Phase 2b — Remaining morphisms

- [ ] `pneuma.morphism.containment` — ContainmentMorphism record + IConnection
  - Set difference: declared(A) \ defined(B) must be empty
  - Gap type: out-of-bounds
- [ ] `pneuma.morphism.ordering` — OrderingMorphism record + IConnection
  - Index comparison: A's interceptor precedes B's
  - Gap type: order-violation

### Phase 3 — Composed paths

Cycles through the morphism graph carrying end-to-end invariants.

- [ ] `pneuma.path.cycles` — three named cycles as data
  - Event-effect-callback: Chart → Mealy → Effects → Chart
  - Observe-dispatch-update: Optics → Caps → Mealy → Optics
  - Full dispatch: Caps → Chart → Mealy → Validator → Optics → Effects → Chart
- [ ] `pneuma.path.core` — ComposedPath record, cycle checker
  - Compose morphisms along FreeMonoid steps
  - Verify cycle closure and precondition chaining

### Phase 4 — Full gap report and public API

- [ ] `pneuma.gap.diff` — diff-reports, failures, gaps-involving
- [ ] `pneuma.refinement` — RefinementMap record
- [ ] `pneuma.core` — public API
  - Constructor functions for all formalisms
  - gap-report entry point
  - check-schema, check-trace, check-gen convenience fns
  - check-morphism, diff-reports, failures

### Phase 5 — Lean 4 proof integration

Adds the `->lean` projection via separate protocols
(`ILeanProjectable`, `ILeanConnection`) in the `pneuma.lean`
namespace layer. Existing formalism and morphism records are extended
via `extend-protocol` — the core protocols are unchanged. Lean
proves properties of the specification (universal); Pneuma tests
conformance of the implementation (sampled). See
[doc/pneuma-lean4-extension.md](doc/pneuma-lean4-extension.md) for
the full design and [doc/dogfood-lean.md](doc/dogfood-lean.md) for
how Pneuma checks its own lean layer.

Build order within this phase follows §9.1 of the extension document.

- [ ] `pneuma.lean.protocol` — ILeanProjectable, ILeanConnection
- [ ] `pneuma.lean.statechart` — extend Statechart (first and most valuable emission)
- [ ] `pneuma.lean.effect-signature` — extend EffectSignature
- [ ] `pneuma.lean.mealy` — extend MealyHandlerSet
- [ ] `pneuma.lean.capability` — extend CapabilitySet
- [x] `pneuma.lean.optic` — extend OpticDeclaration
- [ ] `pneuma.lean.existential` — extend ExistentialMorphism
- [ ] `pneuma.lean.structural` — extend StructuralMorphism
- [ ] `pneuma.lean.core` — public API (emit-lean, emit-lean-cycle, emit-lean-bisim)
- [ ] `proofs/` Lean project structure with `lakefile.lean` and Mathlib dependency
- [ ] CI integration: `lake build` alongside `clojure -M:test`

## Namespace Dependency Graph

```
pneuma.protocol (no deps)
  ├── pneuma.formalism.effect-signature
  ├── pneuma.formalism.capability
  ├── pneuma.formalism.statechart
  ├── pneuma.formalism.mealy
  ├── pneuma.formalism.optic
  ├── pneuma.formalism.resolver
  │
  ├── pneuma.morphism.existential
  ├── pneuma.morphism.structural
  ├── pneuma.morphism.containment
  ├── pneuma.morphism.ordering
  ├── pneuma.morphism.registry ── (formalism/* + morphism/*)
  │
  ├── pneuma.path.cycles ── (morphism.registry)
  ├── pneuma.path.core ── (path.cycles + morphism/*)
  │
  ├── pneuma.gap.core ── (protocol + formalism/* + morphism/*)
  ├── pneuma.gap.diff ── (gap.core)
  │
  ├── pneuma.refinement ── (protocol)
  │
  └── pneuma.core ── (everything except lean)

pneuma.lean.protocol (no deps)
  ├── pneuma.lean.statechart ── (lean.protocol + formalism.statechart)
  ├── pneuma.lean.effect-signature ── (lean.protocol + formalism.effect-signature)
  ├── pneuma.lean.mealy ── (lean.protocol + formalism.mealy)
  ├── pneuma.lean.capability ── (lean.protocol + formalism.capability)
  ├── pneuma.lean.optic ── (lean.protocol + formalism.optic)
  ├── pneuma.lean.existential ── (lean.protocol + morphism.existential)
  ├── pneuma.lean.structural ── (lean.protocol + morphism.structural)
  └── pneuma.lean.core ── (all lean.* namespaces)

proofs/                         (Lean 4 — generated by ->lean, not Clojure)
  ├── Pneuma/*.lean             (generated type defs + property statements)
  └── Proofs/*.lean             (human-written proofs importing Pneuma/)
```
