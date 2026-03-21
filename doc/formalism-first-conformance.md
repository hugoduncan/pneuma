# Formalism-First Conformance Checking

**Using Mathematical Objects as Living Specifications for Event-Sourced Architectures in Clojure**

*Design Document — Draft, March 2026*

---

## Table of Contents

1. [The Core Idea](#1-the-core-idea)
2. [The Mathematical Objects](#2-the-mathematical-objects)
3. [The Projection Protocol](#3-the-projection-protocol)
4. [Morphisms — Connections Between Formalisms](#4-morphisms--connections-between-formalisms)
5. [Composed Paths — Cycle-Level Invariants](#5-composed-paths--cycle-level-invariants)
6. [The Refinement Map](#6-the-refinement-map)
7. [The Three-Layer Gap Report](#7-the-three-layer-gap-report)
8. [Why No Specification Language](#8-why-no-specification-language)
9. [Implementation Strategy](#9-implementation-strategy)
10. [Relationship to Existing Work](#10-relationship-to-existing-work)
11. [Conclusion](#11-conclusion)

---

## 1. The Core Idea

This document describes an approach to conformance checking where mathematical formalisms — not a specification language — serve as the single source of truth for an architecture's intended behavior. The formalisms live as Clojure data structures in the same runtime as the implementation they describe. Every checking artifact (schemas, monitors, generators, gap reports) is a mechanical projection from these mathematical objects.

The approach eliminates the specification language entirely. Where a conventional formal methods workflow involves writing a spec in some notation, compiling it to checking tools, and running those tools against the implementation, this design collapses the first two steps. The mathematical objects *are* the specification, and they project themselves into checking artifacts through a shared protocol.

> **The central claim:** A specification language is a lossy encoding of mathematical structure. If you remove it and work with the mathematical objects directly — represented as data in the implementation language — you get a tighter feedback loop, compositional reasoning, and richer gap analysis.

The architecture of the checking system has three layers, corresponding to increasing levels of compositional structure:

- **Objects:** six mathematical formalisms, each describing one architectural layer in isolation. A Harel statechart for session lifecycle, an algebraic effect signature for the side-effect vocabulary, Mealy machine declarations for handlers, optics for subscriptions, functional dependencies for resolvers, and capability sets for extensions.
- **Morphisms:** typed connections between formalisms. These capture the contracts at formalism boundaries — that a raised event has a handler, that an effect's callback references a valid transition, that a capability set only names defined events. There are four kinds: existential references, structural matches, containment constraints, and ordering constraints.
- **Composed paths:** cycles through the morphism graph where end-to-end invariants live. The event-effect-callback loop, the observe-dispatch-update loop, and the full interceptor-mediated dispatch cycle each impose constraints that no single formalism or morphism can see.

The gap report has a corresponding three-layer structure: object gaps (per-formalism), morphism gaps (per-connection), and path gaps (per-cycle). Most real bugs live at the morphism and path levels — a handler that omits a guard the statechart requires, an effect callback that re-enters the chart in an invalid state, a subscription path that diverges from the handler's update path.

The target architecture is an event-sourced coding agent with a single state atom, pure event handlers, effect-as-data side effects, Harel statecharts governing session lifecycle, interceptor chains for cross-cutting concerns, Specter-based reactive subscriptions, Pathom-based federated query resolution, and a capability-bounded extension system. The approach generalizes to any architecture with similar structural regularity, but the examples in this document are drawn from this specific system.

---

## 2. The Mathematical Objects

Six formalisms are needed to cover the architecture. Each one is grounded in well-studied mathematics, but represented concretely as a Clojure map. An optional seventh — the indexed coalgebra — serves as a unifying frame but is not required for the checking machinery to function.

### 2.1. Harel Statechart

The session lifecycle is governed by a statechart in the sense of Harel (1987). A statechart is a tuple *(S, ≤, T, C, H, δ)* where *S* is a finite set of states, *≤* is a hierarchy (nesting), *T* is a set of transitions, *C* is the orthogonality relation (parallel regions), *H* captures history semantics, and *δ* is the microstep function that computes a new configuration from a current configuration and an event.

As Clojure data, this is a map with keys for each component of the tuple:

```clojure
{:states     #{:idle :generating :awaiting-approval
               :tool-executing :tool-error
               :extensions-idle :extension-running}
 :hierarchy  {:session/root #{:conversation :extensions}
              :conversation #{:idle :generating :awaiting-approval
                              :tool-executing :tool-error}
              :extensions   #{:extensions-idle :extension-running}}
 :parallel   #{:session/root}
 :initial    {:conversation :idle, :extensions :extensions-idle}
 :transitions
 [{:source :idle    :event :user-submit   :target :generating
   :raise [:start-generation]}
  {:source :generating :event :generation-complete :target :idle}
  {:source :generating :event :tool-requested :target :awaiting-approval}
  {:source :generating :event :user-cancel :target :idle
   :raise [:cancel-generation]}
  {:source :awaiting-approval :event :user-approved
   :target :tool-executing :raise [:execute-tool]}
  {:source :awaiting-approval :event :auto-approved
   :target :tool-executing :raise [:execute-tool]}
  {:source :tool-executing :event :tool-complete :target :generating
   :raise [:resume-generation]}
  {:source :tool-executing :event :tool-error :target :tool-error}
  {:source :tool-error :event :retry-tool :target :tool-executing}
  {:source :tool-error :event :skip-tool :target :generating
   :raise [:resume-generation]}
  {:source :tool-error :event :user-cancel :target :idle}
  {:source :extensions-idle :event :extension-activated
   :target :extension-running}
  {:source :extension-running :event :extension-complete
   :target :extensions-idle}]}
```

The step function *δ* is computable from this data: given a configuration (set of active states) and an event, find a transition whose source is in the active set and whose event matches, then produce the new configuration by removing the source and adding the target. Parallel regions are independent — a transition in `:conversation` does not affect the active state in `:extensions`.

The key property this buys us: **reachability analysis**. From the initial configuration, we can enumerate every reachable configuration by breadth-first search over the transition relation. An invariant like "no reachable configuration contains both `:idle` and `:tool-executing` in the conversation region" is a finite check over this set.

### 2.2. Algebraic Effect Signature

The effect system follows the algebraic effects tradition. An effect signature is a set of operation symbols, each with a typed arity. In Plotkin and Power's formulation, effects are operations of an algebraic theory; in the programming languages tradition (Koka, Eff, Frank), they are the constructors of a free monad over a signature functor.

For this architecture, the effect signature describes the side effects that handlers may request. Handlers are pure — they return *descriptions* of effects, never execute them. The descriptions are interpreted at the boundary by the effect execution layer. This is precisely the free/interpret decomposition.

```clojure
{:operations
 {:ai/generate   {:session-id :SessionId :messages [:List :Message]
                   :model :ModelId
                   :on-token :EventRef :on-complete :EventRef
                   :on-error :EventRef}
  :tool/execute   {:session-id :SessionId :tool :ToolId
                   :args [:Map :String :Any]
                   :on-complete :EventRef :on-error :EventRef}
  :http           {:method :HttpMethod :url :Url :body :Any
                   :on-success :EventRef :on-failure :EventRef}
  :schedule       {:delay-ms :Nat :event :EventRef}
  :notify         {:ext-id :ExtId :type :NotifType
                   :message :String}}}
```

The mathematical content is that each operation has a return-type position (the callback events) that closes the interaction loop. The `:on-complete` and `:on-error` fields are *continuations* — they tell the system how to re-enter the pure core after the impure boundary has done its work. The replay determinism property follows: if you suppress the effect execution (as during replay), the state trace is identical because the pure handlers always produce the same `{:db, :effects}` output for the same `{:db, :event}` input.

### 2.3. Mealy Transitions

Each event handler has the shape *S × E → S × Eff\** — a current state and an event produce a new state and a list of effect descriptions. This is a Mealy machine (output depends on both state and input), but with structured output (the effect list) rather than a flat output alphabet.

When combined with a writer monad over effects and a state monad over the atom, each handler is a Kleisli arrow *E → T(Unit)* where *T(X) = S → (S × List(Eff) × X)*. The composition of handlers — including processing raised events from statechart transitions — is Kleisli composition in this monad.

As data, each transition declaration captures the handler's contract:

```clojure
{:id :submit-prompt
 :params  [{:name :sid :type :SessionId}
           {:name :prompt :type :String}]
 :guards  [{:check :member :args [:sid [:keys-of :sessions]]}
           {:check :in-state? :args [:sid :idle]}]
 :updates [{:path [:sessions :sid :messages]
            :op :append
            :value {:role :user :content :prompt}}]
 :effects [{:op :ai/generate
            :fields {:session-id :sid
                     :messages [:get [:sessions :sid :messages]]
                     :model [:get [:sessions :sid :model]]
                     :on-complete [:event-ref :generation-complete :sid]
                     :on-error [:event-ref :generation-error :sid]}}]}
```

This is not a handler implementation — it is a *mathematical description* of what the handler should do. The actual Clojure `defmethod` may or may not conform to it. The gap between the two is precisely what we measure.

### 2.4. Optics

Subscriptions are optics — lenses, traversals, and folds from the functional optics hierarchy. A lens focuses on exactly one value and supports both get and set. A traversal focuses on zero or more values. A fold is read-only. The Specter library provides the concrete implementation, but the mathematical structure is what matters for conformance checking.

Each optic declaration specifies a path into the state map and a classification:

```clojure
;; A lens: focuses on exactly one list
{:id :session-messages
 :optic-type :Lens
 :params [{:name :sid :type :SessionId}]
 :path [:sessions :sid :messages]}

;; A derived subscription: combines multiple optic sources
{:id :session-summary
 :optic-type :Derived
 :params [{:name :sid :type :SessionId}]
 :sources {:msgs [:sessions :sid :messages]
           :config [:sc/sessions :sid :config]}
 :derivations {:message-count [:length :msgs]
               :state [:first :config]
               :last-role [:get-in [:last :msgs] :role]}}
```

The change-detection property of optics is what makes subscriptions precise: a subscription with optic *O* fires when *view(O, s) ≠ view(O, s')* and is silent otherwise. This precision prevents unnecessary recomputation and is verifiable — we can check that the Specter path in the implementation resolves to the same value as the optic declaration predicts.

### 2.5. Functional Dependencies (Resolvers)

Pathom resolvers are functional dependencies in the database-theoretic sense: each resolver declares a set of input attributes and a set of output attributes, asserting that the outputs are determinable from the inputs. The resolver graph is an attribute-labeled directed hypergraph where each resolver is a hyperedge from its input set to its output set.

```clojure
{:resolvers
 [{:id :session-messages-resolver
   :input #{:session/id}
   :output #{:session/messages :session/message-count}
   :source :local}
  {:id :git-status
   :input #{:session/working-dir}
   :output #{:session/git-status :session/git-branch}
   :source [:external :git]}
  {:id :file-contents
   :input #{:file/path}
   :output #{:file/contents :file/language}
   :source [:external :filesystem]}]}
```

Query planning is reachability in this hypergraph: given a set of known attributes, which output attributes can be reached by chaining resolvers? This is equivalent to the chase algorithm in database dependency theory, or to Datalog evaluation. The check is: for every query an extension can issue, are all requested attributes reachable from the available inputs?

### 2.6. Capability Sets

Each extension agent operates within a bounded capability set — a finite description of which events it may dispatch, which subscriptions it may observe, and which queries it may issue. The mathematical foundation is substructural: capabilities are resources that must be explicitly granted, not ambient authorities.

```clojure
{:id :TestRunnerCaps
 :dispatch  #{:session/inject-context :session/request-tool
              :extension/store-data}
 :subscribe #{:session/messages :session/active-states
              :session/tool-history}
 :query     #{:session/git-status :session/last-test-run}}
```

The safety property is: every dispatch, subscribe, and query operation performed by an agent with capability set *C* must be a member of *C*. This is checkable both statically (does the extension code reference events outside its set?) and dynamically (does the interceptor chain enforce the bound at runtime?).

### 2.7. The Indexed Coalgebra (Unifying Frame)

An indexed coalgebra is a family *αᵢ : S → Fᵢ(S)* where each component observes shared state *S* through its own functor *Fᵢ*. The six formalisms above are components of one indexed coalgebra over the shared state atom:

- The statechart component observes the state through the chart configuration lens.
- The Mealy component observes the state through the full (db, event) → (db', effects) step.
- The optics component observes the state through each declared lens/traversal/fold.
- The resolver component observes the state through attribute dependencies.
- The capability component observes the state through the extension metadata on events.

The indexed coalgebra gives us **bisimulation** as a notion of behavioral equivalence: two system states are indistinguishable to agent *i* if and only if the functor *Fᵢ* produces the same observation on both states. This is the formal criterion for when a refactor is safe — if the observations are bisimilar, no extension or frontend can tell the difference.

The unifying frame is not required for the checking machinery. Each formalism stands alone, projects itself into checking artifacts independently, and produces its own section of the gap report. The coalgebra provides a language for talking about how the formalisms relate to each other, which becomes important when reasoning about cross-layer properties (like: does the statechart's guard imply the Mealy handler's precondition?).

---

## 3. The Projection Protocol

Each mathematical object implements a single protocol that projects it into four kinds of checking artifacts. The protocol is the core mechanism of the entire system — it is how abstract mathematics becomes concrete runtime checking.

```clojure
(defprotocol IProjectable
  (->schema  [this]  "Produce a Malli schema for structural validation.")
  (->monitor [this]  "Produce a trace monitor for behavioral checking.")
  (->gen     [this]  "Produce a test.check generator for property testing.")
  (->gap-type [this] "Produce the gap type descriptor for this formalism."))
```

The protocol is deliberately minimal. Each method takes only `this` — the mathematical object — and returns a checking artifact. There is no compilation context, no symbol table, no cross-references to other formalisms. Each formalism is self-contained in what it can project. Cross-formalism checks (like verifying that raised events from the statechart have corresponding Mealy handlers) happen at a higher level, after the individual projections.

### 3.1. Schema Projection

`->schema` produces a Malli schema that validates the structural shape of the relevant state. The output is a standard Malli schema value — it can be passed to `m/validate`, `m/explain`, or `mg/generate` directly.

What each formalism projects as a schema:

- Harel statechart → the set of valid configurations as a Malli `:enum`.
- Effect signature → a Malli `:multi` schema keyed on `:effect/type`, each branch validating the operation's fields.
- Mealy transitions → input/output schemas for each handler: what shape the db must have before, what shape after.
- Optics → the return type of each optic when applied to a conforming state.
- Functional dependencies → the attribute reachability graph (which attributes are derivable from which inputs).
- Capability sets → the set membership constraints (each dispatched event must be in the declared set).

### 3.2. Monitor Projection

`->monitor` produces a function that consumes event log entries and returns verdicts. The event log is the existing log already captured by the system's log interceptor — no additional instrumentation is needed.

Each log entry has the shape:

```clojure
{:event     [:session/submit-prompt :s1 "Fix the test"]
 :db-before {... snapshot ...}
 :db-after  {... snapshot ...}
 :effects   [{:effect/type :ai/generate ...}]
 :timestamp 1711234567890}
```

The monitor for a Harel statechart checks that the configuration transition implicit in each log entry (the chart states active in `:db-before` vs. `:db-after`) is a valid step in the chart's step function *δ*. The monitor for an effect signature checks that every effect description in `:effects` conforms to the signature's operation schema. The monitor for a Mealy transition checks that the state diff (`:db-before` vs. `:db-after`) matches the declared updates. Each monitor is independent and can be run in parallel.

### 3.3. Generator Projection

`->gen` produces a test.check generator that creates valid inputs for property-based testing.

The statechart's generator is the most interesting: it performs a random walk over the reachable state space, producing sequences of events that are *valid according to the chart*. These sequences are then fed through the actual `dispatch!` function, and the results are checked by the monitors. This is the bridge between the mathematical model and the running code — the model generates the test cases, the code runs them, and the monitors judge the outcomes.

Effect signature generators produce well-typed effect description maps. Mealy transition generators produce `(db, event)` pairs where the db satisfies the handler's preconditions. Optic generators produce state maps where the optic resolves to a value of the declared type.

### 3.4. Gap Type Projection

`->gap-type` produces the data structure that describes how a conformance failure is classified for this formalism. Each formalism has its own gap taxonomy:

- Statechart gaps: missing state, missing transition, unreachable state, invalid configuration.
- Effect gaps: absent operation, missing field, wrong field type, undeclared callback target.
- Mealy gaps: absent handler, missing guard, wrong state update, wrong effect emission.
- Optic gaps: broken path (Specter path doesn't resolve), wrong derivation, missing subscription.
- Resolver gaps: missing resolver, unreachable attribute, wrong output attributes.
- Capability gaps: unauthorized dispatch, unauthorized subscription, missing enforcement.

Every gap is assigned one of three statuses: **conforms** (spec equals implementation), **absent** (spec exists, implementation doesn't), or **diverges** (both exist, they disagree). The diverges status carries a structured payload specific to the gap type — not just "wrong" but *how* it's wrong.

---

## 4. Morphisms — Connections Between Formalisms

The six formalisms do not exist in isolation. They reference each other constantly: a handler guard names a chart state, an effect callback names a transition, a capability set names an optic. These cross-references are where most real bugs live — not inside a single formalism, but at the boundary between two. A renamed chart state silently breaks seven guards. A missing callback target means an effect completes into the void.

We promote these connections to first-class mathematical objects called **morphisms**. Each morphism is a typed edge in the formalism graph, connecting a source formalism to a target formalism with a specific contract. Morphisms implement their own checking protocol, produce their own gap types, and can generate their own test cases.

```clojure
(defprotocol IConnection
  (check   [this a b] "Check the boundary contract between a and b.")
  (->gap   [this a b] "Produce a typed gap if the contract is violated.")
  (->gen   [this a b] "Generate test cases that exercise the boundary."))
```

### 4.1. Four Kinds of Morphism

Every connection between two formalisms falls into one of four categories, each with different checking semantics.

#### 4.1.1. Existential references

One formalism names an identifier that must exist in another. A Mealy handler guard says `in-state? sid :idle` — `:idle` must be a state in the statechart. An effect's `:on-complete` says `:generation-complete` — that must be a defined transition. If either side is renamed, the reference dangles.

Checking is set membership: for each reference in the source formalism, verify the identifier exists in the target formalism's namespace. The gap type is `dangling-ref` with fields for the source formalism, target formalism, and missing identifier. The generator produces rename scenarios — systematically changing identifiers in one formalism and verifying that the reference check catches the break.

The full inventory of existential references in this architecture:

- Mealy guards reference statechart states (in-state? checks).
- Effect callback fields reference Mealy transition names (on-complete, on-error, on-token).
- Statechart raise clauses reference events that must be handled by some transition.
- Capability dispatch sets reference Mealy transition names.
- Capability subscribe sets reference optic identifiers.
- Capability query sets reference resolver output attributes.
- Derived subscription sources reference optic paths that must resolve.

#### 4.1.2. Structural matches

One formalism produces output that must conform to another's schema. A Mealy handler emits an effect description — that description must match the effect signature's declared fields and types. The statechart raises an event — that event must be consumable by some handler with the right parameter shape.

Checking uses Malli: the source formalism's output is validated against the target formalism's input schema. The gap type is `shape-mismatch` with the path to the divergence, the expected type, and the actual type. The generator produces values at the source formalism's output boundary and feeds them to the target, exercising the shape contract.

The structural matches in this architecture:

- Mealy handler effect emissions must match the effect signature operation schema.
- Statechart raised events must match the parameter shape of the handling transition.
- Mealy handler state updates must produce state that conforms to the declared schema.

#### 4.1.3. Containment constraints

One formalism's declared set must be a subset of another's defined set. A capability set's `:dispatch` entries must all be defined transitions. Its `:subscribe` entries must all be defined optics. Handler update paths must write to locations that the declared optics can read.

Checking is set difference: compute `declared(A) \ defined(B)` and report any non-empty result. The gap type is `out-of-bounds` with the violating items and the bound they exceeded. The generator produces test cases that use only the intersection — valid inputs that exercise the boundary — and separately produces inputs from the difference set to verify they're caught.

The containment constraints in this architecture:

- Capability dispatch ⊆ defined Mealy transitions.
- Capability subscribe ⊆ defined optic identifiers.
- Capability query attributes ⊆ reachable resolver output attributes.
- Handler update paths ⊆ paths observed by declared optics.

#### 4.1.4. Ordering constraints

The interceptor chain imposes a sequential ordering on when formalisms act. The permission interceptor (capabilities) must fire before the statechart interceptor. The statechart must fire before the handler (Mealy). The validator must fire after the handler. These are temporal precedence constraints on the dispatch pipeline.

Checking is index comparison: for each declared ordering pair, verify that the earlier formalism's interceptor appears before the later one in the chain. The gap type is `order-violation` with the two formalisms and their actual positions. There is no generator for ordering constraints — they are structural, not behavioral.

### 4.2. The Connection Registry

The registry enumerates all morphisms as data:

```clojure
{:chart->mealy/guards      {:type :existential :from :chart :to :mealy}
 :chart->mealy/raised      {:type :structural  :from :chart :to :mealy}
 :mealy->effects/emits     {:type :structural  :from :mealy :to :effects}
 :effects->mealy/callbacks {:type :existential :from :effects :to :mealy}
 :caps->mealy/dispatch     {:type :containment :from :caps :to :mealy}
 :caps->optics/subscribe   {:type :containment :from :caps :to :optics}
 :caps->resolvers/query    {:type :containment :from :caps :to :resolvers}
 :mealy->optics/updates    {:type :containment :from :mealy :to :optics}
 :optics->resolvers/feeds  {:type :existential :from :optics :to :resolvers}
 :chain/perm-before-chart  {:type :ordering :a :permissions :b :chart}
 :chain/chart-before-handler {:type :ordering :a :chart :b :handler}
 :chain/handler-before-validator {:type :ordering :a :handler :b :validator}}
```

The registry is itself computable. Given the set of formalisms, we can enumerate every pair and determine which morphism types apply by inspecting the formalism's interface — does it reference identifiers? Does it produce structured output consumed by another? Does it declare sets that must be bounded? The enumeration is a fixed-point computation over the formalism graph's adjacency structure.

This matters because adding a new formalism automatically generates new connections. If you introduce a "timing constraints" formalism that references chart states and Mealy transitions, the existential reference morphisms to those formalisms are discovered, not manually declared.

> **Why morphisms matter:** In isolation, each formalism can be internally consistent — every chart state has transitions, every effect operation has typed fields, every handler has a guard. But the system fails at the boundaries: a guard references a state that was renamed, a callback targets a transition that was removed, a capability permits an event that no handler accepts. Morphisms are where the integration bugs live, and they need the same formal treatment as the formalisms themselves.

---

## 5. Composed Paths — Cycle-Level Invariants

The morphisms form a directed graph over the formalisms. This graph has cycles, and those cycles are where the strongest architectural invariants live — invariants that cannot be stated in any single formalism or any single morphism, but only in their composition.

Mathematically, this is category structure. The formalisms are objects, the morphisms are arrows, and composition says: if there is a morphism *f : A → B* and a morphism *g : B → C*, then the composition *g ∘ f : A → C* must be well-typed. When the composed path forms a cycle back to the starting formalism, the composition law says the cycle must be *consistent* — the data that exits the cycle must be acceptable to the formalism where it re-enters.

### 5.1. The Event-Effect-Callback Cycle

This is the tightest and most critical cycle in the architecture:

1. The statechart processes an event and raises a new event (e.g. `:start-generation`).
2. The raised event dispatches to a Mealy handler, which emits an effect (e.g. `ai/generate`).
3. The effect description contains a callback event reference (e.g. `:on-complete` → `:generation-complete`).
4. When the effect completes, the callback event re-enters the statechart as a new event.

The cycle-level invariant is: **when the callback event re-enters the chart, the chart must be in a state that accepts it**. The chart must be in `:generating` when `:generation-complete` arrives, not `:idle` or any other state. But nothing in the statechart alone knows what state the chart will be in after the effect completes — that depends on what other events might arrive in the meantime. And nothing in the effect signature alone knows which chart state the callback requires — it only knows the callback's name.

The cycle invariant is checkable by composing the morphisms: trace the path from the chart transition that raises the event, through the handler, through the effect signature, to the callback event, and verify that the callback's target state is reachable from the chart state the system will be in when the callback fires. This is a reachability query over the composed morphism chain.

```clojure
;; Cycle: chart --raise--> mealy --emit--> effects --callback--> chart
{:cycle :event-effect-callback
 :path [:chart :mealy :effects :chart]
 :invariant
 (fn [chart mealy effects]
   (for [t (transitions-that-raise chart)
         :let [raised (:raise t)
               handler (find-handler mealy raised)
               emitted (effects-of handler)
               callbacks (callback-events emitted)]
         cb callbacks
         :let [re-entry-state (target-state-after t)
               valid? (accepts-event? chart re-entry-state cb)]]
     {:transition t :callback cb
      :re-entry-state re-entry-state
      :valid? valid?}))}
```

### 5.2. The Observe-Dispatch-Update Cycle

This cycle governs extension behavior:

1. An extension subscribes to state via an optic (e.g. `session-messages`).
2. When the optic fires, the extension dispatches an event within its capability bounds (e.g. `session/inject-context`).
3. A Mealy handler processes the event and updates state, potentially at a path the optic observes.
4. If the update path overlaps the optic path, the optic fires again, potentially creating an infinite loop.

The cycle-level invariant has two parts. First, the **convergence property**: the cycle must terminate. An extension's reaction to a subscription notification must not trigger the same subscription again, or if it does, the system must converge in finite steps. Second, the **path coherence property**: the optic paths the extension observes must be related to the state paths the handler updates — otherwise the extension is reacting to data and acting on unrelated data, which is probably a bug even if it doesn't loop.

```clojure
;; Cycle: optics --observe--> caps --dispatch--> mealy --update--> optics
{:cycle :observe-dispatch-update
 :path [:optics :caps :mealy :optics]
 :invariant
 (fn [optics caps mealy]
   (for [sub (subscriptions-visible-to caps)
         :let [watch-path (optic-path optics sub)]
         event (dispatchable-events caps)
         :let [handler (find-handler mealy event)
               update-paths (update-paths-of handler)
               overlaps? (paths-overlap? watch-path update-paths)]]
     {:subscription sub :event event
      :watch-path watch-path :update-paths update-paths
      :feedback-risk overlaps?}))}
```

### 5.3. The Full Dispatch Cycle

The largest cycle encompasses the entire interceptor chain and contains the other two cycles as subsequences:

1. An event enters the system.
2. The permission interceptor checks it against the dispatching agent's capability set.
3. The statechart interceptor processes it against the active configuration, raising domain events.
4. The handler interceptor runs the Mealy handler, producing state updates and effect descriptions.
5. The validator interceptor checks the new state against the schema.
6. The state is applied to the atom. Subscriptions are notified.
7. Effects are executed. Callbacks produce new events that re-enter the cycle.

The cycle-level invariant is **precondition chaining**: each step's precondition must be established by the previous step. The statechart expects a valid configuration in the state — established by the previous dispatch cycle's handler. The handler expects that the statechart has validated the transition — established by the statechart interceptor running first. The validator expects that the handler has produced conforming state — established by the handler interceptor. The ordering constraint morphisms (Section 4.1.4) are a necessary condition, but precondition chaining is stronger: it's not just that A runs before B, but that A's postcondition implies B's precondition.

This is the formal content of the interceptor chain. The chain is not just an ordered list of middleware — it is a composed morphism where each step's contract depends on the previous step's guarantee. A missing interceptor is not just an absent feature; it is a broken link in the precondition chain, and every subsequent interceptor's guarantees become void.

### 5.4. Path Enumeration and Checking

The set of cycles is computable from the morphism graph by standard cycle-detection algorithms (Tarjan's, Johnson's). Each cycle produces a path-level checker that composes the morphisms along the path and verifies the end-to-end invariant.

Path-level generators are more powerful than morphism-level generators. A morphism generator produces test cases at one boundary. A path generator produces entire execution scenarios — sequences of events that traverse the full cycle and test the re-entry invariant. The statechart's reachability analysis guides the generation: only event sequences that correspond to valid paths through the chart are produced, ensuring the tests are meaningful rather than random.

> **The categorical structure:** This is literally a category. Objects = formalisms. Morphisms = connections. Composition = path traversal. The composition law (well-typedness of composed morphisms) is the cycle invariant. The identity morphism on each formalism is its internal consistency check (IProjectable). This structure is not metaphorical — it is the data structure you implement. The morphism graph is a Clojure map. The composition table is computed from it. The gap report is the failure set of the categorical laws.

---

## 6. The Refinement Map

The refinement map is the bridge between the mathematical objects and the Clojure implementation. It is a function *r : ConcreteState → AbstractState* that explains how to read the formalism's concepts out of the running system.

For this architecture, the refinement map is almost trivial because of the single-atom design. The entire concrete state is one Clojure map, and the mathematical objects describe structure within that same map. The refinement map is essentially a collection of Specter paths:

```clojure
{:atom-ref       #'agent.state/app-db
 :session-state  (fn [db sid] (get-in db [:sessions sid]))
 :chart-config   (fn [db sid] (get-in db [:sc/sessions sid :config]))
 :active-effects (fn [db] (:pending-effects db))
 :event-log-ref  #'agent.state/event-log
 :source-nss     '[agent.handlers agent.effects agent.interceptors
                   agent.subscriptions agent.resolvers]}
```

When the refinement map is well-defined, checking becomes mechanical: the schema projection validates `(m/explain schema @atom-ref)`; the monitor replays the event log and checks each entry; the generator produces events, dispatches them, and verifies the outcomes.

When the implementation is partial or incorrect, the refinement map *breaks down* — and **where it breaks down is the gap report**. The failure can happen at any of the three levels. An object-level failure: the schema check finds the atom doesn't match the statechart's expected configuration set. A morphism-level failure: the monitor finds an event log entry where a handler emitted an effect with a missing callback field. A path-level failure: the generator produces an event sequence that traverses the effect-callback cycle and the chart rejects the re-entry event.

> **Architectural advantage:** The single-atom, event-sourced, effects-as-data design was chosen for testability and replay. It turns out these same properties make formal conformance checking nearly free. The event log provides the traces. The atom provides the snapshots. The effect descriptions provide the behavioral observations. Most architectures require heavy instrumentation to extract this data.

---

## 7. The Three-Layer Gap Report

The gap report is the output of the entire system. It is a typed data structure — a Clojure map — with three layers corresponding to the three levels of structure: objects, morphisms, and composed paths.

### 7.1. Layer 1 — Object Gaps

Per-formalism conformance, as produced by the `IProjectable` protocol. Each formalism reports which of its components are present, absent, or divergent in the implementation.

```clojure
 :object-gaps
 {:chart
  {:states
   {:idle :conforms, :generating :conforms,
    :awaiting-approval :conforms, :tool-executing :conforms,
    :tool-error :absent}
   :transitions
   {:idle->generating :conforms,
    :tool-executing->tool-error :absent,
    :tool-error->tool-executing :absent}}
  :effects
  {:ai/generate :conforms,
   :tool/execute {:status :diverges
                  :detail {:missing-fields #{:on-error}}}
   :http :absent, :schedule :absent}
  :interceptors
  {:chain-order {:expected [:perm :log :chart :handler :validate :trim]
                 :actual [:log :handler]
                 :missing #{:perm :chart :validate :trim}}}}
```

### 7.2. Layer 2 — Morphism Gaps

Per-connection conformance, as produced by the `IConnection` protocol. Each morphism reports whether the boundary contract between its source and target formalisms holds.

```clojure
 :morphism-gaps
 {:existential
  [{:id :chart->mealy/guards
    :status :conforms}
   {:id :effects->mealy/callbacks
    :status :diverges
    :detail {:dangling-refs
             [{:from [:tool/execute :on-error]
               :target :tool-execution-error
               :reason :no-such-transition}]}}
   {:id :caps->optics/subscribe
    :status :conforms}]
  :structural
  [{:id :mealy->effects/emits
    :status :diverges
    :detail {:shape-mismatches
             [{:transition :approve-tool
               :effect :tool/execute
               :path [:on-error]
               :expected :EventRef
               :actual :missing}]}}]
  :containment
  [{:id :caps->mealy/dispatch
    :status :absent
    :reason :capability-system-not-implemented}
   {:id :mealy->optics/updates
    :status :diverges
    :detail {:unobserved-updates
             [{:handler :approve-tool
               :update-path [:sessions :sid :tool-history]
               :no-optic-covers-path true}]}}]
  :ordering
  [{:id :chain/chart-before-handler
    :status :diverges
    :detail {:expected-before :chart
             :expected-after :handler
             :actual-chain [:log :handler]
             :reason :chart-interceptor-absent}}]}
```

### 7.3. Layer 3 — Path Gaps

Per-cycle conformance, checking the end-to-end invariant across the full composed path. These are the deepest bugs — they emerge only from the interaction of multiple formalisms.

```clojure
 :path-gaps
 {:event-effect-callback
  [{:transition {:source :generating :event :tool-requested
                 :target :awaiting-approval}
    :handler :tool-requested-handler
    :effect :tool/execute
    :callback :tool-execution-complete
    :re-entry-state :awaiting-approval
    :expected-re-entry-state :tool-executing
    :status :diverges
    :detail "Callback re-enters chart at :awaiting-approval
             but :tool-execution-complete requires :tool-executing.
             The handler advanced the chart to :awaiting-approval
             instead of :tool-executing before emitting the effect."}]
  :observe-dispatch-update
  [{:subscription :session-messages
    :extension :test-runner
    :dispatches :session/inject-context
    :handler-updates [:sessions :sid :session/context]
    :feedback-risk false
    :status :conforms}]
  :full-dispatch
  [{:precondition-chain
    [{:step :permissions :postcondition :event-authorized
      :status :absent :reason :interceptor-missing}
     {:step :chart :postcondition :transition-valid
      :status :absent :reason :interceptor-missing}
     {:step :handler :postcondition :state-updated
      :status :conforms}
     {:step :validator :postcondition :state-schema-holds
      :status :absent :reason :interceptor-missing}]}]}
```

### 7.4. Reading the Gap Report

The three layers are read bottom-up for diagnosis. An object gap ("the `:tool-error` state is absent") explains *what* is missing. A morphism gap ("the effect callback references a non-existent transition") explains *where* the integration fails. A path gap ("the callback re-enters the chart in the wrong state") explains *why* the system misbehaves end-to-end.

The three layers are computed top-down for efficiency. Object gaps are cheapest — they're structural checks against the atom and the source code. Morphism gaps require pairs of objects and are slightly more expensive. Path gaps require composed sequences and are the most expensive, involving reachability analysis and generative testing. Skipping to path-level checking without resolving object-level gaps first wastes time: a missing handler (object gap) will cascade into a dangling callback reference (morphism gap) and a broken re-entry invariant (path gap). Fix the object gap and the other two may resolve automatically.

The gap report is itself data. It can be diffed against previous reports to show implementation progress. It can be filtered by layer, by formalism, or by severity. The trajectory of gap reports over time — the series of partial simulation relations as the implementation converges on the specification — is a computable audit trail of architectural conformance.

---

## 8. Why No Specification Language

The spec-language-free approach is not merely a simplification. It enables capabilities that a spec language cannot provide.

### 8.1. Computation Over Formalisms

Because the mathematical objects are live Clojure data, you can compute with them. Take the parallel product of two statecharts to model their joint behavior. Compute the transitive closure of the resolver dependency graph to determine attribute reachability. Check whether the intersection of two capability sets is empty. Compute the bisimulation quotient of two chart versions to verify that a refactor preserves observable behavior.

Critically, you can compute over the *morphism graph* too. Enumerate all cycles. Check that every existential reference resolves. Compute the longest path through the formalism graph and identify the invariant it imposes. None of these operations are possible when the formalism is encoded as text in a spec file.

### 8.2. Same-Runtime Feedback

The math objects live in the same process as the implementation. When you modify a transition declaration at the REPL, the schema, monitor, and generator projections update immediately — they are derived values, not compiled artifacts. The morphism checks also re-run: did your rename break an existential reference? Did your new effect field violate a structural match? The answer is instant.

The gap report can be computed interactively during development. Run `(gap-report)` at the REPL after changing a handler, and see instantly whether object, morphism, and path gaps are resolved or introduced.

### 8.3. Compositional Extension

Adding a new formalism means implementing `IProjectable` for a new record type. Adding the connections means implementing `IConnection` for each morphism to other formalisms. The existing formalisms and morphisms don't change. There is no grammar to extend, no parser to modify, no new syntax to design. The morphism graph grows automatically as new edges are discovered.

### 8.4. Richer Gap Analysis

A spec language can tell you "this handler is wrong." The formalism-first approach can tell you "this handler is wrong because it omits a guard that the statechart requires for the `:awaiting-approval → :tool-executing` transition (morphism gap), and this missing guard means the effect-callback cycle cannot close correctly because the callback will re-enter the chart in the wrong state (path gap)." The causal chain from object gap to morphism gap to path gap is traversable because all the structure is in memory and queryable.

---

## 9. Implementation Strategy

The system is built bottom-up through the three layers. Objects first, then morphisms, then composed paths. Each layer is independently useful before the next exists.

### 9.1. Build Order

**Phase 1 — Objects (formalisms in isolation):**

- Harel statechart as data, with `->schema` and `->monitor`. This alone catches invalid state transitions.
- Effect signature as data, with `->schema`. Catches malformed effect descriptions.
- Mealy transition declarations, with `->monitor` and `->gen`. Catches handler bugs via generative testing.
- Optic declarations, with path-resolution checking. Catches broken subscription paths.
- Resolver functional dependencies, with reachability computation. Catches unreachable attributes.
- Capability sets, with `->monitor`. Catches permission violations.

**Phase 2 — Morphisms (connections between formalisms):**

- Existential reference checks across all pairs. Catches dangling references after renames.
- Structural match checks (Mealy output vs. effect signature input). Catches shape mismatches.
- Containment checks (capability sets vs. defined events/optics/resolvers). Catches over-broad permissions.
- Ordering checks (interceptor chain positions). Catches missing or misordered interceptors.

**Phase 3 — Composed paths (cycle-level invariants):**

- Event-effect-callback cycle checker. Catches broken re-entry conditions.
- Observe-dispatch-update cycle checker. Catches subscription feedback loops.
- Full dispatch precondition chain checker. Catches broken interceptor dependencies.

**Phase 4 — Gap report synthesis:**

- Merge all three layers into the unified report structure.
- Gap diffing across time (compare successive reports to show progress).
- REPL integration for interactive conformance checking.

### 9.2. Dependencies

The checking system requires only the standard Clojure ecosystem:

- Malli for schema validation and generation (structural checking).
- test.check for property-based testing (behavioral checking via generators).
- Specter for path resolution verification (optic checking).
- The existing event log and state atom (no new infrastructure).

No external theorem provers, model checkers, or formal methods tools are required for the core checking loop. The mathematical objects are Clojure maps; the projections are Clojure functions; the morphism checks are Clojure functions over pairs of maps; the gap report is a Clojure map. The entire system lives in one codebase, one runtime, one REPL.

### 9.3. What To Defer

The indexed coalgebra framing (Section 2.7) is useful for understanding how the formalisms relate but is not needed for the checking machinery. Build it only if cross-formalism reasoning becomes a bottleneck.

Bisimulation computation over statecharts is valuable for verifying refactors but is a second-order concern — get basic gap reporting working first.

Automatic cycle enumeration (Johnson's algorithm over the morphism graph) can be deferred by hardcoding the three known cycles. Generalize only when new formalisms introduce new cycles.

Integration with external tools (Alloy for relational model-finding, TLA+ for temporal model checking, Lean for proof) is a future extension. The mathematical objects can be serialized to these tools' input formats, but this requires translation layers that are not part of the core system.

---

## 10. Relationship to Existing Work

This approach draws on several research traditions but combines them in a way that, to our knowledge, has not been articulated before.

### 10.1. Runtime Verification

The trace monitor projection is a form of runtime verification. The key difference from standard runtime verification is that the monitors are *derived from* mathematical objects rather than written independently, and that they check at three levels (object, morphism, path) rather than a single property level.

### 10.2. Refinement Checking

The gap report describes a partial refinement relation. The three-layer structure refines the classical approach: object gaps are structural refinement failures, morphism gaps are interface refinement failures, and path gaps are behavioral refinement failures. The architectural patterns in this system — single atom, event sourcing, effects as data — make the refinement map nearly trivial, which is what makes the approach practical.

### 10.3. Algebraic Effects

The effect signature formalism follows Plotkin and Power's algebraic effects, as implemented in languages like Koka, Eff, and Frank. The contribution here is the observation that the free/interpret decomposition — chosen for software engineering reasons — also provides the mathematical structure needed for formal conformance checking, *including* the morphism-level checks on callback references and structural matches with handler output.

### 10.4. Category Theory

The three-layer architecture (objects, morphisms, composed paths) is a category in the technical sense, and the cycle-level invariants are composition laws. This connects to Jacobs' coalgebras for state-based systems, Turi and Plotkin's mathematical operational semantics, and the profunctor optics literature. The connection graph structure is an instance of what the applied category theory community calls a "typed graph" or "knowledge graph with schema" — a graph where the edges carry typed contracts.

### 10.5. Property-Based Testing

The generator projections extend property-based testing in the QuickCheck tradition. The novelty is three-fold: generators are derived from mathematical objects, not hand-written; morphism-level generators test boundary contracts between components, not just component internals; and path-level generators produce entire execution scenarios that traverse architectural cycles, not just individual function calls.

---

## 11. Conclusion

The central insight is that a specification language is a detour. If the goal is to check whether an implementation conforms to an architectural intent, and if that intent has mathematical structure, then the shortest path is to represent the mathematics directly as data and project checking artifacts mechanically.

The deeper insight — the one this revision adds — is that the components are not enough. A system where every formalism is internally consistent can still be broken at the boundaries. A handler can be well-typed and still emit effects whose callbacks dangle. A statechart can be well-formed and still reject events that arrive via the effect-callback cycle. A capability set can be well-bounded and still permit events to no-handler targets. The connections between formalisms need the same formal treatment as the formalisms themselves.

The three-layer architecture (objects, morphisms, composed paths) captures this. Objects describe components. Morphisms describe contracts at boundaries. Composed paths describe end-to-end invariants that emerge from interaction. The gap report reflects all three layers, giving diagnosis at increasing depth: *what* is missing, *where* the integration fails, and *why* the system misbehaves.

This works because of two coincidences that are not coincidences. First, the architecture was designed for testability and replay — single atom, pure handlers, effects as data, event log — and these same properties make formal conformance checking tractable. Second, Clojure's data-oriented design means mathematical objects have natural representations as Clojure values. There is no impedance mismatch between the math and the code.

The result is a conformance checking system that lives in the REPL, requires no external tools, produces three-layer structured diagnostic output, and gets richer as you add more formalisms and discover more connections between them. It turns the event log you already have into a formal verification trace, the atom you already have into a model-checkable state space, the effects you already describe as data into a verifiable interaction protocol, and — most importantly — the *connections* between these layers into checkable architectural invariants.
