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
        data (mr-permissions/query-list-api-client-permissions mr :mr-type mr-type)]
    (sd/response_ok data))
  )

(defn- handle_list-user-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (mr-permissions/query-list-user-permissions mr :mr-type mr-type)]
    (sd/response_ok data)))

(defn- handle_list-group-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (mr-permissions/query-list-group-permissions mr :mr-type mr-type)]
    (sd/response_ok data)))

(defn- handle_list-perms-type
  [req]
  (let [p-type (-> req :parameters :path :type)
        mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (case p-type
               "api-client" (mr-permissions/query-list-api-client-permissions mr :mr-type mr-type)
               "user" (mr-permissions/query-list-user-permissions mr :mr-type mr-type)
               "group" (mr-permissions/query-list-group-permissions mr :mr-type mr-type))]
    (sd/response_ok data)))

(defn- handle_list-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        a-data (mr-permissions/query-list-api-client-permissions mr :mr-type mr-type)
        u-data (mr-permissions/query-list-user-permissions mr :mr-type mr-type)
        g-data (mr-permissions/query-list-group-permissions mr :mr-type mr-type)]
    (sd/response_ok {:api-clients a-data :users u-data :groups g-data})))

(def media-entry-routes
  ["/media-entry/:media_entry_id/perms"
   ["/"
    {:get
     {:summary "Query media-entry permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}}}]
   ["/:type"
    {:get
     {:summary "Query media-entry permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-perms-type
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:type s/Str
                          :media_entry_id s/Uuid}}}}]
  ])
(def old [
   ["/api-client"
    {:get
     {:summary "Query media-entry permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-api-client-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}}}]
   
   ["/user"
   {:get
    {:summary "Query media-entry permissions."
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
     {:summary "Query media-entry permissions."
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
    {:get
     {:summary "Query collection permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-user-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}}}]

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