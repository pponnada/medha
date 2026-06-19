(ns medha.db)

(def DB-NAME    "medha")
(def DB-VERSION 1)

(defonce ^:private db-conn (atom nil))

(defn- req->promise [req]
  (js/Promise.
    (fn [resolve reject]
      (set! (.-onsuccess req) #(resolve (.. % -target -result)))
      (set! (.-onerror   req) #(reject  (.. % -target -error))))))

(defn open-db []
  (if @db-conn
    (js/Promise.resolve @db-conn)
    (js/Promise.
      (fn [resolve reject]
        (let [req (.open js/indexedDB DB-NAME DB-VERSION)]
          (set! (.-onupgradeneeded req)
                (fn [e]
                  (let [db (.. e -target -result)]
                    (letfn [(make-store [name opts]
                              (when-not (.contains (.-objectStoreNames db) name)
                                (.createObjectStore db name (clj->js opts))))]
                      (make-store "settings"      {:keyPath "key"})
                      (make-store "students"      {:keyPath "id" :autoIncrement true})
                      (let [at (make-store "attempts" {:keyPath "id" :autoIncrement true})]
                        (when at
                          (.createIndex at "by_student"  "student_id" #js{:unique false})
                          (.createIndex at "by_problem"  "problem_id" #js{:unique false})))
                      (let [rs (make-store "responses" {:keyPath "id" :autoIncrement true})]
                        (when rs
                          (.createIndex rs "by_attempt"  "attempt_id"     #js{:unique false})
                          (.createIndex rs "by_student"  "student_id"     #js{:unique false})
                          (.createIndex rs "pending"     "pending_review" #js{:unique false})))
                      (let [sm (make-store "skill_mastery" {:keyPath "id" :autoIncrement true})]
                        (when sm
                          (.createIndex sm "by_student" "student_id" #js{:unique false})
                          (.createIndex sm "by_skill"   "skill_id"   #js{:unique false})))
                      (let [ri (make-store "review_items" {:keyPath "id" :autoIncrement true})]
                        (when ri
                          (.createIndex ri "by_student"    "student_id"       #js{:unique false})
                          (.createIndex ri "by_next_date"  "next_review_date"  #js{:unique false})))
                      (let [lp (make-store "lesson_progress" {:keyPath "id" :autoIncrement true})]
                        (when lp
                          (.createIndex lp "by_student" "student_id" #js{:unique false})
                          (.createIndex lp "by_lesson"  "lesson_id"  #js{:unique false})))
                      (let [qa (make-store "quiz_attempts" {:keyPath "id" :autoIncrement true})]
                        (when qa (.createIndex qa "by_student" "student_id" #js{:unique false})))
                      (let [an (make-store "analytics" {:keyPath "id" :autoIncrement true})]
                        (when an
                          (.createIndex an "by_student" "student_id" #js{:unique false})
                          (.createIndex an "by_type"    "event_type" #js{:unique false})))))))
          (set! (.-onsuccess req)
                (fn [e]
                  (let [db (.. e -target -result)]
                    ;; When another context deletes/upgrades the DB (e.g. DevTools clear),
                    ;; close gracefully so the next open-db call re-establishes cleanly.
                    (set! (.-onversionchange db)
                          (fn []
                            (.close db)
                            (reset! db-conn nil)))
                    (reset! db-conn db)
                    (resolve db))))
          (set! (.-onerror req)
                #(reject (.. % -target -error))))))))

(defn- with-store [store-name mode f]
  (.then (open-db)
         (fn [db]
           (let [tx    (.transaction db #js[store-name] mode)
                 store (.objectStore tx store-name)]
             (f store)))))

(defn get-setting [k]
  (.then
    (with-store "settings" "readonly" #(req->promise (.get % k)))
    (fn [rec] (when rec (.-value rec)))))

(defn put-setting! [k v]
  (with-store "settings" "readwrite"
    #(req->promise (.put % (clj->js {:key k :value v})))))

(defn put-record! [store-name rec]
  (with-store store-name "readwrite"
    #(req->promise (.add % (clj->js rec)))))

(defn update-record! [store-name rec]
  (with-store store-name "readwrite"
    #(req->promise (.put % (clj->js rec)))))

(defn get-record [store-name key]
  (.then
    (with-store store-name "readonly" #(req->promise (.get % key)))
    (fn [r] (when r (js->clj r :keywordize-keys true)))))

(defn get-all-records [store-name]
  (.then
    (with-store store-name "readonly"
      (fn [store]
        (js/Promise.
          (fn [resolve reject]
            (let [req (.getAll store)]
              (set! (.-onsuccess req) #(resolve (.. % -target -result)))
              (set! (.-onerror   req) #(reject  (.. % -target -error))))))))
    (fn [arr] (mapv #(js->clj % :keywordize-keys true) (or arr #js[])))))

(defn get-by-index [store-name index-name value]
  (.then
    (with-store store-name "readonly"
      (fn [store]
        (js/Promise.
          (fn [resolve reject]
            (let [idx (.index store index-name)
                  req (.getAll idx value)]
              (set! (.-onsuccess req) #(resolve (.. % -target -result)))
              (set! (.-onerror   req) #(reject  (.. % -target -error))))))))
    (fn [arr] (mapv #(js->clj % :keywordize-keys true) (or arr #js[])))))

(defn delete-record! [store-name key]
  (with-store store-name "readwrite"
    #(req->promise (.delete % key))))
