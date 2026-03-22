# Pneuma — Structural Domain Model

**Structural formalism for the conformance checking domain described in
[formalism-first-conformance.md](formalism-first-conformance.md).**

This document models Pneuma at its *meta-level*: the architecture of
the conformance checking system itself — formalisms, projections,
morphisms, composed paths, and gap reports. The six mathematical
objects (statecharts, effect signatures, etc.) appear as sorts whose
internal structure is captured by constructions. The *object-level*
content of those formalisms (a specific statechart's states, a specific
handler's guards) lives one level down and is modeled structurally here
but instantiated at runtime.

---

## Part I — The Domain

### 1. Sorts

```
E = { Formalism,
      Statechart, EffectSignature, MealyDeclaration,
      OpticDeclaration, ResolverGraph, CapabilitySet,
      State, Transition, Operation, Resolver, Attribute,
      Projection, Morphism, ComposedPath,
      Gap, GapReport, RefinementMap, EventLogEntry }
```

19 sorts total, grouped into four clusters.

#### 1.1 Formalism sorts

The six mathematical objects that describe architectural layers, plus
their coproduct.

| Sort | Mathematical basis | Describes |
|---|---|---|
| **Formalism** | Coproduct of the six below | Any formalism, generically |
| **Statechart** | Harel statechart tuple *(S, ≤, T, C, H, δ)* | Session lifecycle |
| **EffectSignature** | Algebraic effect signature | Side-effect vocabulary |
| **MealyDeclaration** | Mealy machine *(S × E → S × Eff\*)* | Handler contracts |
| **OpticDeclaration** | Functional optics (lens, traversal, fold) | Subscriptions |
| **ResolverGraph** | Functional dependency hypergraph | Query resolution |
| **CapabilitySet** | Substructural resource set | Extension permissions |

#### 1.2 Component sorts

Sub-entities with independent identity, referenced across formalisms.
These are the *targets* of cross-formalism references — the things
that dangle when a rename happens.

| Sort | Lives in | Referenced by |
|---|---|---|
| **State** | Statechart | MealyDeclaration guards, CapabilitySet |
| **Transition** | Statechart | EffectSignature callbacks, raised events |
| **Operation** | EffectSignature | MealyDeclaration emissions |
| **Resolver** | ResolverGraph | CapabilitySet queries |
| **Attribute** | ResolverGraph | CapabilitySet, Resolver I/O |

#### 1.3 Checking infrastructure sorts

The conformance checking machinery that operates over formalisms.

| Sort | Role |
|---|---|
| **Projection** | Checking artifact derived from a formalism via IProjectable |
| **Morphism** | Typed connection between two formalisms |
| **ComposedPath** | Cycle through the morphism graph carrying end-to-end invariants |
| **Gap** | Individual conformance finding at any layer |
| **GapReport** | Three-layer aggregation of all gaps |
| **RefinementMap** | Bridge from mathematical objects to implementation state |
| **EventLogEntry** | A trace entry consumed by monitors |

### 2. Value Sorts

```
V = { String, Symbol, Keyword, Nat, Bool, Instant, Path }
```

Plus domain-specific enumerations:

```
GapStatus      = { :conforms, :absent, :diverges }
MorphismKind   = { :existential, :structural, :containment, :ordering }
OpticType      = { :Lens, :Traversal, :Fold, :Derived }
ProjectionKind = { :schema, :monitor, :generator, :gap-type, :lean }
GapLayer       = { :object, :morphism, :path }
Verdict        = { :ok, :violation }
```

`Verdict` is the return type of a monitor applied to an event log
entry — either the entry conforms or it violates the formalism's
behavioral contract.

### 3. Arrows

#### 3.1 Identity arrows

Every sort with independent identity carries an identifier:

```
id : State            → Keyword
id : Transition       → Keyword
id : Operation        → Keyword
id : Resolver         → Keyword
id : Attribute        → Keyword
id : OpticDeclaration → Keyword
id : MealyDeclaration → Keyword
id : CapabilitySet    → Keyword
id : Morphism         → Keyword
id : ComposedPath     → Keyword
```

#### 3.2 Statechart arrows

```
initial     : Statechart  → Configuration     -- total (initial config)
```

Transition structure:

```
source      : Transition → State              -- total
target      : Transition → State              -- total
event       : Transition → Keyword            -- total
raise       : Transition →? Keyword           -- partial (not all raise)
```

#### 3.3 EffectSignature arrows

```
returnType  : Operation → Keyword             -- total (the callback position)
```

Fields within operations are dependent on the operation name — see
Sigma construction (§4.4).

#### 3.4 MealyDeclaration arrows

```
handledEvent : MealyDeclaration → Keyword     -- total
```

Guards, updates, and effect emissions are ordered sub-structures — see
FreeMonoid constructions (§4.5).

#### 3.5 OpticDeclaration arrows

```
opticType   : OpticDeclaration → OpticType    -- total
path        : OpticDeclaration → Path         -- total
```

#### 3.6 Resolver arrows

Input and output attributes are set-valued — see PowerSet
constructions (§4.7).

#### 3.7 Morphism arrows

```
sourceFormalism : Morphism → Formalism        -- total
targetFormalism : Morphism → Formalism        -- total
morphismKind    : Morphism → MorphismKind     -- total
```

#### 3.8 ComposedPath arrows

```
startFormalism : ComposedPath → Formalism     -- total
```

The path itself is a FreeMonoid(Morphism) — see §4.10.

#### 3.9 Gap arrows

```
status   : Gap → GapStatus                   -- total
layer    : Gap → GapLayer                     -- total
```

The `detail` field is status-dependent — see Sigma construction (§4.11).

#### 3.10 Projection arrows

```
projectionKind  : Projection → ProjectionKind  -- total
sourceFormalism : Projection → Formalism       -- total
```

#### 3.11 EventLogEntry arrows

```
event     : EventLogEntry → Keyword           -- total
timestamp : EventLogEntry → Instant           -- total
```

State snapshots (db-before, db-after) and emitted effects are
structured — see §4.13.

#### 3.12 RefinementMap arrows

```
atomRef      : RefinementMap → Path           -- total
eventLogRef  : RefinementMap → Path           -- total
```

Source namespaces and state accessors are set/list-valued — see §4.12.

#### 3.13 Cross-formalism reference arrows

These arrows are the *substance* of what Pneuma checks. Each is a
relationship between a component in one formalism and a component in
another. The Morphism sort *reifies* these arrows as first-class
objects.

```
guardRefs     : MealyDeclaration → State             -- via guards
callbackRefs  : Operation        → Transition         -- via on-complete/on-error
emissionRefs  : MealyDeclaration → Operation          -- via emitted effects
dispatchRefs  : CapabilitySet    → MealyDeclaration   -- via dispatch set
subscribeRefs : CapabilitySet    → OpticDeclaration   -- via subscribe set
queryRefs     : CapabilitySet    → Attribute           -- via query set
updateRefs    : MealyDeclaration → OpticDeclaration   -- via update paths
sourceRefs    : OpticDeclaration → Attribute           -- derived subs source optics
feedsRefs     : OpticDeclaration → Resolver            -- optic feeds resolver input
raiseRefs     : Transition       → MealyDeclaration   -- raised events need handlers
```

All cross-formalism reference arrows are mediated through sets (a
MealyDeclaration has *multiple* guards each referencing a State). The
arrows above are the underlying per-element relationships; the
set-level relationships are PowerSet-lifted versions. This distinction
matters: an existential morphism check iterates over the set and
verifies each element.

---

## Part II — Constructions, Axioms, Morphisms

### 4. Constructions

#### 4.1 Formalism as Coproduct

```
Formalism = Statechart ⊔ EffectSignature ⊔ MealyDeclaration
          ⊔ OpticDeclaration ⊔ ResolverGraph ⊔ CapabilitySet
```

The six formalisms are mutually exclusive alternatives. Each implements
IProjectable, but their internal structures are entirely different.
Case analysis (elimination) is the `IProjectable` protocol dispatch.

#### 4.2 Statechart as dependent record

Statechart is a product of several constructions over its components:

```
Statechart = {
  states      : 𝒫(State)
  hierarchy   : Forest(State)
  parallel    : 𝒫(State)           -- parallel region roots
  initial     : Configuration
  transitions : 𝒫(Transition)
}
```

Where:

- `𝒫(State)` — PowerSet: the unordered set of all states.
- `Forest(State)` — the nesting hierarchy. Hierarchy is a forest
  because multiple top-level regions (e.g. `:conversation`,
  `:extensions`) are siblings under a root.
- `Configuration = 𝒫(State)` — a valid configuration is a set of
  active states, one per region.
- `𝒫(Transition)` — the set of all transitions.

The step function *δ : Configuration × Keyword → Configuration* is
*derived* from this data, not stored. Given a configuration and an
event, δ finds a matching transition, removes the source state, and
adds the target state. Parallel regions are independent.

#### 4.3 EffectSignature as PowerSet of dependent operations

```
EffectSignature = 𝒫(Operation)
```

Where each Operation has dependent structure:

```
Operation = Σ(name : Keyword). Fields(name)
```

The `Fields` family maps each operation name to its typed field set.
For example, `Fields(:ai/generate) = { :session-id → :SessionId,
:messages → List(:Message), :on-complete → EventRef, ... }`. The
callback fields (`:on-complete`, `:on-error`) are the *continuation
positions* — they close the interaction loop.

#### 4.4 MealyDeclaration as dependent record with FreeMonoid components

```
MealyDeclaration = {
  id      : Keyword
  params  : List(Param)
  guards  : List(Guard)
  updates : List(StateUpdate)
  effects : List(EffectEmission)
}
```

Where:

- `Param = { name : Keyword, type : Keyword }` — a typed parameter.
- `Guard = { check : Keyword, args : List(Keyword) }` — a
  precondition. Guards that reference chart states carry existential
  references to the Statechart.
- `StateUpdate = { path : Path, op : Keyword, value : Any }` — a
  declarative state mutation.
- `EffectEmission = { op : Keyword, fields : Map(Keyword, Any) }` —
  an effect description referencing an Operation.

The three `List(...)` fields are FreeMonoids. Their ordering matters:
guards are checked in sequence (short-circuit), updates are applied in
sequence (later updates see earlier state), effects are emitted in
sequence (for deterministic replay).

#### 4.5 OpticDeclaration as Sigma

```
OpticDeclaration = Σ(opticType : OpticType). Config(opticType)
```

- `Config(:Lens)    = { params : List(Param), path : Path }`
- `Config(:Traversal) = { params : List(Param), path : Path }`
- `Config(:Fold)    = { params : List(Param), path : Path }`
- `Config(:Derived) = { params : List(Param),
                        sources : Map(Keyword, Path),
                        derivations : Map(Keyword, DerivationExpr) }`

The optic type determines what structure follows. A `:Derived` optic
has richer structure — it composes multiple source optics and applies
derivation expressions.

#### 4.6 ResolverGraph as PowerSet + hypergraph

```
ResolverGraph = 𝒫(Resolver)
```

Where:

```
Resolver = {
  id     : Keyword
  input  : 𝒫(Attribute)
  output : 𝒫(Attribute)
  source : ResolverSource
}
```

The resolver graph is a *hypergraph*: each resolver is a hyperedge from
its input attribute set to its output attribute set. Reachability in
this hypergraph determines which attributes are derivable from which
inputs — this is equivalent to the chase algorithm in database
dependency theory.

`ResolverSource = :local ⊔ External(Keyword)` — a coproduct
distinguishing local resolvers from external ones.

#### 4.7 CapabilitySet as Product of three PowerSets

```
CapabilitySet = {
  id        : Keyword
  dispatch  : 𝒫(Keyword)    -- events this agent may dispatch
  subscribe : 𝒫(Keyword)    -- optics this agent may observe
  query     : 𝒫(Keyword)    -- attributes this agent may query
}
```

This is a product of three PowerSets. The substructural semantics:
capabilities are resources that must be explicitly granted. An agent
*without* a capability in its set is denied the corresponding
operation.

#### 4.8 Projection as Coproduct

```
Projection = Schema ⊔ Monitor ⊔ Generator ⊔ GapTypeDesc ⊔ LeanSource
```

Each variant has a distinct formal structure:

- `Schema` — a Malli schema value. Closed under Malli's algebra:
  `m/validate : Schema × Any → Bool` and
  `m/explain : Schema × Any → Option(Explanation)` and
  `mg/generate : Schema → test.check.Generator(Any)`.
  The schema describes valid structural shapes for the formalism's
  relevant state.

- `Monitor` — a function `EventLogEntry → Verdict`. The monitor
  consumes a single trace entry and returns `:ok` or `:violation`.
  A violation carries a structured detail payload (a Gap with layer
  `:object`). Monitors are pure — the same entry always produces the
  same verdict — and independent across formalisms.

- `Generator` — a test.check generator. The generator's output type
  depends on the source formalism:

  | Formalism | Generator output type |
  |---|---|
  | Statechart | `List(Keyword)` — valid event sequences (random walks) |
  | EffectSignature | `Map(Keyword, Any)` — well-typed effect descriptions |
  | MealyDeclaration | `(Map × Keyword)` — `(db, event)` pairs satisfying guards |
  | OpticDeclaration | `Map(Keyword, Any)` — state maps where the optic resolves |
  | ResolverGraph | `𝒫(Keyword)` — sets of input attributes |
  | CapabilitySet | `Keyword` — events within the capability bound |

  The generator is the bridge between specification and
  implementation: the formalism generates valid inputs, the
  implementation runs them, the monitor judges the output.

- `GapTypeDesc` — a data map describing the taxonomy of conformance
  failures for this formalism. Structure:

  ```
  GapTypeDesc = {
    formalism : Keyword,
    gap-kinds : 𝒫(GapKind),
    statuses  : { :conforms, :absent, :diverges }
  }
  ```

  Where `GapKind` is formalism-specific (e.g. `:missing-state`,
  `:unreachable-state` for statecharts; `:missing-field`,
  `:wrong-field-type` for effect signatures).

- `LeanSource` — a string of Lean 4 source code. Contains three
  components: type definitions (inductive types, structures mirroring
  the Clojure data), property statements (theorem declarations with
  `sorry` placeholders), and proof scaffolding (helper lemmas,
  `Fintype`/`DecidableEq` instances). The output is self-contained —
  it imports only Mathlib, not a Pneuma-specific Lean library.

  | Formalism | Lean emission |
  |---|---|
  | Statechart | Inductive `State`, `Config`, `step` function, reachability, safety theorems |
  | EffectSignature | Inductive `Effect` type, field structures per operation |
  | MealyDeclaration | Handler contract as function with pre/postconditions, replay determinism |
  | OpticDeclaration | Path resolution types (deferred — not on critical path) |
  | ResolverGraph | Attribute reachability types (deferred — not on critical path) |
  | CapabilitySet | Set membership bounds as propositions |

  The `->lean` projection on `IConnection` emits boundary propositions
  and composition theorems for morphism pairs. Cycle-level `->lean`
  emits the strongest theorems: cycle closure, precondition chaining,
  and callback re-entry safety.

The five variants are the five methods of `IProjectable`. Each
formalism produces all five.

#### 4.9 MorphismKind as Coproduct

```
MorphismKind = Existential ⊔ Structural ⊔ Containment ⊔ Ordering
```

Each kind carries different checking semantics:

- **Existential** — set membership: identifier in A must exist in B.
  Gap type: `dangling-ref`.
- **Structural** — schema validation: output of A must conform to
  input schema of B. Gap type: `shape-mismatch`.
- **Containment** — subset: declared set in A must be ⊆ defined set
  in B. Gap type: `out-of-bounds`.
- **Ordering** — index comparison: A's interceptor must precede B's.
  Gap type: `order-violation`.

#### 4.10 ComposedPath as FreeMonoid with cycle closure

```
ComposedPath = {
  id        : Keyword
  steps     : List(Morphism)     -- FreeMonoid(Morphism)
  invariant : CycleInvariant
}
```

The `steps` field is a FreeMonoid — an ordered sequence of morphisms
that compose along the formalism graph. The cycle closure property
(Axiom A5) requires `targetFormalism(last(steps)) =
sourceFormalism(first(steps))`.

Three known cycles in the architecture:

| Cycle | Path | Invariant |
|---|---|---|
| Event-effect-callback | Chart → Mealy → Effects → Chart | Callback re-enters chart in a state that accepts it |
| Observe-dispatch-update | Optics → Caps → Mealy → Optics | Cycle converges; paths are coherent |
| Full dispatch | Caps → Chart → Mealy → Validator → Optics → Effects → Chart | Each step's postcondition implies next step's precondition |

#### 4.11 Gap as Sigma (status-dependent detail)

```
Gap = Σ(status : GapStatus). Detail(status)
```

- `Detail(:conforms) = {}` — no detail needed.
- `Detail(:absent)   = { reason : String }` — why the spec component
  has no implementation.
- `Detail(:diverges) = Σ(gapKind : GapKind). Payload(gapKind)` — a
  further dependent structure carrying the specific divergence
  information. The `Payload` depends on both the GapStatus and the
  formalism-specific gap kind.

This double Sigma captures the design doc's emphasis: diverges carries
not just "wrong" but *how* it's wrong — missing fields, dangling
references, broken re-entry states, etc.

#### 4.12 GapReport as Product of three PowerSet layers

```
GapReport = {
  objectGaps   : 𝒫(Gap)     -- per-formalism gaps
  morphismGaps : 𝒫(Gap)     -- per-connection gaps
  pathGaps     : 𝒫(Gap)     -- per-cycle gaps
}
```

A product of three PowerSets, one per layer. The layers are read
bottom-up for diagnosis (object → morphism → path tells what → where →
why) and computed top-down for efficiency (object gaps are cheapest,
path gaps most expensive).

#### 4.13 RefinementMap as dependent record

```
RefinementMap = {
  atomRef       : Ref(Path)           -- pointer to state atom
  eventLogRef   : Ref(Path)           -- pointer to event log
  stateAccessors : Map(Keyword, Fn)   -- per-formalism state readers
  sourceNss     : 𝒫(Symbol)          -- implementation namespaces
}
```

The refinement map uses Ref for the atom and event log pointers — these
are mutable references into the running system. The `stateAccessors`
map is a dependent structure: each accessor's type depends on which
formalism it reads from.

#### 4.14 EventLogEntry as dependent record

```
EventLogEntry = {
  event     : Keyword
  dbBefore  : Map(Keyword, Any)
  dbAfter   : Map(Keyword, Any)
  effects   : List(EffectEmission)
  timestamp : Instant
}
```

The `effects` field is a FreeMonoid — emitted effects are ordered.

#### 4.15 The formalism graph as Presheaf

The morphism registry is a typed directed graph — a Presheaf over a
small category whose objects are the six formalisms and whose arrows
are the morphism kinds:

```
FormalismGraph = Presheaf(FormalismCategory)
```

Where `FormalismCategory` has:

- Objects: `{ Statechart, EffectSignature, MealyDeclaration,
  OpticDeclaration, ResolverGraph, CapabilitySet }`
- Arrows: the morphism kinds between pairs (existential, structural,
  containment, ordering)

The presheaf assigns to each formalism pair the set of morphisms
between them. The connection registry (§4.1.2 of the design doc) is an
instance of this presheaf.

This is the structure that makes cycle enumeration and composition
checking possible — it's the data structure you implement. The
morphism graph is a Clojure map. The composition table is computed from
it. The gap report is the failure set of the categorical laws.

### 5. Axioms

#### 5.1 Formalism axioms

**A1 — Projection completeness:**
```
∀ f : Formalism,
  ∃ s : Schema, m : Monitor, g : Generator, d : GapTypeDesc, l : LeanSource
  such that →schema(f) = s ∧ →monitor(f) = m ∧ →gen(f) = g
        ∧ →gap-type(f) = d ∧ →lean(f) = l
```
Every formalism projects to all five checking artifacts.

**A2 — Formalism exhaustiveness:**
```
∀ f : Formalism,
  f ∈ Statechart ⊔ EffectSignature ⊔ MealyDeclaration
    ⊔ OpticDeclaration ⊔ ResolverGraph ⊔ CapabilitySet
```
The coproduct is exhaustive — no seventh formalism kind.

#### 5.2 Statechart axioms

**A3 — Reachability:**
```
∀ sc : Statechart, ∀ s ∈ states(sc),
  Reachable(s, initial(sc), transitions(sc))
```
Every declared state is reachable from the initial configuration via
some sequence of transitions. Unreachable states are object-level gaps.

**A4 — Configuration validity:**
```
∀ sc : Statechart, ∀ c ∈ ReachableConfigs(sc),
  |c ∩ region(r)| = 1   for each region r
```
A valid configuration has exactly one active state per region. Parallel
regions are independent — a transition in one region does not affect
the active state in another.

**A5 — Transition determinism:**
```
∀ sc : Statechart, ∀ c : Configuration, ∀ e : Keyword,
  |{ t ∈ transitions(sc) | source(t) ∈ c ∧ event(t) = e }| ≤ 1
```
At most one transition fires for a given configuration and event.
(Non-determinism would break replay.)

#### 5.3 Effect signature axioms

**A6 — Callback closure:**
```
∀ sig : EffectSignature, ∀ op ∈ operations(sig),
  ∀ cb ∈ callbackFields(op),
    cb is a valid EventRef
```
Every callback field in every operation must be a well-formed event
reference. What "valid" means is checked by the existential morphism
to the Mealy layer.

#### 5.4 Morphism axioms

**A7 — Morphism well-typedness:**
```
∀ m : Morphism,
  sourceFormalism(m) ∈ Formalism ∧ targetFormalism(m) ∈ Formalism
```
Source and target of every morphism are defined formalisms.

**A8 — Morphism composability:**
```
∀ m₁ m₂ : Morphism,
  targetFormalism(m₁) = sourceFormalism(m₂)
  ⟹ ∃ m₃ : Morphism, m₃ = m₂ ∘ m₁
     ∧ sourceFormalism(m₃) = sourceFormalism(m₁)
     ∧ targetFormalism(m₃) = targetFormalism(m₂)
```
Morphisms compose when the target of one equals the source of the
next. This is the categorical structure — formalisms are objects,
morphisms are arrows, and composition is well-typed.

**A9 — Existential reference resolution:**
```
∀ m : Morphism, morphismKind(m) = :existential,
  ∀ ref ∈ references(sourceFormalism(m)),
    ref ∈ identifiers(targetFormalism(m))
```
Every identifier referenced by the source formalism exists in the
target formalism's namespace.

**A10 — Structural match conformance:**
```
∀ m : Morphism, morphismKind(m) = :structural,
  ∀ output ∈ outputs(sourceFormalism(m)),
    validates?(→schema(targetFormalism(m)), output)
```
Output of the source formalism conforms to the input schema of the
target.

**A11 — Containment subset:**
```
∀ m : Morphism, morphismKind(m) = :containment,
  declared(sourceFormalism(m)) ⊆ defined(targetFormalism(m))
```
The declared set in the source is a subset of the defined set in the
target.

**A12 — Ordering precedence:**
```
∀ m : Morphism, morphismKind(m) = :ordering,
  interceptorIndex(sourceFormalism(m)) < interceptorIndex(targetFormalism(m))
```
The source formalism's interceptor appears before the target's in the
chain.

#### 5.5 ComposedPath axioms

**A13 — Cycle closure:**
```
∀ p : ComposedPath,
  targetFormalism(last(steps(p))) = sourceFormalism(first(steps(p)))
```
A composed path returns to its starting formalism.

**A14 — Precondition chaining:**
```
∀ p : ComposedPath, ∀ i ∈ 1..len(steps(p))-1,
  postcondition(steps(p)[i]) ⟹ precondition(steps(p)[i+1])
```
Each step's postcondition implies the next step's precondition. This
is the strongest invariant — it's what makes the interceptor chain
correct, not just ordered.

**A15 — Event-effect-callback re-entry:**
```
∀ p : ComposedPath, id(p) = :event-effect-callback,
  ∀ (t, cb) ∈ raiseCallbackPairs(p),
    accepts?(chart, targetStateAfter(t), cb)
```
When an effect callback re-enters the chart, the chart must be in a
state that accepts it. The chart must be in the state the system will
be in *after the effect completes*, not the state it was in when the
effect was emitted.

#### 5.6 Gap report axioms

**A16 — Gap status trichotomy:**
```
∀ g : Gap,
  status(g) ∈ { :conforms, :absent, :diverges }
  ∧ exactly one holds
```

**A17 — Layer stratification:**
```
∀ g : Gap,
  layer(g) = :object   ⟹ g references exactly one formalism
  layer(g) = :morphism  ⟹ g references exactly one morphism (two formalisms)
  layer(g) = :path      ⟹ g references exactly one ComposedPath (≥2 formalisms)
```
Gaps at each layer reference exactly the right level of structure.

**A18 — Diagnostic ordering:**
```
∀ r : GapReport,
  resolving objectGaps(r) may resolve morphismGaps(r)
  resolving morphismGaps(r) may resolve pathGaps(r)
  ¬(resolving pathGaps(r) resolves objectGaps(r))
```
Fixing flows top-down: object → morphism → path. Object-level fixes
may cascade to resolve morphism and path gaps. Path-level fixes never
resolve object gaps.

#### 5.7 RefinementMap axioms

**A19 — Refinement totality:**
```
∀ rm : RefinementMap, ∀ f : Formalism,
  ∃ accessor ∈ stateAccessors(rm) for f
```
Every formalism concept maps to a concrete implementation path.

**A20 — Refinement coherence:**
```
∀ rm : RefinementMap,
  deref(atomRef(rm)) is a valid Clojure map
  ∧ deref(eventLogRef(rm)) is a valid sequence of EventLogEntry
```
The refinement map points to live, well-formed runtime state.

#### 5.8 Projection contract axioms

These axioms formalize the semantic contracts of the IProjectable
protocol methods — what the return values must satisfy, and how the
four projections relate to each other and to the gap report.

**A21 — Schema well-formedness:**
```
∀ f : Formalism,
  m/validate(→schema(f), x) ∈ Bool   for all x
  ∧ m/schema?(→schema(f)) = true
```
`→schema` always returns a valid Malli schema. The schema is usable
with `m/validate`, `m/explain`, and `mg/generate` without error.

**A22 — Monitor purity:**
```
∀ f : Formalism, ∀ e : EventLogEntry,
  →monitor(f)(e) ∈ Verdict
  ∧ →monitor(f)(e) = →monitor(f)(e)     (referential transparency)
```
`→monitor` returns a function from EventLogEntry to Verdict. The
function is pure — the same entry always produces the same verdict.
Monitors carry no mutable state.

**A23 — Monitor–schema coherence:**
```
∀ f : Formalism, ∀ e : EventLogEntry,
  ¬m/validate(→schema(f), dbAfter(e))
  ⟹ →monitor(f)(e) = :violation
```
If the state after an event violates the formalism's schema, the
monitor must report a violation. The monitor is at least as strict
as the schema — it catches everything the schema catches (structural
violations) plus behavioral violations the schema cannot see (e.g.
invalid state transitions).

**A24 — Generator–schema consistency:**
```
∀ f : Formalism, ∀ x ∈ sample(→gen(f)),
  m/validate(→schema(f), x) = true
```
Every value produced by the generator conforms to the schema. The
generator only produces valid inputs — it is the schema's
constructive witness.

**A25 — Generator–monitor integration:**
```
∀ f : Formalism,
  ∀ trace ∈ traces(→gen(f), dispatch!),
    ∀ e ∈ trace,
      →monitor(f)(e) = :ok
  ∨ the trace reveals a conformance gap
```
When the generator produces inputs, the implementation runs them via
`dispatch!`, and the monitor judges each resulting event log entry,
the outcome is either all-ok (implementation conforms) or a
discovered gap. This is the generative testing contract: model
generates, implementation runs, monitor judges.

**A26 — Gap-type descriptor completeness:**
```
∀ f : Formalism,
  ∀ g : Gap where sourceFormalism(g) = f,
    gapKind(g) ∈ gap-kinds(→gap-type(f))
```
Every gap produced by checking formalism `f` has a gap-kind that
appears in `f`'s gap-type descriptor. The descriptor is an exhaustive
taxonomy of failure modes for the formalism.

**A27 — Projection determinism:**
```
∀ f : Formalism,
  →schema(f) = →schema(f)
  ∧ →monitor(f) = →monitor(f)
  ∧ →gen(f) = →gen(f)
  ∧ →gap-type(f) = →gap-type(f)
  ∧ →lean(f) = →lean(f)
```
All five projections are deterministic. The same formalism always
produces the same schema, monitor, generator, gap-type descriptor,
and Lean source. Projections are derived values, not stateful
computations.

**A28 — Lean well-formedness:**
```
∀ f : Formalism,
  →lean(f) is a syntactically valid Lean 4 source fragment
  ∧ the emitted type definitions are a faithful structural translation
    of the Clojure data (injective, no information loss)
```
`→lean` produces a string of Lean 4 source code containing type
definitions, property statements (with `sorry` placeholders), and
proof scaffolding. The translation is mechanical and injective — the
same formalism always produces the same Lean output (determinism
follows from A27's pattern). The emitted code is self-contained,
importing only Mathlib.

**A29 — IConnection contract:**
```
∀ conn : Morphism, ∀ src tgt : Formalism, ∀ rm : RefinementMap,
  check(conn, src, tgt, rm) ⊆ { g : Gap | layer(g) = :morphism }
  ∧ ∀ g ∈ check(conn, src, tgt, rm),
      status(g) ∈ GapStatus
```
`check` returns a sequence of morphism-layer gaps. Every returned
gap has a valid status. The check is total — it returns an empty
sequence (not an error) when the boundary contract holds.

**A30 — IConnection Lean emission:**
```
∀ conn : Morphism, ∀ src tgt : Formalism,
  →lean(conn, src, tgt) is a syntactically valid Lean 4 source fragment
  containing a boundary proposition and (optionally) a composition theorem
```
The `->lean` method on `IConnection` emits Lean propositions about
morphism pairs — `∀ ... ∈ ...` statements for existential and
containment morphisms, type-compatibility propositions for structural
morphisms, and index comparisons for ordering morphisms.

### 6. Morphisms

#### 6.1 Projection functors

```
→schema   : Formalism → Schema
→monitor  : Formalism → Monitor
→gen      : Formalism → Generator
→gap-type : Formalism → GapTypeDesc
→lean     : Formalism → LeanSource
```

Five functors from the coproduct `Formalism` to the coproduct
`Projection`. These are the `IProjectable` protocol methods. Each
preserves the identity of the formalism — the projection is
*determined by* the formalism. These are natural in the sense that
adding a new formalism (extending the coproduct) requires only
implementing the five methods; existing formalisms and their
projections are unchanged.

The first four projections produce runtime checking artifacts
(sampled). The fifth (`→lean`) produces Lean 4 source code for
kernel-verified proofs (universal). See
[pneuma-lean4-extension.md](pneuma-lean4-extension.md) for the
translation rules and proof targets.

**Preserves:** Coproduct structure (case analysis).

#### 6.2 Morphism composition

```
compose : Morphism × Morphism →? Morphism
  where targetFormalism(m₁) = sourceFormalism(m₂)
```

Partial — defined only when the morphisms are composable (target of
first = source of second). This is literal categorical composition.
The resulting morphism's checking semantics combine the checking
semantics of its components.

**Preserves:** FormalismGraph presheaf structure. Composition in the
morphism graph corresponds to path traversal in the formalism category.

#### 6.3 Morphism checking (IConnection)

```
check : Morphism × Formalism × Formalism → Gap
```

The `IConnection` protocol. Given a morphism and its source/target
formalisms, produces a Gap. The checking semantics depend on the
MorphismKind:

| Kind | Check | Gap type |
|---|---|---|
| Existential | Set membership | `dangling-ref` |
| Structural | Schema validation | `shape-mismatch` |
| Containment | Set difference | `out-of-bounds` |
| Ordering | Index comparison | `order-violation` |

**Preserves:** MorphismKind coproduct structure (case analysis by kind).

#### 6.4 Gap report aggregation

```
aggregate : 𝒫(Gap) × 𝒫(Gap) × 𝒫(Gap) → GapReport
```

Coproduct injection of the three layers into the product structure of
GapReport. Each input set corresponds to one layer (object, morphism,
path).

**Preserves:** Product structure. The three layers are independent —
aggregation is a pure product construction.

#### 6.5 Gap report diffing

```
diff : GapReport × GapReport → GapReport
```

An endomorphism on GapReport. Given two reports (e.g. yesterday's and
today's), produces a delta report showing which gaps were introduced,
resolved, or changed status. This is a set-theoretic operation on each
layer independently.

**Preserves:** Product structure (operates per-layer), PowerSet
structure (set difference/symmetric difference).

#### 6.6 Monitor replay

```
replay : Monitor × List(EventLogEntry) → 𝒫(Gap)
```

Applies a monitor (projected from a formalism) to a FreeMonoid of
event log entries. The monitor consumes entries in order, producing
gaps for each entry that violates the formalism's behavioral
invariants. This is a FreeMonoid homomorphism — it maps concatenation
of logs to union of gaps.

**Preserves:** FreeMonoid structure (replay of `log₁ ++ log₂` =
replay of `log₁` ∪ replay of `log₂`, modulo stateful monitors that
track configuration).

#### 6.7 Generator-based checking

```
genCheck : Generator × Fn → 𝒫(Gap)
```

Applies a generator (projected from a formalism) to the actual
dispatch function, producing gaps for property violations. The
statechart generator performs random walks over reachable states; the
Mealy generator produces valid `(db, event)` pairs; the effect
generator produces well-typed effect descriptions.

**Preserves:** Generator structure. The generated test cases are
*valid according to the formalism* — the model generates, the
implementation runs, the monitor judges.

#### 6.8 Refinement interpretation

```
refine : RefinementMap × Formalism → ConcreteChecker
```

Applies the refinement map to a formalism, producing a concrete
checker bound to the running system. The refinement map is the functor
from abstract mathematical structure to concrete Clojure state — it
explains how to read the formalism's concepts out of the atom and event
log.

**Preserves:** Ref (pointer validity). The `atomRef` and
`eventLogRef` must dereference to live state.

#### 6.9 Connection registry enumeration

```
enumerate : 𝒫(Formalism) → 𝒫(Morphism)
```

Given the set of formalisms, enumerates every pair and determines
which morphism kinds apply by inspecting each formalism's interface.
This is a fixed-point computation over the FormalismGraph presheaf's
adjacency structure: adding a new formalism automatically generates
new connections.

**Preserves:** Presheaf structure. The enumeration is a natural
transformation from the presheaf of formalism interfaces to the
presheaf of morphisms.

#### 6.10 Cycle detection

```
findCycles : FormalismGraph → 𝒫(ComposedPath)
```

Tarjan's or Johnson's algorithm over the morphism graph. Each
detected cycle becomes a ComposedPath with its associated invariant.
The three known cycles (event-effect-callback, observe-dispatch-update,
full dispatch) are discoverable from the graph structure.

**Preserves:** FormalismGraph presheaf structure. Cycles are a
property of the graph's categorical structure.

### 7. 2-Cells

#### 7.1 Parallel projection strategies

For a given formalism, the five projections (→schema, →monitor, →gen,
→gap-type, →lean) are parallel morphisms from `Formalism` to `Projection`
(different projection kinds). These are not 2-Cells in the strict
sense — they have different codomains within the `Projection`
coproduct.

However, within a single projection kind, there may be alternative
strategies. For example, a statechart's `→gen` could use:

- Random walk generation (uniform over transitions)
- Coverage-guided generation (targeting uncovered states)
- Boundary generation (focusing on guard edge cases)

These are genuine 2-Cells:

```
α : randomWalkGen ⟹ coverageGen   : Statechart → Generator
β : coverageGen   ⟹ boundaryGen   : Statechart → Generator
```

#### 7.2 Parallel cycle-checking strategies

For the event-effect-callback cycle, there are parallel checking
approaches:

- Exhaustive enumeration of all raise-callback pairs
- Symbolic reachability analysis over the configuration space
- Generative testing with random walks

```
γ : exhaustiveCheck ⟹ symbolicCheck : ComposedPath → 𝒫(Gap)
```

These relate different strategies for the same structural
transformation (cycle → gaps).

#### 7.3 Assessment

The 2-Cell structure is thin. The primary domain is a 1-category with
formalisms as objects, morphisms as arrows, and composition as path
traversal. The 2-Cells that exist capture *alternative implementation
strategies* for the same structural operation, not fundamental domain
structure.

---

## Part III — Appendices

### Appendix A: Construction Summary

| # | Construction | Applied to | Structural question |
|---|---|---|---|
| 4.1 | Coproduct | Formalism, MorphismKind, Projection, GapStatus | Choice between alternatives |
| 4.2 | Product + PowerSet + Forest | Statechart | Multi-component composite |
| 4.3 | PowerSet + Sigma | EffectSignature, Operation | Set of tag-dependent records |
| 4.4 | FreeMonoid | MealyDeclaration (guards, updates, effects) | Ordered sequences |
| 4.5 | Sigma | OpticDeclaration, Gap | Tag determines structure |
| 4.6 | PowerSet (hypergraph) | ResolverGraph | Set of hyperedges |
| 4.7 | Product of PowerSets | CapabilitySet | Independent bounded sets |
| 4.10 | FreeMonoid + cycle | ComposedPath | Ordered composable sequence |
| 4.12 | Product | GapReport | Independent layers |
| 4.13 | Ref | RefinementMap | Mutable pointers to runtime |
| 4.15 | Presheaf | FormalismGraph | Schema-indexed graph structure |

### Appendix B: Axiom Summary

| # | Name | Pattern | Layer |
|---|---|---|---|
| A1 | Projection completeness | Totality | Object |
| A2 | Formalism exhaustiveness | Coverage | Object |
| A3 | Reachability | Coverage | Object |
| A4 | Configuration validity | Membership | Object |
| A5 | Transition determinism | Uniqueness | Object |
| A6 | Callback closure | Totality | Object |
| A7 | Morphism well-typedness | Totality | Morphism |
| A8 | Morphism composability | Commutativity | Morphism |
| A9 | Existential ref resolution | Membership | Morphism |
| A10 | Structural match | SubsetOf | Morphism |
| A11 | Containment subset | SubsetOf | Morphism |
| A12 | Ordering precedence | LessThan | Morphism |
| A13 | Cycle closure | Equation | Path |
| A14 | Precondition chaining | Implication | Path |
| A15 | Callback re-entry | Membership | Path |
| A16 | Gap status trichotomy | Coverage | Report |
| A17 | Layer stratification | Totality | Report |
| A18 | Diagnostic ordering | Implication | Report |
| A19 | Refinement totality | Totality | Infrastructure |
| A20 | Refinement coherence | Totality | Infrastructure |
| A21 | Schema well-formedness | Totality | Projection |
| A22 | Monitor purity | Equation | Projection |
| A23 | Monitor–schema coherence | Implication | Projection |
| A24 | Generator–schema consistency | Membership | Projection |
| A25 | Generator–monitor integration | Implication | Projection |
| A26 | Gap-type descriptor completeness | SubsetOf | Projection |
| A27 | Projection determinism | Equation | Projection |
| A28 | Lean well-formedness | Totality | Projection |
| A29 | IConnection contract | SubsetOf | Projection |
| A30 | IConnection Lean emission | Totality | Projection |

### Appendix C: Morphism Catalogue

| # | Morphism | Source | Target | Preserves |
|---|---|---|---|---|
| 6.1 | →schema | Formalism | Schema | Coproduct |
| 6.1 | →monitor | Formalism | Monitor | Coproduct |
| 6.1 | →gen | Formalism | Generator | Coproduct |
| 6.1 | →gap-type | Formalism | GapTypeDesc | Coproduct |
| 6.1 | →lean | Formalism | LeanSource | Coproduct |
| 6.2 | compose | Morphism × Morphism | Morphism | Presheaf |
| 6.3 | check | Morphism × F × F | Gap | Coproduct |
| 6.4 | aggregate | 𝒫(Gap)³ | GapReport | Product |
| 6.5 | diff | GapReport² | GapReport | Product + PowerSet |
| 6.6 | replay | Monitor × List(ELE) | 𝒫(Gap) | FreeMonoid |
| 6.7 | genCheck | Generator × Fn | 𝒫(Gap) | Generator |
| 6.8 | refine | RefinementMap × F | Checker | Ref |
| 6.9 | enumerate | 𝒫(Formalism) | 𝒫(Morphism) | Presheaf |
| 6.10 | findCycles | FormalismGraph | 𝒫(ComposedPath) | Presheaf |

### Appendix D: Cross-Reference Inventory

The complete inventory of cross-formalism references — every arrow
that crosses a formalism boundary and is therefore checked by a
Morphism.

| Source | Target | Reference type | Morphism kind |
|---|---|---|---|
| MealyDeclaration (guards) | Statechart (states) | `in-state?` checks | Existential |
| EffectSignature (callbacks) | MealyDeclaration (transitions) | `on-complete`, `on-error` | Existential |
| Statechart (raise clauses) | MealyDeclaration (handled events) | Raised events need handlers | Existential |
| CapabilitySet (dispatch) | MealyDeclaration (ids) | Dispatchable events | Containment |
| CapabilitySet (subscribe) | OpticDeclaration (ids) | Observable subscriptions | Containment |
| CapabilitySet (query) | ResolverGraph (attributes) | Queryable attributes | Containment |
| MealyDeclaration (emissions) | EffectSignature (operations) | Emitted effect shapes | Structural |
| Statechart (raised events) | MealyDeclaration (params) | Raised event parameter shapes | Structural |
| MealyDeclaration (updates) | MealyDeclaration (output schema) | State after update | Structural |
| MealyDeclaration (update paths) | OpticDeclaration (paths) | Handler writes ⊆ optic reads | Containment |
| OpticDeclaration (sources) | ResolverGraph (attributes) | Derived sub source attrs | Existential |
| Interceptor chain | All formalisms | Ordering positions | Ordering |
