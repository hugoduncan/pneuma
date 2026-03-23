# Pneuma × Code Generation: From Model to Implementation Scaffolding

**Extending Pneuma with specification-derived code generation.**

*Extension Document — Draft, March 2026*

---

## Table of Contents

1. [The Generation Boundary](#1-the-generation-boundary)
2. [The Fill-Point Architecture](#2-the-fill-point-architecture)
3. [What Each Formalism Generates](#3-what-each-formalism-generates)
4. [The ->code Projection](#4-the--code-projection)
5. [Generated Frame vs Human Fill](#5-generated-frame-vs-human-fill)
6. [Morphism-Derived Tests](#6-morphism-derived-tests)
7. [Regeneration and Drift Detection](#7-regeneration-and-drift-detection)
8. [Project Structure Generation](#8-project-structure-generation)
9. [The Development Workflow](#9-the-development-workflow)
10. [Relationship to Other Projections](#10-relationship-to-other-projections)
11. [Limitations and Non-Goals](#11-limitations-and-non-goals)

---

## 1. The Generation Boundary

Pneuma's formalisms contain enough structural information to generate not just checking artifacts but the implementation scaffolding itself. The statechart knows every state and transition — it can emit `defmethod` stubs. The effect signature knows every operation and its typed fields — it can emit executor dispatch. The interceptor chain knows its ordering — it can emit the chain definition. The morphism registry knows every cross-reference — it can emit the tests that verify those references.

The critical design question is where the boundary falls between what can be generated and what requires human judgment. The answer is clean: structural code is generated, business logic is not.

**Generated (the frame):** dispatch keys, arities, guard preconditions, state transition wiring, callback references, schema validation, chain ordering, subscription declarations for lens-based optics, resolver skeletons with input/output attribute declarations, capability guard checks, and all morphism-derived test assertions.

**Human-written (the fill):** what an AI completion actually does, how to parse tool output, what the permission policy enforces, how derived subscriptions compute their values, what the validation rules check, how resolver bodies query external data, and what constitutes a realistic integration test scenario.

The frame is everything derivable from the *structure* of the mathematical objects. The fill is everything that requires *domain knowledge* — the semantic content that makes the system do something useful rather than merely structurally correct.

> **The core proposition:** The `->code` projection generates structurally correct frames that reference named fill points. The developer implements those fill points in separate files. Regeneration replaces the frames; the fills survive because they are physically separate. The scaffolding carries the formalism's invariants — guards, orderings, callback wiring, schema contracts — so the developer cannot accidentally violate them without changing the formalism data itself.

---

## 2. The Fill-Point Architecture

The central design challenge for any code generation system is what happens on *regeneration*. When the model changes, the generated code must be rewritten — but if the developer has written business logic inside the generated code, that logic is lost. The standard approach of `TODO` markers or `BEGIN-GENERATED / END-GENERATED` fences is fragile: developers inevitably edit across the boundary, merge conflicts corrupt the fences, and the system degrades to a one-time scaffold that's manually maintained.

Pneuma solves this by making the generated frame and the human fill *physically separate* — different namespaces, different files, connected by a registry. The generated code never contains business logic. The human code never contains structural wiring. Regeneration replaces the first; the second is untouched.

### 2.1. The fill registry

The infrastructure is three functions in a single namespace:

```clojure
;; pneuma.fills — the indirection layer (never regenerated)
(ns pneuma.fills)

(defonce registry (atom {}))

(defn reg-fill
  "Register a fill implementation. k is a qualified keyword,
   f is a function whose arity matches the fill point's contract."
  [k f]
  (swap! registry assoc k f))

(defn fill
  "Look up and invoke a fill point. Throws if unregistered."
  [k & args]
  (if-let [f (get @registry k)]
    (apply f args)
    (throw (ex-info (str "Unregistered fill point: " k
                         "\nDeclare it in your fills namespace.")
                    {:fill-point k :args args}))))

(defn fill-or
  "Look up a fill point, returning default-val if unregistered.
   Useful for optional customization points."
  [k default-val & args]
  (if-let [f (get @registry k)]
    (apply f args)
    default-val))
```

### 2.2. How generated code uses fill points

The generated code calls `fill` at every point where business logic is needed, passing a qualified keyword and the arguments the business logic will need:

```clojure
;; In generated handler frame:
(let [messages (fill :submit/messages db session-id)]
  ...)

;; In generated effect executor frame:
(let [result (fill :ai-generate/execute session-id messages model)]
  ...)

;; In generated interceptor frame:
(let [permitted? (fill :permission/check ctx)]
  ...)
```

### 2.3. How human code registers fills

The developer writes implementations in separate files that `->code` never touches:

```clojure
;; agent.handlers.fills — HUMAN-WRITTEN, NEVER REGENERATED
(ns agent.handlers.fills
  (:require [pneuma.fills :refer [reg-fill]]
            [agent.state :as state]))

(reg-fill :submit/messages
  (fn [db session-id]
    (let [messages (state/session-messages db session-id)]
      (into [{:role "system" :content (state/system-prompt db)}]
            messages))))

(reg-fill :submit/model
  (fn [db session-id]
    (or (state/session-model-override db session-id)
        (state/default-model db)
        "claude-sonnet-4-20250514")))
```

### 2.4. The fill manifest

Alongside the generated code, `->code` produces a manifest — a data file listing every fill point the generated code expects, with its argument signature, return type, and documentation:

```clojure
;; fill_manifest.edn — REGENERATED alongside the code
{:submit/messages
 {:args     [db session-id]
  :returns  :messages-list
  :doc      "Extract message history for AI generation"
  :handler  :session/submit-prompt}

 :submit/model
 {:args     [db session-id]
  :returns  :model-id
  :doc      "Resolve model for this session"
  :handler  :session/submit-prompt}

 :submit/update-db
 {:args     [db session-id event]
  :returns  :db
  :doc      "Additional db updates on prompt submission"
  :handler  :session/submit-prompt}

 :approve/on-error-event
 {:args     [session-id tool]
  :returns  :event-vector
  :doc      "Error callback event for tool approval"
  :handler  :session/approve-tool}}
```

At startup, Pneuma validates that every fill point in the manifest has a registered implementation with the correct arity. Missing or mismatched fills are reported immediately — not discovered at runtime when the code path is hit.

### 2.5. The key property

The generated frame and the human fill live in different files. Regeneration replaces the `generated.clj` files and the `fill_manifest.edn`. It never touches the `fills.clj` files. The developer's business logic survives every regeneration.

### 2.6. Fill-point combinators

For common patterns, Pneuma provides combinators that produce fill implementations from declarative configuration:

```clojure
;; pneuma.fills.combinators
(defn from-config
  "Fill that reads from config at the given path."
  [config-path]
  (fn [db & _args]
    (get-in (state/config db) config-path)))

(defn from-session
  "Fill that reads a session field."
  [field-key]
  (fn [db session-id & _args]
    (get-in db [:sessions session-id field-key])))

(defn const-val
  "Fill that always returns the same value."
  [v]
  (fn [& _args] v))

;; Usage in fills namespace
(reg-fill :submit/model   (from-config [:ai :default-model]))
(reg-fill :submit/messages (from-session :messages))
```

Many fill points, especially configuration lookups and simple data extraction, can be expressed as one-liners using combinators. Complex fills (API calls, multi-step transformations) remain ordinary functions.

---

## 3. What Each Formalism Generates

### 3.1. Statechart → handler multimethod stubs

The statechart knows its states, transitions, events, and raised actions. From this, `->code` generates:

- A `defmulti` dispatching on event type.
- One `defmethod` per transition, with the dispatch key derived from the event.
- Guard preconditions (`:pre`) derived from the transition's source state and any declared guards.
- State transition logic that moves the session to the target state.
- Effect emission slots with the correct effect constructor and callback event references.
- Named fill points for all business logic — message extraction, model selection, additional state updates.

```clojure
;; agent.handlers.generated — REGENERATED ON EVERY MODEL CHANGE
;; DO NOT EDIT — all business logic goes in agent.handlers.fills
(ns agent.handlers.generated
  (:require [agent.state :as state]
            [agent.effects :as fx]
            [pneuma.fills :refer [fill fill-or]]))

(defmulti handle-event (fn [db event] (:type event)))

;; Transition: :idle → :generating on :user-submit
;; Raises: :start-generation
(defmethod handle-event :session/submit-prompt
  [db {:keys [session-id] :as event}]
  {:pre [(= :idle (state/conv-state db session-id))]}
  (let [db' (-> db
                 (state/set-conv-state session-id :generating)
                 (fill :submit/update-db db session-id event))]
    {:db db'
     :effects [(fx/ai-generate
                 {:session-id  session-id
                  :messages    (fill :submit/messages db session-id)
                  :model       (fill :submit/model db session-id)
                  :on-complete [:session/generation-complete session-id]
                  :on-error    [:session/generation-error session-id]})]}))

;; Transition: :generating → :idle on :generation-complete
(defmethod handle-event :session/generation-complete
  [db {:keys [session-id response] :as event}]
  {:pre [(= :generating (state/conv-state db session-id))]}
  (let [db' (-> db
                 (state/set-conv-state session-id :idle)
                 (fill :generation-complete/update-db
                       db session-id response))]
    {:db db'
     :effects []}))

;; Transition: :awaiting-approval → :tool-executing on :user-approved
;; Raises: :execute-tool
(defmethod handle-event :session/approve-tool
  [db {:keys [session-id] :as event}]
  {:pre [(= :awaiting-approval (state/conv-state db session-id))
         (some? (state/pending-tool db session-id))]}
  (let [tool (state/pending-tool db session-id)
        db'  (-> db
                  (state/set-conv-state session-id :tool-executing)
                  (state/clear-pending-tool session-id)
                  (state/append-tool-history session-id tool))]
    {:db db'
     :effects [(fx/tool-execute
                 {:session-id  session-id
                  :tool        tool
                  :on-complete [:session/tool-complete session-id]
                  :on-error    (fill :approve/on-error-event
                                     session-id tool)})]}))
```

The guard preconditions, state transitions, effect constructors, and callback references are all derived from the formalism data. The `fill` calls are the only points where the developer's code enters.

The corresponding fills file:

```clojure
;; agent.handlers.fills — HUMAN-WRITTEN, NEVER REGENERATED
(ns agent.handlers.fills
  (:require [pneuma.fills :refer [reg-fill]]
            [agent.state :as state]))

(reg-fill :submit/messages
  (fn [db session-id]
    (let [messages (state/session-messages db session-id)]
      (into [{:role "system" :content (state/system-prompt db)}]
            messages))))

(reg-fill :submit/model
  (fn [db session-id]
    (or (state/session-model-override db session-id)
        (state/default-model db)
        "claude-sonnet-4-20250514")))

(reg-fill :submit/update-db
  (fn [db session-id event]
    (-> db
        (state/append-message session-id
          {:role "user" :content (:prompt event)})
        (state/touch-session session-id))))

(reg-fill :generation-complete/update-db
  (fn [db session-id response]
    (state/append-message db session-id
      {:role "assistant" :content response})))

(reg-fill :approve/on-error-event
  (fn [session-id tool]
    [:session/tool-error session-id (:tool-id tool)]))
```

### 3.2. Effect signature → executor dispatch

The effect signature knows its operations and their typed fields. From this, `->code` generates:

- A `defmulti` dispatching on effect type.
- One `defmethod` per operation, with destructured arguments matching the signature's fields.
- Schema validation preconditions derived from the field types via `->schema`.
- A contract comment noting that the executor must eventually dispatch one of its callback events.
- A single fill point for the actual effect execution logic.

```clojure
;; agent.effects.generated — REGENERATED
(ns agent.effects.generated
  (:require [malli.core :as m]
            [pneuma.fills :refer [fill]]))

(defmulti execute-effect! (fn [effect] (:type effect)))

;; Operation: :ai/generate
(defmethod execute-effect! :ai/generate
  [{:keys [session-id messages model on-complete on-error] :as effect}]
  {:pre [(m/validate SessionId session-id)
         (m/validate [:sequential Message] messages)
         (m/validate ModelId model)]}
  ;; Contract: must eventually dispatch on-complete or on-error
  (fill :ai-generate/execute effect))

;; Operation: :tool/execute
(defmethod execute-effect! :tool/execute
  [{:keys [session-id tool on-complete on-error] :as effect}]
  {:pre [(m/validate SessionId session-id)
         (m/validate ToolInvocation tool)]}
  ;; Contract: must eventually dispatch on-complete or on-error
  (fill :tool-execute/execute effect))
```

### 3.3. Interceptor chain → chain definition

The interceptor chain specification knows its members and their ordering constraints. From this, `->code` generates:

- The chain vector with interceptors in the correct order.
- Interceptors whose logic is fully derivable from the formalisms (no fill points): the statechart interceptor (calls `sc/step`), the trim-replay interceptor (strips effects when replaying).
- Frame-only interceptors with fill points for logic that requires domain knowledge.

```clojure
;; agent.interceptors.generated — REGENERATED
(ns agent.interceptors.generated
  (:require [pneuma.fills :refer [fill fill-or]]))

(def interceptor-chain
  [;; Position 0: permission (must precede statechart)
   {:id    :permission
    :before (fn [ctx] (fill :permission/check ctx))
    :after  identity}

   ;; Position 1: log
   {:id    :log
    :before (fn [ctx] (fill-or :log/before ctx ctx))
    :after  (fn [ctx] (fill-or :log/after ctx ctx))}

   ;; Position 2: statechart (must precede handler)
   ;; FULLY GENERATED — no fill points
   {:id    :statechart
    :before (fn [ctx]
              (let [chart-result (sc/step (:chart ctx) (:event ctx))]
                (assoc ctx :chart-result chart-result)))
    :after  identity}

   ;; Position 3: handler (must precede validator)
   {:id    :handler
    :before (fn [ctx]
              (let [result (handle-event (:db ctx) (:event ctx))]
                (merge ctx result)))
    :after  identity}

   ;; Position 4: validator
   {:id    :validator
    :before identity
    :after  (fn [ctx] (fill :validator/check ctx))}

   ;; Position 5: trim-replay
   ;; FULLY GENERATED — no fill points
   {:id    :trim-replay
    :before identity
    :after  (fn [ctx]
              (if (:replaying? ctx)
                (dissoc ctx :effects)
                ctx))}])
```

Note that the log interceptor uses `fill-or` — logging is optional, and if no fill is registered, the context passes through unchanged.

### 3.4. Optics → subscription declarations

```clojure
;; agent.subscriptions.generated — REGENERATED
(ns agent.subscriptions.generated
  (:require [pneuma.fills :refer [fill]]))

;; Lens subscriptions — FULLY GENERATED, no fill points
(reg-sub :session/conv-state
  (fn [db [_ sid]]
    (get-in db [:sessions sid :conversation :state])))

(reg-sub :session/pending-tool
  (fn [db [_ sid]]
    (get-in db [:sessions sid :pending-tool])))

(reg-sub :session/messages
  (fn [db [_ sid]]
    (get-in db [:sessions sid :messages])))

;; Simple derived — FULLY GENERATED
(reg-sub :session/message-count
  :<- [:session/messages]
  (fn [messages] (count messages)))

;; Complex derived — fill point
(reg-sub :session/active-ids
  (fn [db _]
    (fill :subs/active-session-ids db)))
```

### 3.5. Resolvers → Pathom resolver skeletons

```clojure
;; agent.resolvers.generated — REGENERATED
(ns agent.resolvers.generated
  (:require [com.wsscode.pathom3.connect.operation :as pco]
            [pneuma.fills :refer [fill]]))

(pco/defresolver session-by-id
  [{:keys [db]} {:session/keys [id]}]
  {::pco/input  [:session/id]
   ::pco/output [:session/messages :session/conv-state
                 :session/pending-tool :session/tool-history]}
  (fill :resolver/session-by-id db id))

(pco/defresolver tool-metadata
  [{:keys [tool-registry]} {:tool/keys [id]}]
  {::pco/input  [:tool/id]
   ::pco/output [:tool/name :tool/description :tool/parameters-schema]}
  (fill :resolver/tool-metadata tool-registry id))
```

### 3.6. Capabilities → permission guard code

Capability guards are fully generated with no fill points — they're pure membership checks against declared sets:

```clojure
;; agent.capabilities.generated — REGENERATED (100% generated)
(ns agent.capabilities.generated)

(def extension-capabilities
  {:code-executor
   {:dispatch #{:session/tool-complete :session/tool-error}
    :observe  #{:session/conv-state :session/pending-tool}
    :query    #{:tool/metadata}}

   :search-agent
   {:dispatch #{:session/search-complete}
    :observe  #{:session/messages :session/conv-state}
    :query    #{:session/messages}}})

(defn authorized-dispatch? [ext-type event-type]
  (contains? (get-in extension-capabilities [ext-type :dispatch])
             event-type))

(defn authorized-observe? [ext-type sub-key]
  (contains? (get-in extension-capabilities [ext-type :observe])
             sub-key))

(defn authorized-query? [ext-type attr]
  (contains? (get-in extension-capabilities [ext-type :query])
             attr))
```

---

## 4. The ->code Projection

The `->code` projection is a new method on both `IProjectable` and `IConnection`:

```clojure
(defprotocol IProjectable
  (->schema   [this])    ;; → Malli schema
  (->monitor  [this])    ;; → trace checker
  (->gen      [this])    ;; → test.check generator
  (->gap-type [this])    ;; → gap type descriptor
  (->lean     [this])    ;; → Lean 4 source
  (->doc      [this])    ;; → document fragment
  (->code     [this]))   ;; → code fragment with fill points

(defprotocol IConnection
  (check  [this a b])
  (->gap  [this a b])
  (->gen  [this a b])
  (->lean [this a b])
  (->code [this a b]))   ;; → test assertions for this morphism
```

### 4.1. What `->code` returns

The output of `->code` is a data structure describing the code to be generated. Each form carries metadata about its fill points — their names, argument signatures, return types, and documentation. A renderer converts this to Clojure source code with `fill` calls at the appropriate locations. The fill manifest is emitted as a separate `.edn` file.

```clojure
;; (->code session-chart) returns:
{:namespace 'agent.handlers.generated
 :requires  '[[agent.state :as state]
               [agent.effects :as fx]
               [pneuma.fills :refer [fill fill-or]]]
 :forms     [...]  ;; defmulti, defmethods with fill calls

 :fill-manifest
 {:submit/messages  {:args '[db session-id]
                     :returns :messages-list
                     :doc "Extract message history for AI generation"
                     :handler :session/submit-prompt}
  :submit/model     {:args '[db session-id]
                     :returns :model-id
                     :doc "Resolve model for this session"
                     :handler :session/submit-prompt}
  :submit/update-db {:args '[db session-id event]
                     :returns :db
                     :doc "Additional db updates on prompt submission"
                     :handler :session/submit-prompt}}}
```

### 4.2. Composing code fragments

The fragments from individual formalisms are composed into a project-level code generation plan:

```clojure
(p/emit-project
  {:formalisms   [session-chart effect-sig mealy-handlers
                  optics resolvers caps]
   :morphisms    (p/morphism-registry)
   :target-dir   "src/agent"
   :test-dir     "test/agent"
   :gap-report   (p/gap-report {:refinement-map {...}})})
```

This produces generated frames with fill-point calls, a fill manifest, a full test suite, and skeleton `fills.clj` files for any fill points that don't yet have registered implementations. Existing fills files are never overwritten.

---

## 5. Generated Frame vs Human Fill

### 5.1. Fully generated (no fill points)

These require no human input — the formalism data determines the complete implementation:

- Guard preconditions, state transitions, schema validation, callback wiring.
- Chain ordering, lens subscriptions, capability guards.
- Statechart interceptor, trim-replay interceptor.
- All morphism, ordering, and cycle tests.

### 5.2. Fill-point code (generated frame + human fill)

These have generated structure with named fill points for business logic:

- **Handler bodies (75% generated).** Guards, transitions, effect emissions are generated. Message extraction, model selection, additional state updates are fill points.
- **Effect emissions (85% generated).** Constructor calls and callbacks are generated. Computed fields are fill points.
- **Effect executor bodies (40% generated).** Dispatch, destructuring, and validation are generated. The actual execution is a single fill point per operation.
- **Derived subscriptions (50% generated).** Input signal declarations are generated. Complex derivations are fill points.
- **Resolver bodies (60% generated).** Input/output declarations are generated. Resolution logic is a fill point.
- **Permission interceptor (30% generated).** Position and slots are generated. The policy is a fill point.

---

## 6. Morphism-Derived Tests

The morphism registry produces complete test code — no fill points needed. Every morphism is a checkable contract between two formalisms.

### 6.1. Existential reference tests

```clojure
;; Generated from morphism :effects->mealy/callbacks
(deftest effect-callbacks-reference-existing-handlers
  (testing "every effect callback targets an existing handler"
    (doseq [op (keys (:operations effect-sig))
            cb-field [:on-complete :on-error]]
      (let [target (get-in effect-sig [:operations op cb-field])]
        (when target
          (is (contains? handler-registry target)
              (str "Callback " cb-field " of " op
                   " references " target
                   " which has no handler")))))))
```

### 6.2. Structural match tests

```clojure
;; Generated from morphism :mealy->effects/emits
(deftest handler-emissions-match-effect-signatures
  (testing "every emitted effect conforms to its operation's schema"
    (doseq [[handler-id handler] handler-registry
            effect-template (:effects (handler-contract handler))]
      (let [op-schema (get-in effect-sig
                        [:operations (:type effect-template) :schema])]
        (when op-schema
          (is (m/validate op-schema effect-template)
              (str handler-id " emits " (:type effect-template)
                   " with shape that doesn't match the signature")))))))
```

### 6.3. Containment tests

```clojure
;; Generated from morphism :caps->mealy/dispatch
(deftest extension-dispatch-within-handler-events
  (testing "every extension dispatches only events that have handlers"
    (doseq [[ext-type caps] extension-capabilities]
      (is (clojure.set/subset?
            (:dispatch caps)
            (set (keys handler-registry)))
          (str ext-type " dispatches events with no handler: "
               (clojure.set/difference
                 (:dispatch caps)
                 (set (keys handler-registry))))))))
```

### 6.4. Ordering tests

```clojure
;; Generated from ordering morphisms
(deftest interceptor-ordering-constraints
  (let [positions (into {} (map-indexed (fn [i ic] [(:id ic) i])
                                       interceptor-chain))]
    (testing "permission precedes statechart"
      (is (< (positions :permission) (positions :statechart))))
    (testing "statechart precedes handler"
      (is (< (positions :statechart) (positions :handler))))
    (testing "handler precedes validator"
      (is (< (positions :handler) (positions :validator))))))
```

### 6.5. Cycle tests

```clojure
;; Generated from cycle :event-effect-callback
(deftest event-effect-callback-cycle-closes
  (testing "every callback re-enters the chart at a valid state"
    (tc/quick-check 200
      (prop/for-all [trace (gen-event-trace session-chart 10)]
        (let [result (reduce dispatch-and-collect-effects
                            initial-state trace)]
          (every? (fn [callback-event]
                    (chart-accepts? session-chart
                                   (:state result)
                                   callback-event))
                  (collect-callbacks (:effects result))))))))
```

### 6.6. Fill-point contract tests

Because each fill point has a declared contract, Pneuma generates property tests that verify the human-written fills produce values the generated frame can consume:

```clojure
;; Generated in agent.fills_test.clj
(deftest fill-contracts
  (testing ":submit/messages returns a valid message list"
    (tc/quick-check 100
      (prop/for-all [db (gen-db) sid (gen-session-id)]
        (let [result (fill :submit/messages db sid)]
          (m/validate [:sequential Message] result)))))

  (testing ":submit/model returns a valid model ID"
    (tc/quick-check 100
      (prop/for-all [db (gen-db) sid (gen-session-id)]
        (let [result (fill :submit/model db sid)]
          (m/validate ModelId result)))))

  (testing ":approve/on-error-event returns a dispatchable event"
    (tc/quick-check 100
      (prop/for-all [sid (gen-session-id) tool (gen-tool)]
        (let [result (fill :approve/on-error-event sid tool)]
          (and (vector? result)
               (keyword? (first result))))))))
```

The generated frame guarantees structural correctness. The fill contract tests guarantee that the human-written business logic produces values the frame can use. Together, they give end-to-end type safety without a type system.

---

## 7. Regeneration and Drift Detection

### 7.1. What regeneration replaces

On every regeneration, the following files are overwritten: all `generated.clj` files, the `fill_manifest.edn`, and all fully generated test files. The following files are **never touched**: all `fills.clj` files, `pneuma/fills.clj`, and human-written integration tests.

### 7.2. The fill-status operation

After regeneration, Pneuma compares the new manifest against the registered fills:

```clojure
(p/fill-status)
;; => {:ok       [:submit/update-db :submit/model :approve/on-error-event]
;;     :missing  [:tool-error/retry-logic :tool-error/skip-logic]
;;     :orphaned [:legacy/reset-session]
;;     :arity-mismatch [{:key :submit/messages
;;                        :registered-arity 2
;;                        :expected-arity 3
;;                        :new-args '[db session-id opts]}]}
```

**Ok.** Fill points with matching implementations. Nothing to do.

**Missing.** New fill points with no implementation. The developer needs to write these. Pneuma reports the full contract:

```
Missing fill: :tool-error/retry-logic
  Args:     [db session-id error]
  Returns:  :db
  Doc:      Decide whether to retry and update db accordingly
  Handler:  :session/tool-error
```

**Orphaned.** Registered fills no longer called by any generated code. The implementation still works; it's just never invoked. The developer can delete it or keep it as a utility.

**Arity mismatch.** Fill points whose argument lists changed. The registered implementation has the wrong arity. Pneuma reports both old and new signatures.

### 7.3. The code-diff operation

In addition to fill-point diffing, `code-diff` compares the full generated code against the existing version:

```clojure
(p/code-diff session-chart 'agent.handlers.generated)
;; => {:new-methods         [:session/tool-error-retry
;;                           :session/tool-error-skip]
;;     :removed-methods     [:session/legacy-reset]
;;     :guard-changes       [{:handler :session/approve-tool
;;                            :change  :added-guard}]
;;     :new-fill-points     [:tool-error/retry-logic
;;                           :tool-error/skip-logic]
;;     :removed-fill-points [:legacy/reset-session]}
```

### 7.4. CI integration

```yaml
- name: Pneuma fill-point validation
  run: clojure -M:pneuma check-fills
  # Fails if any fill points are missing or have arity mismatches
  # Orphaned fills produce warnings, not failures

- name: Pneuma morphism tests
  run: clojure -M:test -m agent.morphisms-test

- name: Pneuma fill contract tests
  run: clojure -M:test -m agent.fills-test
```

---

## 8. Project Structure Generation

The formalisms determine the module boundaries. The pattern is consistent: every `generated.clj` has a corresponding `fills.clj`. The fills file is created as a skeleton on first generation but never overwritten afterward.

```
src/
  pneuma/
    fills.clj                         ← infrastructure, never changes
    fills/combinators.clj             ← fill helpers

  agent/
    handlers/generated.clj            ← REGENERATED
    handlers/fills.clj                ← human-written

    effects/generated.clj             ← REGENERATED
    effects/fills.clj                 ← human-written

    interceptors/generated.clj        ← REGENERATED
    interceptors/fills.clj            ← human-written

    subscriptions/generated.clj       ← REGENERATED
    subscriptions/fills.clj           ← human-written (derived subs only)

    resolvers/generated.clj           ← REGENERATED
    resolvers/fills.clj               ← human-written

    capabilities/generated.clj        ← REGENERATED (often 100% generated)

    fill_manifest.edn                 ← REGENERATED

test/
  agent/morphisms_test.clj            ← REGENERATED, 100% generated
  agent/ordering_test.clj             ← REGENERATED, 100% generated
  agent/cycles_test.clj               ← REGENERATED, 90% generated
  agent/fills_test.clj                ← REGENERATED, contract tests for fills
  agent/integration_test.clj          ← human-written
```

---

## 9. The Development Workflow

### 9.1. Starting a new feature

1. **Define the formalism changes.** Add new states, transitions, effects, or morphisms.

2. **Regenerate.**
   ```clojure
   (p/regenerate!
     {:formalisms [session-chart effect-sig mealy-handlers ...]
      :morphisms  (p/morphism-registry)
      :target-dir "src/agent"
      :test-dir   "test/agent"})
   ```

3. **Check fill status.**
   ```clojure
   (p/fill-status)
   ;; => {:missing [:tool-error/retry-logic :tool-error/skip-logic] ...}
   ```

4. **Implement the missing fills.** Open the relevant `fills.clj`, write `reg-fill` for each missing fill point. The manifest provides the contract.

5. **Run tests.** `(run-tests 'agent.morphisms-test 'agent.fills-test)`

6. **Verify.** `(p/fill-status)` — all fills should show `:ok`.

### 9.2. Refactoring

1. Modify the formalism data.
2. Regenerate. The generated files are rewritten. The fills files are untouched.
3. `fill-status` reports new, orphaned, and arity-mismatched fills.
4. Update the fills.
5. Run tests.

### 9.3. The REPL as fill browser

```clojure
;; What fills does the submit handler need?
(p/fills-for :session/submit-prompt)
;; => [{:key :submit/messages :args [db session-id] :status :ok}
;;     {:key :submit/model :args [db session-id] :status :ok}
;;     {:key :submit/update-db :args [db session-id event] :status :ok}]

;; Show all missing fills with their contracts
(p/missing-fills)
;; => [{:key :tool-error/retry-logic
;;      :args [db session-id error]
;;      :returns :db
;;      :doc "Decide whether to retry failed tool"
;;      :handler :session/tool-error}]
```

---

## 10. Relationship to Other Projections

| Projection | Output | Purpose |
|---|---|---|
| `->schema` | Malli schema | Structural validation of atom snapshots |
| `->monitor` | Trace checker | Behavioral checking of event log |
| `->gen` | test.check generator | Generative testing of event sequences |
| `->gap-type` | Gap type descriptor | Classification of conformance failures |
| `->lean` | Lean 4 source | Formal proofs of architectural invariants |
| `->doc` | Document fragment | Human-readable architecture documentation |
| `->code` | Code frames + fill manifest | Implementation scaffolding with structural invariants |

The projections reinforce each other. The morphism-derived tests from `->code` are the runtime equivalent of the morphism-derived theorems from `->lean`. The fill contract tests use Malli schemas from `->schema` to validate fill return values. The fill manifest is the code-level expression of the gap report — missing fills correspond to `:absent` entries, arity mismatches to `:diverges` entries. The seven projections form a complete loop: define formalisms → generate scaffolding → implement fills → check conformance → prove invariants → document the architecture → regenerate on change.

---

## 11. Limitations and Non-Goals

### 11.1. Not a general-purpose code generator

The `->code` projection generates code for a specific architectural pattern: single-atom event-sourced systems with multimethods, interceptor chains, and subscription-based views.

### 11.2. Business logic is not derivable

The formalisms describe structure, not semantics. This content is inherently behind fill points. An LLM could plausibly implement some fill points given the rich context (fill contract, schemas, docstrings) — a natural future extension.

### 11.3. Fill points add one level of indirection

The `fill` call is a runtime hash-map lookup — nanoseconds. For Pneuma's target domain (event-sourced agents at hundreds of requests per second), the overhead is negligible.

### 11.4. Fill files must be loaded after generated files

The generated code defines multimethods and the chain; the fills register implementations. Clojure's namespace loading handles this naturally.

### 11.5. Not a replacement for implementation

Generated scaffolding plus empty fills is structurally correct but does nothing useful. The value is that the developer starts from a correct frame with a clear manifest of what needs implementing.

---

## Appendix: The Full Projection Family

```clojure
(defprotocol IProjectable
  (->schema   [this]  "Malli schema for structural validation.")
  (->monitor  [this]  "Trace checker for behavioral checking.")
  (->gen      [this]  "test.check generator for property testing.")
  (->gap-type [this]  "Gap type descriptor for failure classification.")
  (->lean     [this]  "Lean 4 type definitions and proof targets.")
  (->doc      [this]  "Human-readable document fragment.")
  (->code     [this]  "Code frames with fill points + fill manifest."))

(defprotocol IConnection
  (check  [this a b]  "Check the boundary contract between a and b.")
  (->gap  [this a b]  "Produce a gap descriptor for boundary violations.")
  (->gen  [this a b]  "Produce a generator for cross-formalism tests.")
  (->lean [this a b]  "Produce Lean 4 composition theorems.")
  (->code [this a b]  "Produce test assertions for this morphism."))
```

The developer writes the mathematical objects and the business logic (as fill-point implementations in separate files). Everything structural in between — checking, testing, proving, documenting, and scaffolding — is projection. Regeneration is safe because the projections and the fills never share a file.
