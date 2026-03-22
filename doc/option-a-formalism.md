# Option A — Structural Formalism

**Structural domain model for the Records + Protocols + Data Registry
architecture described in
[system-architecture-prose.md](system-architecture-prose.md) §3.**

This document models the *implementation architecture* — the Clojure
artifacts and their structural relationships — not the mathematical
domain Pneuma checks (that model is in
[structural-domain-model.md](structural-domain-model.md)). The sorts
here are namespaces, protocols, records, and registries. The axioms
are the invariants that make Option A correct: acyclic dependencies,
protocol completeness, registry resolution, and layer stratification.

The two models operate at different levels. The domain model describes
*what* Pneuma checks. This model describes *how* Pneuma is built.
The mapping between them — which domain sort is realized by which
Clojure artifact — is captured explicitly in Appendix D.

---

## Part I — The Domain

### 1. Sorts

```
E = { Namespace, Protocol, ProtocolMethod, Record,
      RegistryEntry, RefKind, CycleDescriptor, Layer }
```

8 sorts.

#### 1.1 Organizational sorts

| Sort | Description | Instance count in Option A |
|---|---|---|
| **Namespace** | A Clojure `.clj` file with a `ns` declaration. The fundamental unit of code organization, dependency management, and file-size accounting. | ~18 |
| **Layer** | A build/dependency stratum. Namespaces are partitioned into layers; dependencies only flow from later layers to earlier ones. | 4 |

The four layers, in dependency order:

```
protocol < formalism < morphism < integration
```

Where `integration` subsumes `path/*`, `gap/*`, `refinement`, and
`core`.

#### 1.2 Dispatch sorts

| Sort | Description | Instance count |
|---|---|---|
| **Protocol** | A Clojure `defprotocol`. Defines a set of methods that records must implement. | 3 |
| **ProtocolMethod** | A single method declared within a protocol. Has a name, an argument signature, and a docstring. | 8 |
| **Record** | A Clojure `defrecord` type. Has positional fields and implements one or more protocols. | 10 |

The three protocols:

| Protocol | Methods | Implemented by |
|---|---|---|
| `IProjectable` | `->schema`, `->monitor`, `->gen`, `->gap-type`, `->lean` | 6 formalism records |
| `IConnection` | `check`, `->lean` | 4 morphism kind records |
| `IReferenceable` | `extract-refs` | 6 formalism records |

The ten records:

| Record | Layer | Protocols |
|---|---|---|
| `Statechart` | formalism | IProjectable, IReferenceable |
| `EffectSignature` | formalism | IProjectable, IReferenceable |
| `MealyDeclaration` | formalism | IProjectable, IReferenceable |
| `OpticDeclaration` | formalism | IProjectable, IReferenceable |
| `ResolverGraph` | formalism | IProjectable, IReferenceable |
| `CapabilitySet` | formalism | IProjectable, IReferenceable |
| `ExistentialMorphism` | morphism | IConnection |
| `StructuralMorphism` | morphism | IConnection |
| `ContainmentMorphism` | morphism | IConnection |
| `OrderingMorphism` | morphism | IConnection |

#### 1.3 Registry sorts

| Sort | Description | Instance count |
|---|---|---|
| **RegistryEntry** | An entry in the connection registry. Describes a typed edge in the formalism graph: which formalisms it connects, what kind of morphism it represents, and which reference-extraction operations to use. | 12 |
| **RefKind** | A keyword naming a reference-extraction operation. The `IReferenceable` protocol dispatches on this keyword to extract the relevant identifiers from a formalism. | ~12 |

#### 1.4 Path sorts

| Sort | Description | Instance count |
|---|---|---|
| **CycleDescriptor** | A data description of a named cycle through the morphism graph. Carries the ordered list of morphism steps and the invariant function. | 3 |

### 2. Value Sorts

```
V = { Symbol, Keyword, String, Nat, Bool }
```

- **Symbol** — Clojure namespace-qualified symbols (`pneuma.protocol`).
- **Keyword** — Clojure keywords (`:existential`, `:chart->mealy/guards`).
- **Nat** — natural numbers (line counts, method arities).

### 3. Arrows

#### 3.1 Identity arrows

```
name      : Namespace      → Symbol
name      : Protocol       → Symbol
name      : ProtocolMethod → Symbol
name      : Record         → Symbol
id        : RegistryEntry  → Keyword
name      : RefKind        → Keyword
name      : CycleDescriptor → Keyword
name      : Layer          → Keyword
```

#### 3.2 Layer membership

```
layer     : Namespace → Layer            -- total
layer     : Record    → Layer            -- total
```

Every namespace and every record belongs to exactly one layer.

#### 3.3 Containment

```
definedIn : Protocol       → Namespace   -- total
definedIn : ProtocolMethod → Namespace   -- total (same ns as owning protocol)
definedIn : Record         → Namespace   -- total
```

Each protocol, method, and record is defined in exactly one
namespace. The `definedIn` arrow for a ProtocolMethod always equals
the `definedIn` of its owning Protocol — this is Axiom A4.

#### 3.4 Protocol structure

```
owningProtocol : ProtocolMethod → Protocol  -- total
```

Every method belongs to exactly one protocol.

#### 3.5 Registry structure

```
kind          : RegistryEntry → Keyword     -- total (morphism kind)
fromRecord    : RegistryEntry → Record      -- total (source formalism)
toRecord      : RegistryEntry → Record      -- total (target formalism)
sourceRefKind : RegistryEntry → RefKind     -- total
targetRefKind : RegistryEntry → RefKind     -- total
```

Every registry entry fully specifies its connection: which records it
bridges, what kind of morphism it represents, and which
reference-extraction operations to apply on each side.

#### 3.6 Cycle structure

The `steps` of a CycleDescriptor are ordered — captured by the
FreeMonoid construction (§4.7). Each step references a RegistryEntry.

#### 3.7 Layer ordering

```
precedes : Layer → Layer                    -- partial
```

A strict partial order on layers. In Option A this is a total order
(a chain): `protocol < formalism < morphism < integration`.

---

## Part II — Constructions, Axioms, Morphisms

### 4. Constructions

#### 4.1 Namespace dependency graph

```
deps : Namespace → 𝒫(Namespace)
```

Each namespace carries a set of namespaces it depends on (its
`:require` clause). The full dependency structure is a directed
graph over Namespace, constrained to be acyclic (Axiom A1).

This is a **PowerSet** construction on Namespace. The graph structure
emerges from the collection of all `deps` arrows.

#### 4.2 Namespaces fibered over Layers

```
π : Namespace → Layer
Fiber(l) = { ns | layer(ns) = l }
```

A **Fibration** partitioning namespaces into layers. The four fibers:

| Layer | Fiber contents |
|---|---|
| `protocol` | `pneuma.protocol` |
| `formalism` | `pneuma.formalism.statechart`, `.effect-signature`, `.mealy`, `.optic`, `.resolver`, `.capability` |
| `morphism` | `pneuma.morphism.registry`, `.existential`, `.structural`, `.containment`, `.ordering` |
| `integration` | `pneuma.path.core`, `.cycles`, `pneuma.gap.core`, `.diff`, `pneuma.refinement`, `pneuma.core` |

The fibration is non-trivial because the dependency constraint (Axiom
A2) is stated in terms of fibers: dependencies only cross fiber
boundaries in the `precedes` direction.

#### 4.3 Protocol as PowerSet of methods

```
methods : Protocol → 𝒫(ProtocolMethod)
```

Each protocol owns a set of methods. The set is unordered — method
declaration order is irrelevant in Clojure protocols.

| Protocol | methods |
|---|---|
| IProjectable | `{ ->schema, ->monitor, ->gen, ->gap-type, ->lean }` |
| IConnection | `{ check, ->lean }` |
| IReferenceable | `{ extract-refs }` |

#### 4.4 Record as Product of fields and protocol implementations

```
Record ≅ { name    : Symbol,
           fields  : List(Keyword),
           impls   : 𝒫(Protocol) }
```

A **Product** of:

- `fields` — a **FreeMonoid** of keywords. Field order matters in
  `defrecord` because it determines the positional constructor
  argument order.
- `impls` — a **PowerSet** of Protocol. The set of protocols this
  record implements.

The `impls` set is the structural core of Option A's dispatch
mechanism. A formalism record's `impls` always contains both
`IProjectable` and `IReferenceable` (Axiom A5). A morphism kind
record's `impls` always contains `IConnection` (Axiom A6).

#### 4.5 Connection registry as PowerSet

```
ConnectionRegistry = 𝒫(RegistryEntry)
```

The connection registry is an unordered set of registry entries. It
is a **PowerSet** construction — the natural morphisms are subset
inclusion (for filtering), union (for merging registries), and
intersection (for finding shared connections).

Each RegistryEntry is a **Product** of five keyword/record fields:

```
RegistryEntry ≅ { id            : Keyword,
                  kind          : Keyword,
                  fromRecord    : Record,
                  toRecord      : Record,
                  sourceRefKind : RefKind,
                  targetRefKind : RefKind }
```

The registry is pure data — no closures, no functions. The behavioral
indirection goes through `IReferenceable` dispatch, keyed by the
`RefKind` keywords stored in each entry.

#### 4.6 RefKind as Sigma (dispatch key)

```
RefKind = Σ(name : Keyword). ExtractFn(name)
```

A **Sigma** (dependent pair). The keyword `name` is the data part
(stored in the registry, inspectable, serializable). The `ExtractFn`
is the behavioral part — the actual extraction function, which lives
inside the `IReferenceable` implementation on the formalism record.

The dependent structure captures the key design decision: the
*identity* of a reference extraction operation (the keyword) is
separated from its *implementation* (the protocol method body). The
registry stores only the identity; the behavior is looked up through
protocol dispatch at check time.

#### 4.7 CycleDescriptor as FreeMonoid with closure

```
CycleDescriptor ≅ { name      : Keyword,
                    steps     : List(RegistryEntry),
                    invariant : InvariantFn }
```

The `steps` field is a **FreeMonoid** of RegistryEntry — an ordered
sequence of morphism steps that compose along the formalism graph.
The ordering matters: step *i*'s target must equal step *i+1*'s
source.

The cycle closure constraint (Axiom A9) requires that the last
step's target equals the first step's source.

#### 4.8 Layer chain as total order

```
(Layer, <) where protocol < formalism < morphism < integration
```

The four layers form a **totally ordered set** — a chain. This is a
degenerate **Semilattice** (every pair has a meet and join because
the order is total).

The chain structure is the backbone of the architecture's acyclicity
guarantee. Axiom A2 states that namespace dependencies respect this
ordering.

### 5. Axioms

#### 5.1 Dependency invariants

**A1 — Acyclicity:**
```
∀ ns₁ ns₂ : Namespace,
  ns₂ ∈ deps(ns₁) ⟹ ¬(ns₁ ∈ deps*(ns₂))
```
The namespace dependency graph is a DAG. `deps*` is the transitive
closure. No namespace can transitively depend on itself.

**A2 — Layer respect:**
```
∀ ns₁ ns₂ : Namespace,
  ns₂ ∈ deps(ns₁) ⟹ layer(ns₂) ≤ layer(ns₁)
```
Dependencies only flow from later layers to earlier layers (or within
the same layer). A formalism namespace may depend on `protocol` but
never on `morphism` or `integration`. This is the enforcement of the
build order.

**A3 — Protocol isolation:**
```
∀ ns : Namespace,
  layer(ns) = protocol ⟹ deps(ns) = ∅   (within the project)
```
The `protocol` layer has no intra-project dependencies. Protocols are
defined without reference to any formalism, morphism, or integration
code. This is what makes the dependency graph acyclic at the
inter-layer level.

#### 5.2 Protocol completeness

**A4 — Method co-location:**
```
∀ pm : ProtocolMethod,
  definedIn(pm) = definedIn(owningProtocol(pm))
```
Every protocol method is defined in the same namespace as its owning
protocol. (This is a consequence of how `defprotocol` works in
Clojure, but worth stating because it constrains the namespace
layout.)

**A5 — Formalism protocol coverage:**
```
∀ r : Record, layer(r) = formalism,
  { IProjectable, IReferenceable } ⊆ impls(r)
```
Every formalism record implements both `IProjectable` (five
projection methods) and `IReferenceable` (reference extraction).
This is the structural guarantee behind Axiom A1 of the domain model
(projection completeness).

**A6 — Morphism protocol coverage:**
```
∀ r : Record, layer(r) = morphism,
  IConnection ∈ impls(r)
```
Every morphism kind record implements `IConnection`.

**A7 — Implementation totality:**
```
∀ r : Record, ∀ p ∈ impls(r), ∀ m ∈ methods(p),
  r has an implementation of m
```
If a record claims to implement a protocol, every method of that
protocol has an implementation body in the record. (Enforced by the
Clojure compiler — a `defrecord` that extends a protocol must
provide all methods.)

This is the axiom that makes Option A's choice of protocols over
multimethods structurally significant. Multimethods (Option B) cannot
enforce this — a missing `defmethod` is only detected at call time.

#### 5.3 Registry invariants

**A8 — Registry entry resolution:**
```
∀ e : RegistryEntry,
  fromRecord(e) ∈ { r : Record | layer(r) = formalism }
  ∧ toRecord(e) ∈ { r : Record | layer(r) = formalism }
```
Every registry entry's `from` and `to` resolve to existing formalism
records. No dangling references in the registry.

**A9 — RefKind coverage:**
```
∀ e : RegistryEntry,
  sourceRefKind(e) is handled by extract-refs on fromRecord(e)
  ∧ targetRefKind(e) is handled by extract-refs on toRecord(e)
```
Every RefKind named in a registry entry has a corresponding
`extract-refs` implementation on the referenced record. The
`IReferenceable` dispatch must succeed for every RefKind the registry
names.

**A10 — Registry inspectability:**
```
∀ e : RegistryEntry,
  pr-str(e) is readable ∧ read-string(pr-str(e)) = e
```
Every registry entry is pure data — printable, serializable, and
round-trippable. No closures, no opaque objects. This is the
structural consequence of storing RefKind keywords rather than
functions in the registry.

#### 5.4 Cycle invariants

**A11 — Cycle step adjacency:**
```
∀ c : CycleDescriptor, ∀ i ∈ 1..len(steps(c))-1,
  toRecord(steps(c)[i]) = fromRecord(steps(c)[i+1])
```
Each step's target record equals the next step's source record. The
steps form a composable chain in the formalism graph.

**A12 — Cycle closure:**
```
∀ c : CycleDescriptor,
  toRecord(last(steps(c))) = fromRecord(first(steps(c)))
```
The chain closes — the last step's target equals the first step's
source.

#### 5.5 Size and distribution invariants

**A13 — File size bound:**
```
∀ ns : Namespace,
  lineCount(ns) < 800
```

**A14 — Formalism isolation:**
```
∀ r₁ r₂ : Record, layer(r₁) = layer(r₂) = formalism, r₁ ≠ r₂,
  definedIn(r₁) ≠ definedIn(r₂)
```
Each formalism record is defined in its own namespace. No two
formalism records share a namespace. This distributes complexity and
keeps each file focused on one formalism.

#### 5.6 Extension invariants

**A15 — Extension locality (protocols):**
```
∀ new_r : Record (added to the system),
  Invariance(definedIn(p), t_before, t_after)
  for all existing p : Protocol
```
Adding a new record does not modify any existing protocol definition.
Protocols are immutable after initial definition.

**A16 — Extension locality (records):**
```
∀ new_r : Record (added to the system),
  Invariance(definedIn(r), t_before, t_after)
  for all existing r : Record
```
Adding a new record does not modify any existing record definition.
Existing formalism and morphism records are untouched.

**A17 — Extension by accretion (registry):**
```
∀ new_e : RegistryEntry (added to the system),
  ConnectionRegistry_after = ConnectionRegistry_before ∪ { new_e }
```
Extending the registry is pure set union. Existing entries are
unchanged. The new entry is `assoc`'d into the map.

### 6. Morphisms

#### 6.1 Layer inclusion

```
include : Record → Fiber(layer(r)) ⊆ Namespace
```

Placing a new record into a layer is a **fibration section** — it
selects a namespace within the target fiber and defines the record
there. The inclusion must respect Axiom A14 (one record per namespace
for formalism layer).

**Preserves:** Fibration structure (the record lands in the correct
fiber).

#### 6.2 Protocol dispatch

```
dispatch : Record × ProtocolMethod → Implementation
```

Protocol dispatch is the central morphism of Option A. Given a record
and a protocol method, it resolves to the method body defined on that
record. This is a **case analysis** (coproduct elimination) over the
implicit coproduct of records that implement the protocol.

For IProjectable, dispatch resolves one of the six formalism records
to one of five projection methods — 30 resolutions total. Each is
independent (Axiom A7 guarantees totality).

**Preserves:** Coproduct structure (exhaustive, disjoint dispatch).

#### 6.3 Registry extension

```
extend : ConnectionRegistry × RegistryEntry → ConnectionRegistry
```

Adding a registry entry is a **PowerSet** morphism — subset inclusion
from the old registry to the new. The operation is `assoc` on the
Clojure map, which is set union at the structural level.

**Preserves:** PowerSet structure. Existing entries are invariant
(Axiom A17).

#### 6.4 Registry-to-dispatch bridge

```
resolve : RegistryEntry × Record × Record → Gap sequence
```

The bridge from registry data to behavioral checking. Given a
registry entry and its source/target records:

1. Read `sourceRefKind` and `targetRefKind` from the entry (data
   lookup).
2. Call `extract-refs` on the source record with the source RefKind
   (IReferenceable dispatch).
3. Call `extract-refs` on the target record with the target RefKind
   (IReferenceable dispatch).
4. Call `check` on the morphism kind record with the extracted ref
   sets (IConnection dispatch).

This morphism composes three protocol dispatches with one data
lookup. The composition is well-typed because of Axioms A8 (registry
entries resolve to existing records), A9 (RefKinds are handled), and
A6 (morphism records implement IConnection).

**Preserves:** Product structure (the five fields of RegistryEntry
are independently resolved) and Sigma structure (the RefKind keyword
determines which extraction function runs).

#### 6.5 Formalism addition

```
addFormalism : System × NewFormalismSpec → System'
```

The five-step extension process from §3.5 of the architecture
document, formalized as a single composite morphism:

1. Create a new Record in a new Namespace within the `formalism`
   fiber (fibration section — §6.1).
2. Implement IProjectable and IReferenceable on the record (protocol
   dispatch extension — §6.2).
3. Add a constructor to `pneuma.core` (integration layer
   modification).
4. Add RegistryEntry values to the ConnectionRegistry (registry
   extension — §6.3).
5. Optionally add a CycleDescriptor (FreeMonoid extension on the
   cycle list).

Steps 1–2 create new artifacts. Steps 3–5 extend existing data
structures by accretion. No existing protocol, record, or registry
entry is modified (Axioms A15, A16, A17).

**Preserves:** Fibration (new namespace lands in correct layer),
PowerSet (registry grows by union), FreeMonoid (cycle list grows by
append).

#### 6.6 Gap report assembly pipeline

```
assemble : 𝒫(Record) × ConnectionRegistry × List(CycleDescriptor) → GapReport
```

The full data flow from architecture artifacts to gap report:

1. For each formalism Record, call the four IProjectable methods →
   `𝒫(ObjectGap)`.
2. For each RegistryEntry, resolve via §6.4 → `𝒫(MorphismGap)`.
3. For each CycleDescriptor, compose the resolved morphisms along
   the FreeMonoid steps → `𝒫(PathGap)`.
4. Assemble the three sets into a Product → `GapReport`.

This is a pipeline of PowerSet homomorphisms (each stage maps a set
of inputs to a set of gaps) followed by a Product construction (the
three layers are independent).

**Preserves:** PowerSet (gaps accumulate by union), FreeMonoid
(cycles compose in step order), Product (layers are independent).

### 7. 2-Cells

The model is a 1-category.

The extension morphism (§6.5) is the only structurally significant
operation, and there is only one way to perform it (the five-step
process). There are no parallel strategies for adding a formalism,
extending the registry, or assembling the gap report — the
architectural choices of Option A have collapsed the design space to
a single path.

If Option B's multimethods were also modeled, there would be a 2-Cell
between the protocol dispatch morphism (§6.2) and the multimethod
dispatch morphism — two parallel strategies for the same structural
operation (type-based dispatch). But within Option A alone, no such
parallelism exists.

---

## Part III — Appendices

### Appendix A: Construction Summary

| # | Construction | Applied to | Structural role |
|---|---|---|---|
| 4.1 | PowerSet | `deps : Namespace → 𝒫(Namespace)` | Dependency graph |
| 4.2 | Fibration | `π : Namespace → Layer` | Layer partitioning |
| 4.3 | PowerSet | `methods : Protocol → 𝒫(ProtocolMethod)` | Protocol structure |
| 4.4 | Product + FreeMonoid + PowerSet | Record | Fields × protocol implementations |
| 4.5 | PowerSet | ConnectionRegistry = `𝒫(RegistryEntry)` | Registry |
| 4.5 | Product | RegistryEntry | Five-field composite |
| 4.6 | Sigma | RefKind | Keyword-dispatched extraction |
| 4.7 | FreeMonoid | `steps : CycleDescriptor → List(RegistryEntry)` | Cycle composition |
| 4.8 | Semilattice (chain) | `(Layer, <)` | Build order |

### Appendix B: Axiom Summary

| # | Name | Pattern | Structural consequence |
|---|---|---|---|
| A1 | Acyclicity | Graph constraint | No circular namespace deps |
| A2 | Layer respect | Implication | Deps flow toward earlier layers only |
| A3 | Protocol isolation | Equation | protocol.clj has no project deps |
| A4 | Method co-location | Equation | Methods live in protocol's namespace |
| A5 | Formalism protocol coverage | SubsetOf | Formalism records implement IProjectable (5 methods) + IReferenceable |
| A6 | Morphism protocol coverage | Membership | Morphism records implement IConnection |
| A7 | Implementation totality | Coverage | All protocol methods have bodies |
| A8 | Registry entry resolution | Membership | from/to point to existing records |
| A9 | RefKind coverage | Implication | Every named RefKind is handled |
| A10 | Registry inspectability | Equation | Registry entries are pure data |
| A11 | Cycle step adjacency | Equation | Consecutive steps share records |
| A12 | Cycle closure | Equation | Last step's target = first step's source |
| A13 | File size bound | LessThan | Every namespace < 800 lines |
| A14 | Formalism isolation | Inequality | One formalism record per namespace |
| A15 | Extension locality (protocols) | Immutability | Adding a record doesn't change protocols |
| A16 | Extension locality (records) | Immutability | Adding a record doesn't change records |
| A17 | Extension by accretion | SubsetOf | Registry grows by union only |

### Appendix C: Morphism Catalogue

| # | Morphism | Domain | Codomain | Preserves |
|---|---|---|---|---|
| 6.1 | Layer inclusion | Record | Fiber(Layer) | Fibration |
| 6.2 | Protocol dispatch | Record × ProtocolMethod | Implementation | Coproduct |
| 6.3 | Registry extension | Registry × Entry | Registry | PowerSet |
| 6.4 | Registry-to-dispatch bridge | Entry × Record × Record | `𝒫(Gap)` | Product, Sigma |
| 6.5 | Formalism addition | System × Spec | System' | Fibration, PowerSet, FreeMonoid |
| 6.6 | Gap report assembly | Records × Registry × Cycles | GapReport | PowerSet, FreeMonoid, Product |

### Appendix D: Domain-to-Architecture Mapping

How the sorts from the
[domain model](structural-domain-model.md) are realized by the
Clojure artifacts of Option A.

| Domain sort | Clojure realization | Architecture sort |
|---|---|---|
| Statechart | `defrecord Statechart` | Record (formalism layer) |
| EffectSignature | `defrecord EffectSignature` | Record (formalism layer) |
| MealyDeclaration | `defrecord MealyDeclaration` | Record (formalism layer) |
| OpticDeclaration | `defrecord OpticDeclaration` | Record (formalism layer) |
| ResolverGraph | `defrecord ResolverGraph` | Record (formalism layer) |
| CapabilitySet | `defrecord CapabilitySet` | Record (formalism layer) |
| Formalism (coproduct) | Implicit — six record types, dispatched by protocol | Protocol (IProjectable) |
| Projection | Return value of IProjectable methods | ProtocolMethod output |
| Morphism | `defrecord` per kind + IConnection | Record (morphism layer) |
| ComposedPath | Data map with `List(RegistryEntry)` | CycleDescriptor |
| Gap | Plain Clojure map | (not a distinct architecture sort) |
| GapReport | Plain Clojure map with three keys | (not a distinct architecture sort) |
| RefinementMap | Plain Clojure map with var refs | (not a distinct architecture sort) |
| EventLogEntry | Plain Clojure map | (not a distinct architecture sort) |

The four domain sorts realized as plain maps (Gap, GapReport,
RefinementMap, EventLogEntry) do not appear as architecture sorts
because they carry no dispatch behavior — they are data flowing
through the system, not structural elements of the system.

### Appendix E: What Option A buys over Options B and C

Each axiom in this model corresponds to a structural guarantee. This
table maps guarantees to whether they are enforced statically
(compile time), by convention, or not at all, in each option.

| Axiom | Option A | Option B | Option C |
|---|---|---|---|
| A5, A6 — Protocol coverage | Compiler enforced | Runtime check | Runtime check |
| A7 — Implementation totality | Compiler enforced | Runtime check | N/A (no dispatch) |
| A10 — Registry inspectability | Structural (no closures) | Structural | Structural |
| A14 — Formalism isolation | Convention | Convention | Violated (single file) |
| A15, A16 — Extension locality | Structural (protocols are closed) | Structural (multimethods are open) | Violated (edit central file) |
| A17 — Extension by accretion | Structural | Structural | Violated |

Option A's distinguishing property is that Axioms A5, A6, and A7 are
**compiler-enforced**. In a system whose purpose is catching gaps
between specification and implementation, having the implementation's
own structural invariants enforced at compile time rather than runtime
is architecturally coherent — the tool practices what it preaches.
This extends to the `->lean` projection: the protocol mechanism
ensures every formalism provides Lean emission alongside its runtime
projections.
