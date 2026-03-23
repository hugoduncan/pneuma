# Pneuma

**Living mathematical specifications for Clojure architectures.**

Pneuma embeds mathematical formalisms — statecharts, effect signatures, optics, functional dependencies — directly as Clojure data in the same runtime as your implementation. No specification language. No compilation step. The math objects project themselves into Malli schemas, trace monitors, generative tests, and structured gap reports that tell you exactly where and how your code diverges from your architectural intent.

```clojure
(require '[pneuma.core :as p])

;; Define the math. It lives in your runtime.
(def session-chart
  (p/statechart
    {:label "Session Lifecycle"
     :states #{:idle :generating :awaiting-approval :tool-executing :tool-error}
     :initial {:root :idle}
     :transitions
     [{:source :idle :event :user-submit :target :generating}
      {:source :generating :event :tool-requested :target :awaiting-approval}
      {:source :awaiting-approval :event :user-approved :target :tool-executing}
      {:source :tool-executing :event :tool-complete :target :generating}
      {:source :tool-executing :event :tool-error :target :tool-error}
      {:source :generating :event :generation-complete :target :idle}]}))

(def effect-sig
  (p/effect-signature
    {:label "AI Operations"
     :operations
     {:ai/generate  {:input {:session-id :SessionId :model :ModelId}
                     :output :String}
      :tool/execute {:input {:session-id :SessionId :tool :ToolId}
                     :output :Any}}}))

(def caps
  (p/capability-set
    {:label "Test Runner"
     :id :test-runner
     :dispatch #{:ai/generate :tool/execute}}))

;; Connect formalisms with morphisms
(def registry
  {:caps->ops
   (p/existential-morphism
     {:id :caps->ops
      :from :capability-set
      :to :effect-signature
      :source-ref-kind :dispatch-refs
      :target-ref-kind :operation-ids})})

;; One call. Three-layer gap report.
(p/gap-report
  {:formalisms {:statechart session-chart
                :effect-signature effect-sig
                :capability-set caps}
   :registry registry})
```

```clojure
;; The report is data. Up to four layers deep.
{:object-gaps    [...]  ;; per-formalism: what's missing
 :morphism-gaps  [...]  ;; per-connection: where integration breaks
 :path-gaps      [...]  ;; per-cycle: why the system misbehaves end-to-end
 :fill-gaps      [...]} ;; per-fill-point: what business logic is unimplemented
```

---

## Public API

Everything is accessed through `pneuma.core`.

### Formalism constructors

```clojure
;; All constructors require :label — a human-readable name for the instance
(p/statechart {:label "..." ...})         ;; Harel statechart (S, ≤, T, C, H, δ)
(p/effect-signature {:label "..." ...})   ;; Algebraic effect signature
(p/mealy-handler-set {:label "..." ...})  ;; Handler contracts (guards, updates, effects)
(p/optic-declaration {:label "..." ...})  ;; Lenses, traversals, folds, derived subs
(p/resolver-graph {:label "..." ...})     ;; Functional dependency hypergraph
(p/capability-set {:label "..." ...})     ;; Dispatch/subscribe/query bounds
(p/type-schema {:label "..." :types {...}})  ;; Named type definitions
```

### Morphism constructors

```clojure
(p/existential-morphism {...})  ;; id in A must exist in B
(p/structural-morphism {...})   ;; A's output conforms to B's schema
(p/containment-morphism {...})  ;; A ⊆ B
(p/ordering-morphism {...})     ;; A precedes B in chain
```

### Per-formalism checking

```clojure
;; Structural: does this value conform to the formalism's schema?
(p/check-schema effect-sig {:op :ai/generate :session-id :s1 :model :gpt4})
;; => {:status :conforms}

;; Behavioral: does the event log conform to the formalism's monitor?
(p/check-trace effect-sig [{:operation :ai/generate :fields {:session-id :s1}}])
;; => {:status :conforms, :entries-checked 1}

;; Generative: do random valid inputs satisfy the schema?
(p/check-gen effect-sig {:num-tests 100})
;; => {:status :conforms, :tests-run 100}
```

### Morphism checking

```clojure
(p/check-morphism morphism-record source-formalism target-formalism)
;; => [{:id :caps->ops, :kind :existential, :status :conforms}]
```

### Gap report

```clojure
(def report
  (p/gap-report
    {:formalisms {:statechart session-chart
                  :effect-signature effect-sig
                  :capability-set caps}
     :registry registry
     ;; Optional: include fill-point gaps as a fourth layer
     :fill-manifest manifest}))

;; Each gap is a map with :layer, :status, and :detail
;; {:layer :morphism, :id :caps->ops, :kind :existential,
;;  :status :diverges, :detail {:dangling-refs #{:bogus-method}}}

(p/failures report)          ;; non-conforming gaps only
(p/has-failures? report)     ;; quick pass/fail predicate
(p/gaps-involving report :capability-set)  ;; filter by formalism
```

### Report diffing

```clojure
(def diff (p/diff-reports old-report new-report))
;; => {:object-gaps   {:introduced [...] :resolved [...] :changed [...]}
;;     :morphism-gaps {:introduced [...] :resolved [...] :changed [...]}
;;     :path-gaps     {:introduced [...] :resolved [...] :changed [...]}}

(p/has-changes? diff)
```

### Path discovery

```clojure
;; Cycles in the morphism graph are found automatically
(p/find-paths registry)
;; => [#ComposedPath{:id :caps->ops->ops->caps, :steps [...]}]
```

### Refinement map

```clojure
(p/refinement-map
  {:atom-ref      #'app-state/db
   :event-log-ref #'app-state/event-log
   :accessors     {:session (fn [db sid] (get-in db [:sessions sid]))}
   :source-nss    '[app.handlers app.effects]})
```

### Lean 4 proof emission

```clojure
(require '[pneuma.lean.core :as lean])

;; Per-formalism: type definitions + property theorems
(lean/emit-lean session-chart)

;; Per-morphism: boundary propositions
(lean/emit-lean-conn morphism source-formalism target-formalism)

;; All paths: per-step boundaries + composition theorem
(lean/emit-lean-paths formalisms registry)

;; Unified system file: gap-report-driven proofs
(lean/emit-lean-system "my-spec" {:formalisms {...} :registry {...}})

;; Everything at once
(lean/emit-lean-all "my-spec" {:formalisms {...} :registry {...}})
;; => {:formalisms {kind lean-src}
;;     :morphisms  {id lean-src}
;;     :paths      [{:id path-id :lean-src string}]
;;     :system     lean-src}
```

### Code generation

```clojure
(require '[pneuma.code.core :as code])

;; Per-formalism: code fragments with fill points
(code/emit-code session-chart {:target-ns 'agent.handlers})
;; => {:namespace agent.handlers, :forms [...], :fill-manifest {...}}

;; Full project: sources, tests, manifest, fills skeleton
(code/emit-project
  {:formalisms {:statechart session-chart
                :effect-signature effect-sig
                :capability-set caps}
   :registry registry
   :opts {:target-ns {:statechart 'agent.handlers
                      :effect-signature 'agent.effects
                      :capability-set 'agent.caps}}})

;; Fill-point status: what's implemented, what's missing
(code/fill-status manifest)
;; => {:ok [...] :missing [...] :orphaned [...] :arity-mismatch [...]}

;; Structural diff between generated versions
(code/code-diff new-code old-code)
;; => {:new-methods [...] :removed-methods [...] :new-fill-points [...]}
```

Generated frames carry formalism invariants (guards, orderings, schema contracts). Business logic lives in separate fill-point files that are never regenerated. See [pneuma-codegen-extension.md](doc/pneuma-codegen-extension.md).

---

## Why

Most formal methods tools live outside your codebase. You write a spec in one language, compile it with a separate toolchain, and run checkers that produce opaque pass/fail results. The spec and the code drift apart. The checking happens in CI if you're lucky, never if you're not.

Pneuma takes a different position: the mathematical objects that describe your architecture should be values in your runtime, queryable at the REPL, updated in the same editor session as your code, and capable of telling you — instantly, interactively — how your implementation diverges from your intent.

The formalisms aren't decorative. Each one mechanically projects into checking artifacts:

| Formalism | `->schema` | `->monitor` | `->gen` | `->code` |
|---|---|---|---|---|
| Harel statechart | Valid configurations | Event log vs. step function δ | Random walks over reachable states | defmulti + defmethod stubs with guards |
| Effect signature | Per-operation field schemas | Effect descriptions in log | Well-typed effect maps | Executor dispatch with schema validation |
| Mealy handlers | Handler input/output shapes | db-before/db-after diffs | (state, event) pairs | Handler contracts with fill points |
| Optics | Return types of `view` | Subscription change detection | States where paths resolve | Subscription declarations |
| Func. dependencies | Attribute reachability | Resolver output validation | Reachable query inputs | Resolver skeletons |
| Capability sets | Set membership bounds | Extension dispatch metadata | Bounded event selections | Guard checks (100% generated) |

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

The morphism graph has cycles, and those cycles carry invariants that no single formalism or morphism can see. Cycles are discovered automatically via Johnson's algorithm — no application-specific cycles are hardcoded.

Path-level gaps tell you *why* the system misbehaves end-to-end.

---

## Example: modelling integrant

Pneuma includes a formal model of [integrant](https://github.com/weavejester/integrant)'s architecture as a regression test. The model captures integrant's lifecycle state machine, multimethod contracts, and per-phase capability bounds — then verifies the model against integrant's actual code and emits machine-checked Lean 4 proofs.

```clojure
;; test-regression/pneuma/integrant/integrant_spec.clj

;; Lifecycle as a Harel statechart
(def lifecycle
  (p/statechart
    {:label "Integrant Lifecycle"
     :states    #{:uninitialized :expanded :running :suspended :halted}
     :hierarchy {:root #{:uninitialized :expanded :running :suspended :halted}}
     :initial   {:root :uninitialized}
     :transitions
     [{:source :uninitialized :event :expand  :target :expanded}
      {:source :expanded      :event :init    :target :running}
      {:source :uninitialized :event :init    :target :running}
      {:source :running       :event :halt    :target :halted}
      {:source :running       :event :suspend :target :suspended}
      {:source :suspended     :event :resume  :target :running}
      {:source :suspended     :event :halt    :target :halted}]}))

;; Seven multimethods as an effect signature
(def multimethod-sig
  (p/effect-signature
    {:label "Integrant Multimethods"
     :operations
     {:init-key    {:input {:key :ConfigKey :value :ConfigValue}
                    :output :InitializedValue}
      :halt-key!   {:input {:key :ConfigKey :value :InitializedValue}
                    :output :UnitResult}
      :resume-key  {:input {:key :ConfigKey :value :ConfigValue
                            :old-value :ConfigValue :old-impl :InitializedValue}
                    :output :InitializedValue}
      ;; ... + suspend-key!, resolve-key, expand-key, assert-key
      }}))

;; Per-phase capability bounds
(def init-phase-caps
  (p/capability-set
    {:label "Init Phase"
     :id :init-phase
     :dispatch #{:assert-key :expand-key :init-key :resolve-key}}))

;; Morphisms connect the formalisms
;; e.g. every phase's dispatch set ⊆ declared operations
```

The model-based tests exercise integrant's real code against the spec:
- **Statechart monitor**: real `ig/init` → `ig/suspend!` → `ig/resume` → `ig/halt!` traced through `->monitor`
- **Effect signature monitor**: multimethod calls verified against declared operations
- **Capability monitors**: per-phase operation checks for all 5 lifecycle phases
- **Property-based testing**: `check-gen` generates valid configs, `check-schema` validates them
- **Lean verification**: system proof emits with no `sorry`, all `decide`, compiles clean

```bash
bb test-regression       # model-based tests (fast)
bb test-regression-lean  # lean proof emission + compilation
```

---

## Browsable HTML documentation

Pneuma generates browsable HTML for its own specifications:

```bash
bb gen-html              # generates doc/generated/index.html
open doc/generated/index.html
```

Each spec page shows formalisms, morphism connection graphs, gap reports,
and the generated Lean 4 proof code — with collapsible sections and
summary/detail toggle.

---

## Development

```bash
bb test                  # unit tests (fast, skips lean compilation)
bb test-all              # unit + lean + regression + regression-lean
bb test-lean             # lean compilation tests only
bb test-regression       # regression tests (external project models)
bb test-regression-lean  # regression lean compilation tests
bb lake                  # build Lean proofs
bb gen-html              # generate browsable HTML docs
bb lint                  # clj-kondo
bb fmt                   # check formatting
bb ci                    # lint + fmt + test-all + lake build
```

---

## Requirements

- Clojure 1.12+
- [Malli](https://github.com/metosin/malli) — schema validation and generation
- [test.check](https://github.com/clojure/test.check) — property-based testing

Optional:
- [Lean 4](https://leanprover.github.io/) + [elan](https://github.com/leanprover/elan) — for `->lean` proof emission and `lake build`

---

## Design documents

- [formalism-first-conformance.md](doc/formalism-first-conformance.md) — mathematical foundations
- [structural-domain-model.md](doc/structural-domain-model.md) — 19 sorts, 30 axioms, 14 morphisms
- [system-architecture-prose.md](doc/system-architecture-prose.md) — Option A: Records + Protocols + Data Registry
- [pneuma-lean4-extension.md](doc/pneuma-lean4-extension.md) — Lean 4 proof integration
- [pneuma-codegen-extension.md](doc/pneuma-codegen-extension.md) — code generation from formalisms

---

## Name

Pneuma (πνεῦμα) — Greek for breath, or vital spirit. In Stoic philosophy, pneuma is the active organizing principle that pervades matter and gives it structure from within. Not imposed from outside, but intrinsic. The formalisms are the pneuma of the architecture: invisible, ever-present, breathing with the system.

---

## License

EPL-2.0
