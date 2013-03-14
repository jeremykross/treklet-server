(ns treklet.server.core
  (:gen-class)
  (:require [hiccup.core         :as hic]
            [hiccup.page-helpers :as hic-help]
            [cljs.closure        :as cljsc]))

(def output-dir "/home/jkross/Projects/treklet/serve-dir")

(defn tmpl-localstorage-server
  []
  (hic/html
    [:head]
    [:body
     (hic-help/include-js "localstorage-server.js")
     [:script
      "goog.require('treklet.server.localstorage.core');"]
     [:script
      "treklet.server.localstorage.core.start();"]]))

(defn -main
  [& args]
  (spit (str output-dir "localstorage.html") 
        (tmpl-localstorage-server)))
