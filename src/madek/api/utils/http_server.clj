(ns madek.api.utils.http-server
  (:require
   [aleph.http :as http-server]
   [taoensso.timbre :refer [info]]))

(defonce _server (atom nil))

(defn stop []
  (when-let [server @_server]
    (info stop)
    (.close server)
    (reset! _server nil)))

(defn start [conf main-handler]
  "Starts (or stops and then starts) the webserver"
  (let [server-conf (conj {:ssl? false
                           :join? false}
                          (select-keys conf [:port :host]))]
    (stop)
    (info "starting server " server-conf)
    (reset! _server (http-server/start-server main-handler server-conf)))
  (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (stop)))))
