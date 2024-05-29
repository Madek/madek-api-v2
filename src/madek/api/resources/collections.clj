(ns madek.api.resources.collections
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.authorization :as authorization]
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.collections.index :refer [get-index]]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.helper :refer [convert-map-if-exist]]
   [madek.api.utils.helper :refer [mslurp]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [info]]))

(defn handle_get-collection [request]
  (let [collection (:media-resource request)
        cleanedcol (dissoc collection :table-name :type
                     ;:responsible_delegation_id
                     ; TODO Frage clipboard_user
                     ;:clipboard_user_id
                           )]
    (sd/response_ok cleanedcol)))

(defn handle_get-index [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (info "handle_get-index" "\nquery-params\n" query-params)
    (get-index qreq)))

(defn handle_create-collection [req]
  (try
    (catcher/with-logging {}
      (if-let [auth-id (-> req :authenticated-entity :id)]
        (let [req-data (-> req :parameters :body)
              ins-data (assoc req-data :creator_id auth-id :responsible_user_id auth-id)
              ins-data (convert-map-if-exist ins-data)
              tx (:tx req)
              query (-> (sql/insert-into :collections)
                        (sql/values [ins-data])
                        (sql/returning :*)
                        sql-format)
              ins-result (jdbc/execute! tx query)]
          (sd/logwrite req (str "handle_create-collection: " ins-result))
          (if-let [result (first ins-result)]
            (sd/response_ok result)
            (sd/response_failed "Could not create collection" 406)))
        (sd/response_failed "Could not create collection. Not logged in." 406)))
    (catch Exception ex (sd/parsed_response_exception ex))))

(defn handle_update-collection [req]
  (try
    (catcher/with-logging {}
      (let [collection (:media-resource req)
            col-id (:id collection)
            data (-> req :parameters :body)
            tx (:tx req)
            query (-> (sql/update :collections)
                      (sql/set (convert-map-if-exist data))
                      (sql/where [:= :id col-id])
                      (sql/returning :*)
                      sql-format)
            result (jdbc/execute! tx query)]

        (sd/logwrite req (str "handle_update-collection: " col-id result))

        (if result
          (sd/response_ok result)
          (sd/response_failed "Could not update collection." 422))))
    (catch Exception ex
      (sd/response_exception ex))))

(defn handle_delete-collection [req]
  (try
    (catcher/with-logging {}
      (let [collection (:media-resource req)
            tx (:tx req)
            col-id (:id collection)
            query (-> (sql/delete-from :collections)
                      (sql/where [:= :id col-id])
                      (sql/returning :*)
                      sql-format)
            delresult (jdbc/execute-one! tx query)]

        (sd/logwrite req (str "handle_delete-collection: " col-id delresult))
        (if delresult
          (sd/response_ok delresult)
          (sd/response_failed (str "Could not delete collection: " col-id) 422))))
    (catch Exception ex
      (sd/response_failed (str "Could not delete collection: " (ex-message ex)) 500))))

;; TODO :layout and :sorting are special types
;(def schema_layout_types
;  (s/enum "grid" "list" "miniature" "tiles"))
;
;(def schema_sorting_types
;  (s/enum "created_at ASC"
;          "created_at DESC"
;          "title ASC"
;          "title DESC"
;          "last_change"
;          "manual ASC"
;          "manual DESC"))
;
;(def schema_default_resource_type
;  (s/enum "collections" "entries" "all"))
;
;(def schema_collection-import
;  {;(s/optional-key :id) s/Uuid
;   (s/optional-key :get_metadata_and_previews) s/Bool
;
;   (s/optional-key :layout) schema_layout_types
;   (s/optional-key :is_master) s/Bool
;   (s/optional-key :sorting) schema_sorting_types
;   (s/optional-key :default_context_id) (s/maybe s/Str) ;;caution
;   ;(s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
;   (s/optional-key :workflow_id) (s/maybe s/Uuid)
;
;   ;; TODO: only one (:responsible_user_id OR :responsible_delegation_id) should be set (uuid & null check)
;   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
;   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)
;
;   (s/optional-key :default_resource_type) schema_default_resource_type})
;
;(def schema_collection-update
;  {(s/optional-key :layout) schema_layout_types
;   (s/optional-key :is_master) s/Bool
;   (s/optional-key :sorting) schema_sorting_types
;   (s/optional-key :default_context_id) (s/maybe s/Str)
;
;   ;(s/optional-key :get_metadata_and_previews) s/Bool
;   ;(s/optional-key :responsible_user_id) s/Uuid
;
;   ;(s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
;   (s/optional-key :workflow_id) (s/maybe s/Uuid)
;   ;(s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)
;
;   (s/optional-key :default_resource_type) schema_default_resource_type})
;
;(def schema_collection-query
;  {(s/optional-key :page) s/Int
;   (s/optional-key :count) s/Int
;   (s/optional-key :full_data) s/Bool
;
;   (s/optional-key :collection_id) s/Uuid
;   (s/optional-key :order) s/Str
;
;   (s/optional-key :creator_id) s/Uuid
;   (s/optional-key :responsible_user_id) s/Uuid
;
;   (s/optional-key :clipboard_user_id) s/Uuid
;   (s/optional-key :workflow_id) s/Uuid
;   (s/optional-key :responsible_delegation_id) s/Uuid
;
;   ;; collections
;   (s/optional-key :public_get_metadata_and_previews) s/Bool
;
;   ;; collection_user_permissions
;   (s/optional-key :me_get_metadata_and_previews) s/Bool
;   (s/optional-key :me_edit_permission) s/Bool
;   (s/optional-key :me_edit_metadata_and_relations) s/Bool})

;(def schema_collection-export
;  {:id s/Uuid
;   (s/optional-key :get_metadata_and_previews) s/Bool
;
;   (s/optional-key :layout) schema_layout_types
;   (s/optional-key :is_master) s/Bool
;   (s/optional-key :sorting) schema_sorting_types
;
;   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
;   (s/optional-key :creator_id) s/Uuid
;
;   (s/optional-key :default_context_id) (s/maybe s/Str)
;
;   (s/optional-key :created_at) s/Any
;   (s/optional-key :updated_at) s/Any
;   (s/optional-key :meta_data_updated_at) s/Any
;   (s/optional-key :edit_session_updated_at) s/Any
;
;   (s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
;   (s/optional-key :workflow_id) (s/maybe s/Uuid)
;   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)
;
;   (s/optional-key :default_resource_type) schema_default_resource_type})

(def ring-routes
  ["/"
   {:swagger {:tags ["api/collection"]}}
   ["collections"
    {:get
     {:summary (sd/sum_usr "Query/List collections.")
      :handler handle_get-index
      :swagger {:produces ["application/json" "application/octet-stream"]}
      :parameters {:query (get-schema :collections.schema_collection-query)}
      :coercion reitit.coercion.schema/coercion
      :responses {200 {:body {:collections [(get-schema :collections.schema_collection-export)]}}}}}]

   ["collection"
    {:post
     {:summary (sd/sum_usr "Create collection")

      ;:description "CAUTION: Either :responsible_user_id OR :responsible_user_id has to be set - not both (db-constraint)"
      :description (mslurp "./md/collections-post.md")

      :handler handle_create-collection
      :swagger {:produces "application/json"
                :consumes "application/json"}
      :parameters {:body (get-schema :collections.schema_collection-import)}
      :middleware [authorization/wrap-authorized-user]
      :coercion reitit.coercion.schema/coercion
      :responses {200 {:body (get-schema :collections.schema_collection-export)}
                  406 {:body s/Any}}}}]

   ["collection/:collection_id"
    {:get {:summary (sd/sum_usr_pub "Get collection for id.")
           :handler handle_get-collection
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]

           :swagger {:produces "application/json"}
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}}
           :responses {200 {:body (get-schema :collections.schema_collection-export)}
                       404 {:body s/Any}
                       422 {:body s/Any}}}

     :put {:summary (sd/sum_usr "Update collection for id.")
           :handler handle_update-collection
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :swagger {:produces "application/json"
                     :consumes "application/json"}
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}
                        :body (get-schema :collections.schema_collection-update)}
           :responses {;200 {:body (get-schema :collections.schema_collection-export)} ;; TODO: fixme
                       200 {:body s/Any}
                       404 {:body s/Any}
                       422 {:body s/Any}}}

     ; TODO Frage: wer darf eine col löschen: nur der benutzer und der responsible
     ; TODO check owner or responsible
     :delete {:summary (sd/sum_usr "Delete collection for id.")
              :handler handle_delete-collection
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :swagger {:produces "application/json"
                        :consumes "application/json"}
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:collection_id s/Uuid}}
              :responses {200 {:body (get-schema :collections.schema_collection-export)}
                          404 {:body s/Any}
                          422 {:body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
