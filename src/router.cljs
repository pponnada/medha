(ns medha.router
  (:require [medha.state :as state]))

(defn- parse-hash []
  (let [h (.-hash js/location)]
    (if (or (nil? h) (= h "") (= h "#"))
      "/"
      (subs h 1))))

(defn- parse-path [path]
  (let [parts (remove empty? (clojure.string/split path #"/"))]
    (case (first parts)
      nil        {:view :home   :params {}}
      "lesson"   {:view :lesson :params {:lesson-id (keyword "lesson" (second parts))}}
      "quiz"     {:view :quiz   :params {:quiz-id   (keyword "quiz"   (second parts))}}
      "review"   {:view :review :params {}}
      "progress" {:view :progress :params {}}
      "teacher"  (case (second parts)
                   nil          {:view :teacher-pin    :params {}}
                   "dashboard"  {:view :teacher-dash   :params {}}
                   "mastery"    {:view :teacher-mastery :params {}}
                   "progress"   {:view :teacher-progress :params {}}
                   "struggle"   {:view :teacher-struggle :params {}}
                   "reviews"    {:view :teacher-reviews  :params {}}
                   "pending"    {:view :teacher-pending  :params {}}
                   "authoring"  {:view :teacher-authoring :params {}}
                   "skills"     {:view :teacher-skills  :params {}}
                   "settings"   {:view :teacher-settings :params {}}
                   {:view :teacher-pin :params {}})
      {:view :home :params {}})))

(defn current-route []
  (parse-path (parse-hash)))

(defn navigate! [path]
  (set! (.-hash js/location) path))

(defn on-route-change []
  (state/set-state! :route (current-route)))

(defn init! []
  (.addEventListener js/window "hashchange" on-route-change)
  (on-route-change))
