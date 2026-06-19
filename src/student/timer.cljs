(ns medha.student.timer)

(def IDLE-TIMEOUT-MS (* 5 60 1000))

(defonce ^:private state
  (atom {:running        false
         :started-at     nil
         :elapsed-ms     0
         :last-active-at nil
         :idle-timer     nil}))

(defn- clear-idle-timer! []
  (when-let [t (:idle-timer @state)]
    (js/clearTimeout t)
    (swap! state assoc :idle-timer nil)))

(defn- reset-idle-timer! []
  (clear-idle-timer!)
  (when (:running @state)
    (swap! state assoc
           :last-active-at (.now js/Date)
           :idle-timer
           (js/setTimeout
             (fn []
               (when (:running @state)
                 (swap! state assoc
                        :elapsed-ms  (+ (:elapsed-ms @state)
                                        (- (.now js/Date) (:started-at @state)))
                        :started-at  nil
                        :running     false)))
             IDLE-TIMEOUT-MS))))

(defn- on-activity []
  (when-not (:running @state)
    (swap! state assoc
           :running    true
           :started-at (.now js/Date)))
  (reset-idle-timer!))

(defonce ^:private activity-listeners
  (do
    (doseq [evt ["mousemove" "mousedown" "keydown" "touchstart" "scroll"]]
      (.addEventListener js/document evt on-activity #js{:passive true}))
    true))

(defn start! []
  (swap! state assoc
         :running        true
         :started-at     (.now js/Date)
         :elapsed-ms     0
         :last-active-at (.now js/Date))
  (reset-idle-timer!))

(defn stop! []
  (clear-idle-timer!)
  (let [s @state
        total (+ (:elapsed-ms s)
                 (if (:running s)
                   (- (.now js/Date) (:started-at s))
                   0))]
    (swap! state assoc :running false :elapsed-ms 0 :started-at nil)
    total))

(defn elapsed []
  (let [s @state]
    (+ (:elapsed-ms s)
       (if (and (:running s) (:started-at s))
         (- (.now js/Date) (:started-at s))
         0))))
