# Pneuma × Lean 4: From Runtime Checking to Proof

**Extending Pneuma with kernel-verified architectural invariants.**

*Extension Document — Draft, March 2026*

---

## Table of Contents

1. [The Two Checking Modalities](#1-the-two-checking-modalities)
2. [What Lean Proves That Pneuma Cannot Test](#2-what-lean-proves-that-pneuma-cannot-test)
3. [The ->lean Projection](#3-the--lean-projection)
4. [Translation: Clojure Data to Lean Terms](#4-translation-clojure-data-to-lean-terms)
5. [The Five Proof Targets](#5-the-five-proof-targets)
6. [The Proof/Test Boundary](#6-the-prooftest-boundary)
7. [Workflow](#7-workflow)
8. [The Trust Architecture](#8-the-trust-architecture)
9. [Implementation Strategy](#9-implementation-strategy)
10. [Limitations and Non-Goals](#10-limitations-and-non-goals)

---

## 1. The Two Checking Modalities

Pneuma's runtime checking — Malli schemas, trace monitors, test.check generators, gap reports — operates by sampling. It validates atom snapshots, replays event log entries, generates 1000 random event sequences, and reports where the implementation diverges from the mathematical objects. This is fast, interactive, and REPL-native. It is also fundamentally incomplete: 1000 passing tests do not prove that test 1001 would pass.

Lean 4 operates by proof. It constructs terms in the calculus of inductive constructions, and the Lean kernel — a small, trusted program — checks that every step is valid. A Lean proof of "no reachable configuration violates invariant *I*" is a certificate that holds for *all* configurations, not just the ones we happened to test. It is slow, non-interactive, and requires manual effort. It is also complete within its scope: once proved, the property is settled.

These are complementary, not competing. Pneuma gives you fast feedback during development. Lean gives you permanent guarantees about the design. The same mathematical objects — Clojure maps representing statecharts, effect signatures, morphisms, and cycles — serve as the source for both. The only difference is the projection target: `->schema` produces a Malli value, `->lean` produces a `.lean` file.

> **The core proposition:** Because Pneuma's formalisms are already mathematical objects with well-defined semantics, they have natural Lean representations. The `->lean` projection is not a translation between two unrelated systems — it is a change of notation for the same mathematics.

---

## 2. What Lean Proves That Pneuma Cannot Test

There are five specific properties where the gap between sampled testing and universal proof matters. Each one corresponds to a Pneuma checking level (object, morphism, path) but strengthens it from "we checked the cases we could generate" to "this holds for all cases."

### 2.1. Chart safety (object level)

Pneuma's trace monitor checks event log entries against the statechart's step function *δ*. If the log contains a transition from `:idle` directly to `:tool-executing`, the monitor catches it. But it can only check transitions that actually occurred. If a certain event sequence has never happened in practice — say, a rapid `user-cancel` during `tool-executing` followed by an immediate `user-submit` — the monitor has nothing to say about it.

Lean proves: **for all reachable configurations** *c* of the statechart starting from the initial configuration, and **for all events** *e* in the event alphabet, the successor configuration *δ(c, e)* satisfies invariant *I*. This is a finite exhaustive check — the statechart has finitely many states and transitions — but it covers every possible execution path, including ones the system has never traversed.

### 2.2. Replay determinism (object level)

Pneuma's generative tests produce random event sequences, run them through `dispatch!`, then replay them with effects suppressed, and verify the final state matches. This checks that the specific traces generated are deterministic under replay. It cannot guarantee that some untested trace exhibits non-determinism.

Lean proves: **the replay theorem is a structural consequence of the architecture's design**. Because handlers are pure functions of `(state, event)`, and because the `trim-effects-on-replay` interceptor suppresses all effects during replay, the state trace under replay is identical to the state trace under live execution, for all possible traces. This is not a property of any specific trace — it is a property of the handler purity contract and the interceptor chain structure.

### 2.3. Composition well-typedness (morphism level)

Pneuma's morphism checks verify individual boundary contracts: does this callback reference an existing transition? Does this effect emission match the signature schema? But morphisms compose, and the composition of individually valid morphisms may still be ill-typed if the intermediate types don't align.

Lean proves: **for all morphisms *f : A → B* and *g : B → C* in the morphism registry, the composition *g ∘ f : A → C* preserves the boundary contracts**. Concretely: if every callback in the effect signature points to a valid Mealy transition (morphism 1), and every Mealy transition that handles a raised event produces an effect whose fields match the signature (morphism 2), then the composition — from chart raise to effect emission — is type-safe end-to-end. This is combinatorial: twelve morphisms, four types, three cycles. Sampling is unlikely to hit every composition; proof covers them all.

### 2.4. Cycle well-formedness (path level)

Pneuma's path checks test specific callback re-entry scenarios by generating event sequences that traverse the event-effect-callback cycle. But the number of possible paths through the cycle is exponential in the number of transitions, effects, and callback combinations. Generative testing explores a fraction.

Lean proves: **for every transition that raises an event, for every handler that processes that event, for every effect that handler emits, and for every callback in that effect, the callback event is accepted by the chart in the state the system will be in when the callback fires**. This is the cycle-level invariant from Section 5.1 of the main document, universally quantified. It's the property that says the event-effect-callback loop never breaks.

### 2.5. Bisimulation under refactoring (path level)

Pneuma can diff two gap reports to show what changed between two versions of the formalisms. But it cannot prove that two statechart versions are *behaviorally equivalent* — that no agent can distinguish them through any sequence of observations. Two charts might produce the same gap report on tested traces but diverge on untested ones.

Lean proves: **two statecharts are bisimilar** if and only if there exists a bisimulation relation *R* over their state spaces such that related states have identical observations and every transition from a related state leads to a related successor. For finite statecharts this is decidable (partition refinement), and Lean can construct the witnessing relation or report the distinguishing trace.

---

## 3. The ->lean Projection

The `->lean` projection is a new method on the `IProjectable` protocol and the `IConnection` protocol. It sits alongside the existing projections:

```clojure
(defprotocol IProjectable
  (->schema   [this])    ;; → Malli schema
  (->monitor  [this])    ;; → trace checking function
  (->gen      [this])    ;; → test.check generator
  (->gap-type [this])    ;; → gap type descriptor
  (->lean     [this]))   ;; → string of Lean 4 source code

(defprotocol IConnection
  (check  [this a b])
  (->gap  [this a b])
  (->gen  [this a b])
  (->lean [this a b]))   ;; → Lean 4 theorem statement + proof sketch
```

The output of `->lean` is a string — a `.lean` file fragment containing type definitions, property statements, and proof scaffolding. It is not a compiled Lean term; it is source code that must be fed to the Lean compiler. The reason is pragmatic: Lean proofs often require human guidance (choosing the right tactic, providing a witness, decomposing a complex goal), and emitting source code gives the developer a starting point to work from rather than a black box.

### 3.1. What `->lean` emits

For each formalism, `->lean` produces three things:

**Type definitions.** The Lean equivalents of the Clojure data. A statechart becomes an inductive `State` type, a `Transition` structure, and a `step` function. An effect signature becomes an inductive `Effect` type with fields.

**Property statements.** The theorems to be proved, expressed as Lean `theorem` declarations with `sorry` placeholders for the proofs. These are the proof obligations — the developer fills them in.

**Proof scaffolding.** Helper lemmas, decidability instances, and tactic hints that make the proofs tractable. For finite types, this includes `Fintype` and `DecidableEq` instances and `decide` invocations that let Lean's kernel check the property by exhaustive evaluation.

### 3.2. Example: statechart emission

```clojure
(->lean session-chart)
```

Produces:

```lean
import Mathlib.Tactic

-- Generated by Pneuma from statechart :SessionLifecycle

inductive ConvState where
  | idle
  | generating
  | awaitingApproval
  | toolExecuting
  | toolError
  deriving DecidableEq, Fintype, Repr

inductive ExtState where
  | extensionsIdle
  | extensionRunning
  deriving DecidableEq, Fintype, Repr

structure Config where
  conv : ConvState
  ext  : ExtState
  deriving DecidableEq, Fintype, Repr

def initialConfig : Config :=
  { conv := .idle, ext := .extensionsIdle }

inductive Event where
  | userSubmit
  | generationComplete
  | toolRequested
  | userCancel
  | userApproved
  | autoApproved
  | toolComplete
  | toolError
  | retryTool
  | skipTool
  | extensionActivated
  | extensionComplete
  deriving DecidableEq, Fintype, Repr

def step (c : Config) (e : Event) : Config :=
  match c.conv, e with
  | .idle,              .userSubmit         => { c with conv := .generating }
  | .generating,        .generationComplete => { c with conv := .idle }
  | .generating,        .toolRequested      => { c with conv := .awaitingApproval }
  | .generating,        .userCancel         => { c with conv := .idle }
  | .awaitingApproval,  .userApproved       => { c with conv := .toolExecuting }
  | .awaitingApproval,  .autoApproved       => { c with conv := .toolExecuting }
  | .toolExecuting,     .toolComplete       => { c with conv := .generating }
  | .toolExecuting,     .toolError          => { c with conv := .toolError }
  | .toolError,         .retryTool          => { c with conv := .toolExecuting }
  | .toolError,         .skipTool           => { c with conv := .generating }
  | .toolError,         .userCancel         => { c with conv := .idle }
  | _, _ => c  -- events not handled in current state are ignored

-- Reachability: the set of configs reachable from initial via any event sequence
def reachable (c : Config) : Prop :=
  ∃ (events : List Event), events.foldl step initialConfig = c

-- PROOF TARGET: no reachable config has conv = idle AND ext = extensionRunning
-- simultaneously with conv = toolExecuting (example invariant)
theorem idle_toolExec_exclusive :
    ∀ c : Config, reachable c →
      ¬(c.conv = .idle ∧ c.conv = .toolExecuting) := by
  intro c ⟨events, h⟩
  intro ⟨h1, h2⟩
  simp [h1] at h2

-- PROOF TARGET: chart safety — all reachable configs satisfy the invariant
-- (fill in your specific invariant)
theorem chart_safety (inv : Config → Prop)
    (h_init : inv initialConfig)
    (h_step : ∀ c e, inv c → inv (step c e)) :
    ∀ c, reachable c → inv c := by
  intro c ⟨events, hc⟩
  subst hc
  induction events with
  | nil => exact h_init
  | cons e es ih => exact h_step _ e ih
```

The emitted code is self-contained — it imports only Mathlib tactics, not a Pneuma-specific Lean library. This is deliberate: the Lean proofs should be readable and auditable without understanding Pneuma's internals.

---

## 4. Translation: Clojure Data to Lean Terms

Each Pneuma formalism has a canonical mapping to Lean types. The mappings are mechanical and injective — no information is lost in translation.

### 4.1. Statechart → Fintype + step function

| Clojure | Lean |
|---|---|
| `#{:idle :generating ...}` | `inductive State where \| idle \| generating \| ...` |
| `{:source :idle :event :user-submit :target :generating}` | pattern match clause in `def step` |
| `{:parallel #{:session/root}}` | product type `Config := conv × ext` |
| `{:initial {:conversation :idle :extensions :extensions-idle}}` | `def initialConfig : Config` |
| Reachability | `∃ events, foldl step init events = c` |

The statechart is finite, so all types derive `Fintype` and `DecidableEq`. This means properties over reachable configurations are decidable — Lean can check them by exhaustive evaluation with the `decide` tactic. For small charts (under ~20 states), `decide` alone closes the proof. For larger charts, inductive proofs over event lists are needed.

### 4.2. Effect signature → inductive type family

| Clojure | Lean |
|---|---|
| `{:operations {:ai/generate {...} :tool/execute {...}}}` | `inductive Effect where \| generate \| execute \| ...` |
| `{:session-id :SessionId :messages [:List :Message]}` | `structure GenerateArgs where session_id : SessionId; messages : List Message` |
| `:on-complete :EventRef` | field `on_complete : Event` in the args structure |

The typed fields of each operation become a Lean structure. The `EventRef` type — which in Clojure is a keyword pointing to a transition — becomes a concrete `Event` in Lean, closing the loop.

### 4.3. Mealy transitions → dependent function type

| Clojure | Lean |
|---|---|
| `{:id :submit-prompt :guards [...] :updates [...] :effects [...]}` | clause in `def handle : State → Event → State × List Effect` |
| `{:check :in-state? :args [:sid :idle]}` | precondition `c.conv = .idle` in the function's domain |
| `{:path [...] :op :append :value ...}` | postcondition on the output state |

The handler contract becomes a function with pre/postconditions. In Lean, this is either a dependent function type (where the output type depends on the input satisfying the precondition) or a plain function with separate theorems about its behavior.

### 4.4. Morphisms → propositions about pairs

| Clojure | Lean |
|---|---|
| Existential reference `{:from :effects :to :mealy}` | `∀ cb ∈ callbacks(sig), ∃ t ∈ transitions, t.event = cb` |
| Structural match `{:from :mealy :to :effects}` | `∀ t ∈ transitions, ∀ e ∈ effects(t), valid_effect sig e` |
| Containment `{:from :caps :to :mealy}` | `caps.dispatch ⊆ transitions.events` |
| Ordering `{:a :chart :b :handler}` | `chain.indexOf chart < chain.indexOf handler` |

Each morphism becomes a proposition (a term of type `Prop` in Lean). The existential and containment morphisms become `∀ ... ∈ ...` statements. The structural match morphisms become type-compatibility propositions. The ordering morphisms are trivially decidable.

### 4.5. Composed paths → composition theorems

| Clojure | Lean |
|---|---|
| Event-effect-callback cycle | `theorem cycle_closes : ∀ t cb, raises(t) → callback(cb) → chart.accepts(target_state(t), cb)` |
| Observe-dispatch-update cycle | `theorem no_divergence : ∀ sub event, ¬(feedback_loop sub event)` or explicit convergence bound |
| Full dispatch cycle | `theorem precondition_chain : ∀ i, post(chain[i]) → pre(chain[i+1])` |

The cycle theorems are the crown jewels — they're the properties that justify the entire architectural design. They're also the hardest to prove, because they quantify over composed morphism paths.

---

## 5. The Five Proof Targets

Each proof target corresponds to a specific `->lean` invocation and produces a specific `.lean` file.

### 5.1. Chart safety

```clojure
(p/emit-lean session-chart :safety
  {:invariant '(fn [c] (not (and (= (:conv c) :idle)
                                  (= (:conv c) :tool-executing))))})
```

Emits a `.lean` file with the `State`, `Config`, `step`, `reachable` definitions and a `theorem chart_safety` that the invariant holds for all reachable configurations. For invariants over finite types, the proof is typically `by decide` or a short induction.

### 5.2. Replay determinism

```clojure
(p/emit-lean mealy-handlers :replay-determinism)
```

Emits a `.lean` file that defines the handler as a pure function, defines `replay` as the handler with effects stripped, and states the theorem that `∀ trace, state_trace(replay(trace)) = state_trace(live(trace))`. The proof follows from the purity of handlers — `strip_effects` doesn't affect the `state` component of the handler's output.

### 5.3. Morphism composition

```clojure
(p/emit-lean-morphisms
  [:chart->mealy/raised :mealy->effects/emits :effects->mealy/callbacks]
  :composition)
```

Emits a `.lean` file with all three morphism propositions and a composition theorem that their conjunction implies end-to-end type safety from chart raise to effect callback. The proof composes the three morphism proofs.

### 5.4. Cycle well-formedness

```clojure
(p/emit-lean-cycle :event-effect-callback
  {:chart session-chart :mealy mealy-handlers :effects effect-sig})
```

Emits a `.lean` file with the full cycle path, the re-entry invariant, and a theorem that the cycle closes for all transitions. This is the most complex proof target — it requires reasoning about the chart's state after the effect-callback round trip.

### 5.5. Bisimulation

```clojure
(p/emit-lean-bisim session-chart-v1 session-chart-v2)
```

Emits a `.lean` file that defines both charts, defines bisimulation as a relation over their state spaces, and either constructs the witnessing bisimulation relation (proving equivalence) or produces a distinguishing trace (proving non-equivalence). For finite charts, this is decidable by partition refinement.

---

## 6. The Proof/Test Boundary

Not everything should be proved. The split between what Lean proves and what Pneuma tests is principled:

**Lean proves properties of the specification** — statements about the mathematical objects themselves, independent of any implementation. "The statechart never reaches a bad configuration." "The effect-callback cycle always closes." "Two chart versions are bisimilar." These are architectural invariants. They're true by construction of the formalisms, and proving them once settles them permanently.

**Pneuma tests conformance of the implementation** — statements about whether the Clojure code matches the mathematical objects. "Does this handler emit the right effects?" "Does this interceptor chain have the right order?" "Does this Specter path resolve?" These change with every commit. They need to be fast, not permanent. A proof that `approve-tool` conforms to its Mealy declaration would be invalidated the moment someone edits the handler — you'd need to re-prove it every time, which defeats the purpose.

The boundary is clean:

| Property | Who checks it | Why |
|---|---|---|
| No bad chart configuration is reachable | Lean | Universal, architectural, stable |
| Replay is deterministic | Lean | Structural, follows from purity contract |
| Morphism compositions are well-typed | Lean | Combinatorial, hard to cover by sampling |
| Cycles close correctly | Lean | Universal quantification over paths |
| Bisimulation holds across refactor | Lean | Requires witnessing relation |
| Handler emits correct effects | Pneuma | Changes every commit |
| Interceptor chain has right order | Pneuma | Structural check, trivially fast |
| Atom conforms to schema | Pneuma | Runtime snapshot, needs live data |
| Extension stays within capability bounds | Pneuma | Needs runtime dispatch metadata |
| Gap report shows progress | Pneuma | Needs successive snapshots |

The interaction between the two is that Lean proofs *validate the specification*, and Pneuma tests *check the implementation against the validated specification*. Lean tells you the design is sound. Pneuma tells you the code matches the design. Together, they give you a chain from mathematical proof to running code.

---

## 7. Workflow

### 7.1. Day-to-day development

Lean is not involved. The developer works in the REPL with Pneuma's runtime checks:

```clojure
;; Edit a handler, then check
(p/gap-report {:formalisms [...] :refinement-map {...}})
;; => {:object-gaps {...} :morphism-gaps {...} :path-gaps {...}}
```

The feedback loop is sub-second. Object, morphism, and path gaps update interactively.

### 7.2. Locking down an invariant

When a property should be settled permanently — typically during design review or before a major release — the developer invokes `->lean`:

```clojure
;; Emit the chart safety proof target
(spit "proofs/chart_safety.lean"
  (p/emit-lean session-chart :safety
    {:invariant '(fn [c] (exclusive? (:conv c) (:ext c)))}))
```

The developer opens `proofs/chart_safety.lean` in VS Code with the Lean extension, fills in the proof (or discovers that the invariant doesn't hold and needs to fix the chart), and commits the `.lean` file alongside the Clojure source.

### 7.3. CI integration

The Lean files compile in CI alongside the Clojure tests. If a formalism changes in a way that invalidates a proof, the Lean file fails to compile, and the CI pipeline catches it. This is the enforcement mechanism: proved properties stay proved.

```yaml
# In CI pipeline
- name: Pneuma runtime checks
  run: clojure -M:test -m pneuma.runner

- name: Lean proof verification
  run: lake build
  working-directory: proofs/
```

### 7.4. Regeneration after formalism changes

If the Clojure formalism changes (a state is added to the chart, a new effect operation is defined), the `->lean` output changes too. The developer re-runs `(p/emit-lean ...)`, which produces updated type definitions and potentially invalidates existing proofs. The developer then repairs the proofs — or discovers that the change broke an invariant, which is exactly the situation Lean is designed to catch.

The key discipline: **never edit the generated type definitions by hand**. They are derived from the Clojure data and must stay in sync. Edit only the proof bodies. If the type definitions need to change, change the Clojure formalism and regenerate.

---

## 8. The Trust Architecture

The system has a layered trust structure:

**Most trusted: Lean's kernel.** A small C++ program that checks proof terms. It does not know about Pneuma, Clojure, or statecharts. It only knows the calculus of inductive constructions. If it accepts a proof, the theorem holds — modulo bugs in the kernel itself, which is the smallest trusted computing base in the stack.

**Highly trusted: the ->lean projection.** This is Clojure code that translates Pneuma's mathematical objects into Lean terms. If the translation is incorrect — if it produces a `step` function that doesn't match the Clojure statechart — then the Lean proofs are about the wrong system. The translation is simple enough to audit by inspection (it's a straightforward structural recursion over maps), but it is the single point where the Clojure and Lean worlds must agree.

**Trusted: Pneuma's formalisms.** The Clojure maps representing statecharts, effect signatures, and morphisms. These are the source of truth for the architecture's intent. If they're wrong — if the statechart doesn't accurately describe the intended session lifecycle — then both Lean and Pneuma are checking the wrong thing.

**Least trusted: the implementation.** The actual `defmethod` handlers, interceptor chain, Specter subscriptions, Pathom resolvers. These are what Pneuma's gap report evaluates. They are not trusted at all — they are the subject of checking, not part of the checking infrastructure.

The trust chain runs: Lean kernel → `->lean` translation → Pneuma formalisms → implementation. Each layer vouches for the one above it, and nothing vouches for the implementation — that's the whole point.

> **The critical audit surface:** The `->lean` projection is the only code that must be manually verified for correctness. Everything upstream (Lean kernel) is independently verified. Everything downstream (Pneuma runtime checks) produces sampled evidence, not proofs. If you invest review effort anywhere, invest it in the translation functions.

---

## 9. Implementation Strategy

### 9.1. Build order

The Lean integration is Phase 5 of the overall Pneuma build plan, after the three-layer runtime checking system is working.

**Step 1: Statechart emission.** This is the simplest and most valuable target. The statechart is a finite structure, Lean's `Fintype`/`DecidableEq` instances make proofs tractable, and chart safety is the most commonly desired invariant. Start here.

**Step 2: Effect signature emission.** Emit the inductive `Effect` type and field structures. This enables morphism composition proofs (step 4) because the effect types need to exist in Lean before you can state composition theorems about them.

**Step 3: Mealy handler emission.** Emit the handler contract as a Lean function. This is more complex because the handler has preconditions (guards) and postconditions (state updates, effect emissions). The replay determinism proof (Section 5.2) becomes available at this step.

**Step 4: Morphism emission.** Emit the boundary propositions. Each morphism type (existential, structural, containment, ordering) has a fixed translation pattern. The composition theorem across morphism chains becomes available here.

**Step 5: Cycle emission.** Emit the cycle-level theorems. These depend on all previous steps. The event-effect-callback cycle theorem is the primary target.

**Step 6: Bisimulation emission.** Emit the bisimulation checker for pairs of statecharts. This is useful for validating refactors but is not on the critical path.

### 9.2. Dependencies

The Lean integration adds:

- Lean 4 toolchain (installed separately, not a Clojure dependency)
- `lake` build system for managing the Lean project
- Mathlib (the Lean mathematical library) for tactic support

The Clojure side requires no new dependencies — `->lean` is a pure function from Clojure maps to strings.

### 9.3. Lean project structure

```
proofs/
├── lakefile.lean          # Lean build configuration
├── Pneuma/
│   ├── Basic.lean         # Shared definitions (SessionId, EventRef, etc.)
│   ├── Chart.lean         # Generated: statechart types + step function
│   ├── Effects.lean       # Generated: effect signature types
│   ├── Handlers.lean      # Generated: handler contracts
│   ├── Morphisms.lean     # Generated: boundary propositions
│   ├── Cycles.lean        # Generated: composition theorems
│   └── Bisim.lean         # Generated: bisimulation checker
├── Proofs/
│   ├── ChartSafety.lean   # Human-written proofs of chart properties
│   ├── Replay.lean        # Human-written replay determinism proof
│   ├── Composition.lean   # Human-written composition proofs
│   └── CycleClosure.lean  # Human-written cycle well-formedness proofs
└── README.md
```

The `Pneuma/` directory contains generated files — regenerated by `->lean` whenever the Clojure formalisms change. The `Proofs/` directory contains human-written proofs that import the generated definitions. This separation ensures that regeneration doesn't overwrite proof work.

---

## 10. Limitations and Non-Goals

### 10.1. Lean does not check the implementation

Lean proves properties of the mathematical objects — the specification, not the code. It cannot verify that `(defmethod handle-event :session/submit-prompt ...)` conforms to the Mealy transition declaration. That's Pneuma's job. The two systems have disjoint responsibilities: Lean validates the design, Pneuma checks the code against the design.

### 10.2. The translation is not formally verified

The `->lean` projection is Clojure code that produces Lean source. It is not itself proved correct in Lean. A bug in the translation could produce Lean terms that don't faithfully represent the Clojure formalisms. This is the trust boundary (Section 8). The mitigation is that the translations are structurally simple — a `for` loop over a map producing `match` clauses — and can be audited by comparing the Clojure data with the generated Lean definitions side by side.

A future extension could formalize the translation itself in Lean, proving that the generated `step` function is a faithful representation of the Clojure statechart map. This requires a Lean model of Clojure maps, which is feasible but out of scope for the initial implementation.

### 10.3. Proofs require human effort

Lean is not an automated theorem prover. It is an interactive proof assistant. The `->lean` projection emits `sorry` placeholders that the developer must fill in with proofs. For simple properties over finite types, the `decide` tactic closes the proof automatically. For more complex properties (cycle well-formedness, bisimulation), the developer must guide the proof.

This is by design. The goal is not to automate proof — it is to make proof *possible* for the properties that matter most, and to have those proofs checked by a trusted kernel. The developer effort is invested once per property and pays off permanently.

### 10.4. Not a replacement for testing

Lean proofs and Pneuma tests coexist permanently. You do not "graduate" from Pneuma to Lean. The proofs cover architectural invariants; the tests cover implementation conformance. A system with all proofs passing and all tests failing has a correct design and a broken implementation. A system with all tests passing and no proofs has high confidence but no guarantees. The goal is both.

### 10.5. Performance is not a concern

The `->lean` projection runs once, manually, when you want to lock down a property. It is not in the hot path. It can take seconds to produce the `.lean` file. The Lean compilation can take minutes for complex proofs. This is acceptable because it happens outside the development loop — it's a design-time activity, not a REPL activity.

---

## Appendix: Example End-to-End

Starting from the Clojure statechart:

```clojure
(def session-chart
  (p/statechart
    {:states #{:idle :generating :awaiting-approval
               :tool-executing :tool-error}
     :initial :idle
     :transitions
     [{:source :idle :event :user-submit :target :generating}
      {:source :generating :event :generation-complete :target :idle}
      {:source :generating :event :tool-requested :target :awaiting-approval}
      {:source :awaiting-approval :event :user-approved :target :tool-executing}
      {:source :tool-executing :event :tool-complete :target :generating}
      {:source :tool-executing :event :tool-error :target :tool-error}
      {:source :tool-error :event :retry-tool :target :tool-executing}
      {:source :tool-error :event :skip-tool :target :generating}]}))
```

Step 1: Runtime check with Pneuma.

```clojure
(p/check-schema session-chart @app-db)
;; => {:status :conforms}

(p/check-trace session-chart @event-log)
;; => {:status :conforms, :entries-checked 847}

(p/check-gen session-chart {:dispatch-fn dispatch! :num-tests 1000})
;; => {:status :conforms, :tests-run 1000}
```

Step 2: Emit Lean proof target.

```clojure
(spit "proofs/Pneuma/Chart.lean"
  (p/emit-lean session-chart :definitions))

(spit "proofs/Proofs/ChartSafety.lean"
  (p/emit-lean session-chart :safety
    {:invariant '(fn [c] (not (= c :tool-executing))
                         ;; when we're in tool-error
                         ;; (simplified example)
                         )}))
```

Step 3: Prove in Lean.

```lean
-- In proofs/Proofs/ChartSafety.lean
import Pneuma.Chart

-- The invariant: tool-executing and tool-error are never
-- simultaneously active (trivially true in non-parallel region,
-- but let's prove it anyway as an example)
theorem no_simultaneous_exec_error :
    ∀ (events : List Event),
      let c := events.foldl step .idle
      ¬(c = .toolExecuting ∧ c = .toolError) := by
  intro events
  simp
  intro h1 h2
  rw [h1] at h2
  exact ConvState.noConfusion h2
```

Step 4: CI verifies both.

```
$ clojure -M:test     # Pneuma runtime checks pass
$ cd proofs && lake build   # Lean proof checks pass
```

The chart safety invariant is now settled. If someone modifies the statechart in a way that breaks the invariant, the Lean file will fail to compile in CI. If someone modifies the implementation in a way that breaks conformance with the chart, the Pneuma gap report will flag it. Both failure modes are caught, by different mechanisms, at different levels of trust.
