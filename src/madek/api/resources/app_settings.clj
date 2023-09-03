(ns madek.api.resources.app-settings
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [logbug.debug :as debug]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]
   
   [madek.api.resources.shared :as sd]))

(defn db_get-app-settings []
  (let [query (->
               (sql/select :*)
               (sql/from :app_settings)
               (sql/format))
        result (first (jdbc/query (get-ds) query))]
    result))

(defn handle_get-app-settings
  [req]
  (sd/response_ok (db_get-app-settings)))

; TODO def schema import export

(def admin-routes 
  [
   ["/app-settings"
    {:get {:summary "Get App Settings."
           :handler handle_get-app-settings
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:body s/Any}}}
     ; TODO app-settings update
     :put {:summary "Update App Settings."
           :handler handle_get-app-settings
           :swagger {:produces "application/json"
                     :consumes  "application/json"}
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:body s/Any}}
           }}]
  ])

(def user-routes 
  [
   ["/app-settings"
     {:get {:summary "Get App Settings."
            :handler handle_get-app-settings
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :coercion reitit.coercion.schema/coercion
            :responses {200 {:body s/Any}}}}
    ]
  ])