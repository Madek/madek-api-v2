(ns madek.api.management
  (:require
   [clojure.tools.logging :as logging]
   [logbug.debug :as debug]
   [logbug.thrown :as thrown]
   [madek.api.authentication.basic :as basic-auth]
   [madek.api.utils.config :as config :refer [get-config]]
   [madek.api.utils.rdbms :as rdbms]))

(defn- shutdown [_]
  (future
    (Thread/sleep 500)
    (System/exit 0))
  {:status 204})

(defn- get-status [_]
  ; checks DB connection
  {:status 200 :body {:msg "OK" :db (rdbms/check-connection)}})

(defn mw-management-auth [handler]
  (fn [request]
    (if-let [password (-> request basic-auth/extract :password)]
      (if-not (= password "secret");(-> (get-config) :madek_master_secret))
        {:status 401 :body {:msg "Password doesn't match the madek_master_secret" :pw password :mpw (-> (get-config) :madek_master_secret)}}
        (handler request))
      {:status 401 :body {:msg "The management pages require basic password authentication."}})))

(def api-routes
  ["/management"
   ["/status" {:get {:middleware [mw-management-auth]
                     :handler get-status}}]
   ["/shutdown" {:post {:middleware [mw-management-auth]
                        :handler shutdown}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
