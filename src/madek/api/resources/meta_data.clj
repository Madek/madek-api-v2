(ns madek.api.resources.meta-data
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.pagination :as pagination]
    [madek.api.resources.meta-data.index :as meta-data.index]
    [madek.api.resources.meta-data.meta-datum :as meta-datum]
    [madek.api.resources.shared :as sd]
    [madek.api.utils.rdbms :as rdbms]
    [reitit.coercion.schema]
    [schema.core :as s]
    ))

(def routes
  (cpj/routes
    (cpj/GET "/media-entries/:media_entry_id/meta-data/" _ meta-data.index/get-index)
    (cpj/GET "/collections/:collection_id/meta-data/" _ meta-data.index/get-index)
    (cpj/GET "/meta-data/:meta_datum_id" _ meta-datum/get-meta-datum)
    (cpj/GET "/meta-data/:meta_datum_id/data-stream" _ meta-datum/get-meta-datum-data-stream)
    (cpj/GET "/meta-data-roles/:meta_datum_id" _ meta-datum/get-meta-datum-role)
    (cpj/ANY "*" _ sd/dead-end-handler)
    ))

(def ring-routes
  ["/meta-data"
   ["/:meta_datum_id" {:get {:handler meta-datum/get-meta-datum
                             :middleware [sd/ring-wrap-add-meta-datum-with-media-resource
                                          sd/ring-wrap-authorization]
                             :summary "Get meta-data for id"
                             :description "Get meta-data for id. TODO: should return 404, if no such meta-data role exists."
                             :coercion reitit.coercion.schema/coercion
                             :parameters {:path {:meta_datum_id s/Str}}
                              ; TODO coercion
                             :responses {200 {:body s/Any}
                                         401 {:body s/Any}
                                         403 {:body s/Any}
                                         500 {:body s/Any}}}}]

   ["/:meta_datum_id/data-stream" {:get {:handler meta-datum/get-meta-datum-data-stream
                                         ; TODO json meta-data: fix response conversion error
                                         :middleware [sd/ring-wrap-add-meta-datum-with-media-resource
                                                      sd/ring-wrap-authorization]
                                         :summary "Get meta-data data-stream."
                                         :description "Get meta-data data-stream."
                                         :coercion reitit.coercion.schema/coercion
                                         :parameters {:path {:meta_datum_id s/Str}}}}]
                                          ;:responses {200 {:body s/Any}
                                                      ;422 {:body s/Any}}
   ["/:meta_datum_id/role" {:get {:summary "Get meta-data role for id"
                                  :handler meta-datum/handle_get-meta-datum-role
                                  :description "Get meta-data role for id. TODO: should return 404, if no such meta-data role exists."
                                  :coercion reitit.coercion.schema/coercion
                                  :parameters {:path {:meta_datum_id s/Str}}
                                  :responses {200 {:body s/Any}}}}]

   ["/:collection_id/by-collection-id" {:get {:summary "Get meta-data for collection."
                                                   :handler meta-data.index/get-index
                                                   :middleware [sd/ring-wrap-add-media-resource
                                                                sd/ring-wrap-authorization]
                                                   ; TODO 401s test fails
                                                   :coercion reitit.coercion.schema/coercion
                                                   :parameters {:path {:collection_id s/Str}}
                                                   :responses {200 {:body s/Any}}}}]

   ["/:media_entry_id/by-media-entry-id" {:get {:summary "Get meta-data for media-entry."
                                                      :handler meta-data.index/get-index
                                                      ; TODO 401s test fails
                                                      :middleware [sd/ring-wrap-add-media-resource
                                                                   sd/ring-wrap-authorization]
                                                      :coercion reitit.coercion.schema/coercion
                                                      :parameters {:path {:media_entry_id s/Str}}
                                                      :responses {200 {:body s/Any}}}}]])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
