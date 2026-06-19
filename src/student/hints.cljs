(ns medha.student.hints
  (:require [reagent.core :as r]))

(defonce ^:private hint-state
  (r/atom {:question-id    nil
           :hints          []
           :revealed-count 0
           :worked-example nil}))

(defn init! [question-id hints-vec]
  (let [all       (vec hints-vec)
        body      (if (> (count all) 0) (butlast all) [])
        worked-ex (last all)]
    (reset! hint-state
            {:question-id    question-id
             :hints          (vec body)
             :worked-example worked-ex
             :revealed-count 0})))

(defn revealed-count []
  (:revealed-count @hint-state))

(defn worked-example-revealed? []
  (let [s @hint-state]
    (>= (:revealed-count s)
        (+ (count (:hints s)) 1))))

(defn can-reveal-more? []
  (let [s @hint-state]
    (< (:revealed-count s)
       (+ (count (:hints s)) 1))))

(defn reveal-next! []
  (swap! hint-state update :revealed-count inc))

(defn independence-factor []
  (let [s     @hint-state
        n     (:revealed-count s)
        total (+ (count (:hints s)) 1)]
    (cond
      (>= n total) 0.0
      (= n 0)      1.0
      :else        (max 0.0 (- 1.0 (* n 0.2))))))

(defn hints-view []
  (let [s @hint-state]
    [:div.hints-container
     (when (pos? (:revealed-count s))
       [:div.hints-revealed
        (map-indexed
          (fn [i text]
            ^{:key i}
            [:div.hint-item
             [:span.hint-label (str "Hint " (inc i))]
             [:p text]])
          (take (min (:revealed-count s) (count (:hints s)))
                (:hints s)))
        (when (worked-example-revealed?)
          [:div.hint-item.worked-example
           [:span.hint-label "Worked Example"]
           [:p (:worked-example s)]])])
     (when (can-reveal-more?)
       [:button.btn-hint {:on-click reveal-next!}
        (if (= (:revealed-count s) (count (:hints s)))
          "Show worked example"
          "Get a hint")])]))
