(ns medha.student.export
  (:require [medha.db :as db]))

(defn- iso-date []
  (-> (js/Date.) .toISOString (.slice 0 10)))

(defn- trigger-download! [json-str filename]
  (let [blob (js/Blob. #js[json-str] #js{:type "application/json"})
        url  (.createObjectURL js/URL blob)
        a    (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.click a)
    (js/setTimeout #(.revokeObjectURL js/URL url) 1000)))

(defn export-data! [student-id]
  (-> (js/Promise.all
        #js[(db/get-by-index "attempts"        "by_student"   student-id)
            (db/get-by-index "responses"       "by_student"   student-id)
            (db/get-by-index "skill_mastery"   "by_student"   student-id)
            (db/get-by-index "review_items"    "by_student"   student-id)
            (db/get-by-index "lesson_progress" "by_student"   student-id)
            (db/get-by-index "analytics"       "by_student"   student-id)])
      (.then
        (fn [[attempts responses mastery reviews progress analytics]]
          (let [payload {:exported-at (-> (js/Date.) .toISOString)
                         :student-id  student-id
                         :attempts    attempts
                         :responses   responses
                         :mastery     mastery
                         :reviews     reviews
                         :progress    progress
                         :analytics   analytics}
                json    (.stringify js/JSON (clj->js payload) nil 2)]
            (trigger-download! json (str "medha-export-" (iso-date) ".json"))
            :ok)))
      (.catch #(js/console.error "Export failed:" %))))
