(ns madek.api.resources.collections
  (:require
   [clojure.java.io :as io]
   [clojure.spec.alpha :as sa]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.authorization :as authorization]
   [madek.api.resources.collections.index :refer [get-index]]
   [madek.api.resources.shared.core :as fl]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.coercion.spec-alpha-definition-map :as sp-map]
   [madek.api.utils.coercion.spec-alpha-definition-nil :as sp-nil]
   [madek.api.utils.helper :refer [convert-map-if-exist mslurp]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]
   [schema.core :as s]
   [spec-tools.core :as st]
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

; TODO :layout and :sorting are special types
(def schema_layout_types
  (s/enum "grid" "list" "miniature" "tiles"))

(def schema_sorting_types
  (s/enum "created_at ASC"
          "created_at DESC"
          "title ASC"
          "title DESC"
          "last_change ASC"
          "last_change DESC"
          "manual ASC"
          "manual DESC"))

(def schema_default_resource_type
  (s/enum "collections" "entries" "all"))

(def schema_collection-import
  {;(s/optional-key :id) s/Uuid
   (s/optional-key :get_metadata_and_previews) s/Bool

   (s/optional-key :layout) schema_layout_types
   (s/optional-key :is_master) s/Bool
   (s/optional-key :sorting) schema_sorting_types
   (s/optional-key :default_context_id) (s/maybe s/Str) ;;caution
   ;(s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
   (s/optional-key :workflow_id) (s/maybe s/Uuid)

   ;; TODO: only one (:responsible_user_id OR :responsible_delegation_id) should be set (uuid & null check)
   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)

   (s/optional-key :default_resource_type) schema_default_resource_type})

(sa/def :usr/collections-update (sa/keys :opt-un [::sp/layout ::sp/is_master ::sp/sorting ::sp-nil/default_context_id
                                                  ::sp-nil/workflow_id ::sp/default_resource_type]))

(sa/def :adm/collections-update (sa/keys :opt-un [::sp/layout ::sp/is_master ::sp/sorting ::sp-nil/default_context_id
                                                  ::sp-nil/workflow_id ::sp/default_resource_type

                                                  ::sp-nil/deleted_at]))

(sa/def :collection-query/query-def (sa/keys :opt-un [::sp/full_data ::sp/collection_id ::sp/order ::sp/creator_id
                                                      ::sp/responsible_user_id ::sp/clipboard_user_id ::sp/workflow_id
                                                      ::sp/responsible_delegation_id ::sp/public_get_metadata_and_previews
                                                      ::sp/me_get_metadata_and_previews ::sp/me_edit_permission
                                                      ::sp/me_edit_metadata_and_relations
                                                      ::sp/page ::sp/size]))

(sa/def :collection-query/query-admin-def (sa/keys :opt-un [::sp/full_data ::sp/collection_id ::sp/order ::sp/creator_id
                                                            ::sp/responsible_user_id ::sp/clipboard_user_id ::sp/workflow_id
                                                            ::sp/responsible_delegation_id ::sp/public_get_metadata_and_previews
                                                            ::sp/me_get_metadata_and_previews ::sp/me_edit_permission
                                                            ::sp/me_edit_metadata_and_relations
                                                            ::sp/page ::sp/size
                                                            ::sp-map/filter_softdelete]))

(def schema_collection-export
  {:id s/Uuid
   (s/optional-key :get_metadata_and_previews) s/Bool

   (s/optional-key :layout) schema_layout_types
   (s/optional-key :is_master) s/Bool
   (s/optional-key :sorting) schema_sorting_types

   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
   (s/optional-key :creator_id) s/Uuid

   (s/optional-key :default_context_id) (s/maybe s/Str)

   (s/optional-key :deleted_at) s/Any
   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any
   (s/optional-key :meta_data_updated_at) s/Any
   (s/optional-key :edit_session_updated_at) s/Any

   ;(s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
   (s/optional-key :clipboard_user_id) (s/maybe s/Str)      ; is a string OR just cast it to uuid in response
   (s/optional-key :workflow_id) (s/maybe s/Uuid)
   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)

   (s/optional-key :default_resource_type) schema_default_resource_type})

(sa/def :usr/collections-put (sa/keys :req-un [::sp/id ::sp/created_at ::sp-nil/deleted_at] :opt-un [::sp/get_metadata_and_previews ::sp/layout ::sp/is_master ::sp/sorting
                                                                                                     ::sp-nil/responsible_user_id ::sp/creator_id ::sp-nil/default_context_id
                                                                                                 ;::sp/deleted_at
                                                                                                     ::sp/updated_at ::sp/meta_data_updated_at
                                                                                                     ::sp/edit_session_updated_at ::sp-nil/clipboard_user_id ::sp-nil/workflow_id
                                                                                                     ::sp-nil/responsible_delegation_id ::sp/default_resource_type]))

(sa/def :usr/collections
  (sa/keys :req-un [::sp/id
                    ::sp/created_at
                    ::sp-nil/deleted_at
                    ;::sp/child_id
                    ;::sp/parent_id
                    ]
           :opt-un [::sp/get_metadata_and_previews
                    ::sp/layout
                    ::sp/is_master
                    ::sp/sorting
                    ::sp-nil/responsible_user_id
                    ::sp/creator_id
                    ::sp-nil/default_context_id
                    ::sp/updated_at
                    ::sp/meta_data_updated_at
                    ::sp/edit_session_updated_at
                    ::sp-nil/clipboard_user_id
                    ::sp-nil/workflow_id
                    ::sp-nil/responsible_delegation_id
                    ::sp/default_resource_type
                    ::sp-nil/position
                    ::sp-nil/order

                    ;; is this really really required
                    ;::sp/child_id
                    ;::sp/parent_id
                    ]))

(sa/def :usr-collection-list/groups (st/spec {:spec (sa/coll-of :usr/collections)
                                              :description "A list of persons"}))

(sa/def ::response-collections-body (sa/keys :req-un [:usr-collection-list/groups]))

(def ring-admin-routes
  ["/"
   {:openapi {:tags ["admin/collection"]}}
   ["collections"
    {:get
     {:summary (sd/sum_usr "Query/List collections.")
      :middleware [wrap-authorize-admin!]
      :handler handle_get-index
      :coercion spec/coercion
      :parameters {:query :collection-query/query-admin-def}
      :responses {200 {:description "Returns the list of collections."
                       :body ::response-collections-body}}}}]

   ["collection/:collection_id"
    {:put {:summary (sd/sum_usr "Update collection for id.")
           :handler handle_update-collection
           :description (mslurp (io/resource "md/collections-put.md"))
           :middleware [wrap-authorize-admin!
                        jqh/ring-wrap-authorization-edit-permissions
                        jqh/ring-wrap-add-media-resource
                        jqh/ring-wrap-authorization-edit-metadata]
           :swagger {:produces "application/json"
                     :consumes "application/json"}
           :coercion reitit.coercion.spec/coercion
           :parameters {:path {:collection_id uuid?}
                        :body :adm/collections-update}
           :responses {200 {:description "Returns the updated collection."
                            :body (st/spec {:spec (sa/coll-of :usr/collections-put)
                                            :description "A list of persons"})}
                       404 {:description "Collection not found."
                            :body any?}
                       422 {:description "Could not update collection."
                            :body any?}}}}]])

(def ring-routes
  ["/"
   {:openapi {:tags ["api/collection"]}}
   ["collections"
    {:get
     {:summary (fl/?no-auth?(sd/sum_usr "Query/List collections."))
      :handler handle_get-index
      :coercion spec/coercion
      :parameters {:query :collection-query/query-def}
      :responses {200 {:description "Returns the list of collections."
                       :body ::response-collections-body}}}}]

   ["collection"
    {:post
     {:summary (fl/?no-auth? (sd/sum_usr "Create collection"))

      ;:description "CAUTION: Either :responsible_user_id OR :responsible_user_id has to be set - not both (db-constraint)"
      :description (mslurp (io/resource "md/collections-post.md"))

      :handler handle_create-collection
      :swagger {:produces "application/json"
                :consumes "application/json"}
      :parameters {:body schema_collection-import}
      :middleware [authorization/wrap-authorized-user]
      :coercion reitit.coercion.schema/coercion
      :responses {200 {:description "Returns the created collection."
                       :body schema_collection-export}
                  406 {:description "Could not create collection."
                       :body s/Any}}}}]

   ["collection/:collection_id"
    {:get {:summary (fl/?no-auth? (sd/sum_usr_pub "Get collection for id. b8a02655-b499-4516-8c96-e18ff849698e"))
           :handler handle_get-collection
           :middleware [jqh/ring-wrap-add-media-resource
                        jqh/ring-wrap-authorization-view]

           :swagger {:produces "application/json"}
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}}
           :responses {200 {:description "Returns the collection."
                            :body schema_collection-export}
                       404 {:description "Collection not found."
                            :body s/Any}
                       422 {:description "Could not get collection."
                            :body s/Any}}}

     :put {
           :summary (fl/?token? (sd/sum_usr "Update collection for id."))
           :description "
b8a02655-b499-4516-8c96-e18ff849698e\n\n
{\n  \"layout\": \"grid\",\n  \"is_master\": true,\n  \"sorting\": \"title ASC\",\n  \"default_context_id\": null,\n  \"workflow_id\": null,\n  \"default_resource_type\": \"collections\"\n}
"
           :handler handle_update-collection
           :middleware [jqh/ring-wrap-add-media-resource
                        jqh/ring-wrap-authorization-edit-metadata]
           :swagger {:produces "application/json"
                     :consumes "application/json"}
           :coercion reitit.coercion.spec/coercion
           :parameters {:path {:collection_id uuid?}
                        :body :usr/collections-update}
           :responses {200 {:description "Returns the updated collection."

                            ;:body :usr/collections}         ;;map only causes error
                            :body :usr-collection-list/groups}
                            ;:body any?}

                       404 {:description "Collection not found."
                            :body any?}
                       422 {:description "Could not update collection."
                            :body any?}}}

; TODO Frage: wer darf eine col löschen: nur der benutzer und der responsible
     ; TODO check owner or responsible
     :delete {:summary (fl/?token? (sd/sum_usr "Delete collection for id."))
              :handler handle_delete-collection
              :middleware [jqh/ring-wrap-add-media-resource
                           jqh/ring-wrap-authorization-edit-permissions]
              :swagger {:produces "application/json"
                        :consumes "application/json"}
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:collection_id s/Uuid}}
              :responses {200 {:description "Returns the deleted collection."
                               :body schema_collection-export}
                          404 {:description "Collection not found."
                               :body s/Any}
                          422 {:description "Could not delete collection."
                               :body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
