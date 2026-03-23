(ns pneuma.code.capability
    "Code generation for CapabilitySet formalisms.
  Extends CapabilitySet with ICodeProjectable via extend-protocol.
  Emits capability guard checks — often 100% generated with no fill
  points, since the guards are purely structural."
    (:require [pneuma.code.protocol :as cp])
    (:import [pneuma.formalism.capability CapabilitySet]))

(defn- capability-guard-forms
       "Generates guard check forms for each capability kind."
       [{:keys [id dispatch subscribe query]}]
       (into []
             (keep identity)
             [(when (seq dispatch)
                    {:type :guard-check
                     :capability-id id
                     :kind :dispatch
                     :allowed dispatch
                     :comment (str "Dispatch guard: " id
                                   " allows " dispatch)})
              (when (seq subscribe)
                    {:type :guard-check
                     :capability-id id
                     :kind :subscribe
                     :allowed subscribe
                     :comment (str "Subscribe guard: " id
                                   " allows " subscribe)})
              (when (seq query)
                    {:type :guard-check
                     :capability-id id
                     :kind :query
                     :allowed query
                     :comment (str "Query guard: " id
                                   " allows " query)})]))

(defn- emit-code-capability
       "Generates a code fragment map for a CapabilitySet."
       [{:keys [label] :as cap} opts]
       (let [target-ns (:target-ns opts)
             guards (capability-guard-forms cap)]
            {:namespace target-ns
             :label label
             :requires []
             :forms guards
             :fill-manifest {}
             :metadata {:formalism :capability-set
                        :guard-count (count guards)}}))

(extend-protocol cp/ICodeProjectable
                 CapabilitySet
                 (->code [this opts]
                         (emit-code-capability this opts)))
