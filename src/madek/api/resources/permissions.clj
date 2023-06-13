(ns madek.api.resources.permissions 
  (:require 
   [schema.core :as s]
   [reitit.coercion.schema]
   [madek.api.resources.shared :as sd]
   
   [madek.api.resources.vocabularies.permissions :as voc-perms]
   [madek.api.resources.media-resources.permissions :as mr-permissions]
   [clojure.tools.logging :as logging]))


(defn mr-table-type [media-resource]
  (case (:type media-resource)
    "MediaEntry" "media_entry"
    "Collection" "collection"
    :default (throw ((ex-info "Invalid media-resource type" {:status 500})))))

(defn get-entity-perms [mr]
  (case (:type mr)
    "MediaEntry" (select-keys mr [:creator_id
                                  :responsible_user_id
                                  :is_published
                                  :get_metadata_and_previews
                                  :get_full_size])
    "Collection" (select-keys mr [:creator_id
                                  :responsible_user_id
                                  :get_metadata_and_previews
                                  ])
    :default (throw ((ex-info "Invalid media-resource type" {:status 500})))))

(defn handle_get_entity_perms
  [req]
  (let [mr (-> req :media-resource)
        data (get-entity-perms mr)]
    (sd/response_ok data)))

(defn- handle_update-resource-perm-value
  [req]
  (let [perm-name (keyword (-> req :parameters :path :perm_name))
        perm-val (-> req :parameters :path :perm_val)
        perm-data {perm-name perm-val}
        mr (-> req :media-resource)
        upd-result (mr-permissions/update-resource-permissions mr perm-data)]
    (if (seq? upd-result)
      (sd/response_ok (mr-permissions/resource-permission-get-query mr))
      (sd/response_failed (str "Could not update permissions" upd-result) 400)) ; TODO error code
    ))

(defn- handle_update-ressource-perms
  [req]
  (let [perm-data (-> req :parameters :body)
        mr (-> req :media-resource)
        upd-result (mr-permissions/update-resource-permissions mr perm-data)]
    (if (seq? upd-result)
      (sd/response_ok (mr-permissions/resource-permission-get-query mr))
      (sd/response_failed (str "Could not update permissions" upd-result) 400)) ; TODO error code
    ))

; b657884f-cb08-4443-8351-78b726ac7b13
;(defn- handle_list-api-client-perms
;  [req]
;  (let [mr (-> req :media-resource)
;        mr-type (mr-table-type mr)
;        data (mr-permissions/query-list-api-client-permissions mr mr-type)]
;    (sd/response_ok data))
;  )

(defn- handle_list-user-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (mr-table-type mr)
        data (mr-permissions/query-list-user-permissions mr mr-type)]
    (sd/response_ok data)))

(defn- handle_get-user-perms
  [req]
  (let [user-id (-> req :parameters :path :user_id)
        mr (-> req :media-resource)
        mr-type (mr-table-type mr)
        data (mr-permissions/query-get-user-permissions mr mr-type user-id)]
    (sd/response_ok data)))

; TODO delete user perm
; TODO create user perm
(defn handle_create-user-perms [req]
  (let [user-id (-> req :parameters :path :user_id)
        mr (-> req :media-resource)
        mrt (mr-table-type mr)
        data (-> req :parameters :body)]
    (mr-permissions/create-user-permissions mr mrt user-id data)
    ))

(defn handle_delete-user-perms [req]
  (let [user-id (-> req :parameters :path :user_id)
        mr (-> req :media-resource)
        mrt (mr-table-type mr)]
    (mr-permissions/delete-user-permissions mr mrt user-id)))

; TODO
; use wrapper for perm entry existence
; use wrapper for user-id
; use case for perm-name
; use case default as error
; check if perm entry exists, if not create it
; delete if all perms are set to false
(defn- handle_update-user-perms
  [req]
  (let [user-id (-> req :parameters :path :user_id)
        perm-name (keyword (-> req :parameters :path :perm_name))
        perm-val (-> req :parameters :path :perm_val)
        mr (-> req :media-resource)
        mr-type (mr-table-type mr)
        upd-result (mr-permissions/update-user-permissions mr mr-type user-id perm-name perm-val)]
    (if (seq? upd-result)
      (sd/response_ok (mr-permissions/query-get-user-permissions mr mr-type user-id))
      (sd/response_failed (str "Could not update permissions" upd-result) 400)) ; TODO error code
    ))

; TODO delete group perm
; TODO create group perm
(defn handle_create-group-perms [req]
  (let [group-id (-> req :parameters :path :group_id)
        mr (-> req :media-resource)
        mrt (mr-table-type mr)
        data (-> req :parameters :body)]
    (mr-permissions/create-group-permissions mr mrt group-id data)
    ))

(defn handle_delete-group-perms [req]
  (let [group-id (-> req :parameters :path :group_id)
        mr (-> req :media-resource)
        mrt (mr-table-type mr)]
    (mr-permissions/delete-group-permissions mr mrt group-id)))


(defn- handle_list-group-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (mr-permissions/query-list-group-permissions mr mr-type)]
    (sd/response_ok data)))

(defn- handle_get-group-perms
  [req]
  (let [group-id (-> req :parameters :path :group_id)
        mr (-> req :media-resource)
        mr-type (mr-table-type mr)
        data (mr-permissions/query-get-group-permissions mr mr-type group-id)]
    (sd/response_ok data)))

(defn- handle_update-group-perms
  [req]
  (let [group-id (-> req :parameters :path :group_id)
        perm-name (keyword (-> req :parameters :path :perm_name))
        perm-val (-> req :parameters :path :perm_val)
        mr (-> req :media-resource)
        mr-type (mr-table-type mr)
        upd-result (mr-permissions/update-group-permissions mr mr-type group-id perm-name perm-val)]
    (if (seq? upd-result)
      (sd/response_ok (mr-permissions/query-get-group-permissions mr mr-type group-id))
      (sd/response_failed (str "Could not update permissions" upd-result) 400)) ; TODO error code
    ))


(defn- handle_list-perms-type
  [req]
  (let [p-type (-> req :parameters :path :type)
        mr (-> req :media-resource)
        mr-type (case (:type mr)
                  "MediaEntry" "media_entry"
                  "Collection" "collection")
        data (case p-type
               "entity" (get-entity-perms mr)
               ;"api-client" (mr-permissions/query-list-api-client-permissions mr mr-type)
               "user" (mr-permissions/query-list-user-permissions mr mr-type)
               "group" (mr-permissions/query-list-group-permissions mr mr-type))]
    (sd/response_ok data)))

(defn- handle_list-perms
  [req]
  (let [mr (-> req :media-resource)
        mr-type (mr-table-type mr)
        e-data (get-entity-perms mr)
        ; responsible user
        ; TODO delegations
        ;a-data (mr-permissions/query-list-api-client-permissions mr mr-type)
        u-data (mr-permissions/query-list-user-permissions mr mr-type)
        g-data (mr-permissions/query-list-group-permissions mr mr-type)]
    (sd/response_ok {;:api-clients a-data
                     :media-resource e-data
                     :users u-data 
                     :groups g-data})))


(def valid_permission_names
  ["get_metadata_and_previews"
   "get_full_size"
   "edit_metadata"
   "edit_permissions"])

(def valid_permission_keys
  (map keyword valid_permission_names))

(def schema_update-collection-perms
  {(s/optional-key :get_metadata_and_previews) s/Bool
   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
   (s/optional-key :clipboard_user_id) (s/maybe s/Uuid)
   (s/optional-key :workflow_id) (s/maybe s/Uuid)
   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)
   })

(def schema_create-collection-user-permission
  {:get_metadata_and_previews s/Bool
   :edit_metadata_and_relations s/Bool
   :edit_permissions s/Bool})

(def schema_create-collection-group-permission
  {:get_metadata_and_previews s/Bool
   :edit_metadata_and_relations s/Bool
   })

(def schema_update-media-entry-perms
  {(s/optional-key :get_metadata_and_previews) s/Bool
   (s/optional-key :get_full_size) s/Bool
   (s/optional-key :responsible_user_id) (s/maybe s/Uuid)
   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})

(def schema_create-media-entry-user-permission
  {:get_metadata_and_previews s/Bool
   :get_full_size s/Bool
   :edit_metadata s/Bool
   :edit_permissions s/Bool})

(def schema_create-media-entry-group-permission
  {:get_metadata_and_previews s/Bool
   :get_full_size s/Bool
   :edit_metadata s/Bool
   })


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


   ; TODO patch for entity perms
    ; get_metadata_and_previews
    ; get_full_size
    ; responsible_user_id
    ; responsible_delegation_id
    ; TODO beware to let not update perm fields in media-entry or collection patch/update
   
   ["/resource"
    {:get
     {:summary "Query media-entry permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_get_entity_perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}}

     :put
     {:summary "Update media-entry entity permissions"
      :description (str "Valid perm_name values are" valid_permission_names)
      :handler handle_update-ressource-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-edit-permissions]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}
                   :body schema_update-media-entry-perms}}}]
   
   ["/resource/:perm_name/:perm_val"
    {:put {:summary "Update media-entry entity permissions"
           :description (str "Valid perm_name values are" valid_permission_names)
           :handler handle_update-resource-perm-value
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid
                               :perm_name (s/enum "get_metadata_and_previews"
                                                  "get_full_size"
                                                  "is_published")
                               :perm_val s/Bool}}}}]

   ;["/api-client"
   ; {:get
   ;  {:summary "List media-entry api-client permissions."
   ;   :swagger {:produces "application/json"}
   ;   :content-type "application/json"
   ;   :handler handle_list-api-client-perms
   ;   :middleware [sd/ring-wrap-add-media-resource
   ;                sd/ring-wrap-authorization-view]
   ;   :coercion reitit.coercion.schema/coercion
   ;   :parameters {:path {:media_entry_id s/Uuid}}}}]

   ["/user"
    {:get
     {:summary "Query media-entry user permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-user-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Uuid}}}
     ;TODO patch entity perms
     }]

   ["/user/:user_id"
    {:get {:summary "Get media-entry user permissions."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_get-user-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid
                               :user_id s/Uuid}}}
     
     :post {:summary "Create media-entry user permissions."
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :handler handle_create-user-perms
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :user_id s/Uuid}
                         :body schema_create-media-entry-user-permission}}
     
     :delete {:summary "Delete media-entry user permissions."
                    :swagger {:produces "application/json"}
                    :content-type "application/json"
                    :handler handle_delete-user-perms
                    :middleware [sd/ring-wrap-add-media-resource
                                 sd/ring-wrap-authorization-edit-permissions]
                    :coercion reitit.coercion.schema/coercion
                    :parameters {:path {:media_entry_id s/Uuid
                                        :user_id s/Uuid}}}
     
     }]

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
                               :perm_val s/Bool}}}}]

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

   ["/group/:group_id"
    {:get {:summary "Get media-entry group permissions."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_get-group-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid
                               :group_id s/Uuid}}}

     :post {:summary "Create media-entry group permissions."
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :handler handle_create-group-perms
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :group_id s/Uuid}
                         :body schema_create-media-entry-group-permission}}

     :delete {:summary "Delete media-entry group permissions."
              :swagger {:produces "application/json"}
              :content-type "application/json"
              :handler handle_delete-group-perms
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:media_entry_id s/Uuid
                                  :group_id s/Uuid}}}}]

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
                               :perm_val s/Bool}}}}]
   
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
      :parameters {:path {:collection_id s/Uuid}}}
   
     }]

   ; TODO check for valid entities
   ["/resource"
    {:get
     {:summary "Query collection permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_get_entity_perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}}
     
     :put 
     {:summary "Update collection entity permissions"
      :description (str "Valid perm_name values are" valid_permission_names)
      :handler handle_update-ressource-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-edit-permissions]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}
                   :body schema_update-collection-perms}}}]
   
   ;TODO patch entity perms
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
                          :perm_val s/Bool}}}}]
   
   
   
   ;["/api-client"
   ; {:get
   ;  {:summary "Query collection permissions."
   ;   :swagger {:produces "application/json"}
   ;   :content-type "application/json"
   ;   :handler handle_list-api-client-perms
   ;   :middleware [sd/ring-wrap-add-media-resource
   ;                sd/ring-wrap-authorization-view]
   ;   :coercion reitit.coercion.schema/coercion
   ;   :parameters {:path {:collection_id s/Uuid}}}}]

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

     :post {:summary "Create collection user permissions."
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :handler handle_create-user-perms
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :user_id s/Uuid}
                         :body schema_create-collection-user-permission}}
     
     :delete {:summary "Delete collection user permissions."
              :swagger {:produces "application/json"}
              :content-type "application/json"
              :handler handle_delete-user-perms
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:collection_id s/Uuid
                                  :user_id s/Uuid}}}
     }]

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
                               :perm_val s/Bool}}}}]

    ; TODO delegations ?
    ; TODO workflows ?


   ["/group"
    {:get
     {:summary "Query collection permissions."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_list-group-perms
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}}}]

   ["/group/:group_id"
    {:get {:summary "Get collection group permissions."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_get-group-perms
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid
                               :group_id s/Uuid}}}
     
     :post {:summary "Create collection group permissions."
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :handler handle_create-group-perms
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :group_id s/Uuid}
                         :body schema_create-collection-group-permission}}

     :delete {:summary "Delete collection group permissions."
              :swagger {:produces "application/json"}
              :content-type "application/json"
              :handler handle_delete-group-perms
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:collection_id s/Uuid
                                  :group_id s/Uuid}}}}]

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
                               :perm_val s/Bool}}}}]])

