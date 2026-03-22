# Worked Example: Psi

A summary of how the structural domain modeling framework was applied to **psi** — a Clojure-based AI development environment that manages sessions, worktrees, and LLM interactions. The full formalism is documented separately in *Psi Domain Model — Structural Formalism* (28 sections, 4 appendices).

The input was a `META.md` file (~60 lines of natural language requirements).

---

## Step 1 → Sorts

21 sorts extracted from the requirements:

```
E = { Psi, Project, GitRepo, Worktree, MetaModel, UI,
      Context, Session, Message, Tool, SystemPrompt,
      CapabilityGraph, Statechart, ExtensionWidget, Line,
      ConfigSnapshot, PromptLayer, Extension, Model,
      Resource, EQLQuery, EQLMutation }
```

Key decisions:
- `ConfigSnapshot` was separated from `Session` because it has independent algebraic structure (semilattice).
- `Line` was separated from `ExtensionWidget` because it has its own dependent sum structure.
- `EQLQuery` and `EQLMutation` were included as sorts because they are structurally distinct from plain strings — they participate in the presheaf structure.

## Step 2 → Arrows

~30 arrows, both total and partial. Key examples:

```
worksOn  : Psi → Project                   -- total
truth    : Project → GitRepo               -- total
parent   : Session →? Session              -- partial (root sessions have no parent)
worktree : Session → Worktree              -- total
classify : Element → Scope × Phase         -- total
active   : Context → Session               -- total (pointer)
id       : Session → UUID                  -- total
name     : Session →? String               -- partial (optional)
```

Key decisions:
- `parent` is partial, not total — root sessions have no parent.
- `name` and `summary` are partial — they use `Option(String)`.
- `active` is total — there is always an active session (pointer validity axiom).

## Step 3 → Constructions

13 constructions applied:

| Construction | Applied To | Why |
|---|---|---|
| Product lattice | Scope × Phase | Meta model classifies along two independent axes |
| FreeMonoid | Message history, Widget content | Ordered, appendable sequences |
| Forest | Session registry | Hierarchical containment with multiple roots |
| Dependent record | Session | Rich entity with many typed fields |
| Sigma (dependent sum) | Message, Line | Tag determines payload type |
| Option | Session name, summary | Fields may be absent |
| Coproduct | SubagentFork | Exactly two mutually exclusive cases |
| Semilattice | ConfigSnapshot | Inheritance with overrides and a global baseline |
| Fibration | Session → Worktree | Sessions partitioned by filesystem context |
| Retraction | Widget → Vec(String) | Rich content must degrade to plain strings |
| Presheaf | CapabilityGraph over EQL | Schema-indexed queryable capabilities |
| Ref | Active session pointer | Distinguished cursor into the forest |
| Mu (fixpoint) | SessionTree | Self-similar recursive nesting |

## Step 4 → Axioms

21 axioms total (16 domain, 5 doctrine). Key domain axioms:

| # | Name | Insight |
|---|---|---|
| A1 | Lineage Immutability | Parent pointers never change after creation — `Invariance` pattern |
| A2 | Non-blocking Selection | Switching active session doesn't affect other sessions — `Independence` pattern |
| A3 | Independent Evolution | Forked config evolves separately from parent — `Independence` pattern |
| A4 | Worktree Disjointness | Different worktrees mean disjoint filesystems — derived from `Fibration` |
| A5 | Widget Backward Compat | Every Line variant must define flatten — derived from `Retraction` |
| A7 | Active Pointer Validity | Active pointer always references a real node — derived from `Ref` |

Notice how most axioms are *implied by the choice of construction*. Fibrations imply disjointness. Retractions imply round-trip identity. This is the payoff of choosing the right construction — the axioms write themselves.

## Step 5 → Morphisms

14 morphisms identified:

| Morphism | Construction | Key Insight |
|---|---|---|
| Forest homomorphism | Forest | General parent-preserving maps |
| Grafting | Forest | Subtree attachment — endomorphism |
| Pruning | Forest | Left adjoint to grafting |
| Fork pullback | FreeMonoid | Fork is a prefix section with universal property |
| Inheritance arrow | Semilattice | Monotone map from parent to child config |
| Config override | Semilattice | Pointed endomorphisms forming a monoid |
| Detachment | Semilattice + Ref | Coequalizer — copy-on-write at fork time |
| Reclassification | Product lattice + Group | ℤ₂ × ℤ₂ action on classification cells |
| Fiber transport | Fibration | Moving a session to a different worktree |
| Widget extension | Retraction | Embedding new Line types while preserving flatten |
| Capability transformation | Presheaf | Natural transformation between capability states |
| Resolver extension | Presheaf | Morphism in functor category |
| Mutation composition | Presheaf | Endomorphism monoid on capability graph |
| Introspection–rendering | Slice category | Adjunction: filter ⊣ include |

The adjunction `filter ⊣ include` captures the design principle stated in the META: "a UI is conceptually a view over the project's meta model."

## Step 6 → 2-Cells

The model is a 2-category with 2-Cells between:
- Parallel reclassification strategies (different paths through the Scope × Phase lattice)
- Parallel resolver extensions (different ways to enrich the capability presheaf)
- Parallel widget extension paths (different embeddings of old Line into new Line)

## Output Statistics

| Metric | Count |
|---|---|
| Sorts | 21 |
| Arrows | ~30 |
| Constructions applied | 13 |
| Axioms | 21 (16 domain + 5 doctrine) |
| Morphisms | 14 |
| 2-Cell families | 3 |
| Document sections | 28 + 4 appendices |

## Lessons Learned

1. **Start with sorts, not constructions.** It's tempting to jump to "this is a tree" — but getting the sorts right first means constructions snap into place naturally.

2. **Partial arrows are important.** The `→?` notation catches real domain constraints (root sessions, optional fields) that would be invisible in a less precise formalism.

3. **Constructions generate axioms.** Once you identify that config inheritance is a semilattice, monotonicity and bottom-element axioms follow automatically. The construction choice does most of the axiom work.

4. **Morphisms reveal design principles.** The `filter ⊣ include` adjunction wasn't stated in the META — it emerged from the morphism analysis. The formalism surfaced a design principle that was implicit in the natural language.

5. **The doctrine (Part III) was discovered, not designed.** It emerged from asking "what is the formalism made of?" after Parts I and II were complete. But once extracted, it became the reusable framework.
