# Pneuma

**Living mathematical specifications for Clojure architectures.**

Pneuma embeds mathematical formalisms — statecharts, effect signatures, optics, functional dependencies — directly as Clojure data in the same runtime as your implementation. No specification language. No compilation step. The math objects project themselves into Malli schemas, trace monitors, generative tests, and structured gap reports that tell you exactly where and how your code diverges from your architectural intent.

```clojure
(require '[pneuma.core :as p])

;; Define the math. It lives in your runtime.
(def session-chart
  (p/statechart
    {:states #{:idle :generating :awaiting-approval :tool-executing :tool-error}
     :initial :idle
     :transitions
     [{:source :idle :event :user-submit :target :generating}
      {:source :generating :event :tool-requested :target :awaiting-approval}
      {:source :awaiting-approval :event :user-approved :target :tool-executing}
      {:source :tool-executing :event :tool-complete :target :generating}
      {:source :tool-executing :event :tool-error :target :tool-error}
      {:source :generating :event :generation-complete :target :idle}]}))

;; One call. Schemas, monitors, generators, gap report.
(p/gap-report
  {:formalisms [session-chart effect-sig mealy-handlers optics resolvers caps]
   :refinement-map {:atom #'app-db :event-log #'event-log}})
```

```clojure
;; The report is data. Three layers deep.
{:object-gaps    {...}  ;; per-formalism: what's missing
 :morphism-gaps  {...}  ;; per-connection: where integration breaks
 :path-gaps      {...}} ;; per-cycle: why the system misbehaves end-to-end
```

---

## Why

Most formal methods tools live outside your codebase. You write a spec in one language, compile it with a separate toolchain, and run checkers that produce opaque pass/fail results. The spec and the code drift apart. The checking happens in CI if you're lucky, never if you're not.

Pneuma takes a different position: the mathematical objects that describe your architecture should be values in your runtime, queryable at the REPL, updated in the same editor session as your code, and capable of telling you — instantly, interactively — how your implementation diverges from your intent.

The formalisms aren't decorative. Each one mechanically projects into checking artifacts:

| Formalism | `->schema` | `->monitor` | `->gen` |
|---|---|---|---|
| Harel statechart | Valid configurations | Event log vs. step function δ | Random walks over reachable states |
| Effect signature | Per-operation field schemas | Effect descriptions in log | Well-typed effect maps |
| Mealy transitions | Handler input/output shapes | db-before/db-after diffs | (state, event) pairs |
| Optics | Return types of `view` | Subscription change detection | States where paths resolve |
| Func. dependencies | Attribute reachability | Resolver output validation | Reachable query inputs |
| Capability sets | Set membership bounds | Extension dispatch metadata | Bounded event selections |

---

## The three layers

Pneuma checks your system at three levels of structure. Each level catches a different class of bug, and each level builds on the one below it.

### Objects — components in isolation

Each formalism implements `IProjectable` and produces its own schemas, monitors, generators, and gap types. A statechart checks that its states and transitions exist. An effect signature checks that its operations have the right fields. Object-level gaps tell you *what* is missing.

### Morphisms — contracts at boundaries

Formalisms reference each other: a handler guard names a chart state, an effect callback names a transition, a capability set names an optic. Pneuma promotes these cross-references to first-class **morphisms** — typed connections with their own checking protocol. There are four kinds:

- **Existential references** — an identifier in A must exist in B. Catches dangling references after renames.
- **Structural matches** — A's output must conform to B's input schema. Catches shape mismatches at boundaries.
- **Containment constraints** — A's declared set must be ⊆ B's defined set. Catches over-broad permissions.
- **Ordering constraints** — A must precede B in the interceptor chain. Catches misordered middleware.

Morphism-level gaps tell you *where* the integration fails.

### Composed paths — end-to-end invariants

The morphism graph has cycles, and those cycles carry invariants that no single formalism or morphism can see:

- **Event-effect-callback loop** — a chart raises an event, a handler emits an effect, the effect's callback re-enters the chart. The chart must accept it.
- **Observe-dispatch-update loop** — an extension subscribes via an optic, dispatches within its capability bounds, a handler updates state. If the update overlaps the optic path, the cycle must converge.
- **Full dispatch cycle** — every step in the interceptor chain must establish the precondition for the next step. A missing interceptor breaks the chain.

Path-level gaps tell you *why* the system misbehaves end-to-end.

---

## Quick start

### Define formalisms

Each formalism is a Clojure map. No macros, no code generation, no special syntax.

```clojure
(def effect-sig
  (p/effect-signature
    {:operations
     {:ai/generate   {:session-id :SessionId :messages [:List :Message]
                       :model :ModelId
                       :on-complete :EventRef :on-error :EventRef}
      :tool/execute   {:session-id :SessionId :tool :ToolId
                       :args [:Map :String :Any]
                       :on-complete :EventRef :on-error :EventRef}}}))

(def submit-prompt
  (p/mealy-transition
    {:id :submit-prompt
     :params [{:name :sid :type :SessionId} {:name :prompt :type :String}]
     :guards [{:check :in-state? :args [:sid :idle]}]
     :updates [{:path [:sessions :sid :messages] :op :append
                :value {:role :user :content :prompt}}]
     :effects [{:op :ai/generate
                :fields {:session-id :sid
                         :on-complete [:event-ref :generation-complete :sid]
                         :on-error [:event-ref :generation-error :sid]}}]}))
```

### Check individual formalisms

```clojure
;; Structural check: does the atom conform?
(p/check-schema session-chart @app-db)

;; Behavioral check: does the event log conform?
(p/check-trace session-chart @event-log)

;; Generative check: do random valid inputs produce conforming outputs?
(p/check-gen session-chart {:dispatch-fn dispatch! :num-tests 500})
```

### Check connections

```clojure
;; Do all effect callbacks reference real transitions?
(p/check-morphism :effects->mealy/callbacks effect-sig mealy-handlers)

;; Does the capability set stay within bounds?
(p/check-morphism :caps->mealy/dispatch test-runner-caps mealy-handlers)
```

### Full gap report

```clojure
(def report
  (p/gap-report
    {:formalisms [session-chart effect-sig mealy-handlers
                  optics resolvers caps]
     :refinement-map {:atom #'app-db
                      :event-log #'event-log
                      :source-nss '[agent.handlers agent.effects]}}))

;; Filter to just the failures
(p/failures report)

;; Diff against yesterday's report
(p/diff-reports yesterday-report report)

;; What's blocking error recovery?
(p/gaps-involving report :tool-error)
```

---

## The gap report

The report is a Clojure map. Three layers, each with conforms/absent/diverges statuses.

```clojure
{:object-gaps
 {:chart {:states {:idle :conforms, :tool-error :absent}
          :transitions {:idle->generating :conforms
                        :tool-error->tool-executing :absent}}
  :effects {:ai/generate :conforms
            :tool/execute {:status :diverges
                           :detail {:missing-fields #{:on-error}}}}}

 :morphism-gaps
 {:existential
  [{:id :effects->mealy/callbacks
    :status :diverges
    :detail {:dangling-refs
             [{:from [:tool/execute :on-error]
               :target :tool-execution-error
               :reason :no-such-transition}]}}]
  :containment
  [{:id :mealy->optics/updates
    :status :diverges
    :detail {:unobserved-updates
             [{:handler :approve-tool
               :update-path [:sessions :sid :tool-history]
               :no-optic-covers-path true}]}}]}

 :path-gaps
 {:event-effect-callback
  [{:callback :tool-execution-complete
    :re-entry-state :awaiting-approval
    :expected-re-entry-state :tool-executing
    :status :diverges}]}}
```

Read bottom-up for diagnosis: object gaps say *what*, morphism gaps say *where*, path gaps say *why*. Compute top-down for efficiency: fix object gaps first, and the morphism and path gaps may resolve on their own.

---

## Design principles

**The math is the spec.** There is no specification language. The Harel statechart tuple, the algebraic effect signature, the Mealy machine declarations — these are Clojure maps that directly represent mathematical objects. They are not an encoding or a notation. They are the thing.

**Projections, not compilation.** Each formalism implements `IProjectable` and mechanically produces Malli schemas, trace monitors, test.check generators, and gap type descriptors. There is no compiler. The projections are derived values.

**Connections are first-class.** The morphisms between formalisms — existential references, structural matches, containment constraints, ordering constraints — are mathematical objects in their own right, with their own checking protocol. Boundaries are where the bugs live.

**Cycles carry the strongest invariants.** The event-effect-callback loop, the observe-dispatch-update loop, the full dispatch cycle — these composed paths through the morphism graph impose constraints that no single formalism can see. Pneuma checks them.

**Same runtime, instant feedback.** Everything is Clojure data in the same JVM process as your application. Modify a formalism at the REPL and the gap report updates immediately. No build step, no file watching, no separate toolchain.

**Your architecture already did the hard part.** A single state atom, pure event handlers, effects as data, an event log — these patterns were chosen for testability and replay. They also happen to be exactly what formal conformance checking needs. Pneuma just connects the dots.

---

## Requirements

- Clojure 1.11+
- [Malli](https://github.com/metosin/malli) — schema validation and generation
- [test.check](https://github.com/clojure/test.check) — property-based testing
- [Specter](https://github.com/redplanetlabs/specter) — path resolution (for optic checking)

No external theorem provers, model checkers, or formal methods tools. The entire system lives in one codebase, one runtime, one REPL.

---

## Status

Pneuma is a design document and early implementation. The architecture is described in full in [formalism-first-conformance.md](docs/formalism-first-conformance.md). The implementation is being built bottom-up: objects first, then morphisms, then composed paths.

---

## Name

Pneuma (πνεῦμα) — Greek for breath, or vital spirit. In Stoic philosophy, pneuma is the active organizing principle that pervades matter and gives it structure from within. Not imposed from outside, but intrinsic. The formalisms are the pneuma of the architecture: invisible, ever-present, breathing with the system.

---

## License

EPL-2.0
