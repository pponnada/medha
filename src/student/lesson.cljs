(ns medha.student.lesson
  (:require [reagent.core             :as r]
            [medha.db                 :as db]
            [medha.state              :as state]
            [medha.router             :as router]
            [medha.curriculum         :as curriculum]
            [medha.student.responses  :as responses]
            [medha.student.hints      :as hints]
            [medha.student.timer      :as timer]))

(def PHASES [:understand :plan :execute :look-back])
(def PHASE-LABELS {:understand "Understand" :plan "Plan"
                   :execute "Carry Out" :look-back "Look Back"})

;; ── Attempt lifecycle ────────────────────────────────────────────────────────

(defn- start-attempt! [lesson-id student-id]
  (let [now (.now js/Date)
        rec {:student_id      student-id
             :lesson_id       (str lesson-id)
             :started_at      now
             :completed_at    nil
             :active_duration 0
             :content_version "1.0.0"
             :phase_progress  {}}]
    (-> (db/put-record! "lesson_progress" rec)
        (.then (fn [generated-id]
                 (state/assoc-in-state! [:lesson :attempt-id] generated-id)
                 generated-id)))))

(defn- save-response! [lesson-id question attempt-id]
  (let [q-id     (:question/id question)
        response (responses/current-response q-id)
        duration (timer/stop!)]
    (when response
      (db/put-record! "responses"
                      {:attempt_id      attempt-id
                       :student_id      (get-in @state/app-state [:student :id])
                       :question_id     (str q-id)
                       :type            (str (:question/type question))
                       :active_duration  duration
                       :hint_count       (hints/revealed-count)
                       :worked_example   (hints/worked-example-revealed?)
                       :response_value   (:value response)
                       :correct          (:correct response)
                       :graded_by        (some-> (:graded-by response) str)
                       :pending_review   (boolean (:pending-review response))}))))

(defn- complete-lesson! [lesson-id attempt-id]
  (db/update-record! "lesson_progress"
                     {:id           attempt-id
                      :completed_at (.now js/Date)})
  (db/put-record! "analytics"
                  {:student_id  (get-in @state/app-state [:student :id])
                   :event_type  "lesson_completed"
                   :data        {:lesson_id (str lesson-id)}
                   :timestamp   (.now js/Date)}))

;; ── Phase rendering ───────────────────────────────────────────────────────────

(defn- phase-indicator [current-phase]
  [:div.phase-indicator
   (map-indexed
     (fn [i phase]
       ^{:key phase}
       [:div.phase-step
        {:class (cond
                  (= phase current-phase) "active"
                  (< (.indexOf PHASES phase) (.indexOf PHASES current-phase)) "done"
                  :else "")}
        (PHASE-LABELS phase)])
     PHASES)])

(defn- phase-prompts [phase-data]
  (when-let [prompts (:prompts phase-data)]
    [:div.prompts-container
     (map-indexed
       (fn [i p]
         ^{:key i}
         [:div.prompt-item
          [:span.prompt-icon "?"]
          [:p p]])
       prompts)]))

(defn- can-advance? [questions]
  (every? (fn [q]
            (let [r (responses/current-response (:question/id q))]
              (and r (not (nil? (:value r))))))
          questions))

(defn- lesson-phase-view [lesson phase on-advance on-back attempt-id]
  (let [phase-data (get-in lesson [:lesson/phases phase])
        questions  (:questions phase-data)
        hints-map  (:lesson/hints lesson)]
    [:div.phase-view
     [phase-indicator phase]
     [:div.phase-header
      [:h2 (PHASE-LABELS phase)]]
     [phase-prompts phase-data]
     [:div.questions-list
      (map-indexed
        (fn [i q]
          ^{:key i}
          [:div.question-card
           [responses/question-view q (get hints-map (:question/id q))]])
        questions)]
     [:div.phase-nav
      (when on-back
        [:button.btn-secondary {:on-click on-back} "← Back"])
      [:button.btn-primary
       {:on-click  (fn []
                     (doseq [q questions]
                       (save-response! (:lesson/id lesson) q attempt-id))
                     (on-advance))
        :disabled  (not (can-advance? questions))}
       (if (= phase :look-back) "Complete Lesson" "Continue →")]]]))

;; ── Lesson player ─────────────────────────────────────────────────────────────

(defn lesson-view [lesson-id]
  (let [lesson     (curriculum/get-lesson lesson-id)
        phase-idx  (r/atom 0)
        attempt-id (r/atom nil)
        student-id (get-in @state/app-state [:student :id])]
    (when lesson
      (-> (start-attempt! lesson-id student-id)
          (.then #(reset! attempt-id %))))
    (fn [_]
      (if-not lesson
        [:div.error-view
         [:p "Lesson not found."]]
        (let [phase     (nth PHASES @phase-idx)
              is-last   (= phase :look-back)]
          [:div.lesson-view
           [:div.lesson-header
            [:button.btn-back {:on-click #(router/navigate! "/")} "← Curriculum"]
            [:h1.lesson-title (:lesson/title lesson)]]
           [lesson-phase-view
            lesson
            phase
            (fn []  ;; advance
              (responses/clear-responses!)
              (if is-last
                (do
                  (complete-lesson! lesson-id @attempt-id)
                  (router/navigate! "/"))
                (swap! phase-idx inc)))
            (when (pos? @phase-idx)
              (fn []  ;; back
                (responses/clear-responses!)
                (swap! phase-idx dec)))
            @attempt-id]])))))
