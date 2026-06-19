(ns medha.student.desmos)

(defn- desmos-url []
  (let [key (some-> js/window .-MEDHA_CONFIG .-desmosApiKey)]
    (str "https://www.desmos.com/api/v1.9/calculator.js"
         (when (and key (pos? (count key)))
           (str "?apiKey=" key)))))

(defonce ^:private calculators (atom {}))
(defonce ^:private load-promise (atom nil))

(defn available? []
  (and (exists? js/Desmos)
       (fn? (.-GraphingCalculator js/Desmos))))

(defn load! []
  (cond
    (available?)
    (js/Promise.resolve true)

    @load-promise
    @load-promise

    :else
    (let [p (js/Promise.
              (fn [resolve reject]
                (let [script (.createElement js/document "script")]
                  (set! (.-src script) (desmos-url))
                  (set! (.-onload script) #(resolve true))
                  (set! (.-onerror script)
                        (fn []
                          (js/console.warn "Desmos failed to load. Desmos activities will be unavailable.")
                          (resolve false)))
                  (.appendChild (.-head js/document) script))))]
      (reset! load-promise p)
      p)))

(defn mount! [element-id state-json readonly-ids]
  (-> (load!)
      (.then
        (fn [ok]
          (when (and ok (available?))
            (let [el   (.getElementById js/document element-id)
                  calc (.GraphingCalculator js/Desmos el
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
              calc))))))

(defn get-state [element-id]
  (when-let [calc (get @calculators element-id)]
    (.stringify js/JSON (.getState calc))))

(defn destroy! [element-id]
  (when-let [calc (get @calculators element-id)]
    (.destroy calc)
    (swap! calculators dissoc element-id)))
