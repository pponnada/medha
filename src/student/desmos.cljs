(ns medha.student.desmos)

(defn- desmos-url []
  (let [key (some-> js/window .-MEDHA_CONFIG .-desmosApiKey)]
    (str "https://www.desmos.com/api/v1.9/calculator.js"
         (when (and key (pos? (count key)))
           (str "?apiKey=" key)))))

(defn- desmos-fallback-url []
  "https://www.desmos.com/api/v1.9/calculator.js")

(defonce ^:private calculators (atom {}))
(defonce ^:private load-promise (atom nil))

(defn available? []
  (and (exists? js/Desmos)
       (fn? (.-GraphingCalculator js/Desmos))))

(defn- load-script! [url]
  (js/Promise.
    (fn [resolve]
      (let [script (.createElement js/document "script")]
        (set! (.-src script) url)
        (set! (.-async script) true)
        (set! (.-onload script) #(resolve true))
        (set! (.-onerror script)
              (fn []
                (.remove script)
                (resolve false)))
        (.appendChild (.-head js/document) script)))))

(defn load! []
  (cond
    (available?)
    (js/Promise.resolve true)

    @load-promise
    @load-promise

    :else
    (let [keyed-url (desmos-url)
          p (-> (load-script! keyed-url)
                (.then
                  (fn [ok]
                    (if ok
                      true
                      (let [fallback-url (desmos-fallback-url)]
                        (if (= keyed-url fallback-url)
                          false
                          (do (js/console.warn "Desmos key load failed; retrying without apiKey.")
                              (load-script! fallback-url))))))))]
      (reset! load-promise p)
      p)))

(defn mount! [element-id state-json readonly-ids]
  (-> (load!)
      (.then
        (fn [ok]
          (when (and ok (available?))
            (let [el (.getElementById js/document element-id)]
              (if-not el
                (do (js/console.warn "Desmos mount target missing:" element-id)
                    false)
                (try
                  (let [calc (.GraphingCalculator js/Desmos el
                                                  (clj->js {:keypad            false
                                                            :expressionsTopbar false
                                                            :settingsMenu      false
                                                            :zoomButtons       false
                                                            :lockViewport      true
                                                            :border            false}))]
                    (when state-json
                      (try
                        (.setState calc (.parse js/JSON state-json))
                        (catch :default e
                          (js/console.warn "Desmos state parse error:" e))))
                    (doseq [expr-id readonly-ids]
                      (.setExpression calc (clj->js {:id expr-id :readonly true})))
                    (swap! calculators assoc element-id calc)
                    calc)
                  (catch :default e
                    (js/console.error "Desmos calculator failed to initialize:" e)
                    false)))))))))

(defn get-state [element-id]
  (when-let [calc (get @calculators element-id)]
    (.stringify js/JSON (.getState calc))))

(defn destroy! [element-id]
  (when-let [calc (get @calculators element-id)]
    (.destroy calc)
    (swap! calculators dissoc element-id)))
