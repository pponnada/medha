(ns medha.state
  (:require [reagent.core :as r]))

(defonce app-state
  (r/atom
    {:route      {:path "/" :params {}}
     :mode       :student
     :loading    true
     :error      nil
     :curriculum nil
     :student    nil
     :lesson     {:id           nil
                  :phase        nil
                  :question-idx 0
                  :responses    {}
                  :attempt-id   nil}
     :teacher    {:authenticated false}}))

(defn get-in-state [ks]    (get-in @app-state ks))
(defn set-state!   [k v]   (swap! app-state assoc k v))
(defn update-state! [k f]  (swap! app-state update k f))
(defn assoc-in-state! [ks v] (swap! app-state assoc-in ks v))
