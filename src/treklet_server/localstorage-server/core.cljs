(ns treklet-server.localstorage.core
  (:require [treklet.util :as util]
            [goog.events  :as evt]
            [goog.json    :as json]
            [goog.net.xpc :as xpc]
            [goog.net.xpc.CrossPageChannel :as cpc]))

(defn start
  []
  (evt/listenOnce js/window (.LOAD evt/EventType) 
    (fn []
      (let [xpc (.getParameterValue (goog.Uri. (.href (.location js/window))) "xpc")]
        (when xpc
          (let [config (json/parse xpc)
                child-channel (xpc/CrossPageChannel. config)]

            (.registerService child-channel "write"
              (fn [data]
                (let [data (js->clj (json/parse data) :keywordize-keys true)]
                  (.setItem js/localStorage (data :k) (json/serialize (util/clj->js (data :v))))
                  (.send child-channel "on-write-complete"))))

            (.registerService child-channel "read"
              (fn [data]
                (let [data (js->clj (json/parse data) :keywordize-keys true)
                      item (.getItem js/localStorage (data :k))]
                  (.send child-channel "on-read-complete" item))))

            (.registerService child-channel "clear"
              (fn []
                (js/alert "Clearing Localstorage")
                (. js/localStorage (clear))
                (.send child-channel "on-clear-complete")))

            (.connect child-channel
              (fn []
                (js/setTimeout
                  (fn []
                    (.send child-channel "ack")) 1000)))))))))
