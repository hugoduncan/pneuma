# Pneuma — System Architecture

**Architecture design for implementing the structural domain model as
a Clojure system.**

This document considers three architectural options for mapping the 19
sorts, 30 axioms, and 14 morphisms of the [structural domain
model](structural-domain-model.md) to Clojure namespaces, protocols,
and data structures. Each option is evaluated against the constraints
from the [design document](formalism-first-conformance.md) and the
project's coding standards.

---

## 1. Design Forces

The architecture must resolve several tensions.

**Uniform dispatch vs. heterogeneous structure.** The six formalisms
share a common protocol (IProjectable), but their internal structures
are entirely different. A statechart is a tuple of sets, a hierarchy,
and transitions. An effect signature is a set of dependent operations.
A capability set is a product of three power sets. The dispatch
mechanism must be uniform at the protocol boundary while allowing each
formalism full freedom in its internals.

**Data inspectability vs. behavioral richness.** The design document
emphasizes that formalisms are Clojure data — queryable at the REPL,
serializable, diffable. But the projections (→schema, →monitor, →gen,
→gap-type) are behavioral — they produce functions, generators, and
schemas. The architecture must keep the formalisms as data while
attaching behavior through a clean seam.

**Closed exhaustiveness vs. open extension.** Axiom A1 (projection
completeness) requires that every formalism implements all five
projections. Axiom A2 (formalism exhaustiveness) says there are
exactly six kinds. These favor a closed dispatch mechanism with
compile-time guarantees. But the design document also describes
extension — adding a seventh formalism should be possible without
modifying existing code. These pull in opposite directions.

**Cross-formalism references vs. acyclic dependencies.** The morphism
layer needs to reference any pair of formalisms. If the morphism
checking code imports all six formalism namespaces, and any formalism
namespace needs to know about morphisms (for self-description or
dogfooding), we get circular dependencies. The architecture must keep
the namespace dependency graph acyclic.

**File size discipline.** No file may exceed 800 lines. The six
formalisms vary in complexity — the statechart has a step function, a
reachability analyzer, and five projections; the capability set is
comparatively simple. The namespace structure must distribute
complexity so that no single file accumulates too much.

**Build order.** The implementation strategy (§9 of the design
document) specifies bottom-up: objects first, then morphisms, then
composed paths, then gap report. The namespace dependency graph must
respect this order — earlier layers must not depend on later ones.

---

## 2. Shared Decisions

All three options agree on the following.

### 2.1 The coproduct is implicit

The `Formalism` coproduct (Statechart ⊔ EffectSignature ⊔ ...) does
not need an explicit wrapper type in Clojure. The six formalism types
are already disjoint — whether as records (Option A), maps with a
discriminator key (Option B), or plain maps matched by structure
(Option C). The coproduct is the union of these types, and case
analysis is protocol dispatch, multimethod dispatch, or `cond`,
depending on the option.

This is a Clojure-specific advantage. In a language with algebraic
data types the coproduct would be explicit. In Clojure, the open type
system makes the coproduct implicit, and the exhaustiveness guarantee
must be enforced by other means (tests, protocol implementation
requirements, or runtime checks).

### 2.2 The morphism registry is data

All options represent the connection registry (the presheaf over the
formalism graph, §4.15 of the structural model) as a Clojure map. The
registry entries describe which formalisms are connected, what kind of
morphism links them, and how to extract the relevant references from
each formalism. This map is the central data structure of the morphism
layer — it is inspectable at the REPL, filterable, and extensible by
`assoc`.

### 2.3 The gap report is a plain map

The three-layer gap report is a Clojure map with keys
`:object-gaps`, `:morphism-gaps`, and `:path-gaps`. Each value is a
collection of gap maps. No special types — just data. This makes
diffing, filtering, and serialization trivial.

### 2.4 The refinement map holds var references

The refinement map bridges mathematical objects to the running system
by holding `var` references (`#'app-db`, `#'event-log`). Dereferencing
at check time always gets the current value. No state is cached inside
formalisms or morphisms — they are specifications, not snapshots.

### 2.5 Dependency order

Regardless of option, the namespace dependency graph follows the
build order:

```
protocol → formalism/* → morphism/* → path/* → gap/* → core
```

Each arrow means "depends on." No backward dependencies. The
`protocol` namespace has no dependencies on any formalism. The
`core` namespace is the public API and depends on everything.

---

## 3. Option A — Records + Protocols + Data Registry

### 3.1 Idea

Use Clojure protocols for uniform dispatch (IProjectable,
IConnection). Use `defrecord` for each formalism type. Use a pure
data map for the morphism registry. The protocol gives compile-time
enforcement that each formalism implements all five projection
methods. The registry gives runtime inspectability of the morphism
graph.

### 3.2 Namespace layout

```
pneuma/
  protocol.clj                ; IProjectable, IConnection definitions
  formalism/
    statechart.clj            ; Statechart record + IProjectable
    effect_signature.clj      ; EffectSignature record + IProjectable
    mealy.clj                 ; MealyDeclaration record + IProjectable
    optic.clj                 ; OpticDeclaration record + IProjectable
    resolver.clj              ; ResolverGraph record + IProjectable
    capability.clj            ; CapabilitySet record + IProjectable
  morphism/
    registry.clj              ; connection registry as data
    existential.clj           ; ExistentialMorphism + IConnection
    structural.clj            ; StructuralMorphism + IConnection
    containment.clj           ; ContainmentMorphism + IConnection
    ordering.clj              ; OrderingMorphism + IConnection
  path/
    core.clj                  ; ComposedPath, cycle checker
    cycles.clj                ; three named cycles as data
  gap/
    core.clj                  ; Gap construction, GapReport assembly
    diff.clj                  ; diff-reports, failures, gaps-involving
  refinement.clj              ; RefinementMap
  core.clj                    ; public API
```

### 3.3 Protocol design

```
IProjectable
  →schema   : formalism → Malli schema
  →monitor  : formalism → (EventLogEntry → Verdict)
  →gen      : formalism → test.check generator
  →gap-type : formalism → gap type descriptor map
  →lean     : formalism → Lean 4 source code string
```

Each formalism record implements IProjectable directly. The protocol
is defined once in `protocol.clj` and never modified. Adding a new
formalism means creating a new record that implements the protocol —
no existing files change.

```
IConnection
  check  : morphism × source × target × refinement-map → seq of Gap
  →lean  : morphism × source × target → Lean 4 boundary proposition
```

Each morphism kind record (ExistentialMorphism, StructuralMorphism,
ContainmentMorphism, OrderingMorphism) implements IConnection. The
morphism kind determines the checking semantics. The registry maps
morphism ids to morphism records, so checking the full morphism layer
is: iterate the registry, look up the source and target formalisms,
call `check` on each.

### 3.4 The registry as inspectable data

The connection registry entries must be data, not opaque closures.
Each entry carries:

- `:kind` — the morphism kind keyword
- `:from` / `:to` — formalism kind keywords
- `:source-accessor` — a keyword naming a registered function that
  extracts the relevant references from the source formalism
- `:target-accessor` — a keyword naming a registered function that
  extracts the relevant identifiers from the target formalism

The accessor functions live in a separate accessor registry (a map of
keyword → function). This keeps the connection registry itself as pure
data — printable, serializable, diffable — while still allowing the
extraction logic to be arbitrarily complex. The indirection through
keywords means the registry can be inspected without executing any
code: "what does `chart→mealy/guards` connect?" is answerable from
the data alone.

### 3.5 How adding a formalism works

1. Create a new record in `pneuma/formalism/timing.clj` that
   implements `IProjectable`.
2. Add a constructor function to `pneuma/core.clj`.
3. Register accessor functions for the new formalism's extractable
   references.
4. Add entries to the connection registry for any morphisms to/from
   existing formalisms.
5. If the new formalism participates in a new cycle, add a cycle
   descriptor to `path/cycles.clj`.

Steps 1–2 require no changes to existing code. Steps 3–5 extend
existing data structures by `assoc`. No existing protocol
implementations or record definitions are modified.

### 3.6 Trade-offs

**Strengths:**

- Protocol enforcement of Axiom A1 — if a record doesn't implement
  `→schema` or `→lean`, the code won't compile.
- Clear namespace boundaries — each formalism is isolated, each
  morphism kind is isolated.
- The registry is data — inspectable, testable, serializable.
- No file approaches 800 lines. Estimated sizes: each formalism
  ~100–200 lines, each morphism kind ~80–120 lines, registry ~60
  lines, gap report ~150 lines, core ~100 lines.

**Weaknesses:**

- Records are less flexible than plain maps for serialization and
  REPL inspection (printed representation includes the class name,
  `assoc` works but `dissoc` returns a plain map).
- The accessor registry introduces an extra level of indirection.
  A newcomer must understand both the connection registry and the
  accessor registry to understand morphism checking.
- The protocol approach means external users cannot add a formalism
  without creating a new `defrecord` (they cannot use a plain map).

---

## 4. Option B — Plain Maps + Multimethods

### 4.1 Idea

Formalisms are plain Clojure maps with a `:formalism/type` key.
Projection and checking dispatch via multimethods keyed on this type.
No records, no protocols.

### 4.2 How it differs

The formalism constructors validate shape (via Malli) and attach the
discriminator:

```clojure
(defn statechart [m]
  (validate! ::statechart-schema m)
  (assoc m :formalism/type :statechart))
```

The five projection operations and the morphism checking are
multimethods:

```clojure
(defmulti ->schema :formalism/type)
(defmethod ->schema :statechart [sc] ...)
```

### 4.3 Namespace layout

Flatter than Option A — no `formalism/` subdirectory needed since
there are no protocol implementations to group:

```
pneuma/
  dispatch.clj               ; defmulti declarations
  statechart.clj             ; defmethod impls for statechart
  effect_signature.clj       ; defmethod impls for effect sig
  mealy.clj                  ; ...
  optic.clj
  resolver.clj
  capability.clj
  morphism.clj               ; morphism registry + checking
  path.clj                   ; cycle checking
  gap.clj                    ; gap report
  core.clj                   ; public API
```

### 4.4 Trade-offs

**Strengths:**

- True open extension. A downstream library can add a seventh
  formalism by defining `defmethod` implementations in their own
  namespace. No forking required.
- Formalisms are plain maps — maximally inspectable, serializable,
  and REPL-friendly. `pr-str` and `read-string` round-trip cleanly.
- Simpler mental model — everything is a map, dispatch is by keyword.

**Weaknesses:**

- No compile-time enforcement of Axiom A1. If a formalism author
  forgets to implement `→monitor`, the failure is a runtime
  "no method" exception, not a compile error. This can be mitigated
  by a runtime check at construction time, but it's a regression
  from protocol enforcement.
- Multimethod dispatch is globally mutable — `defmethod` calls have
  side effects on the global dispatch table. This complicates
  testing and makes it possible for one namespace's load order to
  affect another's behavior.
- `clj-kondo` and other static analysis tools cannot verify
  multimethod exhaustiveness. The six formalisms are a closed set
  (Axiom A2), but the multimethod mechanism treats them as open.

---

## 5. Option C — Central Interpreter

### 5.1 Idea

Formalisms and morphisms are pure data maps with no attached behavior.
A single interpreter namespace contains all projection, checking, and
gap report logic, dispatching via `case` or `cond` on formalism type
keywords.

### 5.2 How it differs

There is no protocol, no multimethod, no record. The interpreter is a
collection of pure functions:

```clojure
(defn project-schema [formalism]
  (case (:formalism/type formalism)
    :statechart (statechart-schema formalism)
    :effect-sig (effect-sig-schema formalism)
    ...))
```

### 5.3 Namespace layout

```
pneuma/
  schema.clj                 ; all formalism-specific schemas
  engine.clj                 ; project-*, check-*, gap-report
  core.clj                   ; public API (thin wrapper over engine)
```

### 5.4 Trade-offs

**Strengths:**

- Simplest possible mental model. Data in, data out. No dispatch
  mechanism to learn.
- Maximally dogfood-friendly — the system describes itself with the
  same data maps it checks.

**Weaknesses:**

- The interpreter file will exceed 800 lines almost immediately.
  Six formalisms × five projections = 30 branches in the projection
  logic alone, before morphisms or paths.
- Adding a formalism requires editing the central `case` branches.
  This is the opposite of the extension model the design document
  describes ("adding a new formalism means implementing IProjectable
  for a new record type").
- No polymorphism means every structural change to any formalism
  touches the central interpreter. Change isolation is poor.
- The "ball of mud" attractor: simple to start, but complexity
  accumulates in one place rather than distributing across
  boundaries.

---

## 6. Evaluation

### 6.1 Against the design forces

| Force | Option A | Option B | Option C |
|---|---|---|---|
| Uniform dispatch | Protocol | Multimethod | `case` |
| Exhaustiveness (A1) | Compile-time | Runtime only | Runtime only |
| Data inspectability | Good (records print, registry is data) | Best (plain maps) | Best (plain maps) |
| Open extension | New record + protocol impl | `defmethod` in any ns | Edit central file |
| Acyclic deps | Yes (protocol has no deps) | Yes (dispatch.clj has no deps) | Fragile (engine imports everything) |
| File size | Well-distributed | Well-distributed | Concentrated |
| Build order | Natural | Natural | Requires splitting engine |

### 6.2 Against the design document's principles

The design document states five principles (§ Design Principles):

1. **The math is the spec.** All options represent formalisms as
   Clojure data. Options B and C use plain maps; Option A uses
   records (which are also maps). No significant difference.

2. **Projections, not compilation.** All options derive checking
   artifacts from formalisms at call time. No compilation step.
   No difference.

3. **Connections are first-class.** All options reify the morphism
   registry as data. No difference.

4. **Cycles carry the strongest invariants.** All options support
   composed path checking. No difference.

5. **Same runtime, instant feedback.** All options work at the REPL.
   Option A's records print slightly less cleanly than Option B's
   plain maps, but this is minor.

The principles do not strongly discriminate between options. The
deciding factors are the engineering forces: exhaustiveness,
extension, and file size discipline.

### 6.3 Recommendation

**Option A** is the best fit, for two reasons:

First, Axiom A1 (projection completeness) is a hard requirement —
every formalism must implement all five projections. The protocol
mechanism enforces this at the point where a record is defined. The
other options defer this check to runtime, which means a formalism
author can ship incomplete work without immediate feedback. For a
system whose purpose is *catching gaps between specification and
implementation*, having a gap in its own specification enforcement is
a poor signal.

Second, the six formalisms are a closed set for the foreseeable
future (Axiom A2). The design document describes them as grounded in
specific mathematical traditions (Harel, Plotkin/Power, Mealy, optics,
functional dependencies, substructural logic). Adding a seventh
formalism is a deliberate architectural decision, not a casual
extension. The protocol mechanism's cost — requiring a `defrecord` —
is proportional to the significance of the act.

Option B's strength (open extension via `defmethod`) would matter if
Pneuma were a library consumed by many teams adding their own
formalisms. That is not the current use case. If it becomes the use
case later, migrating from protocols to multimethods is
straightforward — the namespace structure and data representation
would remain the same.

### 6.4 Suggested refinement to Option A

The accessor indirection in the morphism registry (§3.4) adds
complexity. A simpler approach: make the accessor functions
protocol methods on the formalism records themselves.

Each formalism could implement a second protocol:

```
IReferenceable
  extract-refs : formalism × ref-kind → set of identifiers
```

Where `ref-kind` is a keyword like `:guard-state-refs` or
`:callback-refs`. The morphism registry entries then carry only
`:from`, `:to`, `:kind`, `:source-ref-kind`, and `:target-ref-kind`
— all keywords, all data. The dispatch to extract the actual
references goes through `IReferenceable`, keeping the logic with the
formalism that owns the data.

This eliminates the separate accessor registry while keeping the
connection registry as pure data.

---

## 7. Build Plan Alignment

The recommended architecture maps directly to the implementation
strategy in §9 of the design document:

**Phase 1 — Objects:** Implement `protocol.clj` and the six
`formalism/*.clj` namespaces. Each formalism gets its record,
constructor, and `IProjectable` implementation. Independently
testable. The statechart comes first (it has the richest structure
and the step function δ). Effect signature second (simplest
projection logic). Then Mealy, optics, resolvers, capabilities.

**Phase 2 — Morphisms:** Implement `morphism/registry.clj` and the
four `morphism/*.clj` kind namespaces. Each kind gets its
`IConnection` implementation. The registry is populated with the
twelve known connections from the design document's §4.2.

**Phase 3 — Composed paths:** Implement `path/cycles.clj` with the
three named cycles and `path/core.clj` with the cycle checker. The
cycle checker composes morphisms along the path and applies the
invariant function.

**Phase 4 — Gap report:** Implement `gap/core.clj` for assembly and
`gap/diff.clj` for diffing, filtering, and querying. Then
`core.clj` as the public API.

Each phase produces independently useful checking capabilities. After
Phase 1, you can check individual formalisms against a running system.
After Phase 2, you can check cross-formalism references. After Phase
3, you can check end-to-end invariants. After Phase 4, you get the
unified report.
