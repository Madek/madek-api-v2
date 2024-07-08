(ns madek.api.resources.permissions.delete
  (:require
   [logbug.catcher :as catcher]
   [madek.api.resources.media-resources.permissions :as mr-permissions]
   [madek.api.resources.permissions.common :refer :all]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [reitit.coercion.schema]
   [schema.core :as s]))

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

;; ### handler ######################################################

(def me.user.user_id {:summary "Delete media-entry user permissions."
                      :swagger {:produces "application/json"}
                      :content-type "application/json"
                      :handler handle_delete-user-perms
                      :middleware [jqh/ring-wrap-add-media-resource
                                   jqh/ring-wrap-authorization-edit-permissions]
                      :coercion reitit.coercion.schema/coercion
                      :parameters {:path {:media_entry_id s/Uuid
                                          :user_id s/Uuid}}
                      :responses {200 {:description "Returns the deleted media-entry user permission."
                                       :body schema_export-media-entry-user-permission}}})

(def me.group.group_id {:summary "Delete media-entry group permissions."
                        :swagger {:produces "application/json"}
                        :content-type "application/json"
                        :handler handle_delete-group-perms
                        :middleware [jqh/ring-wrap-add-media-resource
                                     jqh/ring-wrap-authorization-edit-permissions]
                        :coercion reitit.coercion.schema/coercion
                        :parameters {:path {:media_entry_id s/Uuid
                                            :group_id s/Uuid}}
                        :responses {200 {:description "Returns the deleted media-entry group permission."
                                         :body schema_export-media-entry-group-permission}}})

(def col.user.user_id {:summary "Delete collection user permissions."
                       :swagger {:produces "application/json"}
                       :content-type "application/json"
                       :handler handle_delete-user-perms
                       :middleware [jqh/ring-wrap-add-media-resource
                                    jqh/ring-wrap-authorization-edit-permissions]
                       :coercion reitit.coercion.schema/coercion
                       :parameters {:path {:collection_id s/Uuid
                                           :user_id s/Uuid}}
                       :responses {200 {:description "Returns the deleted collection user permission."
                                        :body schema_export-collection-user-permission}}})

(def col.group.group_id {:summary "Delete collection group permissions."
                         :swagger {:produces "application/json"}
                         :content-type "application/json"
                         :handler handle_delete-group-perms
                         :middleware [jqh/ring-wrap-add-media-resource
                                      jqh/ring-wrap-authorization-edit-permissions]
                         :coercion reitit.coercion.schema/coercion
                         :parameters {:path {:collection_id s/Uuid
                                             :group_id s/Uuid}}
                         :responses {200 {:description "Returns the deleted collection group permission."
                                          :body schema_export-collection-group-permission}}})
