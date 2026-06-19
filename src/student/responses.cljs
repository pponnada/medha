(ns medha.student.responses
  (:require [reagent.core          :as r]
            [medha.student.hints   :as hints]
            [medha.student.desmos  :as desmos]
            [medha.student.timer   :as timer]))

;; Per-question local response state (cleared on question mount)
(defonce ^:private response-state (r/atom {}))

(defn current-response [question-id]
  (get @response-state question-id))

(defn set-response! [question-id value]
  (swap! response-state assoc question-id value))

(defn clear-responses! []
  (reset! response-state {}))

;; ── Multiple Choice ──────────────────────────────────────────────────────────

(defn- multiple-choice [q]
  (let [id             (:question/id q)
        text           (:question/text q)
        options        (:options q)
        correct-answer (:correct-answer q)
        selected       (r/atom nil)]
    (fn [_]
      (let [sel @selected]
        [:div.question-body
         [:p.question-text text]
         [:div.mc-options
          (map-indexed
            (fn [i opt]
              ^{:key i}
              [:button.mc-option
               {:class    (when (= sel opt) "selected")
                :on-click (fn []
                            (reset! selected opt)
                            (set-response! id {:value     opt
                                               :correct   (= opt correct-answer)
                                               :graded-by :auto}))}
               opt])
            options)]]))))

;; ── Observation Chips ────────────────────────────────────────────────────────

(defn- observation-chips [q]
  (let [id       (:question/id q)
        text     (:question/text q)
        chips    (:chips q)
        selected (r/atom #{})]
    (fn [_]
      (let [sel @selected]
        [:div.question-body
         [:p.question-text text]
         [:div.chips-container
          (map-indexed
            (fn [i chip]
              ^{:key i}
              [:button.chip
               {:class    (when (contains? sel chip) "selected")
                :on-click (fn []
                            (swap! selected
                                   (fn [s]
                                     (if (contains? s chip)
                                       (disj s chip)
                                       (conj s chip))))
                            (set-response! id {:value          (vec @selected)
                                               :graded-by      nil
                                               :pending-review false}))}
               chip])
            chips)]]))))

;; ── Sentence Starters ────────────────────────────────────────────────────────

(defn- sentence-starter [q]
  (let [id      (:question/id q)
        text    (:question/text q)
        starter (:starter q)
        value   (r/atom (or starter ""))]
    (fn [_]
      [:div.question-body
       [:p.question-text text]
       [:div.sentence-starter-container
        [:textarea.sentence-input
         {:value     @value
          :rows      4
          :on-change (fn [e]
                       (let [v (.. e -target -value)]
                         (reset! value v)
                         (set-response! id {:value          v
                                            :graded-by      nil
                                            :pending-review true})))}]]])))

;; ── Short Text ───────────────────────────────────────────────────────────────

(defn- short-text [{:question/keys [id text]}]
  (let [value (r/atom "")]
    (fn [_]
      [:div.question-body
       [:p.question-text text]
       [:textarea.text-input
        {:value    @value
         :rows     3
         :on-change (fn [e]
                      (let [v (.. e -target -value)]
                        (reset! value v)
                        (set-response! id {:value          v
                                           :graded-by      nil
                                           :pending-review true})))}]])))

;; ── Long Text ────────────────────────────────────────────────────────────────

(defn- long-text [{:question/keys [id text]}]
  (let [value (r/atom "")]
    (fn [_]
      [:div.question-body
       [:p.question-text text]
       [:textarea.text-input.long
        {:value    @value
         :rows     6
         :on-change (fn [e]
                      (let [v (.. e -target -value)]
                        (reset! value v)
                        (set-response! id {:value          v
                                           :graded-by      nil
                                           :pending-review true})))}]])))

;; ── Drag and Drop ────────────────────────────────────────────────────────────

(defn- drag-drop [q]
  (let [id             (:question/id q)
        text           (:question/text q)
        items          (:items q)
        targets        (:targets q)
        correct-answer (:correct-answer q)
        arrangement    (r/atom {})]
    (fn [_]
      [:div.question-body
       [:p.question-text text]
       [:div.drag-drop-area
        [:div.drag-items
         [:p.drag-label "Items"]
         (map-indexed
           (fn [i item]
             ^{:key i}
             [:div.drag-item
              {:draggable true
               :on-drag-start (fn [e] (.setData (.-dataTransfer e) "text" item))}
              item])
           items)]
        [:div.drop-targets
         [:p.drag-label "Drop here"]
         (map-indexed
           (fn [i target]
             ^{:key i}
             [:div.drop-target
              {:on-drag-over (fn [e] (.preventDefault e))
               :on-drop      (fn [e]
                               (.preventDefault e)
                               (let [item (.getData (.-dataTransfer e) "text")]
                                 (swap! arrangement assoc target item)
                                 (set-response! id {:value       @arrangement
                                                    :correct     (= @arrangement correct-answer)
                                                    :graded-by   :auto})))}
              [:span.target-label target]
              (when-let [placed (get @arrangement target)]
                [:span.placed-item placed])])
           targets)]]])))

;; ── Desmos ───────────────────────────────────────────────────────────────────

(defn- desmos-question [question]
  (let [elem-id    (str "desmos-" (name (:question/id question)))
        load-state (r/atom :loading)]   ; :loading | :ok | :failed
    (r/create-class
      {:component-did-mount
       (fn [_]
         (-> (desmos/mount! elem-id
                            (:desmos/state question)
                            (:desmos/readonly-expressions question))
             (.then (fn [calc]
                      (reset! load-state (if calc :ok :failed))))
             (.catch (fn [_] (reset! load-state :failed)))))
       :component-will-unmount
       #(desmos/destroy! elem-id)
       :reagent-render
       (fn [q]
         (set-response! (:question/id q)
                        {:value nil :graded-by nil :pending-review false})
         [:div.question-body
          [:p.question-text (:question/text q)]
          [:div.desmos-mission
           [:span.mission-label "Mission: "]
           (:desmos/mission q)]
          (case @load-state
            :loading
            [:div.desmos-container
             {:id    elem-id
              :style {:width "100%" :height "400px"}}]

            :ok
            [:div
             [:div.desmos-container
              {:id    elem-id
               :style {:width "100%" :height "400px"}}]
             [:button.btn-secondary
              {:on-click (fn []
                           (let [state (desmos/get-state elem-id)]
                             (set-response! (:question/id q)
                                            {:value          state
                                             :graded-by      nil
                                             :pending-review false})))}
              "Capture my work"]]

            :failed
            [:div.desmos-fallback
             {:style {:background "#fef9c3"
                      :border     "1px solid #fde047"
                      :border-radius "8px"
                      :padding    "1.5rem"
                      :text-align "center"}}
             [:p {:style {:font-weight "600" :margin-bottom "0.5rem"}}
              "Desmos graph unavailable"]
             [:p {:style {:color "#6c757d" :font-size "0.9rem"}}
              "The interactive graph requires the Desmos API, which needs a registered domain. "
              "Describe your observations in the text box below instead."]
             [:textarea.text-input
              {:rows      4
               :style     {:margin-top "1rem"}
               :placeholder "Describe what you observe about the fractions..."
               :on-change (fn [e]
                            (set-response! (:question/id q)
                                           {:value          (.. e -target -value)
                                            :graded-by      nil
                                            :pending-review true}))}]])])})))

;; ── Confidence Rating ────────────────────────────────────────────────────────

(defn confidence-rating [question-id on-select]
  (let [selected (r/atom nil)]
    (fn [_]
      [:div.confidence-container
       [:p.confidence-label "How confident are you?"]
       [:div.confidence-options
        (for [[k label] [[:sure "Sure"] [:unsure "Unsure"] [:guessing "Guessing"]]]
          ^{:key k}
          [:button.confidence-btn
           {:class    (when (= @selected k) "selected")
            :on-click (fn []
                        (reset! selected k)
                        (on-select k))}
           label])]])))

;; ── Question dispatcher ───────────────────────────────────────────────────────

(defn question-view [question hints-vec]
  (when hints-vec
    (hints/init! (:question/id question) hints-vec))
  (timer/start!)
  (fn [q _]
    [:div.question-wrapper
     (case (:question/type q)
       :multiple-choice  [multiple-choice q]
       :observation-chips [observation-chips q]
       :sentence-starter [sentence-starter q]
       :short-text       [short-text q]
       :long-text        [long-text q]
       :drag-drop        [drag-drop q]
       :desmos           [desmos-question q]
       [:div.question-body [:p.question-text (:question/text q)]])
     (when hints-vec
       [hints/hints-view])]))
