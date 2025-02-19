(ns madek.api.resources.permissions.put
  (:require
   [logbug.catcher :as catcher]
   [madek.api.resources.media-resources.permissions :as mr-permissions]
   [madek.api.resources.permissions.common :refer :all]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

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
            auth-entity-id (-> req :authenticated-entity :id)
            perm-name (keyword (-> req :parameters :path :perm_name))
            perm-val (-> req :parameters :path :perm_val)
            mr (-> req :media-resource)
            tx (:tx req)
            mr-type (mr-table-type mr)]
        (if-let [old-perm (mr-permissions/query-get-user-permission mr mr-type user-id tx)]
          (let [upd-result (mr-permissions/update-user-permissions mr mr-type
                                                                   user-id auth-entity-id
                                                                   perm-name perm-val
                                                                   tx)]
            (if (= 1 (::jdbc/update-count upd-result))
              (sd/response_ok (mr-permissions/query-get-user-permission mr mr-type user-id tx))
              (sd/response_failed (str "Could not update permissions" upd-result) 400)))
          (sd/response_not_found "No such resource user permission."))))
    (catch Exception ex (sd/response_exception ex))))

(defn- handle_update-group-perms
  [req]
  (try
    (catcher/with-logging {}
      (let [group-id (-> req :parameters :path :group_id)
            auth-entity-id (-> req :authenticated-entity :id)
            perm-name (keyword (-> req :parameters :path :perm_name))
            perm-val (-> req :parameters :path :perm_val)
            tx (:tx req)
            mr (-> req :media-resource)
            mr-type (mr-table-type mr)]
        (if-let [old-data (mr-permissions/query-get-group-permission mr mr-type group-id tx)]
          (let [upd-result (mr-permissions/update-group-permissions mr mr-type
                                                                    group-id auth-entity-id
                                                                    perm-name perm-val
                                                                    tx)]
            (if (= 1 (::jdbc/update-count upd-result))
              (sd/response_ok (mr-permissions/query-get-group-permission mr mr-type group-id tx))
              (sd/response_failed (str "Could not update permissions" upd-result) 400)))
          (sd/response_not_found "No such resource group permissions."))))
    (catch Exception ex (sd/response_exception ex))))

; TODO only for docu
(def valid_permission_names
  ["get_metadata_and_previews"
   "get_full_size"
   "edit_metadata"
   "edit_permissions"])

;; ### handler ######################################################

(def me.resources {:summary "Update media-entry entity permissions"
                   :description (str "Valid perm_name values are" valid_permission_names)
                   :handler handle_update-ressource-perms
                   :middleware [jqh/ring-wrap-add-media-resource
                                jqh/ring-wrap-authorization-edit-permissions]
                   :coercion reitit.coercion.schema/coercion
                   :parameters {:path {:media_entry_id s/Uuid}
                                :body schema_update-media-entry-perms}
                   :responses {200 {:description "Returns the updated media-entry entity permission."
                                    :body schema_export-media-entry-perms}}})

(def me.resource.perm_name.perm_val {:summary "Update media-entry entity permissions"
                                     :description (str "Valid perm_name values are" valid_permission_names)
                                     :handler handle_update-resource-perm-value
                                     :middleware [jqh/ring-wrap-add-media-resource
                                                  jqh/ring-wrap-authorization-edit-permissions]
                                     :coercion reitit.coercion.schema/coercion
                                     :parameters {:path {:media_entry_id s/Uuid
                                                         :perm_name (s/enum "get_metadata_and_previews"
                                                                            "get_full_size")
                                                         :perm_val s/Bool}}
                                     :responses {200 {:description "Returns the updated media-entry entity permission."
                                                      :body schema_export-media-entry-perms}}})

(def me.user.user_id.perm_name.perm_val {:summary "Update media-entry user permissions"
                                         :description (str "Valid perm_name values are" valid_permission_names)
                                         :handler handle_update-user-perms
                                         :middleware [jqh/ring-wrap-add-media-resource
                                                      jqh/ring-wrap-authorization-edit-permissions]
                                         :coercion reitit.coercion.schema/coercion
                                         :parameters {:path {:media_entry_id s/Uuid
                                                             :user_id s/Uuid
                                                             :perm_name (s/enum "get_metadata_and_previews"
                                                                                "get_full_size"
                                                                                "edit_metadata"
                                                                                "edit_permissions")
                                                             :perm_val s/Bool}}
                                         :responses {200 {:description "Returns the updated media-entry user permission."
                                                          :body schema_export-media-entry-user-permission}}})

(def me.group.group_id.perm_name.permval {:summary "Update media-entry group permissions"
                                          :description (str "Valid perm_name values are" valid_permission_names)
                                          :handler handle_update-group-perms
                                          :middleware [jqh/ring-wrap-add-media-resource
                                                       jqh/ring-wrap-authorization-edit-permissions]
                                          :coercion reitit.coercion.schema/coercion
                                          :parameters {:path {:media_entry_id s/Uuid
                                                              :group_id s/Uuid
                                                              :perm_name (s/enum "get_metadata_and_previews"
                                                                                 "get_full_size"
                                                                                 "edit_metadata")
                                                              :perm_val s/Bool}}
                                          :responses {200 {:description "Returns the updated media-entry group permission."
                                                           :body schema_export-media-entry-group-permission}}})

(def col.resources {:summary "Update collection entity permissions"
                    :description (str "Valid perm_name values are" valid_permission_names)
                    :handler handle_update-ressource-perms
                    :middleware [jqh/ring-wrap-add-media-resource
                                 jqh/ring-wrap-authorization-edit-permissions]
                    :coercion reitit.coercion.schema/coercion
                    :parameters {:path {:collection_id s/Uuid}
                                 :body schema_update-collection-perms}
                    :responses {200 {:description "Returns the updated collection entity permission."
                                     :body schema_export-collection-perms}}})

(def col.resource.perm_name.perm_val {:summary "Update collection entity permissions"
                                      :description (str "Valid perm_name values are" valid_permission_names)
                                      :handler handle_update-resource-perm-value
                                      :middleware [jqh/ring-wrap-add-media-resource
                                                   jqh/ring-wrap-authorization-edit-permissions]
                                      :coercion reitit.coercion.schema/coercion
                                      :parameters {:path {:collection_id s/Uuid
                                                          :perm_name (s/enum "get_metadata_and_previews")
                                                          :perm_val s/Bool}}
                                      :responses {200 {:description "Returns the updated collection entity permission."
                                                       :body schema_export-collection-perms}}})

(def col.user.user_id.perm_name.perm_val {:summary "Update collection user permissions"
                                          :handler handle_update-user-perms
                                          :middleware [jqh/ring-wrap-add-media-resource
                                                       jqh/ring-wrap-authorization-edit-permissions]
                                          :coercion reitit.coercion.schema/coercion
                                          :parameters {:path {:collection_id s/Uuid
                                                              :user_id s/Uuid
                                                              :perm_name (s/enum "get_metadata_and_previews"
                                                                                 "edit_metadata_and_relations"
                                                                                 "edit_permissions")
                                                              :perm_val s/Bool}}
                                          :responses {200 {:description "Returns the updated collection user permission."
                                                           :body schema_export-collection-user-permission}}})

(def col.group.group_id.perm_name.perm_val {:summary "Update collection group permissions"
                                            :description (str "Valid perm_name values are" valid_permission_names)
                                            :handler handle_update-group-perms
                                            :middleware [jqh/ring-wrap-add-media-resource
                                                         jqh/ring-wrap-authorization-edit-permissions]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:collection_id s/Uuid
                                                                :group_id s/Uuid
                                                                :perm_name (s/enum "get_metadata_and_previews"
                                                                                   "edit_metadata_and_relations")
                                                                :perm_val s/Bool}}
                                            :responses {200 {:description "Returns the updated collection group permission."
                                                             :body schema_export-collection-group-permission}}})
