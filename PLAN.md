# Pneuma — Implementation Plan

Build order for the conformance checking system, sequenced for
earliest possible dogfooding.

## Design Documents

- [doc/formalism-first-conformance.md](doc/formalism-first-conformance.md) — mathematical foundations
- [doc/structural-domain-model.md](doc/structural-domain-model.md) — 19 sorts, 28 axioms, 13 morphisms
- [doc/system-architecture-prose.md](doc/system-architecture-prose.md) — Option A: Records + Protocols + Data Registry
- [doc/option-a-formalism.md](doc/option-a-formalism.md) — structural formalism for the architecture
- [doc/dogfood-protocol.md](doc/dogfood-protocol.md) — how pneuma checks its own protocol layer

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

- [ ] `pneuma.dogfood.protocol` — formalism instances describing pneuma.protocol (TODO)
  - EffectSignature instance for the six protocol methods
  - CapabilitySet instances for formalism and morphism records
  - Registry entries connecting them
  - Test: gap-report produces expected results

### Phase 2a — Remaining formalisms

Build order within this phase is flexible. Each is independently
testable.

- [ ] `pneuma.formalism.statechart` — Statechart record + IProjectable
  - Harel statechart tuple (S, ≤, T, C, H, δ)
  - Step function δ: Configuration × Event → Configuration
  - Reachability analysis (BFS over transition relation)
  - →schema: valid configurations as Malli :enum
  - →monitor: check config transitions match δ
  - →gen: random walks over reachable states
  - →gap-type: :missing-state, :missing-transition, :unreachable-state, :invalid-config
- [ ] `pneuma.formalism.mealy` — MealyDeclaration record + IProjectable
  - Handler contracts: guards, updates, effect emissions
  - →schema: input/output schemas per handler
  - →monitor: check db-before/db-after diffs match declared updates
  - →gen: (db, event) pairs satisfying guards
  - →gap-type: :absent-handler, :missing-guard, :wrong-update, :wrong-emission
- [ ] `pneuma.formalism.optic` — OpticDeclaration record + IProjectable
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
  └── pneuma.core ── (everything)
```
