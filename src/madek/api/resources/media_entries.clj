(ns madek.api.resources.media-entries
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.pagination :as pagination]
    [madek.api.resources.media-entries.index :refer [get-index]]
    [madek.api.resources.media-entries.media-entry :refer [get-media-entry]]
    [madek.api.resources.shared :as sd]
    [madek.api.utils.rdbms :as rdbms]
    [reitit.coercion.schema]
    [schema.core :as s]
    ))


(def routes
  (cpj/routes
    (cpj/GET "/media-entries/" _ get-index)
    (cpj/GET "/media-entries/:id" _ get-media-entry)
    (cpj/ANY "*" _ sd/dead-end-handler)
    ))


(defn handle_get-index [req]
  (let [query-params (-> req :parameters :query)
        q1 (-> req :query-params)
        qreq (assoc-in req [:query-params] query-params)
        q2 (-> qreq :query-params)
        
        ]
    (logging/info "handle_get-index" "\nquery\n" query-params "\nq1\n" q1 "\nq2\n" q2 )
    (get-index qreq)))

(defn handle_get-media-entry [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (get-media-entry qreq)
  ))


(def ring-routes 
  ["/media-entries"
   ["/" {:get {:summary "Get list media-entries."
               :swagger {:produces "application/json"}
               :content-type "application/json"
               :handler handle_get-index
               :middleware [sd/ring-wrap-parse-json-query-parameters]
               :coercion reitit.coercion.schema/coercion
               :parameters {:query {(s/optional-key :collection_id) s/Str
                                    (s/optional-key :order) s/Any ;(s/enum "desc" "asc" "title_asc" "title_desc" "last_change" "manual_asc" "manual_desc" "stored_in_collection")
                                    (s/optional-key :filter_by) s/Any
                                    (s/optional-key :me_get_metadata_and_previews) s/Bool
                                    (s/optional-key :me_get_full_size) s/Bool
                                    (s/optional-key :page) s/Str}}}
         ; TODO
         :post {:summary (sd/sum_todo "Create media-entry.")
                :handler (constantly sd/no_impl)}
         }]

   ["/:media_entry_id" {:get {:summary "Get media-entry for id."
                              :handler handle_get-media-entry
                              :swagger {:produces "application/json"}
                              :content-type "application/json"
                              
                              :middleware [sd/ring-wrap-add-media-resource
                                           sd/ring-wrap-authorization]
                              :coercion reitit.coercion.schema/coercion
                              :parameters {:path {:media_entry_id s/Str}}}}]
   
   ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
