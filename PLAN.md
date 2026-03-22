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
- [x] `pneuma.formalism.resolver` — ResolverGraph record + IProjectable
  - Functional dependency hypergraph
  - Reachability computation (chase algorithm)
  - →schema: attribute reachability graph
  - →monitor: resolver output validation
  - →gen: reachable query inputs
  - →gap-type: :missing-resolver, :unreachable-attribute, :wrong-output

### Phase 2b — Remaining morphisms

- [x] `pneuma.morphism.containment` — ContainmentMorphism record + IConnection
  - Set difference: declared(A) \ defined(B) must be empty
  - Gap type: out-of-bounds
- [x] `pneuma.morphism.ordering` — OrderingMorphism record + IConnection
  - Index comparison: A's interceptor precedes B's
  - Gap type: order-violation

### Phase 3 — Composed paths

Generic cycle detection over the morphism graph. Cycles are discovered
automatically via Johnson's algorithm — no hardcoded application-specific
cycles.

- [x] `pneuma.path.graph` — pure graph algorithms
  - `registry->graph`: morphism registry → adjacency map
  - `registry->edge-index`: morphism registry → [from to] → morphisms index
  - `elementary-circuits`: Johnson's algorithm (1975) for all simple cycles
  - Tarjan's SCC as internal helper
- [x] `pneuma.path.core` — ComposedPath record, cycle checker
  - `circuit->paths`: resolve node circuits to ComposedPath records (handles multi-edge)
  - `check-closure`: axiom A13 (cycle closure)
  - `check-adjacency`: axiom A14 (structural precondition chaining)
  - `find-paths`, `check-all-paths`: discovery + checking pipeline
  - Wired into `gap.core/gap-report` for :path-gaps layer

### Phase 4 — Full gap report and public API

- [x] `pneuma.gap.diff` — diff-reports, has-changes?, gaps-involving
  - Per-layer diffing (introduced, resolved, changed)
  - Filtering by formalism kind
- [x] `pneuma.refinement` — RefinementMap record
  - Bridges formalisms to implementation state via var refs
  - deref-state, deref-event-log, access
- [x] `pneuma.core` — public API
  - Constructor re-exports for all 7 formalisms + 4 morphisms
  - gap-report, failures, has-failures? entry points
  - check-schema, check-trace, check-gen per-formalism convenience fns
  - check-morphism, diff-reports, has-changes?, gaps-involving, find-paths

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

- [x] `pneuma.lean.protocol` — ILeanProjectable, ILeanConnection
- [x] `pneuma.lean.statechart` — extend Statechart (first and most valuable emission)
- [x] `pneuma.lean.effect-signature` — extend EffectSignature
- [x] `pneuma.lean.mealy` — extend MealyHandlerSet
- [x] `pneuma.lean.capability` — extend CapabilitySet
- [x] `pneuma.lean.optic` — extend OpticDeclaration
- [x] `pneuma.lean.resolver` — extend ResolverGraph
- [x] `pneuma.lean.existential` — extend ExistentialMorphism (ILeanConnection)
- [x] `pneuma.lean.structural` — extend StructuralMorphism (ILeanConnection)
- [x] `pneuma.lean.containment` — extend ContainmentMorphism (ILeanConnection)
- [x] `pneuma.lean.ordering` — extend OrderingMorphism (ILeanConnection)
- [x] `pneuma.lean.core` — public API
  - emit-lean, emit-lean-conn, emit-lean-system, emit-lean-all
  - emit-lean-path: per-step boundary emission + composition theorem
  - emit-lean-paths: discover all cycles, emit path-level Lean for each
  - Requires all lean extension namespaces for extend-protocol loading
- [x] `proofs/` Lean project structure with `lakefile.lean`
- [x] CI integration: `bb ci` runs lint + fmt + test-all + `lake build`

### Phase 6 — Dogfood: formalism layer

Formal specs for each formalism record's constructor contract,
required fields, and projection outputs. Each spec models one
formalism namespace using EffectSignature (for its IProjectable
methods) and TypeSchema (for its data shapes).

- [x] `spec/pneuma/formalism/effect_signature_spec.clj` — self-spec for EffectSignature
- [x] `spec/pneuma/formalism/capability_spec.clj` — spec for CapabilitySet
- [x] `spec/pneuma/formalism/statechart_spec.clj` — spec for Statechart
- [x] `spec/pneuma/formalism/mealy_spec.clj` — spec for MealyHandlerSet
- [x] `spec/pneuma/formalism/optic_spec.clj` — spec for OpticDeclaration
- [x] `spec/pneuma/formalism/resolver_spec.clj` — spec for ResolverGraph
- [x] `spec/pneuma/formalism/type_schema_spec.clj` — spec for TypeSchema

### Phase 7 — Dogfood: morphism layer

Formal specs for each morphism record's `check` contract —
inputs, outputs, and gap shapes.

- [x] `spec/pneuma/morphism/existential_spec.clj` — spec for ExistentialMorphism
- [x] `spec/pneuma/morphism/structural_spec.clj` — spec for StructuralMorphism
- [x] `spec/pneuma/morphism/containment_spec.clj` — spec for ContainmentMorphism
- [x] `spec/pneuma/morphism/ordering_spec.clj` — spec for OrderingMorphism
- [x] `spec/pneuma/morphism/registry_spec.clj` — spec for connection registry

### Phase 8 — Dogfood: gap and path layers

Formal specs for the gap report structure and path composition
invariants.

- [x] `spec/pneuma/gap/core_spec.clj` — spec for 3-layer gap report structure
- [x] `spec/pneuma/gap/diff_spec.clj` — spec for gap diff operations
- [x] `spec/pneuma/path/core_spec.clj` — spec for ComposedPath and cycle checking
- [x] `spec/pneuma/path/graph_spec.clj` — spec for graph algorithm contracts

### Phase 9 — Dogfood: lean emitters

Formal specs for individual lean emitter output contracts (valid
Lean 4 syntax/structure). The lean protocol layer is already
modeled in Phase 5; this covers the per-formalism and
per-morphism emitters.

- [ ] `spec/pneuma/lean/statechart_spec.clj` — spec for Statechart lean emission
- [ ] `spec/pneuma/lean/effect_signature_spec.clj` — spec for EffectSignature lean emission
- [ ] `spec/pneuma/lean/mealy_spec.clj` — spec for MealyHandlerSet lean emission
- [ ] `spec/pneuma/lean/optic_spec.clj` — spec for OpticDeclaration lean emission
- [ ] `spec/pneuma/lean/resolver_spec.clj` — spec for ResolverGraph lean emission
- [ ] `spec/pneuma/lean/capability_spec.clj` — spec for CapabilitySet lean emission
- [ ] `spec/pneuma/lean/existential_spec.clj` — spec for ExistentialMorphism lean emission
- [ ] `spec/pneuma/lean/structural_spec.clj` — spec for StructuralMorphism lean emission
- [ ] `spec/pneuma/lean/containment_spec.clj` — spec for ContainmentMorphism lean emission
- [ ] `spec/pneuma/lean/ordering_spec.clj` — spec for OrderingMorphism lean emission
- [ ] `spec/pneuma/lean/core_spec.clj` — spec for lean orchestration API

### Phase 10 — Dogfood: core and refinement

Public API surface and refinement map contracts.

- [ ] `spec/pneuma/core_spec.clj` — spec for public API
- [ ] `spec/pneuma/refinement_spec.clj` — spec for RefinementMap

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
  ├── pneuma.path.graph ── (no deps)
  ├── pneuma.path.core ── (path.graph + morphism.*)
  │
  ├── pneuma.gap.core ── (protocol + path.core)
  ├── pneuma.gap.diff ── (no deps)
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
  ├── pneuma.lean.resolver ── (lean.protocol + formalism.resolver)
  ├── pneuma.lean.existential ── (lean.protocol + morphism.existential)
  ├── pneuma.lean.structural ── (lean.protocol + morphism.structural)
  ├── pneuma.lean.containment ── (lean.protocol + morphism.containment)
  ├── pneuma.lean.ordering ── (lean.protocol + morphism.ordering)
  └── pneuma.lean.core ── (all lean.* namespaces)

proofs/                         (Lean 4 — generated by ->lean, not Clojure)
  ├── Pneuma/*.lean             (generated type defs + property statements)
  └── Proofs/*.lean             (human-written proofs importing Pneuma/)
```
