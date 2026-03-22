(ns pneuma.doc.html.context
    "Semantic rendering context for the HTML renderer.
  Pure functions for creating, updating, and deriving context
  maps that propagate down the fragment tree.")

(def ^:private status->priority
     {:diverges :high
      :absent   :medium
      :conforms :low})

(def ^:private priority-rank
     {:high 3 :medium 2 :low 1})

(defn default-ctx
      "Returns a default semantic rendering context.
  Accepts an optional opts map to override defaults."
      ([] (default-ctx {}))
      ([opts]
       (merge {:intent   :detail
               :priority nil
               :frame    :document
               :depth    0
               :base-url ""}
              opts)))

(defn infer-priority
      "Derives priority from status-annotation children of a fragment.
  Returns the highest-severity priority found, or nil if none."
      [fragment]
      (let [annotations (filterv #(= :status-annotation (:kind %))
                                 (:children fragment))
            priorities  (into []
                              (keep #(status->priority (:status %)))
                              annotations)]
           (when (seq priorities)
                 (apply max-key #(priority-rank % 0) priorities))))

(defn infer-frame
      "Derives the frame from a fragment id keyword.
  Uses the keyword namespace to determine the layer."
      [id fallback]
      (if-let [ns (and (keyword? id) (namespace id))]
              (cond
               (= ns "morphism") :morphism
               (= ns "path")     :path
               (= ns "gap")      :document
               :else              fallback)
              fallback))

(defn section-ctx
      "Derives child context from a section fragment and current context.
  Increments depth, updates priority from annotations, updates frame from id."
      [section ctx]
      (-> ctx
          (update :depth inc)
          (assoc :priority (or (infer-priority section) (:priority ctx)))
          (assoc :frame (infer-frame (:id section) (:frame ctx)))))
