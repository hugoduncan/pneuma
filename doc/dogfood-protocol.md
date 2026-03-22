# Dogfooding: Protocol Layer

**How Pneuma describes and checks its own protocol layer using its own
formalism types.**

This document defines the formalism instances that will describe
`pneuma.protocol` — the three protocols (IProjectable, IConnection,
IReferenceable), their methods, and the structural and semantic
contracts on implementations. Once the formalism types are
implemented, these instances become the first real test of the gap
report machinery.

---

## 1. Why the Protocol Layer

The protocol layer is the simplest piece of Pneuma with real
structural invariants worth checking:

- Three protocols, eight methods total.
- Ten records must implement specific protocols (A5, A6 from the
  Option A formalism).
- Method implementations must satisfy return-type contracts (A21–A28
  from the domain model).
- Protocol dispatch must be exhaustive and deterministic.

It's small enough to be the first dogfood target, but structurally
rich enough to exercise the core checking machinery.

## 2. Which Formalism Types Are Needed

The protocol layer maps onto three of the six planned formalisms:

| Protocol concern | Formalism type | What it captures |
|---|---|---|
| Protocols declare typed operations | **EffectSignature** | Each protocol method is an operation with typed input/output |
| Records must implement protocols | **CapabilitySet** | Each record's required protocol methods form a bounded set |
| Return values satisfy contracts | **Morphisms** (structural match) | Output of each method conforms to an expected schema |

The remaining three formalisms (Statechart, MealyDeclaration,
OpticDeclaration) and the ResolverGraph are not needed for the
protocol layer — there's no state machine, no event handling, no
subscriptions, and no query resolution.

## 3. Formalism Instances

### 3.1 EffectSignature: protocol operations

The three protocols are modeled as a single effect signature. Each
protocol method becomes an operation with typed fields.

```clojure
(def protocol-operations
  (p/effect-signature
    {:operations
     {;; IProjectable — four projection methods
      :->schema
      {:input  {:formalism :Formalism}
       :output :MalliSchema}

      :->monitor
      {:input  {:formalism :Formalism}
       :output :MonitorFn}

      :->gen
      {:input  {:formalism :Formalism}
       :output :TestCheckGenerator}

      :->gap-type
      {:input  {:formalism :Formalism}
       :output :GapTypeDesc}

      :->lean
      {:input  {:formalism :Formalism}
       :output :LeanSource}

      ;; IConnection — two methods
      :check
      {:input  {:morphism :Morphism
                :source   :Formalism
                :target   :Formalism
                :rm       :RefinementMap}
       :output :GapSequence}

      :->lean-conn
      {:input  {:morphism :Morphism
                :source   :Formalism
                :target   :Formalism}
       :output :LeanSource}

      ;; IReferenceable — one extraction method
      :extract-refs
      {:input  {:formalism :Formalism
                :ref-kind  :Keyword}
       :output :KeywordSet}}}))
```

The EffectSignature captures the *vocabulary* of operations the
protocol layer defines. The `->schema` projection of this formalism
produces a Malli schema validating that any call to these operations
has the right field shapes. The `->gap-type` projection enumerates
failure modes: `:missing-method`, `:wrong-return-type`,
`:dispatch-failure`.

### 3.2 CapabilitySets: record implementation requirements

Each record type is described by a capability set — the set of
protocol operations it must implement.

```clojure
(def formalism-record-caps
  (p/capability-set
    {:id :formalism-record
     :dispatch #{:->schema :->monitor :->gen :->gap-type :->lean
                 :extract-refs}}))

(def morphism-record-caps
  (p/capability-set
    {:id :morphism-record
     :dispatch #{:check :->lean-conn}}))
```

These are two capability profiles. Every record in the `formalism`
layer must satisfy `formalism-record-caps`. Every record in the
`morphism` layer must satisfy `morphism-record-caps`.

The checking contract is **containment**: `dispatch(caps) ⊆
operations(effect-sig)`. Every operation named in a capability set
must exist in the effect signature. This catches: a capability set
naming a method that doesn't exist (misspelling, stale reference
after a protocol rename).

### 3.3 Morphisms between them

Two morphisms connect these formalisms:

**Existential: capability → effect-signature**

```clojure
{:id   :caps->protocol/operations
 :kind :existential
 :from :caps
 :to   :protocol-ops
 :source-ref-kind :dispatch-refs
 :target-ref-kind :operation-ids}
```

Checks that every operation named in a capability set exists in the
effect signature. This is the formalization of "every protocol method
a record claims to implement actually exists in a protocol."

**Structural: effect-signature → return-type schemas**

```clojure
{:id   :protocol-ops->return-types
 :kind :structural
 :from :protocol-ops
 :to   :return-type-schema
 :source-ref-kind :operation-outputs
 :target-ref-kind :expected-schemas}
```

Checks that the declared output type of each operation matches the
expected schema. This is where axioms A21–A28 become checkable: the
`->schema` operation must return a valid Malli schema, the
`->monitor` operation must return a function from EventLogEntry to
Verdict, etc.

## 4. What the Gap Report Shows

When we run `(p/gap-report ...)` over these formalism instances
against the actual `pneuma.protocol` implementation, the three-layer
report tells us:

**Object gaps** (per-formalism):
- EffectSignature: are all eight operations defined? Do they have the
  right field shapes? → catches missing or malformed protocol
  methods.
- CapabilitySets: are the declared operation sets non-empty and
  well-formed? → catches empty or invalid capability profiles.

**Morphism gaps** (per-connection):
- Existential: does every operation in the capability sets exist in
  the effect signature? → catches misspelled method names, stale
  references.
- Structural: do the operation outputs match the expected schemas? →
  catches methods returning the wrong type.

**Path gaps**: none — no cycles in this two-formalism graph.

## 5. Build Order Implications

To dogfood on the protocol layer, we need to implement the following
in order:

```
1. pneuma.protocol             ✓ done
2. pneuma.formalism.effect-signature
3. pneuma.formalism.capability
4. pneuma.morphism.existential
5. pneuma.morphism.structural
6. pneuma.morphism.registry    (with the two entries from §3.3)
7. pneuma.gap.core             (enough to assemble a two-layer report)
```

After step 7, we can run `gap-report` on pneuma.protocol itself.
This is the earliest possible dogfood — two formalisms, two
morphisms, no composed paths needed.

The statechart formalism (originally planned as first) can be built
in parallel or after this sequence. The dogfooding path prioritizes
EffectSignature and CapabilitySet because they're the simplest
formalisms structurally and they let us eat our own cooking
immediately.

### 5.1 Revised phase 1 build order

Original (from system-architecture-prose.md §7):
```
Statechart → EffectSignature → Mealy → Optics → Resolvers → Capabilities
```

Revised for dogfooding:
```
EffectSignature → CapabilitySet → (dogfood checkpoint) →
Statechart → Mealy → Optics → Resolvers
```

The dogfood checkpoint is the first time we run pneuma on itself.
Everything after it benefits from the confidence that the checking
machinery works on a real (if simple) target.

## 6. What This Doesn't Cover

The protocol layer has no:
- **State machine** — no lifecycle, no transitions. Statechart not
  needed.
- **Event handling** — no events, no handlers. MealyDeclaration not
  needed.
- **Subscriptions** — no reactive paths. OpticDeclaration not needed.
- **Query resolution** — no attribute dependencies. ResolverGraph not
  needed.
- **Composed paths** — no cycles in the formalism graph. Path layer
  not needed.
- **Lean proof targets** — the protocol layer's invariants are
  structural (protocol coverage, return types) rather than
  behavioral. They are fully checkable at runtime by Malli schemas
  and existential morphisms. The `->lean` projection is not needed
  for this dogfood target — Lean proofs become valuable at the
  statechart/cycle level where universal quantification over
  reachable states and event sequences matters.

These all arise when checking a real event-sourced application.
The protocol dogfood exercises the *object* and *morphism* layers
of the gap report but not the *path* layer. Path-layer dogfooding
will require a more complex target (probably Pneuma's own morphism
registry, which does have a graph structure with potential cycles).
