(ns madek.api.resources.permissions 
  (:require 
   [schema.core :as s]
   [reitit.coercion.schema]
   [madek.api.resources.shared :as sd]
   
   [madek.api.resources.vocabularies.permissions :as voc-perms]
   [madek.api.resources.media-resources.permissions :as mr-permissions]))


(defn- handle_list-api-client-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (mr-permissions/query-list-api-client-permissions mr mr-type)]
    (sd/response_ok data))
  )

(defn- handle_list-user-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (mr-permissions/query-list-user-permissions mr mr-type)]
    (sd/response_ok data)))

(defn- handle_get-user-perms
  [req]
  (let [user-id (-> req :parameters :path :user_id)
        mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (mr-permissions/query-get-user-permissions mr mr-type user-id)]
    (sd/response_ok data)))

(defn- handle_update-user-perms
  [req]
  (let [user-id (-> req :parameters :path :user_id)
        perm-name (-> req :parameters :path :perm_name)
        perm-val (-> req :parameters :path :perm_val)
        mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        upd-result (mr-permissions/update-user-permissions mr mr-type user-id perm-name perm-val)]
    (if (= 1 upd-result)
      (sd/response_ok (mr-permissions/query-get-user-permissions mr mr-type user-id))
      (sd/response_failed "Could not update permissions" 400)) ; TODO error code
    ))

(defn- handle_list-group-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (mr-permissions/query-list-group-permissions mr mr-type)]
    (sd/response_ok data)))

(defn- handle_list-perms-type
  [req]
  (let [p-type (-> req :parameters :path :type)
        mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (case p-type
               "api-client" (mr-permissions/query-list-api-client-permissions mr mr-type)
               "user" (mr-permissions/query-list-user-permissions mr mr-type)
               "group" (mr-permissions/query-list-group-permissions mr mr-type))]
    (sd/response_ok data)))

(defn- handle_list-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        a-data (mr-permissions/query-list-api-client-permissions mr mr-type)
        u-data (mr-permissions/query-list-user-permissions mr mr-type)
        g-data (mr-permissions/query-list-group-permissions mr mr-type)]
    (sd/response_ok {:api-clients a-data :users u-data :groups g-data})))

(def media-entry-routes
  ["/media-entry/:media_entry_id/perms"
   ["/"
    {:get
     {:summary "List media-entry permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}}}]
   ;["/:type"
   ; {:get
   ;  {:summary "List media-entry permissions of type [user|group|api_client]."
   ;   :swagger {:produces "application/json"}
   ;   :content-type "application/json"
   ;   :handler handle_list-perms-type
   ;   :middleware [sd/ring-wrap-add-media-resource
   ;                sd/ring-wrap-authorization-view]
   ;   :coercion reitit.coercion.schema/coercion
   ;   :parameters {:path {:type s/Str
   ;                       :media_entry_id s/Uuid}}}}]
  ;])
;(def old [
   ["/api-client"
    {:get
     {:summary "List media-entry api-client permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-api-client-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}}}]
   
   ["/user"
   {:get
    {:summary "Query media-entry user permissions."
     :swagger {:produces "application/json"}
     :content-type "application/json"
     :handler handle_list-user-perms
     :middleware [sd/ring-wrap-add-media-resource
                  sd/ring-wrap-authorization-view]
     :coercion reitit.coercion.schema/coercion
     :parameters {:path {:media_entry_id s/Uuid}}}}
    ]
   
   ["/group"
    {:get
     {:summary "Query media-entry group permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-group-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}}}]
   
   ])


(def collection-routes
  ["/collection/:collection_id/perms"
   ["/"
    {:get
     {:summary "Query collection permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}}}]
   ["/api-client"
    {:get
     {:summary "Query collection permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-api-client-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}}}]

   ["/user"
    {:get {:summary "Query collection permissions."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_list-user-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}}}}]

   ["/user/:user_id"
    {:get {:summary "Get collection user permissions."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_get-user-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid
                               :user_id s/Uuid}}}
     :post {:summary "Create collection user permissions"
            :handler (constantly sd/no_impl)
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :user_id s/Uuid}}}}]

   ["/user/:user_id/:perm_name/:perm_val"
    {:patch {:summary "Update collection user permissions"
             :handler handle_update-user-perms
             :middleware [sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-permissions]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Uuid
                                 :user_id s/Uuid
                                 :perm_name s/Str ; TODO use enumaration of allowed values or document
                                 :perm_val s/Bool}}}}]


   ["/group"
    {:get
     {:summary "Query collection permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-group-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}}}]])

