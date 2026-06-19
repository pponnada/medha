(ns medha.app
  (:require [reagent.core         :as r]
            [reagent.dom          :as rdom]
            [medha.state          :as state]
            [medha.db             :as db]
            [medha.router         :as router]
            [medha.curriculum     :as curriculum]
            [medha.student.lesson :as lesson]
            [medha.student.export :as export]))

;; ── Setup screens ─────────────────────────────────────────────────────────────

(defn- valid-url? [s]
  (and (seq s)
       (or (.startsWith s "http://")
           (.startsWith s "https://"))))

(defn- url-entry-view []
  (let [url   (r/atom "")
        error (r/atom nil)]
    (fn []
      [:div.setup-view
       [:div.setup-card
        [:h1.app-title "Medha"]
        [:p.setup-intro "Enter the curriculum URL shared by your teacher."]
        (when @error [:p.error-msg @error])
        [:input.url-input
         {:type        "url"
          :placeholder "http://..."
          :value       @url
          :on-change   #(do (reset! url (.. % -target -value))
                            (reset! error nil))}]
        [:button.btn-primary
         {:on-click (fn []
                      (if-not (valid-url? @url)
                        (reset! error "URL must start with http:// or https://")
                        (do (state/set-state! :loading true)
                            (-> (curriculum/load-from-url! @url)
                                (.then  #(state/set-state! :loading false))
                                (.catch #(do (state/set-state! :loading false)
                                             (reset! error "Could not load curriculum. Check the URL.")))))))}
         "Load Curriculum"]]])))

(defn- student-setup-view []
  (let [name-val (r/atom "")]
    (fn []
      [:div.setup-view
       [:div.setup-card
        [:h1.app-title "Welcome"]
        [:p.setup-intro "What is your name?"]
        [:input.name-input
         {:type        "text"
          :placeholder "Your name"
          :value       @name-val
          :on-change   #(reset! name-val (.. % -target -value))}]
        [:button.btn-primary
         {:on-click (fn []
                      (when (seq @name-val)
                        (-> (db/put-record! "students" {:name @name-val :created_at (.now js/Date)})
                            (.then (fn [id]
                                     (let [student {:id id :name @name-val}]
                                       (state/set-state! :student student)
                                       (db/put-setting! "student-id" id)))))))}
         "Let's go →"]]])))

;; ── Desmos warning ────────────────────────────────────────────────────────────

(defn- desmos-warning []
  (let [dismissed (r/atom false)]
    (fn []
      ;; Only warn when offline AND Desmos hasn't been cached yet.
      ;; When online, Desmos loads lazily from the network without needing the cache.
      (when (and (not (:desmos-cached @state/app-state))
                 (not (.-onLine js/navigator))
                 (not @dismissed))
        [:div.desmos-warning
         [:p "You're offline and Desmos hasn't been cached yet. Lessons with Desmos activities won't work until you reconnect."]
         [:button.btn-dismiss {:on-click #(reset! dismissed true)} "×"]]))))

;; ── Curriculum map ────────────────────────────────────────────────────────────

(defn- lesson-card [lesson]
  [:div.lesson-card
   {:on-click #(router/navigate! (str "/lesson/" (name (:lesson/id lesson))))}
   [:h3.lesson-title (:lesson/title lesson)]
   [:span.lesson-template (name (or (:lesson/template lesson) :unknown))]])

(defn- chapter-section [chapter]
  [:div.chapter-section
   [:h2.chapter-title (:chapter/title chapter)]
   [:p.chapter-desc (:chapter/description chapter)]
   [:div.lessons-grid
    (map-indexed
      (fn [i ls]
        ^{:key i} [lesson-card ls])
      (:chapter/lessons chapter))]])

(defn- curriculum-home []
  (let [c (curriculum/get-curriculum)
        s (get-in @state/app-state [:student :name])]
    [:div.home-view
     [:div.home-header
      [:div
       [:h1.curriculum-title (:curriculum/title c)]
       [:p.student-greeting (str "Hello, " s "!")]]
      [:button.btn-text {:on-click #(router/navigate! "/teacher")} "Teacher"]]
     [:div.chapters
      (map-indexed
        (fn [i ch]
          ^{:key i} [chapter-section ch])
        (:curriculum/chapters c))]
     [:div.export-row
      [:button.btn-secondary
       {:on-click #(export/export-data! (get-in @state/app-state [:student :id]))}
       "Export my data"]]]))

;; ── Teacher PIN gate ──────────────────────────────────────────────────────────

(defn- hash-pin [pin]
  (-> (.digest (.-subtle js/crypto) "SHA-256"
               (.encode (js/TextEncoder.) pin))
      (.then (fn [buf]
               (let [arr (js/Uint8Array. buf)]
                 (js/Array.from arr
                                (fn [b] (.padStart (.toString b 16) 2 "0"))))))))

(defn- teacher-pin-view []
  (let [pin       (r/atom "")
        error     (r/atom nil)
        first-run (r/atom nil)]
    (-> (db/get-setting "pin")
        (.then #(reset! first-run (nil? %))))
    (fn []
      [:div.setup-view
       [:div.setup-card
        [:h2 (if @first-run "Set Teacher PIN" "Teacher Mode")]
        (when @error [:p.error-msg @error])
        [:input.pin-input
         {:type      "password"
          :maxLength 8
          :value     @pin
          :on-change #(reset! pin (.. % -target -value))}]
        [:button.btn-primary
         {:on-click
          (fn []
            (if @first-run
              (-> (hash-pin @pin)
                  (.then (fn [h]
                           (db/put-setting! "pin" (clojure.string/join h))
                           (state/assoc-in-state! [:teacher :authenticated] true)
                           (router/navigate! "/teacher/dashboard"))))
              (-> (js/Promise.all
                    #js[(db/get-setting "pin") (hash-pin @pin)])
                  (.then (fn [[stored entered]]
                           (if (= stored (clojure.string/join entered))
                             (do (state/assoc-in-state! [:teacher :authenticated] true)
                                 (router/navigate! "/teacher/dashboard"))
                             (reset! error "Incorrect PIN. Try again.")))))))}
         "Enter"]
        [:button.btn-text {:on-click #(router/navigate! "/")} "Cancel"]]])))

;; ── Teacher dashboard ─────────────────────────────────────────────────────────

(defn- teacher-dashboard-view []
  [:div.teacher-view
   [:div.teacher-header
    [:h1 "Teacher Dashboard"]
    [:button.btn-text
     {:on-click (fn []
                  (state/assoc-in-state! [:teacher :authenticated] false)
                  (router/navigate! "/"))}
     "Exit Teacher Mode"]]
   [:div.dashboard-panels
    [:div.panel
     [:h2 "Student"]
     [:p (get-in @state/app-state [:student :name])]
     [:button.btn-secondary
      {:on-click #(export/export-data! (get-in @state/app-state [:student :id]))}
      "Export Data"]]
    [:div.panel
     [:h2 "Pending Reviews"]
     [:p "Mastery and pending review queue available in Version 3."]]
    [:div.panel
     [:h2 "Curriculum"]
     (when-let [c (curriculum/get-curriculum)]
       [:div
        [:p (:curriculum/title c)]
        [:p (str (count (:curriculum/chapters c)) " chapters")]])]]])

;; ── Root router ───────────────────────────────────────────────────────────────

(defn- loading-view []
  [:div.loading-view
   [:div.spinner]
   [:p "Loading..."]])

(defn- error-banner []
  (when-let [err (:error @state/app-state)]
    [:div.error-banner
     [:p err]
     [:button {:on-click #(state/set-state! :error nil)} "×"]]))

(defn root []
  (let [s         @state/app-state
        route     (:route s)
        view      (:view route)
        params    (:params route)
        loading   (:loading s)
        curriculum (:curriculum s)
        student   (:student s)
        teacher-auth (get-in s [:teacher :authenticated])]
    [:div.app
     [error-banner]
     (cond
       loading
       [loading-view]

       (nil? curriculum)
       [url-entry-view]

       (nil? student)
       [student-setup-view]

       (= view :lesson)
       [lesson/lesson-view (:lesson-id params)]

       (= view :teacher-pin)
       [teacher-pin-view]

       (and (= view :teacher-dash) teacher-auth)
       [teacher-dashboard-view]

       (and (clojure.string/starts-with? (name (or view :home)) "teacher") (not teacher-auth))
       [teacher-pin-view]

       :else
       [:div
        [desmos-warning]
        [curriculum-home]])]))

;; ── Boot ──────────────────────────────────────────────────────────────────────

(defn init! []
  (router/init!)
  (-> (db/open-db)
      (.then (fn [_]
               (js/Promise.all
                 #js[(db/get-setting "student-id")
                     (curriculum/desmos-cached?)])))
      (.then (fn [[student-id desmos-ok]]
               (state/set-state! :desmos-cached desmos-ok)
               (-> (curriculum/load-from-cache!)
                   (.then (fn [c]
                            (when (and c student-id)
                              (-> (db/get-record "students" student-id)
                                  (.then #(when % (state/set-state! :student %)))))))
                   ;; Silently fall through to URL entry view on any cache-load failure
                   (.catch (fn [_] nil)))))
      (.then #(state/set-state! :loading false))
      (.catch (fn [e]
                (js/console.error "Boot error:" e)
                (state/set-state! :loading false)
                (state/set-state! :error "Something went wrong. Please refresh.")))))

(defn- main []
  (rdom/render [root] (.getElementById js/document "app"))
  (init!))

;; Boot immediately when this script is evaluated by Scittle
(main)
