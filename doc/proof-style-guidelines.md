# Proof Style Guidelines

Style conventions for Lean 4 proofs in the Pneuma proof layer.
These apply to both generated proof scaffolding and human-written
proofs in `proofs/Proofs/`.

## Principles

1. **The `.lean` file convinces the kernel; the Blueprint convinces
   the developer.** Opaque-but-trustworthy tactics (`decide`, `rfl`)
   are acceptable for finite-state properties. The Blueprint document
   carries the human-readable explanation.

2. **Named intermediate goals over monolithic tactics.** When a proof
   has logical steps, use `have` to name each step. This makes the
   proof navigable and debuggable.

3. **Structured style for non-trivial proofs.** Proofs that go beyond
   `decide`/`rfl`/`cases` should use `calc`, `have`, or `suffices`
   to expose the reasoning structure.

## Tactic patterns

### `decide` — Finite-state exhaustive check

Use for properties that hold by exhaustive enumeration over a finite
type. Most Pneuma morphism boundaries and containment checks fall here.

```lean
theorem dispatch_bounded :
    ∀ op : Op, op ∈ dispatch → op ∈ allOps := by
  decide
```

No structured proof needed — the kernel checks every case.

### `cases ... <;> rfl` — Per-constructor equality

Use for properties that hold by reflexivity on each constructor.

```lean
theorem containment_boundary :
    ∀ s : Source, InTarget s = true := by
  intro s
  cases s <;> rfl
```

### `cases ... <;> simp` — Per-constructor simplification

Use for membership proofs over finite lists.

```lean
theorem allOps_complete :
    ∀ op : Op, op ∈ allOps := by
  intro op
  cases op <;> simp [allOps]
```

### `have` — Named intermediate goals

Use when a proof has distinct logical steps. Each `have` introduces a
named assertion with a short comment explaining what it establishes.

```lean
theorem composition :
    boundary₁ ∧ boundary₂ ∧ boundary₃ := by
  -- Step 1: existential embedding holds
  have h₁ : boundary₁ := existential_boundary
  -- Step 2: containment holds
  have h₂ : boundary₂ := containment_boundary
  -- Step 3: ordering holds
  have h₃ : boundary₃ := ordering_boundary
  exact ⟨h₁, h₂, h₃⟩
```

### `calc` — Equality/inequality chains

Use for proofs that proceed by a chain of equalities or inequalities.
Natural for ordering proofs and reachability arguments.

```lean
theorem ordering_chain :
    index .a < index .c := by
  calc index .a
      _ < index .b := by decide
      _ < index .c := by decide
```

### `suffices` — Backwards reasoning

Use when the proof is clearer stated in the contrapositive or when
working backwards from the goal.

```lean
theorem no_unauthorized :
    dispatches e → e ∈ caps.dispatch := by
  suffices h : e ∉ caps.dispatch → ¬dispatches e by
    contrapose!; exact h
  intro h_unauth
  exact interceptor_blocks h_unauth
```

### Induction with `generalizing`

Use for proofs over event sequences (chart safety, trace properties).
Always generalize the accumulator.

```lean
theorem safety_aux (inv : State → Prop)
    (h_step : ∀ s e, inv s → inv (step s e))
    (s₀ : State) (h₀ : inv s₀)
    (events : List Event) :
    inv (events.foldl step s₀) := by
  induction events generalizing s₀ with
  | nil => exact h₀
  | cons e es ih => exact ih (step s₀ e) (h_step s₀ e h₀)
```

## When to use which

| Property type | Recommended tactic | Example |
|---|---|---|
| Finite membership | `decide` | dispatch ⊆ allOps |
| Per-constructor | `cases <;> rfl` | InTarget returns true |
| List membership | `cases <;> simp` | allOps_complete |
| Multi-step boundary | `have` + `exact ⟨...⟩` | composition theorems |
| Ordering chain | `calc` | interceptor precedence |
| Contrapositive | `suffices` | unauthorized dispatch |
| Event sequences | `induction generalizing` | chart safety |
| Schema validation | `sorry` | structural boundaries |

## Proof scaffolding vs human proofs

Generated scaffolding (from `->lean`) uses the simplest correct tactic
for each property. Human proofs in `proofs/Proofs/` should prefer
structured style for readability, even when a shorter tactic would
suffice.

The scaffolding marks unproved properties with `sorry` and a
`-- PROOF TARGET:` comment. Human proofs replace the `sorry` with
a structured proof, ideally using `have` steps to make the argument
visible.
