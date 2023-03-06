(ns madek.api.resources.roles
  (:require
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.resources.roles.index :refer [get-index]]
    [madek.api.resources.roles.role :refer [get-role, handle_get-role]]
    [madek.api.resources.shared :as sd]
   [reitit.coercion.schema]
    [schema.core :as s]
    ))


(def routes
  (cpj/routes
    (cpj/GET "/roles/" [] get-index)
    (cpj/GET "/roles/:id" _ get-role)
    (cpj/ANY "*" _ sd/dead-end-handler)
    ))


(def schema_export-role
  {:id s/Uuid
   :labels s/Any;{{s/Any s/Any}}
   :created_at s/Any})

(def ring-routes
  ["/roles"
   ["/" {:get {:summary "Get list of roles."
               :description "Get list of roles."
               :handler get-index
               :swagger {:produces "application/json"}
               :parameters {:query {(s/optional-key :page) s/Int}}
               :content-type "application/json"
                ;:accept "application/json"
               :coercion reitit.coercion.schema/coercion}}]
                ;:responses {200 {:body {:people [{:id s/Uuid}]}}}

   ["/:id" {:get {:summary "Get role by id"
            :description "Get a role by id. Returns 404, if no such role exists."
            :swagger {:produces "application/json"}
            :content-type "application/json"
                  ;:accept "application/json"
            :handler handle_get-role
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:id s/Str}}
            :responses {200 {:body schema_export-role}
                        404 {:body s/Str}}}}]]
  )
;### Debug ####################################################################
;(debug/debug-ns *ns*)
