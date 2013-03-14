(ns treklet-server.core
  (:require [cljs.nodejs :as node]
            [treklet.util :as util]))

(def -serve-path- "/home/jkross/Projects/treklet/serve-dir/")

(def -sockio-         (.listen (node/require "socket.io") 8081))
(def -http-           (node/require "http"))
(def -static-         (node/require "node-static"))
(def -file-server-    (new treklet-server.core.-static-/Server -serve-path- (util/clj->js { "cache" 0 })))
(def -express-        (node/require "express"))
(def -fs-             (node/require "fs"))
(def -im-             (node/require "imagemagick"))
(def -uuid-           (node/require "node-uuid"))

(def -client-by-locale-     (atom {}))
(def -client-by-uuid-       (atom {}))
(def -rearrival-notifiers-  (atom {}))

(defrecord Avatar [username balloon-bg-color balloon-text-color name-bg-color name-text-color])

(defn notify-client
  [client evt-name data]
  (when (and client (client :socket))
    (.emit (client :socket) evt-name (util/clj->js data))))

(defn react-client
  [client evt-name f]
  (.on (client :socket) evt-name
    (fn [data]
      (let [clj-data (js->clj data)]
        (if (map? clj-data)
          (f (with-meta clj-data {:js-data data}))
          (f {}))))))


;; Should this have util/clj->js automatically on data?
(defn broadcast
  [locale mesg-type data]
  (let [users (@-client-by-locale- locale)]
    (if users
      (doseq [u @users] (.emit (u :socket) mesg-type (util/clj->js data))))))
  
(defn forward-to-locale
  [client mesg-type]
  (.on (client :socket) mesg-type
    (fn [data]
      (broadcast @(client :locale) (str "user-" mesg-type) data))))

(defn set-user-locale
  [client locale-url]
  (when (and locale-url (not (contains? @-client-by-locale- locale-url)))
    (swap! -client-by-locale- assoc locale-url (atom [])))
  (let [old-users      (@-client-by-locale- @(client :locale))
        new-users      (@-client-by-locale- locale-url)]
    (when old-users
      (doseq [u @old-users]
        (.emit (u :socket) "user-departure" (util/clj->js (client :avatar))))
      (when (some #(= client %) @old-users)
        (swap! old-users util/identity-remove client)))
    (when new-users
      (doseq [u @new-users]
        (.emit (client :socket) "user-arrival" (util/clj->js {:avatar (u :avatar) :uuid (u :uuid)}))

        (if @(u :last-move)
          (.emit (client :socket) "user-move"    ((meta @(u :last-move)) :js-data))) 
        (.emit (u :socket     ) "user-arrival" (util/clj->js {:avatar (client :avatar) :uuid (client :uuid)} )))
      (swap! new-users conj client))

    (if locale-url
      (util/atom-set! (client :locale) locale-url))))

(defn start
  [& _]
  (println "STARTED")
  (comment (.listen (.createServer -http-
    (fn [req resp]
      (.addListener req "end"
        (fn []
          (.serve -file-server- req resp))))) 8080))

  (let [express-app (. -express- (createServer))]
    (.use express-app (. -express- bodyParser (util/clj->js {"uploadDir" "./uploads"} )))

    (.get  express-app "/*"
      (fn [req res]
        (.addListener req "end"
          (fn []
            (.serve -file-server- req res))))) 


    (.post express-app "/avatar-upload"
      (fn [req res next]
        (let [file (.-avatarimage (.-files req))]
          (println (str "PATH: " (.-path file)))
          (.resize -im- (util/clj->js { "srcPath" (.-path file) 
                                        "dstPath" (str -serve-path- "images/" (.-username (.-body req)) ".png")
                                        "width" 64 "height" 64 }) 
                         (fn [err, stdout, stderr]
                           (when err
                            (println (str "error: " err))))) 
          (.send res "okay"))))

    (.listen express-app 8082))

  (.on (.-sockets -sockio-) "connection"
    (fn [socket]
      (.on socket "ready"
        (fn []
          (let [uuid (. -uuid- (v4))]
            (.emit socket "accepted" (util/clj->js {:uuid uuid})))))
      (.on socket "arrival"
        (fn [data]
          (let [data   (js->clj data :keywordize-keys true)
                avatar (data :avatar) 
                client {:uuid      (data :uuid)
                        :username  (avatar :username)
                        :locale    (atom nil) 
                        :last-move (atom nil) 
                        :socket    socket
                        :avatar    avatar      }]

            (swap! -client-by-uuid- assoc (data :uuid) client)
            (set-user-locale client (data :locale))

            (let [notify (@-rearrival-notifiers- (client :uuid))]
              (if notify (notify (data :locale))))

            (forward-to-locale client "speak")

            (react-client client "mesg-for-user"
              (fn [data] (notify-client (@-client-by-uuid-   (data "target-uuid"))  "mesg-for-user"  {"from-uuid" (client :uuid) "user-mesg" (data "user-mesg")})))

            (react-client client "mesg-for-locale"
              (fn [data](println "Have: " @(client :locale) " at " data)  (broadcast @(client :locale) "mesg-for-user" {"from-uuid" (client :uuid) "user-mesg" (data "user-mesg") })))

            (react-client client "move"
                          (fn [data]
                            (util/atom-set! (client :last-move) data) 
                            (broadcast     @(client :locale   ) "user-move" ((meta data) :js-data))))

            (react-client client "disconnect"
                          (fn [data]
                            (swap! -client-by-uuid- dissoc (client :uuid))
                            (swap! -rearrival-notifiers- assoc (client :uuid) 
                                   (fn [new-locale]
                                     (broadcast @(client :locale) 
                                                "user-locale-changed" 
                                                ;Send the entire last-move object.
                                                (util/clj->js 
                                                  {"username" ((client :avatar) :username)
                                                   "last-move" @(client :last-move)
                                                   "locale" new-locale}))
                                     (swap! -rearrival-notifiers- dissoc (client :uuid))))
                            (set-user-locale client nil)))))))))
(set! *main-cli-fn* start)
