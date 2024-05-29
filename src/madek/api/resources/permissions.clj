(ns madek.api.resources.permissions
  (:require
   [logbug.catcher :as catcher]
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.media-resources.permissions :as mr-permissions]
   [madek.api.resources.shared :as sd]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

; TODO delegations ?
; TODO clipboard_user
; TODO logwrite

(defn mr-table-type [media-resource]
  (case (:type media-resource)
    "MediaEntry" "media_entry"
    "Collection" "collection"
    :default (throw ((ex-info "Invalid media-resource type" {:status 500})))))

(defn get-entity-perms
  ([mr] (get-entity-perms mr (:type mr)))
  ([mr type]
   (case type
     "MediaEntry" (select-keys mr [:id
                                   :creator_id
                                   :responsible_user_id
                                   :is_published
                                   :get_metadata_and_previews
                                   :get_full_size
                                   ; TODO delegations
                                   ])
     "Collection" (select-keys mr [:id
                                   :creator_id
                                   :responsible_user_id
                                   :clipboard_user_id
                                   :workflow_id
                                   :get_metadata_and_previews
                                   ; TODO delegations
                                   ])
     :default (throw ((ex-info "Invalid media-resource type" {:status 500}))))))

(defn handle_get_entity_perms
  [req]
  (let [mr (-> req :media-resource)
        data (get-entity-perms mr)]
    (sd/response_ok data)))

(defn- handle_update-resource-perm-value
  [req]
  (try
    (catcher/with-logging {}
      (let [perm-name (keyword (-> req :parameters :path :perm_name))
            perm-val (-> req :parameters :path :perm_val)
            perm-data {perm-name perm-val}
            mr (-> req :media-resource)
            tx (:tx req)
            mr-type (:type mr)
            upd-result (mr-permissions/update-resource-permissions mr perm-data tx)]
        (if (= 1 (::jdbc/update-count upd-result))
          (sd/response_ok (get-entity-perms (mr-permissions/resource-permission-get-query mr tx) mr-type))
          (sd/response_failed (str "Could not update permissions" upd-result) 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn- handle_update-ressource-perms
  [req]
  (try
    (catcher/with-logging {}
      (let [perm-data (-> req :parameters :body)
            mr (-> req :media-resource)
            tx (:tx req)
            mr-type (:type mr)
            upd-result (mr-permissions/update-resource-permissions mr perm-data tx)]
        (if (= 1 (::jdbc/update-count upd-result))
          (sd/response_ok (get-entity-perms (mr-permissions/resource-permission-get-query mr tx) mr-type))
          (sd/response_failed (str "Could not update permissions" upd-result) 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn- handle_list-user-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (mr-table-type mr)
        tx (:tx req)
        data (mr-permissions/query-list-user-permissions mr mr-type tx)]
    (sd/response_ok data)))

(defn- handle_get-user-perms
  [req]
  (let [user-id (-> req :parameters :path :user_id)
        mr (-> req :media-resource)
        tx (:tx req)
        mr-type (mr-table-type mr)]
    (if-let [data (mr-permissions/query-get-user-permission mr mr-type user-id tx)]
      (sd/response_ok data)
      (sd/response_not_found "No such resource user permission."))))

(defn handle_create-user-perms [req]
  (try
    (catcher/with-logging {}
      (let [user-id (-> req :parameters :path :user_id)
            mr (-> req :media-resource)
            mrt (mr-table-type mr)
            tx (:tx req)
            data (-> req :parameters :body)
            result (mr-permissions/create-user-permissions mr mrt user-id data tx)]

        (if (nil? result)
          (sd/response_failed "Could not create user permissions." 422)
          (sd/response_ok result))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-user-perms [req]
  (try
    (catcher/with-logging {}
      (let [user-id (-> req :parameters :path :user_id)
            mr (-> req :media-resource)
            tx (:tx req)
            mrt (mr-table-type mr)]

        (if-let [user-perm (mr-permissions/query-get-user-permission mr mrt user-id tx)]
          (let [delok (mr-permissions/delete-user-permissions mr mrt user-id tx)]
            (if (true? delok)
              (sd/response_ok user-perm)
              (sd/response_failed "Could not delete resource user permission." 406)))
          (sd/response_failed "No such user permission." 404))))
    (catch Exception ex (sd/response_exception ex))))

; TODO
; use wrapper for perm entry existence
; use wrapper for user-id
; use case for perm-name
; use case default as error
; check if perm entry exists, if not create it
; delete if all perms are set to false
(defn- handle_update-user-perms
  [req]
  (try
    (catcher/with-logging {}
      (let [user-id (-> req :parameters :path :user_id)
            perm-name (keyword (-> req :parameters :path :perm_name))
            perm-val (-> req :parameters :path :perm_val)
            mr (-> req :media-resource)
            tx (:tx req)
            mr-type (mr-table-type mr)]
        (if-let [old-perm (mr-permissions/query-get-user-permission mr mr-type user-id tx)]
          (let [upd-result (mr-permissions/update-user-permissions mr mr-type user-id perm-name perm-val tx)]
            (if (= 1 (::jdbc/update-count upd-result))
              (sd/response_ok (mr-permissions/query-get-user-permission mr mr-type user-id tx))
              (sd/response_failed (str "Could not update permissions" upd-result) 400)))
          (sd/response_not_found "No such resource user permission."))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_create-group-perms [req]
  (try
    (catcher/with-logging {}
      (let [group-id (-> req :parameters :path :group_id)
            mr (-> req :media-resource)
            mrt (mr-table-type mr)
            tx (:tx req)
            data (-> req :parameters :body)]

        (if-let [insresult (mr-permissions/create-group-permissions mr mrt group-id data tx)]
          (sd/response_ok insresult)
          (sd/response_failed "Could not create resource group permissions." 422))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-group-perms [req]
  (try
    (catcher/with-logging {}
      (let [group-id (-> req :parameters :path :group_id)
            mr (-> req :media-resource)
            tx (:tx req)
            mrt (mr-table-type mr)]
        (if-let [group-perm (mr-permissions/query-get-group-permission mr mrt group-id tx)]
          (let [delok (mr-permissions/delete-group-permissions mr mrt group-id tx)]
            (if (true? delok)
              (sd/response_ok group-perm)
              (sd/response_failed "Could not delete resource group permission." 422)))
          (sd/response_not_found "No such resource group permission."))))

    (catch Exception ex (sd/response_exception ex))))

(defn- handle_list-group-perms
  [req]
  (let [mr (-> req :media-resource)
        tx (:tx req)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (mr-permissions/query-list-group-permissions mr mr-type tx)]
    (sd/response_ok data)))

(defn- handle_get-group-perms
  [req]
  (let [group-id (-> req :parameters :path :group_id)
        mr (-> req :media-resource)
        tx (:tx req)
        mr-type (mr-table-type mr)]
    (if-let [data (mr-permissions/query-get-group-permission mr mr-type group-id tx)]
      (sd/response_ok data)
      (sd/response_not_found "No such resource group permission."))))

(defn- handle_update-group-perms
  [req]
  (try
    (catcher/with-logging {}
      (let [group-id (-> req :parameters :path :group_id)
            perm-name (keyword (-> req :parameters :path :perm_name))
            perm-val (-> req :parameters :path :perm_val)
            tx (:tx req)
            mr (-> req :media-resource)
            mr-type (mr-table-type mr)]
        (if-let [old-data (mr-permissions/query-get-group-permission mr mr-type group-id tx)]
          (let [upd-result (mr-permissions/update-group-permissions mr mr-type group-id perm-name perm-val tx)]
            (if (= 1 (::jdbc/update-count upd-result))
              (sd/response_ok (mr-permissions/query-get-group-permission mr mr-type group-id tx))
              (sd/response_failed (str "Could not update permissions" upd-result) 400)))
          (sd/response_not_found "No such resource group permissions."))))
    (catch Exception ex (sd/response_exception ex))))

(defn- handle_list-perms-type
  [req]
  (let [p-type (-> req :parameters :path :type)
        mr (-> req :media-resource)
        tx (:tx req)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (case p-type
               "entity" (get-entity-perms mr)
               ;"api-client" (mr-permissions/query-list-api-client-permissions mr mr-type)
               "user" (mr-permissions/query-list-user-permissions mr mr-type tx)
               "group" (mr-permissions/query-list-group-permissions mr mr-type tx))]
    (sd/response_ok data)))

(defn- handle_list-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (mr-table-type mr)
        e-data (get-entity-perms mr)
        tx (:tx req)
        ; responsible user
        ; TODO delegations
        ;a-data (mr-permissions/query-list-api-client-permissions mr mr-type)
        u-data (mr-permissions/query-list-user-permissions mr mr-type tx)
        g-data (mr-permissions/query-list-group-permissions mr mr-type tx)]

    (sd/response_ok {;:api-clients a-data
                     :media-resource e-data
                     :users u-data
                     :groups g-data})))

; TODO only for docu
(def valid_permission_names
  ["get_metadata_and_previews"
   "get_full_size"
   "edit_metadata"
   "edit_permissions"])

;(def valid_permission_keys
;  (map keyword valid_permission_names))

;(def schema_update-collection-perms
;  {(s/optional-key :get_metadata_and_previews) s/Bool
;   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
;   (s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
;   (s/optional-key :workflow_id) (s/maybe s/Uuid)
;   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})
;
;(def schema_create-collection-user-permission
;  {:get_metadata_and_previews s/Bool
;   :edit_metadata_and_relations s/Bool
;   :edit_permissions s/Bool})
;
;(def schema_export-collection-user-permission
;  {:id s/Uuid
;
;   ;; collection_user_permissions
;   :get_metadata_and_previews s/Bool
;   :edit_metadata_and_relations s/Bool
;   :edit_permissions s/Bool
;   :collection_id s/Uuid
;
;   :user_id s/Uuid
;   :updator_id (s/maybe s/Uuid)
;   :created_at s/Any
;   :updated_at s/Any
;   :delegation_id (s/maybe s/Uuid)
;   }
;  )
;

;(def schema_create-collection-group-permission
;  {:get_metadata_and_previews s/Bool
;   :edit_metadata_and_relations s/Bool})
;
;(def schema_export-collection-group-permission
;  {:id s/Uuid
;   :updator_id (s/maybe s/Uuid)
;   :collection_id s/Uuid
;   :group_id s/Uuid
;   :get_metadata_and_previews s/Bool
;   :edit_metadata_and_relations s/Bool
;   ;:delegation_id (s/maybe s/Uuid)
;   :created_at s/Any
;   :updated_at s/Any})
;
;
;
;
;
;
;
;(def schema_export-collection-perms
;  {:id s/Uuid
;   :creator_id s/Uuid
;   (s/optional-key :get_metadata_and_previews) s/Bool
;   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
;
;   (s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
;   (s/optional-key :workflow_id) (s/maybe s/Uuid)
;   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})
;
;(def schema_export_collection-permissions-all
;  {:media-resource schema_export-collection-perms
;   :users [(get-schema :collection_user_permissions.schema_export-collection-user-permission)]
;   :groups [(get-schema :collection_group_permissions.schema_export-collection-group-permission)]})
;
;
;
;
;
;
;
;
;
;(def schema_update-media-entry-perms
;  {(s/optional-key :get_metadata_and_previews) s/Bool
;   (s/optional-key :get_full_size) s/Bool
;   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
;   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})
;
;(def schema_export-media-entry-perms
;  {:id s/Uuid
;   :creator_id s/Uuid
;   :is_published s/Bool
;   (s/optional-key :get_metadata_and_previews) s/Bool
;   (s/optional-key :get_full_size) s/Bool
;   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
;   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})
;
;(def schema_create-media-entry-user-permission
;  {:get_metadata_and_previews s/Bool
;   :get_full_size s/Bool
;   :edit_metadata s/Bool
;   :edit_permissions s/Bool})
;
;(def schema_export-media-entry-user-permission
;  {:id s/Uuid
;   :updator_id (s/maybe s/Uuid)
;   :media_entry_id s/Uuid
;   :user_id s/Uuid
;   :get_metadata_and_previews s/Bool
;   :get_full_size s/Bool
;   :edit_metadata s/Bool
;   :edit_permissions s/Bool
;   :delegation_id (s/maybe s/Uuid)
;   :created_at s/Any
;   :updated_at s/Any})
;
;
;
;
;
;
;
;(def schema_create-media-entry-group-permission
;  {:get_metadata_and_previews s/Bool
;   :get_full_size s/Bool
;   :edit_metadata s/Bool})
;
;(def schema_export-media-entry-group-permission
;  {:id s/Uuid
;   :updator_id (s/maybe s/Uuid)
;   :media_entry_id s/Uuid
;   :group_id s/Uuid
;   :get_metadata_and_previews s/Bool
;   :get_full_size s/Bool
;   :edit_metadata s/Bool
;   :created_at s/Any
;   :updated_at s/Any})
;
;(def schema_export_media-entry-permissions-all
;  {:media-resource schema_export-media-entry-perms
;   :users [schema_export-media-entry-user-permission]
;   :groups [schema_export-media-entry-group-permission]})

(def media-entry-routes
  ["/media-entry/:media_entry_id/perms"
   {:swagger {:tags ["media-entry/perms"]}}
   ["/"
    {:get
     {:summary (sd/sum_usr_pub "List media-entry permissions.")
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}
      :responses {200 {:body (get-schema :media_entry_user_permissions.schema_export_media-entry-permissions-all)}}}}]

   ; TODO patch for entity perms
   ; get_metadata_and_previews
   ; get_full_size
   ; responsible_user_id
   ; responsible_delegation_id
   ; TODO beware to let not update perm fields in media-entry or collection patch/update

   ["/resources"
    {:get
     {:summary "Query media-entry permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_get_entity_perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}
      :responses {200 {:body (get-schema :media_entries.schema_export-media-entry-perms)}}}

     :put
     {:summary "Update media-entry entity permissions"
      :description (str "Valid perm_name values are" valid_permission_names)
      :handler handle_update-ressource-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-edit-permissions]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}
                   :body (get-schema :media_entries.schema_update-media-entry-perms)}
      :responses {200 {:body (get-schema :media_entries.schema_export-media-entry-perms)}}}}]

   ["/resource/:perm_name/:perm_val"
    {:put {:summary "Update media-entry entity permissions"
           :description (str "Valid perm_name values are" valid_permission_names)
           :handler handle_update-resource-perm-value
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid
                               :perm_name (s/enum "get_metadata_and_previews"
                                                  "get_full_size")
                               :perm_val s/Bool}}
           :responses {200 {:body (get-schema :media_entries.schema_export-media-entry-perms)}}}}]

   ["/users"
    {:get
     {:summary "Query media-entry user permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-user-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}
      :responses {200 {:body [(get-schema :media_entry_user_permissions.schema_export-media-entry-user-permission)]}}}}]

   ["/user/:user_id"
    {:get {:summary "Get media-entry user permissions."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_get-user-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid
                               :user_id s/Uuid}}
           :responses {200 {:body (get-schema :media_entry_user_permissions.schema_export-media-entry-user-permission)}}}

     :post {:summary "Create media-entry user permissions."
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :handler handle_create-user-perms
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :user_id s/Uuid}
                         :body (get-schema :media_entry_user_permissions.schema_create-media-entry-user-permission)}
            :responses {200 {:body (get-schema :media_entry_user_permissions.schema_export-media-entry-user-permission)}}}

     :delete {:summary "Delete media-entry user permissions."
              :swagger {:produces "application/json"}
              :content-type "application/json"
              :handler handle_delete-user-perms
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:media_entry_id s/Uuid
                                  :user_id s/Uuid}}
              :responses {200 {:body (get-schema :media_entry_user_permissions.schema_export-media-entry-user-permission)}}}}]

   ["/user/:user_id/:perm_name/:perm_val"
    {:put {:summary "Update media-entry user permissions"
           :description (str "Valid perm_name values are" valid_permission_names)
           :handler handle_update-user-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid
                               :user_id s/Uuid
                               :perm_name (s/enum "get_metadata_and_previews"
                                                  "get_full_size"
                                                  "edit_metadata"
                                                  "edit_permissions")
                               :perm_val s/Bool}}
           :responses {200 {:body (get-schema :media_entry_user_permissions.schema_export-media-entry-user-permission)}}}}]

   ["/groups"
    {:get
     {:summary "Query media-entry group permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-group-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}
      :responses {200 {:body [(get-schema :media_entry_group_permissions.schema_export-media-entry-group-permission)]}}}}]

   ["/group/:group_id"
    {:get {:summary "Get media-entry group permissions."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_get-group-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid
                               :group_id s/Uuid}}
           :responses {200 {:body (get-schema :media_entry_group_permissions.schema_export-media-entry-group-permission)}}}

     :post {:summary "Create media-entry group permissions."
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :handler handle_create-group-perms
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :group_id s/Uuid}
                         :body (get-schema :media_entry_group_permissions.schema_create-media-entry-group-permission)}
            :responses {200 {:body (get-schema :media_entry_group_permissions.schema_export-media-entry-group-permission)}}}

     :delete {:summary "Delete media-entry group permissions."
              :swagger {:produces "application/json"}
              :content-type "application/json"
              :handler handle_delete-group-perms
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:media_entry_id s/Uuid
                                  :group_id s/Uuid}}
              :responses {200 {:body (get-schema :media_entry_group_permissions.schema_export-media-entry-group-permission)}}}}]

   ["/group/:group_id/:perm_name/:perm_val"
    {:put {:summary "Update media-entry group permissions"
           :description (str "Valid perm_name values are" valid_permission_names)
           :handler handle_update-group-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid
                               :group_id s/Uuid
                               :perm_name (s/enum "get_metadata_and_previews"
                                                  "get_full_size"
                                                  "edit_metadata")
                               :perm_val s/Bool}}
           :responses {200 {:body (get-schema :media_entry_group_permissions.schema_export-media-entry-group-permission)}}}}]])

(def collection-routes
  ["/collection/:collection_id/perms"
   {:swagger {:tags ["collection/perms"]}}
   ["/"
    {:get
     {:summary "Query collection permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}
      :responses {200 {:body (get-schema :collection_permissions-all.schema_export-collection-permissions-all)}}}}]

   ["/resources"
    {:get
     {:summary "Query collection permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_get_entity_perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}
      :responses {200 {:body (get-schema :collections-perms.schema_export-collection-perms)}}}

     :put
     {:summary "Update collection entity permissions"
      :description (str "Valid perm_name values are" valid_permission_names)
      :handler handle_update-ressource-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-edit-permissions]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}
                   :body (get-schema :collections-perms.schema_update-collection-perms)}
      :responses {200 {:body (get-schema :collections-perms.schema_export-collection-perms)}}}}]

   ["/resource/:perm_name/:perm_val"
    {:put
     {:summary "Update collection entity permissions"
      :description (str "Valid perm_name values are" valid_permission_names)
      :handler handle_update-resource-perm-value
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-edit-permissions]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid
                          :perm_name (s/enum "get_metadata_and_previews")
                          :perm_val s/Bool}}
      :responses {200 {:body (get-schema :collections-perms.schema_export-collection-perms)}}}}]

   ["/users"
    {:get {:summary "Query collection permissions."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_list-user-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}}
           :responses {200 {:body [(get-schema :collection_user_permissions.schema_export-collection-user-permission)]}}}}]

   ["/user/:user_id"
    {:get {:summary "Get collection user permissions."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_get-user-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid
                               :user_id s/Uuid}}
           :responses {200 {:body (get-schema :collection_user_permissions.schema_export-collection-user-permission)}}}

     :post {:summary "Create collection user permissions."
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :handler handle_create-user-perms
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :user_id s/Uuid}
                         :body (get-schema :collection_user_permissions.schema_create-collection-user-permission)}
            :responses {200 {:body (get-schema :collection_user_permissions.schema_export-collection-user-permission)}}}

     :delete {:summary "Delete collection user permissions."
              :swagger {:produces "application/json"}
              :content-type "application/json"
              :handler handle_delete-user-perms
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:collection_id s/Uuid
                                  :user_id s/Uuid}}
              :responses {200 {:body (get-schema :collection_user_permissions.schema_export-collection-user-permission)}}}}]

   ["/user/:user_id/:perm_name/:perm_val"
    {:put {:summary "Update collection user permissions"
           :handler handle_update-user-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid
                               :user_id s/Uuid
                               :perm_name (s/enum "get_metadata_and_previews"
                                                  "edit_metadata_and_relations"
                                                  "edit_permissions")
                               :perm_val s/Bool}}
           :responses {200 {:body (get-schema :collection_user_permissions.schema_export-collection-user-permission)}}}}]

   ["/groups"
    {:get
     {:summary "Query collection permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-group-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}
      :responses {200 {:body [(get-schema :collection_group_permissions.schema_export-collection-group-permission)]}}}}]

   ["/group/:group_id"
    {:get {:summary "Get collection group permissions."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_get-group-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid
                               :group_id s/Uuid}}
           :responses {200 {:body (get-schema :collection_group_permissions.schema_export-collection-group-permission)}}}

     :post {:summary "Create collection group permissions."
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :handler handle_create-group-perms
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :group_id s/Uuid}
                         :body (get-schema :collection_group_permissions.schema_create-collection-group-permission)}
            :responses {200 {:body (get-schema :collection_group_permissions.schema_export-collection-group-permission)}}}

     :delete {:summary "Delete collection group permissions."
              :swagger {:produces "application/json"}
              :content-type "application/json"
              :handler handle_delete-group-perms
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:collection_id s/Uuid
                                  :group_id s/Uuid}}
              :responses {200 {:body (get-schema :collection_group_permissions.schema_export-collection-group-permission)}}}}]

   ["/group/:group_id/:perm_name/:perm_val"
    {:put {:summary "Update collection group permissions"
           :description (str "Valid perm_name values are" valid_permission_names)
           :handler handle_update-group-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid
                               :group_id s/Uuid
                               :perm_name (s/enum "get_metadata_and_previews"
                                                  "edit_metadata_and_relations")
                               :perm_val s/Bool}}
           :responses {200 {:body (get-schema :collection_group_permissions.schema_export-collection-group-permission)}}}}]])
