(ns madek.api.resources.permissions.post
  (:require
   [logbug.catcher :as catcher]
   [madek.api.resources.media-resources.permissions :as mr-permissions]
   [madek.api.resources.permissions.common :refer :all]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [reitit.coercion.schema]
   [schema.core :as s]))

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

;; ### handler ######################################################

(def me.user.user_id {:summary "Create media-entry user permissions."
                      :swagger {:produces "application/json"}
                      :content-type "application/json"
                      :handler handle_create-user-perms
                      :middleware [jqh/ring-wrap-add-media-resource
                                   jqh/ring-wrap-authorization-edit-permissions]
                      :coercion reitit.coercion.schema/coercion
                      :parameters {:path {:media_entry_id s/Uuid
                                          :user_id s/Uuid}
                                   :body schema_create-media-entry-user-permission}
                      :responses {200 {:body schema_export-media-entry-user-permission}}})

(def me.group.group_id {:summary "Create media-entry group permissions."
                        :swagger {:produces "application/json"}
                        :content-type "application/json"
                        :handler handle_create-group-perms
                        :middleware [jqh/ring-wrap-add-media-resource
                                     jqh/ring-wrap-authorization-edit-permissions]
                        :coercion reitit.coercion.schema/coercion
                        :parameters {:path {:media_entry_id s/Uuid
                                            :group_id s/Uuid}
                                     :body schema_create-media-entry-group-permission}
                        :responses {200 {:body schema_export-media-entry-group-permission}}})

(def col.user.user_id {:summary "Create collection user permissions."
                       :swagger {:produces "application/json"}
                       :content-type "application/json"
                       :handler handle_create-user-perms
                       :middleware [jqh/ring-wrap-add-media-resource
                                    jqh/ring-wrap-authorization-edit-permissions]
                       :coercion reitit.coercion.schema/coercion
                       :parameters {:path {:collection_id s/Uuid
                                           :user_id s/Uuid}
                                    :body schema_create-collection-user-permission}
                       :responses {200 {:body schema_export-collection-user-permission}}})

(def col.group.group_id {:summary "Create collection group permissions."
                         :swagger {:produces "application/json"}
                         :content-type "application/json"
                         :handler handle_create-group-perms
                         :middleware [jqh/ring-wrap-add-media-resource
                                      jqh/ring-wrap-authorization-edit-permissions]
                         :coercion reitit.coercion.schema/coercion
                         :parameters {:path {:collection_id s/Uuid
                                             :group_id s/Uuid}
                                      :body schema_create-collection-group-permission}
                         :responses {200 {:body schema_export-collection-group-permission}}})
