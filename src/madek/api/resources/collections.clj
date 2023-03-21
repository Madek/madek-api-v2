(ns madek.api.resources.collections
  (:require
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.resources.collections.collection :refer [get-collection]]
    [madek.api.resources.collections.index :refer [get-index]]
    [madek.api.resources.shared :as sd]
    [reitit.coercion.schema]
    [schema.core :as s]
    [clojure.java.jdbc :as jdbc]
    [madek.api.utils.rdbms :as rdbms]
    ))

(def routes
  (cpj/routes
    (cpj/GET "/collections/" _ get-index)
    (cpj/GET "/collections/:id" _ get-collection)
    (cpj/ANY "*" _ sd/dead-end-handler)))

(defn handle_get-collection [req]
  (get-collection req))

(defn handle_get-index [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (logging/info "handle_get-index" "\nquery-params\n" query-params)
    (get-index qreq)
    )
  )

(def schema_collection-import 
  {(s/optional-key :id) s/Uuid
   (s/optional-key :get_metadata_and_previews) s/Bool
   (s/optional-key :layout) s/Str ; TODO grid or ?
   (s/optional-key :responsible_user_id) s/Uuid
   (s/optional-key :clipboard_user_id) s/Any ; s/Uuid | nil
   (s/optional-key :workflow_id) s/Any ; s/Uuid | nil
   (s/optional-key :is_master) s/Bool
   (s/optional-key :sorting) s/Str
   (s/optional-key :responsible_delegation_id) s/Any ; s/Uuid | nil
   (s/optional-key :default_context_id) s/Any ; s/Uuid | nil
   })

(def schema_collection-export
  {
   :id s/Uuid
   :get_metadata_and_previews s/Bool
   :created_at s/Any
   :updated_at s/Any
   :meta_data_updated_at s/Any
   :layout s/Str ; TODO grid or ?
   :responsible_user_id s/Uuid
   :creator_id s/Uuid
   :edit_session_updated_at s/Any
   :clipboard_user_id s/Any ; s/Uuid | nil
   :workflow_id s/Any ; s/Uuid | nil
   :is_master s/Bool
   :sorting s/Str
   :responsible_delegation_id s/Any ; s/Uuid | nil
   :default_context_id s/Any ; s/Uuid | nil
  })

(defn handle_create-collection [req]
  (let [auth (-> req :authenticated-entity)
        auth-id (-> auth :id)
        req-data (-> req :parameters :body)
        ins-data (assoc req-data :creator_id auth-id :responsible_user_id auth-id)] 
    (if-let [ins-result (jdbc/insert! (rdbms/get-ds) "collections" ins-data)]
      (sd/response_ok (first ins-result))
      (sd/response_failed "Could not create collection" 406))))

(def ring-routes
  ["/collections"
   ["/" {:get {:handler handle_get-index
               :summary "Get collection ids"
               :description "Get collection id list."
               :swagger {:produces "application/json"}
               :parameters {:query {(s/optional-key :page) s/Str
                                    (s/optional-key :full_data) s/Bool
                                    (s/optional-key :collection_id) s/Str
                                    (s/optional-key :order) s/Str
                                    (s/optional-key :me_get_metadata_and_previews) s/Bool
                                    (s/optional-key :public_get_metadata_and_previews) s/Bool
                                    (s/optional-key :me_get_full_size) s/Bool}}
               :coercion reitit.coercion.schema/coercion
               :responses {200 {:body {:collections [{:id s/Uuid :created_at s/Inst}]}}}}
         ; TODO
         :post {:summary (sd/sum_todo "Create collection")
                :handler handle_create-collection
                :swagger {:produces "application/json"}
                :parameters {:body schema_collection-import}
                :coercion reitit.coercion.schema/coercion
                :responses {200 {:body schema_collection-export }}
                }}]

   ["/:collection_id" {:get {:handler handle_get-collection
                             :middleware [sd/ring-wrap-add-media-resource
                                          sd/ring-wrap-authorization]
                             :summary "Get collection for id."
                             :swagger {:produces "application/json"}
                             :coercion reitit.coercion.schema/coercion
                             :parameters {:path {:collection_id s/Str}}
                             :responses {200 {:body s/Any}}}}] ; TODO response coercion
   ])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
