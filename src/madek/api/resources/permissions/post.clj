(ns madek.api.resources.permissions.post
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

;(defn get-entity-perms
;  ;([mr] (get-entity-perms mr (:type mr)))
;  ([mr type]
;   (case type
;     "MediaEntry" (select-keys mr [:id
;                                   :creator_id
;                                   :responsible_user_id
;                                   :is_published
;                                   :get_metadata_and_previews
;                                   :get_full_size
;                                   ; TODO delegations
;                                   ])
;     "Collection" (select-keys mr [:id
;                                   :creator_id
;                                   :responsible_user_id
;                                   :clipboard_user_id
;                                   :workflow_id
;                                   :get_metadata_and_previews
;                                   ; TODO delegations
;                                   ])
;     :default (throw ((ex-info "Invalid media-resource type" {:status 500}))))))


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


; TODO only for docu
(def valid_permission_names
  ["get_metadata_and_previews"
   "get_full_size"
   "edit_metadata"
   "edit_permissions"])


;; ### handler ######################################################

(def me.user.user_id {:summary "Create media-entry user permissions."
                      :swagger {:produces "application/json"}
                      :content-type "application/json"
                      :handler handle_create-user-perms
                      :middleware [sd/ring-wrap-add-media-resource
                                   sd/ring-wrap-authorization-edit-permissions]
                      :coercion reitit.coercion.schema/coercion
                      :parameters {:path {:media_entry_id s/Uuid
                                          :user_id s/Uuid}
                                   :body (get-schema :media_entry_user_permissions.schema_create-media-entry-user-permission)}
                      :responses {200 {:body (get-schema :media_entry_user_permissions.schema_export-media-entry-user-permission)}}})


(def me.group.group_id {:summary "Create media-entry group permissions."
                        :swagger {:produces "application/json"}
                        :content-type "application/json"
                        :handler handle_create-group-perms
                        :middleware [sd/ring-wrap-add-media-resource
                                     sd/ring-wrap-authorization-edit-permissions]
                        :coercion reitit.coercion.schema/coercion
                        :parameters {:path {:media_entry_id s/Uuid
                                            :group_id s/Uuid}
                                     :body (get-schema :media_entry_group_permissions.schema_create-media-entry-group-permission)}
                        :responses {200 {:body (get-schema :media_entry_group_permissions.schema_export-media-entry-group-permission)}}})


(def col.user.user_id {:summary "Create collection user permissions."
                       :swagger {:produces "application/json"}
                       :content-type "application/json"
                       :handler handle_create-user-perms
                       :middleware [sd/ring-wrap-add-media-resource
                                    sd/ring-wrap-authorization-edit-permissions]
                       :coercion reitit.coercion.schema/coercion
                       :parameters {:path {:collection_id s/Uuid
                                           :user_id s/Uuid}
                                    :body (get-schema :collection_user_permissions.schema_create-collection-user-permission)}
                       :responses {200 {:body (get-schema :collection_user_permissions.schema_export-collection-user-permission)}}})


(def col.group.group_id {:summary "Create collection group permissions."
                         :swagger {:produces "application/json"}
                         :content-type "application/json"
                         :handler handle_create-group-perms
                         :middleware [sd/ring-wrap-add-media-resource
                                      sd/ring-wrap-authorization-edit-permissions]
                         :coercion reitit.coercion.schema/coercion
                         :parameters {:path {:collection_id s/Uuid
                                             :group_id s/Uuid}
                                      :body (get-schema :collection_group_permissions.schema_create-collection-group-permission)}
                         :responses {200 {:body (get-schema :collection_group_permissions.schema_export-collection-group-permission)}}})





