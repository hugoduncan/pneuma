# Construction Reference

Detailed documentation for each construction in the structural domain modeling vocabulary. For each: what it is, its signature, when to use it, what morphisms it admits, what axioms it typically generates, and a domain-neutral example.

---

## Table of Contents

1. [FreeMonoid](#freemonoid)
2. [PowerSet](#powerset)
3. [Product](#product)
4. [Coproduct](#coproduct)
5. [Option](#option)
6. [Sigma](#sigma)
7. [Mu (Fixpoint)](#mu-fixpoint)
8. [Forest / Tree](#forest--tree)
9. [Ref (Pointer)](#ref-pointer)
10. [Semilattice](#semilattice)
11. [Fibration](#fibration)
12. [Retraction](#retraction)
13. [Presheaf](#presheaf)
14. [Group Action](#group-action)

---

## FreeMonoid

**What:** An ordered sequence of elements, with concatenation as the monoidal operation and the empty sequence as identity.

**Signature:** `FreeMonoid : Sort → StructuredSort`

**Notation:** `List(A)`, `Vec(A)`, `History(A)`

**When to use:** The domain has an ordered, appendable collection — message histories, event logs, instruction sequences, transaction ledgers, audit trails.

**Natural morphisms:**
- Prefix embeddings: `take(k, ·)` — extract the first k elements
- Suffix embeddings: `drop(k, ·)` — extract elements after position k
- Concatenation: `xs ++ ys` — append one sequence to another
- Homomorphisms: `fmap(f)` — apply a function to every element

**Typical axioms:**
- Prefix decomposition: `take(k, xs) ++ drop(k, xs) = xs`
- Associativity of concatenation: `(xs ++ ys) ++ zs = xs ++ (ys ++ zs)`
- Identity: `[] ++ xs = xs = xs ++ []`

**Example:** An audit trail is `FreeMonoid(AuditEntry)`. A conversation history is `FreeMonoid(Message)`.

---

## PowerSet

**What:** An unordered collection of elements of a sort.

**Signature:** `PowerSet : Sort → StructuredSort`

**Notation:** `𝒫(A)`

**When to use:** The domain has a set-valued field — permissions, tags, tool sets, resource pools, capabilities, feature flags.

**Natural morphisms:**
- Subset inclusion: `S₁ ⊆ S₂`
- Union: `S₁ ∪ S₂`
- Intersection: `S₁ ∩ S₂`
- Set difference: `S₁ \ S₂`

**Typical axioms:**
- Coverage: `needed(x) ⊆ available(y)` — everything required is provided
- Membership: `x ∈ S` — an element belongs to a set
- Disjointness: `S₁ ∩ S₂ = ∅` — two sets share no elements

**Example:** A user role has `𝒫(Permission)`. A tool configuration has `𝒫(Tool)`.

---

## Product

**What:** A pair (or tuple) of values from two or more sorts.

**Signature:** `Product : Sort × Sort → StructuredSort`

**Notation:** `A × B`

**When to use:** Two independent dimensions classify the same entity — scope × phase, color × size, status × priority, latitude × longitude.

**Natural morphisms:**
- Projections: `π₁ : A × B → A`, `π₂ : A × B → B`
- Diagonal: `Δ : A → A × A` where `Δ(a) = (a, a)`
- Product map: `f × g : A × B → C × D`

**Typical axioms:**
- Classification totality: every element is classified into exactly one cell
- Lattice structure on cells if each factor has an ordering

**Example:** A task classification is `Priority × Status`. A meta model classification is `Scope × Phase`.

---

## Coproduct

**What:** A choice between two or more alternatives, tagged to indicate which.

**Signature:** `Coproduct : Sort × Sort → StructuredSort`

**Notation:** `A ⊔ B`

**When to use:** The domain has mutually exclusive cases — payment by card or bank transfer, node is leaf or branch, fork is full-inherit or clean-slate, result is success or error.

**Natural morphisms:**
- Injections: `inl : A → A ⊔ B`, `inr : B → A ⊔ B`
- Case analysis (elimination): `[f, g] : A ⊔ B → C` given `f : A → C` and `g : B → C`

**Typical axioms:**
- Exhaustiveness: every value is one of the cases
- Disjointness: no value is both cases simultaneously

**Example:** A notification channel is `Email ⊔ SMS ⊔ Push`. A subagent fork is `FullInherit ⊔ CleanSlate`.

---

## Option

**What:** A value that may or may not be present. Equivalent to `A ⊔ 𝟙` (coproduct with the unit type).

**Signature:** `Option : Sort → StructuredSort`

**Notation:** `Option(A)`, `A?`

**When to use:** A field is optional — a session may or may not have a name, a user may or may not have a phone number, an order may or may not have a discount code.

**Natural morphisms:**
- `Some : A → Option(A)` — injection
- `None : 𝟙 → Option(A)` — absence
- `map : (A → B) → Option(A) → Option(B)` — functorial lift
- `getOrElse : Option(A) × A → A` — elimination with default

**Typical axioms:**
- Partiality constraints: conditions under which the value is `None` vs `Some`

**Example:** A patient's emergency contact is `Option(Person)`. A session name is `Option(String)`.

---

## Sigma

**What:** A pair where the type of the second component depends on the value of the first (dependent pair).

**Signature:** `Sigma : (A : Sort) × (A → Sort) → StructuredSort`

**Notation:** `Σ(a : A). B(a)`

**When to use:** The domain has a tag that determines what data follows — a message tagged by role where the payload depends on the role; a form field tagged by type where validation depends on the type; an event tagged by kind where the associated data varies.

**Natural morphisms:**
- Projection to tag: `π₁ : Σ(a : A). B(a) → A`
- Section: choosing a canonical value for each tag

**Typical axioms:**
- Tag-dependent well-formedness: the second component satisfies constraints that depend on the first

**Example:** A form field is `Σ(t : FieldType). ValidationRules(t)`. A message is `Σ(r : Role). Payload(r)`.

---

## Mu (Fixpoint)

**What:** A type defined in terms of itself — the least fixed point of a type operator.

**Signature:** `Mu : (Sort → Sort) → StructuredSort`

**Notation:** `μT. F(T)`

**When to use:** The domain has self-similar nesting — trees, nested comments, recursive document structure, organizational hierarchies, expression trees, filesystem directories.

**Natural morphisms:**
- Fold (catamorphism): `fold : (F(A) → A) → μF → A` — collapse recursive structure
- Unfold (anamorphism): `unfold : (A → F(A)) → A → μF` — build recursive structure
- Structural recursion

**Typical axioms:**
- Well-foundedness: no infinite descent
- Finiteness: every instance has finite depth

**Example:** A comment thread is `μT. CommentData × 𝒫(T)`. A JSON value is `μT. Null ⊔ Bool ⊔ Number ⊔ String ⊔ List(T) ⊔ Map(String, T)`.

---

## Forest / Tree

**What:** A collection of trees — a special case of `𝒫(μT. Data × 𝒫(T))`.

**Signature:** `Forest : Sort → StructuredSort`

**Notation:** `Forest(A)`, `Tree(A)`

**When to use:** The domain has hierarchical containment with multiple roots — session forests, file systems, org charts, category taxonomies, dependency trees.

**Natural morphisms:**
- Homomorphisms: parent-preserving maps `φ` where `parent(φ(s)) = fmap(φ, parent(s))`
- Grafting: attach a subtree onto a target node
- Pruning: detach a subtree (left adjoint to grafting)

**Typical axioms:**
- Lineage immutability: once a parent is assigned, it never changes
- Well-foundedness: lineage chains are finite
- Path uniqueness: each node has exactly one path to its root

**Example:** A session registry is `Forest(Session)` with an active pointer. A file system is `Forest(FileNode)`.

---

## Ref (Pointer)

**What:** A mutable or immutable reference to an element of a sort.

**Signature:** `Ref : Sort → StructuredSort`

**Notation:** `Ref(A)`

**When to use:** The domain has a distinguished pointer or cursor — the active session, the current user, the head of a list, a selected item.

**Natural morphisms:**
- Dereference: `deref : Ref(A) → A` — read the referent
- Update: `set : Ref(A) × A → Ref(A)` — change the referent
- Detachment: `detach : Ref(A) → A` — copy-on-write (dereference and sever the link)

**Typical axioms:**
- Validity: the referent exists (`active ∈ nodes(forest)`)
- Non-dangling: the reference always points to a live element

**Example:** A context has `Ref(Session)` as its active session pointer. An editor has `Ref(Document)` as the currently open file.

---

## Semilattice

**What:** A partially ordered set with a meet (or join) operation and a bottom (or top) element.

**Signature:** `Semilattice : Sort → StructuredSort` (equipped with `⊑` and `⊥`)

**Notation:** `(A, ⊑, ⊥)`

**When to use:** The domain has inheritance, overriding, or cascading defaults — configuration snapshots, permission hierarchies, style inheritance, priority resolution, feature flag cascades.

**Natural morphisms:**
- Monotone maps: `f : A → B` where `a₁ ⊑ a₂ ⟹ f(a₁) ⊑ f(a₂)`
- Overrides: pointed endomorphisms that replace one field while preserving the rest
- Inheritance arrows: maps from parent to child config

**Typical axioms:**
- Bottom element: `∀ a, ⊥ ⊑ a` — the global default is below everything
- Monotonicity of inheritance: if parent₁ ⊑ parent₂ then child₁ ⊑ child₂
- Override associativity: `(o₁ ∘ o₂) ∘ o₃ = o₁ ∘ (o₂ ∘ o₃)`

**Example:** CSS style resolution is a `Semilattice(StyleRule)`. Config inheritance is a `Semilattice(ConfigSnapshot)`.

---

## Fibration

**What:** A functor `π : E → B` where the fibers `π⁻¹(b)` partition the total space.

**Signature:** `Fibration : Sort × Sort → StructuredSort` (total space, base space)

**Notation:** `π : E → B`, `Fiber(b) = { e | π(e) = b }`

**When to use:** The domain has context boundaries — entities partitioned by workspace, region, tenant, time period, environment (dev/staging/prod).

**Natural morphisms:**
- Fiber transport: `transport(e, b') = e'` — move an entity from one context to another
- Fiber restriction: restrict operations to a single fiber

**Typical axioms:**
- Context disjointness: different fibers don't share state
- Transport preservation: transport respects the context boundary (the moved entity lands in the new fiber)

**Example:** Sessions fibered over worktrees: `π : Session → Worktree`. Tenants fibered over regions: `π : Tenant → Region`.

---

## Retraction

**What:** A pair of maps `i : B ↪ A` and `r : A ↠ B` where `r ∘ i = id_B`. The richer type `A` projects losslessly onto a simpler type `B`.

**Signature:** `Retraction : Sort × Sort → StructuredSort` (rich sort, simple sort)

**Notation:** `r : A ↠ B`, `i : B ↪ A`, `r ∘ i = id`

**When to use:** The domain has backward-compatible extension — a rich content model that must degrade gracefully, a new API compatible with old clients, a protocol that evolves while maintaining a minimal baseline.

**Natural morphisms:**
- Retraction-preserving embeddings: `embed : A_old → A_new` where `flatten_new ∘ embed = flatten_old`

**Typical axioms:**
- Round-trip identity: `r ∘ i = id`
- Extension preservation: new embeddings commute with the retraction

**Example:** Rich widget lines retract to plain strings via `text`. A v2 API response retracts to v1 format.

---

## Presheaf

**What:** A contravariant functor from a schema category to **Set** — assigns a set of values to each schema node, with pullback along edges.

**Signature:** `Presheaf : Category → StructuredSort`

**Notation:** `Functor(C^op, Set)`

**When to use:** The domain has a queryable, schema-indexed capability structure — EQL resolvers, GraphQL schemas, knowledge graphs, capability registries, feature stores.

**Natural morphisms:**
- Natural transformations: coherent schema evolution `α : F ⟹ G` (the naturality square commutes)
- Resolver extensions: adding new resolution capabilities
- Endomorphisms (mutations): structure-preserving self-maps

**Typical axioms:**
- Naturality: transformations commute with pullbacks along schema edges
- Coherence: compositions of natural transformations are natural

**Example:** A capability graph is `Presheaf(EQLSchema)`. A GraphQL type system is `Presheaf(TypeSchema)`.

---

## Group Action

**What:** A group `G` acting on a sort `A` via `G × A → A` satisfying identity and compatibility laws.

**Signature:** `GroupAction : Group × Sort → StructuredSort`

**Notation:** `G × A → A`, `g · a`

**When to use:** The domain has reversible, composable transformations on a classification — status toggles, permission flips, dimensional reclassification, symmetry operations.

**Natural morphisms:**
- Equivariant maps: maps `f : A → B` that commute with the group action: `f(g · a) = g · f(a)`

**Typical axioms:**
- Identity: `e · a = a`
- Compatibility: `g · (h · a) = (gh) · a`
- Involution (for ℤ₂ actions): `g · (g · a) = a`
- Orbit structure: which elements are reachable from which

**Example:** Reclassification along Scope and Phase is a `(ℤ₂ × ℤ₂)` action. Color space rotation is an `SO(3)` action.
