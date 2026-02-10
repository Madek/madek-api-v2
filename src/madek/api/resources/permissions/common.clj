(ns madek.api.resources.permissions.common
  (:require
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
                                   :responsible_delegation_id])
     "Collection" (select-keys mr [:id
                                   :creator_id
                                   :responsible_user_id
                                   :clipboard_user_id
                                   :get_metadata_and_previews
                                   :responsible_delegation_id])
     :default (throw ((ex-info "Invalid media-resource type" {:status 500}))))))

(def schema_update-collection-perms
  {(s/optional-key :get_metadata_and_previews) s/Bool
   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
   (s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})

(def schema_create-collection-user-permission
  {:get_metadata_and_previews s/Bool
   :edit_metadata_and_relations s/Bool
   :edit_permissions s/Bool})

(def schema_export-collection-user-permission
  {:id s/Uuid
   :updator_id (s/maybe s/Uuid)
   :creator_id (s/maybe s/Uuid)
   :collection_id s/Uuid
   :user_id s/Uuid
   :get_metadata_and_previews s/Bool
   :edit_metadata_and_relations s/Bool
   :edit_permissions s/Bool
   :delegation_id (s/maybe s/Uuid)
   :created_at s/Any
   :updated_at s/Any})

(def schema_create-collection-group-permission
  {:get_metadata_and_previews s/Bool
   :edit_metadata_and_relations s/Bool})

(def schema_export-collection-group-permission
  {:id s/Uuid
   :updator_id (s/maybe s/Uuid)
   :creator_id (s/maybe s/Uuid)
   :collection_id s/Uuid
   :group_id s/Uuid
   :get_metadata_and_previews s/Bool
   :edit_metadata_and_relations s/Bool
   ;:delegation_id (s/maybe s/Uuid)
   :created_at s/Any
   :updated_at s/Any})

(def schema_export-collection-perms
  {:id s/Uuid
   :creator_id (s/maybe s/Uuid)
   (s/optional-key :get_metadata_and_previews) s/Bool
   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)

   (s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})

(def schema_export_collection-permissions-all
  {:media-resource schema_export-collection-perms
   :users [schema_export-collection-user-permission]
   :groups [schema_export-collection-group-permission]})

(def schema_update-media-entry-perms
  {(s/optional-key :get_metadata_and_previews) s/Bool
   (s/optional-key :get_full_size) s/Bool
   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})

(def schema_export-media-entry-perms
  {:id s/Uuid
   :creator_id (s/maybe s/Uuid)
   :is_published s/Bool
   (s/optional-key :get_metadata_and_previews) s/Bool
   (s/optional-key :get_full_size) s/Bool
   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})

(def schema_create-media-entry-user-permission
  {:get_metadata_and_previews s/Bool
   :get_full_size s/Bool
   :edit_metadata s/Bool
   :edit_permissions s/Bool})

(def schema_export-media-entry-user-permission
  {:id s/Uuid
   :updator_id (s/maybe s/Uuid)
   :creator_id (s/maybe s/Uuid)
   :media_entry_id s/Uuid
   :user_id s/Uuid
   :get_metadata_and_previews s/Bool
   :get_full_size s/Bool
   :edit_metadata s/Bool
   :edit_permissions s/Bool
   :delegation_id (s/maybe s/Uuid)
   :created_at s/Any
   :updated_at s/Any})

(def schema_create-media-entry-group-permission
  {:get_metadata_and_previews s/Bool
   :get_full_size s/Bool
   :edit_metadata s/Bool})

(def schema_export-media-entry-group-permission
  {:id s/Uuid
   :updator_id (s/maybe s/Uuid)
   :creator_id (s/maybe s/Uuid)
   :media_entry_id s/Uuid
   :group_id s/Uuid
   :get_metadata_and_previews s/Bool
   :get_full_size s/Bool
   :edit_metadata s/Bool
   :created_at s/Any
   :updated_at s/Any})

(def schema_export_media-entry-permissions-all
  {:media-resource schema_export-media-entry-perms
   :users [schema_export-media-entry-user-permission]
   :groups [schema_export-media-entry-group-permission]})
