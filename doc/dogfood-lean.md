# Dogfooding: Lean Projection Layer

**How Pneuma describes and checks its own lean projection layer using
its own formalism types.**

This document defines the formalism instances that will describe
`pneuma.lean.protocol` — the two lean protocols (ILeanProjectable,
ILeanConnection), their methods, and the structural contracts on
implementations. This is the lean layer's equivalent of
[dogfood-protocol.md](dogfood-protocol.md).

---

## 1. Why the Lean Layer

The lean layer has checkable structural invariants:

- Two protocols, two methods total.
- Six formalism records must implement `ILeanProjectable` (via
  `extend-protocol`).
- Four morphism records must implement `ILeanConnection` (via
  `extend-protocol`).
- Each `->lean` implementation must produce a non-empty string of
  syntactically valid Lean 4 source.
- The lean operations depend on core formalism data — the same
  records projected by `->schema`, `->monitor`, etc.

It exercises the same checking machinery as the protocol dogfood
(object and morphism layers) but on a structurally distinct target:
a layer that extends existing records via `extend-protocol` rather
than defining new ones.

## 2. Which Formalism Types Are Needed

The lean layer maps onto three of the formalism types plus a type
schema:

| Lean concern | Formalism type | What it captures |
|---|---|---|
| Lean protocols declare operations | **EffectSignature** | `->lean` and `->lean-conn` as operations with typed I/O |
| Records must implement lean protocols | **CapabilitySet** | Each record type's required lean operations |
| Output types are well-defined | **TypeSchema** | `LeanSource` maps to `:string` |
| Lean operations exist and have valid output types | **Morphisms** | Existential and structural morphisms |

The remaining formalisms (Statechart, MealyDeclaration,
OpticDeclaration, ResolverGraph) are not needed — there is no state
machine, no event handling, no subscriptions, and no query resolution
in the lean layer.

## 3. Formalism Instances

### 3.1 EffectSignature: lean operations

The two lean protocols are modeled as a single effect signature.
Each protocol method becomes an operation with typed fields.

```clojure
(def lean-operations
  (p/effect-signature
    {:operations
     {;; ILeanProjectable — one emission method
      :->lean
      {:input  {:formalism :Formalism}
       :output :LeanSource}

      ;; ILeanConnection — one emission method
      :->lean-conn
      {:input  {:morphism :Morphism
                :source   :Formalism
                :target   :Formalism}
       :output :LeanSource}}}))
```

The EffectSignature captures the *vocabulary* of operations the lean
layer defines. The `->schema` projection of this formalism produces
a Malli schema validating that any call to these operations has the
right field shapes. The `->gap-type` projection enumerates failure
modes: `:missing-method`, `:wrong-return-type`.

### 3.2 CapabilitySets: record implementation requirements

Each record type that implements a lean protocol is described by a
capability set.

```clojure
(def lean-formalism-caps
  (p/capability-set
    {:id :lean-formalism-record
     :dispatch #{:->lean}}))

(def lean-morphism-caps
  (p/capability-set
    {:id :lean-morphism-record
     :dispatch #{:->lean-conn}}))
```

Every record in the `formalism` layer that has a lean extension must
satisfy `lean-formalism-caps`. Every record in the `morphism` layer
that has a lean extension must satisfy `lean-morphism-caps`.

### 3.3 TypeSchema: lean output types

```clojure
(def lean-types
  (p/type-schema
    {:Formalism  :any
     :Morphism   :any
     :LeanSource :string}))
```

The `LeanSource` type resolves to `:string` — Lean source is emitted
as a string of Lean 4 code.

### 3.4 Morphisms between them

Three morphisms connect these formalisms:

**Existential: formalism capabilities → lean operations**

```clojure
{:id   :lean-formalism-caps->ops
 :kind :existential
 :from :lean-formalism-caps
 :to   :lean-operations
 :source-ref-kind :dispatch-refs
 :target-ref-kind :operation-ids}
```

Checks that every operation named in the formalism lean capability
set exists in the lean effect signature. Catches misspelled method
names or stale references.

**Existential: morphism capabilities → lean operations**

```clojure
{:id   :lean-morphism-caps->ops
 :kind :existential
 :from :lean-morphism-caps
 :to   :lean-operations
 :source-ref-kind :dispatch-refs
 :target-ref-kind :operation-ids}
```

Same check for morphism capabilities.

**Structural: lean operations → lean types**

```clojure
{:id   :lean-ops->types
 :kind :structural
 :from :lean-operations
 :to   :lean-types
 :source-ref-kind :operation-outputs
 :target-ref-kind :type-ids}
```

Checks that the output type keywords (`:LeanSource`) resolve to
known types in the type schema. Catches unregistered output types.

## 4. Cross-Layer Relationship

The lean layer's records are a subset of the core layer's records.
Every type that implements `ILeanProjectable` also implements
`IProjectable` (and `IReferenceable`). This is not a morphism in the
lean dogfood graph — it is an invariant of the architecture:

```
∀ r : Record,
  ILeanProjectable ∈ impls(r) ⟹ IProjectable ∈ impls(r)
∀ r : Record,
  ILeanConnection ∈ impls(r) ⟹ IConnection ∈ impls(r)
```

This invariant is enforced structurally: the `extend-protocol` in
each `pneuma.lean.*` namespace imports the record class from the
corresponding `pneuma.formalism.*` or `pneuma.morphism.*` namespace.
If the record doesn't exist (because the core formalism wasn't
implemented), the lean extension fails to compile.

## 5. What the Gap Report Shows

When we run `(p/gap-report ...)` over these formalism instances, the
three-layer report tells us:

**Object gaps** (per-formalism):
- EffectSignature: are both lean operations defined? Do they have
  the right field shapes? → catches missing or malformed lean method
  declarations.
- CapabilitySets: are the declared operation sets non-empty and
  well-formed? → catches empty lean capability profiles.
- TypeSchema: does `LeanSource` resolve to a known Malli schema? →
  catches unregistered lean output types.

**Morphism gaps** (per-connection):
- Existential (formalism caps → ops): does `#{:->lean}` exist in
  `#{:->lean :->lean-conn}`? → catches lean methods that the
  capability set names but the effect signature doesn't define.
- Existential (morphism caps → ops): does `#{:->lean-conn}` exist
  in the effect signature? → same check for morphisms.
- Structural (ops → types): does `:LeanSource` resolve to a type
  in the lean type schema? → catches output types not registered.

**Path gaps**: none — no cycles in the lean formalism graph.

## 6. Build Order Implications

To dogfood the lean layer, the following must exist:

```
1. pneuma.protocol                   ✓ done
2. pneuma.formalism.effect-signature ✓ done
3. pneuma.formalism.capability       ✓ done
4. pneuma.formalism.type-schema      ✓ done
5. pneuma.morphism.existential       ✓ done
6. pneuma.morphism.structural        ✓ done
7. pneuma.morphism.registry          ✓ done
8. pneuma.gap.core                   ✓ done
9. pneuma.lean.protocol              (Phase 5, step 0)
10. spec/pneuma/lean_spec.clj        (lean dogfood formalism instances)
11. Test: gap-report produces expected results on lean spec
```

Steps 1–8 are already complete (they were needed for the protocol
dogfood). Steps 9–11 are the lean dogfood checkpoint — the first
time Pneuma checks its own lean layer. This can be done before any
`->lean` implementation exists, because the dogfood checks the
*structure* of the lean protocols (operations, capabilities, types),
not the *behavior* of the emission functions.

## 7. What This Doesn't Cover

The lean dogfood checks structural invariants of the lean protocol
layer. It does not check:

- **Lean source validity** — whether `->lean` output is
  syntactically valid Lean 4. This requires the Lean compiler.
- **Translation faithfulness** — whether the emitted Lean types
  faithfully represent the Clojure data. This is the trust boundary
  (see pneuma-lean4-extension.md §8).
- **Proof completeness** — whether the `sorry` placeholders can be
  filled in. This is a Lean-side concern.
- **Human-readable proof content** — whether docstrings, Blueprint
  entries, and structured proof scaffolding are generated correctly.
  See pneuma-lean4-extension.md §11.
- **State machine** — no lifecycle in the lean layer.
- **Event handling** — no events in the lean layer.
- **Composed paths** — no cycles in the lean formalism graph.
