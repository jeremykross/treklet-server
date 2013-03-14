(defproject treklet "1.0.0-SNAPSHOT"
  :description "Treklet: The Social Browsing Bookmarklet"
  :dependencies [[org.clojure/clojure "1.3.0"]
  		 [hiccup "0.3.7"]
		 [cssgen "0.2.6"]]
  :plugins [[lein-cljsbuild "0.1.8"]]
  :cljsbuild {
    :builds [{
      :source-path "src/treklet_server/server"
      :compiler {
        :output-to "node-server.js"
        :optimizations :simple
        :target :nodejs
      }}]}
  :main treklet-server.server.core)
