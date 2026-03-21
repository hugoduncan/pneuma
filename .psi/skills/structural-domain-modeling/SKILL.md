---
name: structural-domain-modeling
description: "Build rigorous structural entity-relationship models for any domain using category theory, dependent types, and algebraic structures. Use this skill whenever the user asks to model a domain, formalize an entity relationship model, create a structural specification, describe a system's architecture mathematically, or analyze the structure of a codebase/project/system. Also trigger when the user mentions domain modeling, entity relationships, formalisms, structural invariants, schema design, or asks 'what are the entities and relationships in X'. Works from natural language descriptions (requirements docs, design specs, META files, READMEs, user stories) and produces a complete structural model with sorts, arrows, constructions, axioms, and morphisms. Even if the user doesn't mention category theory explicitly — if they want a precise structural description of a domain, use this skill."
---

# Structural Domain Modeling

Build complete structural entity-relationship models for any domain using a six-step procedure grounded in category theory, dependent types, and algebraic structures. The output is a formal specification of objects, morphisms, and axioms — no behavioral semantics.

## When to read reference files

- **Before Step 3** (Apply Constructions): read `references/construction-reference.md` for the full menu of 14 constructions with signatures, selection guidance, natural morphisms, typical axioms, and examples.
- **After completing all steps**: optionally read `references/psi-worked-example.md` for a complete worked example showing how the framework was applied to a real Clojure-based AI development environment.

---

## The Six Primitives

Every structural domain model is composed of exactly six kinds of entity:

```
Primitive = Sort | Arrow | Construction | Axiom | Morphism | Cell₂
```

### Sort
An atomic type name — an entity kind in the domain. No internal structure at the meta level.
```
Sort : Type
Sort = { ... }           -- filled in per domain
```

### Arrow
A typed, directed relationship between sorts, with declared totality:
```
Arrow : Type where
  name     : Symbol
  domain   : Sort
  codomain : Sort
  totality : Total | Partial    -- Partial notated →?
```
Arrows compose: if `f : A → B` and `g : B → C`, then `g ∘ f : A → C`. The collection of sorts and arrows forms the **signature category** (Sig).

### Construction
A functor-valued operation that composes sorts into structured sorts:
```
Construction : Type where
  name   : Symbol
  arity  : List(Sort)
  result : StructuredSort
  laws   : Set(Equation)
```
Constructions are endofunctors on Sig — they preserve composition. See `references/construction-reference.md` for the full vocabulary.

### Axiom
A universally quantified constraint on arrows and constructions:
```
Axiom : Type where
  name       : Symbol
  parameters : List(Sort × Symbol)
  body       : Proposition
```

### Morphism
A structure-preserving map between instances of a construction:
```
Morphism : Type where
  name      : Symbol
  source    : Construction(Sort)
  target    : Construction(Sort)
  preserves : Set(Construction)
```

### 2-Cell
A morphism between parallel morphisms (same source and target):
```
Cell₂ : Type where
  source : Morphism
  target : Morphism
```
Captures multiple strategies for the same structural transformation.

---

## The Proposition Grammar

Axioms use this proposition language:

```
Proposition = Equation(Arrow, Arrow)
            | Implication(Proposition, Proposition)
            | Conjunction(Proposition, Proposition)
            | Disjunction(Proposition, Proposition)
            | Membership(Arrow, Construction)
            | Independence(Arrow, Arrow)
            | Invariance(Arrow, Time, Time)
            | LessThan(Time, Time)
            | Inequality(Arrow, Arrow)
            | SubsetOf(Construction, Construction)
```

Extend if your domain requires additional forms (probabilistic, metric, etc.) — but keep the grammar explicit.

### Common Axiom Patterns

| Pattern | Form | Example |
|---|---|---|
| Immutability | `Invariance(f(x), t₁, t₂)` | "once assigned, never changes" |
| Non-interference | `Independence(f(x), g(y))` | "changing A doesn't affect B" |
| Coverage | `SubsetOf(needed(x), available(y))` | "all required resources present" |
| Totality | `∀ x, Equation(f(x), defined)` | "classification is total" |
| Involution | `Equation(f ∘ g, id)` | "include and exclude are inverses" |
| Commutativity | `Equation(f ∘ g, g ∘ f)` | "order doesn't matter" |
| Membership | `Membership(active, nodes(forest))` | "pointer is valid" |
| Uniqueness | `Implication(Eq(f(x), f(y)), Eq(x, y))` | "identifiers are unique" |

---

## Construction Quick-Reference

For each construction's full documentation (signature, when-to-use, morphisms, axioms, examples), read `references/construction-reference.md`. Here is the summary grouped by structural question:

**Containment & Aggregation:** PowerSet `𝒫(A)`, FreeMonoid `List(A)`, Forest `Forest(A)`

**Choice & Optionality:** Coproduct `A ⊔ B`, Option `Option(A)`

**Combination & Dependency:** Product `A × B`, Sigma `Σ(a:A). B(a)`

**Recursion & Self-Reference:** Fixpoint `μT. F(T)`, Ref `Ref(A)`

**Higher Structure:** Presheaf `Functor(C^op, Set)`, Fibration `π : E → B`, Retraction `r ∘ i = id`, Semilattice `(A, ⊑, ⊥)`, Group Action `G × A → A`

---

## The Six-Step Procedure

Given a domain described in natural language (requirements doc, design spec, META file, README, user stories), apply these steps in order.

### Step 1: Enumerate Sorts

Read the domain description. Every noun naming *a kind of thing* (not an instance) is a candidate sort. Filter:

- Is it a distinct entity, or an attribute? (Attributes become arrows, not sorts.)
- Is it a value type (String, UUID, Instant) or a domain entity? (Value types go in a base sort set.)
- Are two nouns actually the same thing? (Merge them.)

**Output:** `E = { S₁, S₂, ..., Sₙ }`

### Step 2: Define Arrows

For each pair of sorts, ask: is there a directed relationship? For each:

- Name it.
- Declare domain and codomain.
- Declare totality (always defined, or only sometimes?).

Also identify identity functions (identifiers, names, labels) — arrows from a sort to a value sort.

**Output:** arrows forming the signature category Sig.

### Step 3: Apply Constructions

**Read `references/construction-reference.md` before this step.**

For each sort and group of related arrows, match the structural pattern:

- Ordered sequence? → FreeMonoid
- Unordered collection? → PowerSet
- Optional field? → Option
- Mutually exclusive alternatives? → Coproduct
- Self-similar nesting? → Mu (fixpoint)
- Multi-dimensional classification? → Product lattice
- Inheritance / cascading defaults? → Semilattice
- Partitioning by context? → Fibration
- Rich-to-simple backward-compatible projection? → Retraction
- Schema-indexed queryable structure? → Presheaf
- Reversible transformations? → Group action

**Output:** each sort annotated with constructions.

### Step 4: State Axioms

For each construction and arrow, ask: what must always be true? Use the proposition grammar and common patterns.

Focus on:
- **Immutability** — what, once set, never changes?
- **Independence** — what operations don't interfere?
- **Validity** — what pointers must be valid?
- **Coverage** — what must always be available?
- **Totality** — what must always be defined?
- **Compatibility** — what must be preserved under extension?

**Output:** numbered axioms `A1, A2, ..., Aₘ`.

### Step 5: Identify Morphisms

For each construction from Step 3, identify structure-preserving maps:

| Construction | Natural Morphisms |
|---|---|
| Forest | Homomorphisms, grafting, pruning |
| FreeMonoid | Prefix/suffix embeddings, concatenation |
| Semilattice | Monotone maps, overrides |
| Product lattice | Axis-wise moves (group actions) |
| Fibration | Fiber transport |
| Retraction | Retraction-preserving embeddings |
| Presheaf | Natural transformations |
| PowerSet | Subset inclusion, union, intersection |
| Coproduct | Injection, case analysis |

Also look for: adjunctions (lossy projection ⊣ faithful embedding), endomorphism monoids (composable self-maps), universal properties (pullbacks, coequalizers).

**Output:** morphism catalogue with domain, codomain, and preserved construction.

### Step 6: Check for 2-Cells

For parallel morphisms (same source and target), ask: is there a meaningful relationship?

2-Cells arise when there are multiple strategies for the same transformation, multiple extension paths to the same enriched type, or comparable migration strategies.

If none exist, state: "The model is a 1-category." That's fine.

**Output:** 2-Cells, or their explicit absence.

---

## Assembling the Sketch

Steps 1–6 produce a **sketch** (in the sense of Ehresmann):

```
DomainSketch : Sketch where
  Nodes    = Sorts                             (Step 1)
  Arrows   = Arrows                            (Step 2)
  Cones    = { (C, args) | C ∈ Constructions } (Step 3)
  Cocones  = { coproducts, initial objects }    (Step 3)
  Diagrams = Axioms                            (Step 4)
```

A **model** is a structure-preserving functor `M : DomainSketch → Set`. The running system is one such model. **Migrations** between models are natural transformations `α : M₁ ⟹ M₂`.

---

## Level Structure

Every model sits in four levels:

```
Level 0 — Runtime:      concrete instances
Level 1 — Domain Model: sorts, arrows, constructions, axioms, morphisms (Steps 1–6 output)
Level 2 — Doctrine:     the six primitives, construction vocabulary, proposition grammar (this skill)
Level 3 — Foundation:   set theory / type theory / categorical logic (ambient)
```

Downward functors (instantiate, classify, ground) are structure-preserving. Upward recovery does not exist — each level is a lossy compression of the one below.

---

## Quality Checklist

After completing Steps 1–6, verify:

| Check | Question | Fix |
|---|---|---|
| Sort completeness | Every domain noun → sort or arrow? | Add missing sorts |
| Arrow coverage | Every relationship captured? | Add missing arrows |
| Totality correctness | Partial/total correctly declared? | Adjust |
| Construction fit | Each construction matches the pattern? | Swap |
| Axiom grounding | Every axiom in the proposition grammar? | Extend grammar or reformulate |
| Axiom independence | No redundant axioms? | Remove |
| Morphism completeness | All natural transformations accounted for? | Add |
| Morphism preservation | `preserves` sets correct? | Correct |
| 2-Cell check | Parallel morphisms related or independent? | Add or note absence |
| Sketch well-formedness | All cones/cocones match constructions? | Reconcile |

---

## Output Format

Produce a markdown document structured as follows. Adapt section count and depth to the domain's complexity.

```markdown
# [Domain Name] — Structural Formalism

## Sorts
E = { ... }

## Value Sorts
V = { String, UUID, Instant, Int, ... }

## Arrows
[name] : [Sort] → [Sort]           -- total
[name] : [Sort] →? [Sort]          -- partial

## Constructions
[Sort] is structured as [Construction]([args])
-- one subsection per construction applied, with the formal definition

## Axioms
A1  [Name]: [Proposition]
A2  [Name]: ...

## Morphisms
[name] : [source] → [target]
  preserves: [constructions]
-- one subsection per morphism family

## 2-Cells
[name] : [Morphism₁] ⟹ [Morphism₂]
-- or: "The model is a 1-category."

## Appendix: Axiom Summary Table
## Appendix: Construction Summary Table
## Appendix: Morphism Catalogue
```

When writing each section, use the formal notation from this skill (arrows, constructions, propositions) alongside natural language explanation. The document should be readable by someone familiar with basic category theory, but accessible enough that the notation is always explained on first use.
