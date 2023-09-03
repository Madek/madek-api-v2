(ns madek.api.resources.collections
  (:require
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [madek.api.resources.collections.collection :refer [get-collection]]
    [madek.api.resources.collections.index :refer [get-index]]
    [madek.api.resources.shared :as sd]
    [reitit.coercion.schema]
    [schema.core :as s]
    [clojure.java.jdbc :as jdbc]
    [madek.api.utils.rdbms :as rdbms]
    ))


(defn handle_get-collection [request]
  (let [collection (:media-resource request)
        cleanedcol (dissoc collection :table-name :type
                           ;:responsible_delegation_id
                           ; TODO Frage cipboard_user
                           ;:clipboard_user_id
                           )]
    (sd/response_ok cleanedcol))
  )

(defn handle_get-index [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (logging/info "handle_get-index" "\nquery-params\n" query-params)
    (get-index qreq)
    )
  )

; TODO try carch
(defn handle_create-collection [req]
  (let [auth (-> req :authenticated-entity)
        auth-id (-> auth :id)
        req-data (-> req :parameters :body)
        ins-data (assoc req-data :creator_id auth-id :responsible_user_id auth-id)]
    (if-let [ins-result (jdbc/insert! (rdbms/get-ds) "collections" ins-data)]
      (sd/response_ok (first ins-result))
      (sd/response_failed "Could not create collection" 406))))

(defn handle_update-collection [req]
  (try
    (let [collection (:media-resource req)
          col-id (:id collection)
          data (-> req :parameters :body)
          ;upd-data (assoc data )
          whcl ["id = ? " col-id]
          result (jdbc/update! (rdbms/get-ds) :collections data whcl)]
      (if (= 1 (first result))
        (sd/response_ok (sd/query-eq-find-one :collections :id col-id))
        (sd/response_failed "Could not update collection." 422))
      )
    (catch Exception ex 
      (
       (logging/error "Could not update collection." (ex-message ex))
       (sd/response_failed "Could not update collection." 500)
      ))
    )
  )

(defn handle_delete-collection [req]
  (try
    (let [collection (:media-resource req)
          col-id (:id collection)
          delquery ["id = ? " col-id]
          delresult (jdbc/delete! (rdbms/get-ds) :collections delquery)]
      (logging/info "handle_delete-collection" col-id delresult)
      (if (= 1 (first delresult))
        (sd/response_ok collection)
        (sd/response_failed (str "Could not delete collection: " col-id) 422)
        ))
    (catch Exception ex
      (
       (logging/error "Could not delete collection: " (ex-message ex))
       (sd/response_failed (str "Could not delete collection: " (ex-message ex)) 500)
      )
      )
    )
  )

(def schema_collection-import 
  {
   ;(s/optional-key :id) s/Uuid
   (s/optional-key :get_metadata_and_previews) s/Bool

   (s/optional-key :layout) s/Str ; TODO enum grid or ?
   (s/optional-key :is_master) s/Bool
   (s/optional-key :sorting) s/Str ; TODO enum
   (s/optional-key :default_context_id) (s/maybe s/Uuid)
   (s/optional-key :responsible_user_id) s/Uuid
   (s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
   (s/optional-key :workflow_id) (s/maybe s/Uuid)
   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)
   })

(def schema_collection-update
  {
   
   (s/optional-key :layout) s/Str ; TODO enum grid or ? 'grid', 'list', 'miniature' 'tiles'
   (s/optional-key :is_master) s/Bool
   (s/optional-key :sorting) s/Str
   (s/optional-key :default_context_id) (s/maybe s/Uuid)

   ;(s/optional-key :get_metadata_and_previews) s/Bool
   ;(s/optional-key :responsible_user_id) s/Uuid

   ;(s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
   (s/optional-key :workflow_id) (s/maybe s/Uuid)
   ;(s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)
   })

(def schema_collection-query 
  {(s/optional-key :page) s/Str
   (s/optional-key :count) s/Int
   (s/optional-key :full_data) s/Bool
   (s/optional-key :collection_id) s/Str
   (s/optional-key :order) s/Str
   
   (s/optional-key :public_get_metadata_and_previews) s/Bool
   (s/optional-key :me_get_metadata_and_previews) s/Bool
   (s/optional-key :me_edit_permission) s/Bool
   (s/optional-key :me_edit_metadata_and_relations) s/Bool})

(def schema_collection-export
  {
   :id s/Uuid
   :get_metadata_and_previews s/Bool
   
   :layout s/Str ; TODO enum grid or ? 'grid', 'list', 'miniature' 'tiles'
   :is_master s/Bool
   :sorting s/Str
   
   :responsible_user_id s/Uuid
   :creator_id s/Uuid
   
   :default_context_id (s/maybe s/Uuid)

   
   :created_at s/Any
   :updated_at s/Any
   :meta_data_updated_at s/Any
   :edit_session_updated_at s/Any
   
   :default_resource_type s/Any

   :clipboard_user_id (s/maybe s/Uuid)
   :workflow_id (s/maybe s/Uuid)
   :responsible_delegation_id (s/maybe s/Uuid)
   
  })



(def ring-routes
  ["/collections"
   ["/" {:get {:handler handle_get-index
               :summary (sd/sum_usr "Get collection ids")
               :description "Get collection id list."
               :swagger {:produces "application/json"}
               :parameters {:query schema_collection-query
                            }
               :coercion reitit.coercion.schema/coercion
               :responses {200 {:body {:collections [schema_collection-export]}}}}
         ; TODO collections post, patch, delete
         :post {:summary (sd/sum_usr "Create collection")
                :handler handle_create-collection
                :swagger {:produces "application/json"
                          :consumes "application/json"}
                :parameters {:body schema_collection-import}
                :coercion reitit.coercion.schema/coercion
                :responses {200 {:body schema_collection-export }}
                }}]

   ["/:collection_id"
    {:get {:handler handle_get-collection
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           :summary (sd/sum_usr "Get collection for id.")
           :swagger {:produces "application/json"}
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}}
           :responses {200 {:body schema_collection-export}
                       422 {:body s/Any}
                       500 {:body s/Any}}}

     :put {:handler handle_update-collection
           :summary (sd/sum_usr "Update collection for id.")
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :swagger {:produces "application/json"
                     :consumes "application/json"}
           :parameters {:path {:collection_id s/Uuid}
                        :body schema_collection-update}
           :coercion reitit.coercion.schema/coercion}
     
     ; TODO Frage: wer darf eine col löschen: nur der benutzer und der responsible
     :delete {:handler handle_delete-collection
              :summary (sd/sum_usr "Delete collection for id.")
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :swagger {:produces "application/json"
                        :consumes "application/json"}
              :parameters {:path {:collection_id s/Uuid}}
              :coercion reitit.coercion.schema/coercion}
     }
    ]
   ])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
