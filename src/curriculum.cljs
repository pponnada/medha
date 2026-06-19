(ns medha.curriculum
  (:require [medha.db      :as db]
            [medha.state   :as state]
            [clojure.edn   :as edn]))

(defonce ^:private loaded (atom nil))

(defn- find-by-id [coll id-key id]
  (first (filter #(= (get % id-key) id) coll)))

(defn- flatten-lessons [curriculum]
  (for [ch (:curriculum/chapters curriculum)
        ls (:chapter/lessons ch)]
    ls))

(defn get-lesson [lesson-id]
  (when-let [c @loaded]
    (find-by-id (flatten-lessons c) :lesson/id lesson-id)))

(defn get-chapter-for-lesson [lesson-id]
  (when-let [c @loaded]
    (first (filter (fn [ch]
                     (find-by-id (:chapter/lessons ch) :lesson/id lesson-id))
                   (:curriculum/chapters c)))))

(defn get-curriculum [] @loaded)

(defn- validate! [c]
  (let [required [:curriculum/id :curriculum/title :curriculum/chapters]]
    (doseq [k required]
      (when-not (contains? c k)
        (throw (js/Error. (str "Curriculum missing required key: " k)))))
    c))

(defn load-from-url! [url]
  (-> (js/fetch url)
      (.then #(.text %))
      (.then (fn [text]
               (let [c (validate! (edn/read-string text))]
                 (reset! loaded c)
                 (state/set-state! :curriculum c)
                 (.catch (db/put-setting! "curriculum-url" url)
                         #(js/console.warn "Could not persist curriculum URL:" %))
                 c)))
      (.catch (fn [err]
                (js/console.error "Failed to load curriculum:" err)
                (throw err)))))

(defn load-from-cache! []
  (-> (db/get-setting "curriculum-url")
      (.then (fn [url]
               (when (and url
                          (or (.startsWith url "http://")
                              (.startsWith url "https://")))
                 (-> (load-from-url! url)
                     (.catch (fn [err]
                               ;; Stale or invalid URL — clear it so next boot starts fresh
                               (.catch (db/put-setting! "curriculum-url" nil) #())
                               (throw err)))))))))

(defn desmos-cached? []
  (-> (js/caches.open "medha-v10")
      (.then (fn [cache]
               (.match cache "https://www.desmos.com/api/v1.9/calculator.js")))
      (.then boolean)))
