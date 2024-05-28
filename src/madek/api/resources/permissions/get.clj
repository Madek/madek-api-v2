(ns madek.api.resources.permissions.get
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



;; ### handler ######################################################

(def media-entry.media_entry_id.perms {:summary (sd/sum_usr_pub "List media-entry permissions.")
                                       :swagger {:produces "application/json"}
                                       :content-type "application/json"
                                       :handler handle_list-perms
                                       :middleware [sd/ring-wrap-add-media-resource
                                                    sd/ring-wrap-authorization-view]
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:media_entry_id s/Uuid}}
                                       :responses {200 {:body (get-schema :media_entry_user_permissions.schema_export_media-entry-permissions-all)}}})


(def media-entry.media_entry_id.perms.resources {:summary "Query media-entry permissions."
                                                 :swagger {:produces "application/json"}
                                                 :content-type "application/json"
                                                 :handler handle_get_entity_perms
                                                 :middleware [sd/ring-wrap-add-media-resource
                                                              sd/ring-wrap-authorization-view]
                                                 :coercion reitit.coercion.schema/coercion
                                                 :parameters {:path {:media_entry_id s/Uuid}}
                                                 :responses {200 {:body (get-schema :media_entries.schema_export-media-entry-perms)}}})


(def media-entry.media_entry_id.perms.users {:summary "Query media-entry user permissions."
                                             :swagger {:produces "application/json"}
                                             :content-type "application/json"
                                             :handler handle_list-user-perms
                                             :middleware [sd/ring-wrap-add-media-resource
                                                          sd/ring-wrap-authorization-view]
                                             :coercion reitit.coercion.schema/coercion
                                             :parameters {:path {:media_entry_id s/Uuid}}
                                             :responses {200 {:body [(get-schema :media_entry_user_permissions.schema_export-media-entry-user-permission)]}}})



(def media-entry.media_entry_id.perms.user {:summary "Get media-entry user permissions."
                                            :swagger {:produces "application/json"}
                                            :content-type "application/json"
                                            :handler handle_get-user-perms
                                            :middleware [sd/ring-wrap-add-media-resource
                                                         sd/ring-wrap-authorization-edit-permissions]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid
                                                                :user_id s/Uuid}}
                                            :responses {200 {:body (get-schema :media_entry_user_permissions.schema_export-media-entry-user-permission)}}})



(def media-entry.media_entry_id.perms.groups {:summary "Query media-entry group permissions."
                                              :swagger {:produces "application/json"}
                                              :content-type "application/json"
                                              :handler handle_list-group-perms
                                              :middleware [sd/ring-wrap-add-media-resource
                                                           sd/ring-wrap-authorization-view]
                                              :coercion reitit.coercion.schema/coercion
                                              :parameters {:path {:media_entry_id s/Uuid}}
                                              :responses {200 {:body [(get-schema :media_entry_group_permissions.schema_export-media-entry-group-permission)]}}})


(def media-entry.media_entry_id.perms.group.group_id {:summary "Get media-entry group permissions."
                                              :swagger {:produces "application/json"}
                                              :content-type "application/json"
                                              :handler handle_get-group-perms
                                              :middleware [sd/ring-wrap-add-media-resource
                                                           sd/ring-wrap-authorization-edit-permissions]
                                              :coercion reitit.coercion.schema/coercion
                                              :parameters {:path {:media_entry_id s/Uuid
                                                                  :group_id s/Uuid}}
                                              :responses {200 {:body (get-schema :media_entry_group_permissions.schema_export-media-entry-group-permission)}}})



(def collection.collection_id.perms {:summary "Query collection permissions."
                                     :swagger {:produces "application/json"}
                                     :content-type "application/json"
                                     :handler handle_list-perms
                                     :middleware [sd/ring-wrap-add-media-resource
                                                  sd/ring-wrap-authorization-view]
                                     :coercion reitit.coercion.schema/coercion
                                     :parameters {:path {:collection_id s/Uuid}}
                                     :responses {200 {:body (get-schema :collection_permissions-all.schema_export-collection-permissions-all)}}})



(def collection.collection_id.perms.resources {:summary "Query collection permissions."
                                               :swagger {:produces "application/json"}
                                               :content-type "application/json"
                                               :handler handle_get_entity_perms
                                               :middleware [sd/ring-wrap-add-media-resource
                                                            sd/ring-wrap-authorization-view]
                                               :coercion reitit.coercion.schema/coercion
                                               :parameters {:path {:collection_id s/Uuid}}
                                               :responses {200 {:body (get-schema :collections-perms.schema_export-collection-perms)}}})




(def collection.collection_id.perms.users {:summary "Query collection permissions."
                                           :swagger {:produces "application/json"}
                                           :content-type "application/json"
                                           :handler handle_list-user-perms
                                           :middleware [sd/ring-wrap-add-media-resource
                                                        sd/ring-wrap-authorization-view]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:collection_id s/Uuid}}
                                           :responses {200 {:body [(get-schema :collection_user_permissions.schema_export-collection-user-permission)]}}})



(def collection.collection_id.perms.user.user_id {:summary "Get collection user permissions."
                                                  :swagger {:produces "application/json"}
                                                  :content-type "application/json"
                                                  :handler handle_get-user-perms
                                                  :middleware [sd/ring-wrap-add-media-resource
                                                               sd/ring-wrap-authorization-view]
                                                  :coercion reitit.coercion.schema/coercion
                                                  :parameters {:path {:collection_id s/Uuid
                                                                      :user_id s/Uuid}}
                                                  :responses {200 {:body (get-schema :collection_user_permissions.schema_export-collection-user-permission)}}})


(def collection.collection_id.perms.groups {:summary "Query collection permissions."
                                            :swagger {:produces "application/json"}
                                            :content-type "application/json"
                                            :handler handle_list-group-perms
                                            :middleware [sd/ring-wrap-add-media-resource
                                                         sd/ring-wrap-authorization-view]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:collection_id s/Uuid}}
                                            :responses {200 {:body [(get-schema :collection_group_permissions.schema_export-collection-group-permission)]}}})



(def collection.collection_id.perms.group.group_id {:summary "Get collection group permissions."
                                                    :swagger {:produces "application/json"}
                                                    :content-type "application/json"
                                                    :handler handle_get-group-perms
                                                    :middleware [sd/ring-wrap-add-media-resource
                                                                 sd/ring-wrap-authorization-edit-permissions]
                                                    :coercion reitit.coercion.schema/coercion
                                                    :parameters {:path {:collection_id s/Uuid
                                                                        :group_id s/Uuid}}
                                                    :responses {200 {:body (get-schema :collection_group_permissions.schema_export-collection-group-permission)}}})

