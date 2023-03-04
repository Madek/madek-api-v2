(ns madek.api.resources
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [compojure.core :as cpj]
            [logbug.catcher :as catcher]
            [logbug.debug :as debug]
            [madek.api.authentication :as authentication]
            [madek.api.authorization :refer [authorized?]]
            [madek.api.resources.auth-info :as auth-info]
            [madek.api.resources.collection-media-entry-arcs :as collection-media-entry-arcs]
            [madek.api.resources.collections :as collections]
            [madek.api.resources.collections.collection :as rcollection]
            [madek.api.resources.groups :as groups]
            [madek.api.resources.groups.users :as group-users]
            [madek.api.resources.keywords :as keywords]
            [madek.api.resources.keywords.keyword :as keyword]
            [madek.api.resources.media-entries :as media-entries]
            [madek.api.resources.media-entries.media-entry :refer [get-media-entry-for-preview]]
            [madek.api.resources.media-files :as media-files]
            [madek.api.resources.media-files.media-file :as media-files.file]
            [madek.api.resources.media-files.authorization :as media-files.auth]
            [madek.api.resources.meta-data :as meta-data]
            [madek.api.resources.meta-data.index :as meta-data-index]
            [madek.api.resources.meta-data.meta-datum :as meta-datum]
            [madek.api.resources.meta-keys :as meta-keys]
            [madek.api.resources.meta-keys.index :as meta-keys-index]
            [madek.api.resources.meta-keys.meta-key :as meta-key]
            [madek.api.resources.people :as people]
            [madek.api.resources.previews :as previews]
            [madek.api.resources.previews.preview :as preview]
            [madek.api.resources.roles :as roles]
            [madek.api.resources.roles.index :as roles-index]
            [madek.api.resources.roles.role :as roles-role]
            [madek.api.resources.shared :as shared]
            [madek.api.resources.users :as users]
            [madek.api.resources.vocabularies :as vocabularies]
            [madek.api.resources.vocabularies.index :as vocabularies-index]
            [madek.api.resources.vocabularies.vocabulary :as vocabularies-get]
            [madek.api.semver :as semver]
            [madek.api.utils.auth :as auth]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [reitit.coercion.schema]
            [schema.core :as s]
            [cheshire.core :as cheshire]
            ))


;### wrap media resource ######################################################

(defn- get-media-resource
  ([request]
   (catcher/with-logging {}
     (or (get-media-resource request :media_entry_id "media_entries" "MediaEntry")
         (get-media-resource request :collection_id "collections" "Collection"))))
  ([request id-key table-name type]
   (when-let [id (or (-> request :params id-key) (-> request :parameters :path id-key))]
     (logging/info "get-media-resource" "\nid\n" id)
     (when-let [resource (-> (jdbc/query (get-ds)
                                         [(str "SELECT * FROM " table-name "
                                               WHERE id = ?") id]) first)]
         (assoc resource :type type :table-name table-name)))))

(def ^:private get-media-resource-dispatcher
  (cpj/routes
    (cpj/GET "/media-entries/:media_entry_id*" _ get-media-resource)
    (cpj/GET "/collections/:collection_id*" _ get-media-resource)
    (cpj/GET "/previews/:preview_id*" _ #(assoc (get-media-entry-for-preview %)
                                                :type "MediaEntry"
                                                :table-name "media_entries"))))

(defn- add-media-resource [request handler]
  (if-let [media-resource (get-media-resource-dispatcher request)]
    (let [request-with-media-resource (assoc request :media-resource media-resource)]
      (handler request-with-media-resource))
    (let [response-for-not-found-media-resource {:status 404}]
      ((cpj/routes
         (cpj/ANY "/media-entries/:id*" _ response-for-not-found-media-resource)
         (cpj/ANY "/collections/:id*" _ response-for-not-found-media-resource)
         (cpj/ANY "*" _ handler)) request))))

(defn- ring-add-media-resource-preview [request handler]
  (if-let [media-resource (get-media-entry-for-preview request)] 
    (let [mmr (assoc media-resource :type "MediaEntry" :table-name "media_entries")
          request-with-media-resource (assoc request :media-resource mmr)]
      (handler request-with-media-resource))
    {:status 404 :body {:message "No media-resource for preview"}}))

(defn- ring-add-media-resource [request handler]
  (if-let [media-resource (get-media-resource request)]
    (let [request-with-media-resource (assoc request :media-resource media-resource)]
      ;(logging/info "ring-add-media-resource" "\nmedia-resource\n" media-resource)
      (handler request-with-media-resource))
    {:status 404}))

(defn- wrap-add-media-resource [handler]
  (fn [request]
    (add-media-resource request handler)))


(defn- ring-wrap-add-media-resource-preview [handler]
  (fn [request]
    (ring-add-media-resource-preview request handler)))

(defn- ring-wrap-add-media-resource [handler]
  (fn [request]
    (ring-add-media-resource request handler)))

; TODO move to params coercion
(defn- wrap-check-uuid-syntax-conformity [handler]
  (letfn [(return-422-if-not-uuid-conform [request]
            (if (re-find shared/uuid-matcher (-> request :params :resource_id))
              handler
              {:status 422
               :body {:message "The format of the id must be that of an UUID!"}}))]
    (cpj/routes
      (cpj/ANY "/media-entries/:resource_id*" _ return-422-if-not-uuid-conform)
      (cpj/ANY "/collections/:resource_id*" _ return-422-if-not-uuid-conform)
      (cpj/ANY "/previews/:resource_id*" _ return-422-if-not-uuid-conform)
      (cpj/ANY "/media-files/:resource_id*" _ return-422-if-not-uuid-conform)
      (cpj/ANY "/meta-data/:resource_id*" _ return-422-if-not-uuid-conform)
      (cpj/ANY "/meta-data-roles/:resource_id*" _ return-422-if-not-uuid-conform)
      (cpj/ANY "/keywords/:resource_id*" _ return-422-if-not-uuid-conform)
      (cpj/ANY "*" _ handler))))

;### wrap meta-datum with media-resource#######################################

(defn query-meta-datum [request]
  (let [id (or (-> request :params :meta_datum_id) (-> request :parameters :path :meta_datum_id))]
    (logging/info "query-meta-datum" "\nid\n" id)
    (or (-> (jdbc/query (get-ds)
                        [(str "SELECT * FROM meta_data "
                              "WHERE id = ? ") id])
            first)
        (throw (IllegalStateException. (str "We expected to find a MetaDatum for "
                                            id " but did not."))))))

(defn- query-media-resource-for-meta-datum [meta-datum]
  (or (when-let [id (:media_entry_id meta-datum)]
        (get-media-resource {:params {:media_entry_id id}}
                            :media_entry_id "media_entries" "MediaEntry"))
      (when-let [id (:collection_id meta-datum)]
        (get-media-resource {:params {:collection_id id}}
                            :collection_id "collections" "Collection"))
      (throw (IllegalStateException. (str "Getting the resource for "
                                          meta-datum "
                                          is not implemented yet.")))))

(def ^:private query-meta-datum-dispatcher
  (cpj/routes
    (cpj/GET "/meta-data/:meta_datum_id*" [meta_datum_id] query-meta-datum)))

(defn- add-meta-datum-with-media-resource [request handler]
  (if-let [meta-datum (query-meta-datum-dispatcher request)]
    (let [media-resource (query-media-resource-for-meta-datum meta-datum)]
      (logging/info "add-meta-datum-with-media-resource" "\nmeta-datum\n" meta-datum "\nmedia-resource\n" media-resource)
      (handler (assoc request
                      :meta-datum meta-datum
                      :media-resource media-resource)))
    (handler request)))

(defn- ring-add-meta-datum-with-media-resource [request handler]
  (if-let [meta-datum (query-meta-datum request)]
    (let [media-resource (query-media-resource-for-meta-datum meta-datum)]
      (logging/info "add-meta-datum-with-media-resource" "\nmeta-datum\n" meta-datum "\nmedia-resource\n" media-resource)
      (handler (assoc request
                      :meta-datum meta-datum
                      :media-resource media-resource)))
    (handler request)))

(defn- wrap-add-meta-datum-with-media-resource [handler]
  (fn [request]
    (add-meta-datum-with-media-resource request handler)))

(defn- ring-wrap-add-meta-datum-with-media-resource [handler]
  (fn [request]
    (ring-add-meta-datum-with-media-resource request handler)))


;### wrap authorize ###########################################################

(defn- public? [resource]
  (-> resource :get_metadata_and_previews boolean))

(defn- authorize-request-for-handler [request handler]
  (if-let [media-resource (:media-resource request)]
    (if (public? media-resource)
      (handler request)
      (if-let [auth-entity (:authenticated-entity request)]
        (if (authorized? auth-entity media-resource)
          (handler request)
          {:status 403 :body {:message "Not authorized for media-resource"}})
        {:status 401 :body {:message "Not authorized"}}))
    (let [response  {:status 500 :body "No media-resource in request."}]
      (logging/warn 'authorize-request-for-handler response [request handler])
      response)))

(defn- dispatch-authorize [request handler]
  ((cpj/routes
     (cpj/GET "/media-entries/:media_entry_id*" _ #(authorize-request-for-handler % handler))
     (cpj/GET "/collections/:collection_id*" _ #(authorize-request-for-handler % handler))
     (cpj/GET "/meta-data/:meta_datum_id*" _ #(authorize-request-for-handler % handler))
     (cpj/GET "/previews/:preview_id*" _ #(authorize-request-for-handler % handler))
     (cpj/ANY "*" _ handler)) request))

(defn- wrap-authorization [handler]
  (fn [request]
    (dispatch-authorize request handler)))

(defn- ring-wrap-authorization [handler]
  (fn [request]
    (authorize-request-for-handler request handler)))

;### a few redirects ##########################################################

(defn redirect-to-meta-datum-data-stream
  [{{media-entry-id :media_entry_id
     meta-key-id :meta_key_id} :route-params
    context :context :as request}]
  (logging/debug request)
  (if-let [meta-data-id (-> (jdbc/query (get-ds)
                                        [(str "SELECT id FROM meta_data "
                                              "WHERE media_entry_id = ? "
                                              "AND meta_key_id = ?") media-entry-id meta-key-id])
                            first :id)]
    (ring.util.response/redirect (str context "/meta-data/" meta-data-id "/data-stream"))))

(defn redirect-to-media-file-data-stream
  [{{media-entry-id :media_entry_id} :route-params
    context :context :as request}]
  (logging/debug request)
  (if-let [media-file-id (-> (jdbc/query (get-ds)
                                         [(str "SELECT id FROM media_files "
                                               "WHERE media_entry_id = ? ") media-entry-id])
                             first :id)]
    (ring.util.response/redirect (str context "/media-files/" media-file-id "/data-stream"))))


;### ##### ####################################################################

(defn wrap-api-routes [default-handler]
  (-> (cpj/routes
        (cpj/GET "/media-entries/:media_entry_id/meta-data/:meta_key_id/data-stream" _ redirect-to-meta-datum-data-stream)
        (cpj/GET "/media-entries/:media_entry_id/media-file/data-stream" _ redirect-to-media-file-data-stream)
        (cpj/GET "/auth-info" _ auth-info/routes)
        (cpj/ANY "/:media_resource_type/:id/meta-data/" _ meta-data/routes)
        (cpj/ANY "/collection-media-entry-arcs/*" _ collection-media-entry-arcs/routes)
        (cpj/ANY "/collections*" _ collections/routes)
        (cpj/ANY "/groups/*" _ groups/routes)
        (cpj/ANY "/keywords/:keyword_id*" _ keywords/routes)
        (cpj/ANY "/media-entries*" _ media-entries/routes)
        (cpj/ANY "/media-files/:media_file_id*" _ media-files/routes)
        (cpj/ANY "/meta-data/:meta_datum_id*" _ meta-data/routes)
        (cpj/ANY "/meta-data-roles/:meta_datum_id" _ meta-data/routes)
        (cpj/ANY "/meta-keys/*" _ meta-keys/routes)
        (cpj/ANY "/people/*" _ people/routes)
        (cpj/ANY "/roles/*" _ roles/routes)
        (cpj/ANY "/previews/:preview_id*" _ previews/routes)
        (cpj/ANY "/users/*" _ users/routes)
        (cpj/ANY "/vocabularies/*" _ vocabularies/routes)
        (cpj/ANY "*" _ default-handler))
        
      wrap-authorization
      wrap-add-media-resource
      wrap-add-meta-datum-with-media-resource
      wrap-check-uuid-syntax-conformity))
      



(def root
  {:status 200
   :body {:api-version (semver/get-semver)
          :message "Hello Madek User!"}})

(defn try-as-json [value]
  (try (cheshire/parse-string value)
       (catch Exception _
         value)))

(defn- *ring-wrap-parse-json-query-parameters [request handler]
  ;((assoc-in request [:query-params2] (-> request :parameters :query))
  (handler (assoc request :query-params
                  (->> request :query-params
                       (map (fn [[k v]] [k (try-as-json v)]))
                       (into {})))))

(defn- ring-wrap-parse-json-query-parameters [handler]
  (fn [request]
    (*ring-wrap-parse-json-query-parameters request handler)))

;(defn wrap-req-paramters-path-2-params [handler param-key]
;  (fn [request]
;     (let [pp (-> request :parameters :path param-key)]
;       (assoc-in request [:params] pp))))

(def ring-routes
  ["/api" {:middleware [authentication/wrap]}
   ["/" {:get (constantly root)}]
   ["/auth-info" {:get {:handler auth-info/auth-info}}]

   ; collections
   ["/collections"

    ["/" {:get {:handler collections/handle_get-index
                :summary "Get collection ids"
                :description "Get collection id list."
                :swagger {:produces "application/json"}
                ;:middleware [ring-wrap-add-media-resource ]
                ;:middleware [ring-wrap-add-media-resource ring-wrap-authorization]
                :parameters {:query {(s/optional-key :page) s/Str
                                     (s/optional-key :collection_id) s/Str
                                     (s/optional-key :order) s/Str
                                     (s/optional-key :me_get_metadata_and_previews) s/Bool
                                     (s/optional-key :public_get_metadata_and_previews) s/Bool
                                     (s/optional-key :me_get_full_size) s/Bool
                                     }
                             }
                :coercion reitit.coercion.schema/coercion
                :responses {200 {:body {:collections [{:id s/Uuid :created_at s/Inst}]}}}}}]

    ["/:collection_id" {:get {:handler rcollection/handle_get-collection
                              :middleware [ring-wrap-add-media-resource ring-wrap-authorization]
                              :summary "Get collection for id."
                              :swagger {:produces "application/json"}
                              :coercion reitit.coercion.schema/coercion
                              :parameters {:path {:collection_id s/Str}}
                              :responses {200 {:body s/Any}} ; TODO response coercion
                              }}]]


   ["/collection-media-entry-arcs"
    ["/" {:get {:summary "Get collection media-entry arcs."
                :handler collection-media-entry-arcs/arcs
                :swagger {:produces "application/json"}
                :coercion reitit.coercion.schema/coercion

                :responses {200 {:body s/Any}} ; TODO response coercion
                }}]
    ["/:id" {:get {:summary "Get collection media-entry arcs."
                   :handler collection-media-entry-arcs/arc
                   :swagger {:produces "application/json"}
                   :coercion reitit.coercion.schema/coercion
                   :parameters {:path {:id s/Str}}
                   :responses {200 {:body s/Any}} ; TODO response coercion
                   }}]]

   ; groups/ring-routes
   ["/groups"
    ["/" {:get {:summary "Get all group ids"
                :description "Get list of group ids. Paging is used as you get a limit of 100 entries."
                :handler groups/index
                :middleware [auth/wrap-authorize-admin!]
                :swagger {:produces "application/json"}
                :parameters {:query {(s/optional-key :page) s/Int}}
                ;:content-type "application/json"
                ;:accept "application/json"
                :coercion reitit.coercion.schema/coercion
                :responses {200 {:body {:groups [{:id s/Uuid}]}}}}

          :post {:summary "Create a group"
                 :description "Create a group."
                 :handler groups/handle_create-group
                 :middleware [auth/wrap-authorize-admin!]
                 :swagger {:produces "application/json" :consumes "application/json"}
                 :content-type "application/json"
                 :accept "application/json"
                 :coercion reitit.coercion.schema/coercion
                 :parameters {:body groups/schema_import-group}
                 :responses {201 {:body groups/schema_export-group} ;{:id s/Uuid}} ; api1 returns created data
                             500 {:body {:msg s/Any}} ; TODO error handling
                             }
                 }}]

    ["/:id" {:get {:summary "Get group by id"
                   :description "Get group by id. Returns 404, if no such group exists."
                   :swagger {:produces "application/json"}
                   :content-type "application/json"
                   :accept "application/json"
                   :handler groups/handle_get-group
                   :middleware [auth/wrap-authorize-admin!]
                   :coercion reitit.coercion.schema/coercion
                   :parameters {:path {:id s/Str}}
                   :responses {200 {:body groups/schema_export-group}
                               404 {:body s/Str}}}

             :delete {:summary "Deletes a group by id"
                      :description "Delete a group by id"
                      :handler groups/handle_delete-group
                      :middleware [auth/wrap-authorize-admin!]
                      :coercion reitit.coercion.schema/coercion
                      :parameters {:path {:id s/Str}}
                      :responses {403 {:body s/Any}
                                  204 {:body s/Any}}}
             :patch {:summary "Get group by id"
                     :description "Get group by id. Returns 404, if no such group exists."
                     :swagger {:produces "application/json"}
                     :content-type "application/json"
                     :accept "application/json"
                     :handler groups/handle_update-group
                     :middleware [auth/wrap-authorize-admin!]
                     :coercion reitit.coercion.schema/coercion
                     :parameters {:path {:id s/Str} 
                                  :body groups/schema_update-group}
                     :responses {200 {:body s/Any };groups/schema_export-group}
                                 404 {:body s/Any}} ; TODO error handling
                     }
             }]]
     ; groups-users/ring-routes 
   ["/groups/:group-id/users"
    ["/" {:get {:summary "Get group users by id"
                :description "Get group users by id."
                :swagger {:produces "application/json"}
                :content-type "application/json"

                :handler group-users/handle_get-group-users
                :middleware [auth/wrap-authorize-admin!]
                :coercion reitit.coercion.schema/coercion
                :parameters {:path {:group-id s/Str}}
                :responses {200 {:body groups/schema_export-group} ; TODO schema
                            404 {:body s/Str}}
                }

          ; TODO works with tests, but not with the swagger ui
          :put {:summary "Update group users by group-id and list of users."
                :description "Update group users by group-id and list of users."
                :swagger {:consumes "application/json" :produces "application/json"}
                :content-type "application/json"
                :accept "application/json"
                :handler group-users/handle_update-group-users
                :coercion reitit.coercion.schema/coercion
                :parameters {:path {:group-id s/Str}
                             :body {:users 
                                    [ s/Any]
                                    ;[{:id s/Str
                                    ;  :institutional_id s/Str
                                    ;  :email s/Str}]
                                    }}
                             ;:body {:users [s/Any]}}
                :responses {200 {:body s/Any} ;groups/schema_export-group}
                            404 {:body s/Str}}
                }
          }]
    ["/:user-id" {:get {:summary "Get group user by group-id and user-id"
                        :description "Get group user by group-id and user-id."
                        :swagger {:produces "application/json"}
                        :content-type "application/json"

                        :handler group-users/handle_get-group-user
                        :middleware [auth/wrap-authorize-admin!]
                        :coercion reitit.coercion.schema/coercion
                        :parameters {:path {:group-id s/Str :user-id s/Str}}
                        :responses {200 {:body group-users/schema_export-group-user}
                                    404 {:body s/Any} ; TODO error handling
                                    }}

                  :put {:summary "Get group user by group-id and user-id"
                        :description "Get group user by group-id and user-id."
                        :swagger {:produces "application/json"}
                        :content-type "application/json"
                        :handler group-users/handle_add-group-user
                        :middleware [auth/wrap-authorize-admin!]
                        :coercion reitit.coercion.schema/coercion
                        :parameters {:path {:group-id s/Str :user-id s/Str}}
                        :responses {200 {:body group-users/schema_export-group-user-simple}
                                    404 {:body s/Any} ; TODO error handling
                                    }}

                  :delete {:summary "Deletes a group-user by group-id and user-id"
                           :description "Delete a group-user by group-id and user-id."
                           ;:swagger {:produces "application/json"}
                           ;:content-type "application/json"
                           :handler group-users/handle_delete-group-user
                           :middleware [auth/wrap-authorize-admin!]
                           :coercion reitit.coercion.schema/coercion
                           :parameters {:path {:group-id s/Str :user-id s/Str}}
                           :responses {204 {:body s/Any}
                                       406 {:body s/Str} ; TODO error handling
                                       }}}]]
    ;keyword/ring-routes
   ["/keywords"
    ["/" {:get {:handler keyword/handle_query-keywords
                :summary "Get all keywords ids"
                :description "Get keywords id list. TODO query parameters and paging. TODO get full data."
                :coercion reitit.coercion.schema/coercion
                :responses {200 {:body {:keywords [{:id s/Uuid}]}}}}}]
    ["/:id" {:get {:handler keyword/handle_get-keyword

                   :summary "Get keyword for id"
                   :description "Get keyword for id. Returns 404, if no such keyword exists."
                   :coercion reitit.coercion.schema/coercion
                   :parameters {:path {:id s/Uuid}}
                   :responses {200 {:body keyword/schema_export_keyword}
                               404 {:body {:msg s/Str}}}}}]]

   ;links

   ;media_entries
   ["/media-entries"
    ["/" {:get {:summary "Get list media-entries."
                :swagger {:produces "application/json"}
                :content-type "application/json"
                :handler media-entries/handle_get-index
                :middleware [ring-wrap-parse-json-query-parameters]
                :coercion reitit.coercion.schema/coercion
                :parameters {:query {(s/optional-key :collection_id) s/Str
                                     (s/optional-key :order) s/Any ;(s/enum "desc" "asc" "title_asc" "title_desc" "last_change" "manual_asc" "manual_desc" "stored_in_collection")
                                     (s/optional-key :filter_by) s/Any
                                     (s/optional-key :me_get_metadata_and_previews) s/Bool
                                     (s/optional-key :me_get_full_size) s/Bool
                                     (s/optional-key :page) s/Str
                                     }}}}]
    
    ["/:media_entry_id" {:get {:summary "Get media-entry for id."
                   :swagger {:produces "application/json"}
                   :content-type "application/json"
                   :handler media-entries/handle_get-media-entry
                   :middleware [ring-wrap-add-media-resource ring-wrap-authorization]
                   :coercion reitit.coercion.schema/coercion
                   :parameters {:path {:media_entry_id s/Str}}}}]]

   ;media_files
   ["/media-files"
    ["/:media_file_id" {:get {:summary "Get media-file for id."
                              :swagger {:produces "application/json"}
                              :content-type "application/json"
                              :handler media-files.file/get-media-file
                              :middleware [media-files/wrap-find-and-add-media-file
                                           media-files.auth/ring-wrap-authorize-metadata-and-previews]
                              :coercion reitit.coercion.schema/coercion
                              :parameters {:path {:media_file_id s/Str}}}}]
    
    ["/:media_file_id/data-stream" {:get {:summary "Get media-file data-stream for id."
                                          :handler media-files.file/get-media-file-data-stream
                                          :middleware [media-files/wrap-find-and-add-media-file
                                                       media-files.auth/ring-wrap-authorize-full_size]
                                          :coercion reitit.coercion.schema/coercion
                                          :parameters {:path {:media_file_id s/Str}}}}]]

   ;meta_data
   ["/collections/:collection_id/meta-data" {:get {:summary "Get meta-data for collection."
                                                   :handler meta-data-index/get-index
                                                   :middleware [ring-wrap-add-media-resource ring-wrap-authorization]
                                                   ; TODO 401s test fails
                                                   :coercion reitit.coercion.schema/coercion
                                                   :parameters {:path {:collection_id s/Str}}
                                                   :responses {200 {:body s/Any}}}}]

   ["/media-entries/:media_entry_id/meta-data" {:get {:summary "Get meta-data for media-entry."
                                                      :handler meta-data-index/get-index
                                                      ; TODO 401s test fails
                                                      :middleware [ring-wrap-add-media-resource ring-wrap-authorization]
                                                      :coercion reitit.coercion.schema/coercion
                                                      :parameters {:path {:media_entry_id s/Str}}
                                                      :responses {200 {:body s/Any}}}}]

   ["/meta-data"
    ["/:meta_datum_id" {:get {:handler meta-datum/get-meta-datum
                              :middleware [ring-wrap-add-meta-datum-with-media-resource ring-wrap-authorization]
                              :summary "Get meta-data for id"
                              :description "Get meta-data for id. TODO: should return 404, if no such meta-data role exists."
                              :coercion reitit.coercion.schema/coercion
                              :parameters {:path {:meta_datum_id s/Str}}
                              ; TODO coercion
                              :responses {200 {:body s/Any}
                                          401 {:body s/Any}
                                          403 {:body s/Any}
                                          500 {:body s/Any}}}}]
    ["/:meta_datum_id/data-stream" {:get {:handler meta-datum/get-meta-datum-data-stream
                                          ; TODO json meta-data: fix response conversion error
                                          :middleware [ring-wrap-add-meta-datum-with-media-resource ring-wrap-authorization]
                                          :summary "Get meta-data data-stream."
                                          :description "Get meta-data data-stream."
                                          :coercion reitit.coercion.schema/coercion
                                          :parameters {:path {:meta_datum_id s/Str}}
                                          ;:responses {200 {:body s/Any}
                                                      ;422 {:body s/Any}}
                                          }}]] 


   ["/meta-data-roles/:meta_datum_id" {:get {:handler meta-datum/handle_get-meta-datum-role
                                             :summary "Get meta-data role for id"
                                             :description "Get meta-data role for id. TODO: should return 404, if no such meta-data role exists."
                                             :coercion reitit.coercion.schema/coercion
                                             :parameters {:path {:meta_datum_id s/Str}}
                                             :responses {200 {:body s/Any}}}}]

   ;meta_keys
   ["/meta-keys"
    ["/" {:get {:summary "Get all meta-key ids"
                :description "Get list of meta-key ids. Paging is used as you get a limit of 100 entries."
                :handler meta-keys-index/get-index
                :swagger {:produces "application/json"}
                :parameters {:query {(s/optional-key :page) s/Int}}
                ;:content-type "application/json"
                ;:accept "application/json"
                :coercion reitit.coercion.schema/coercion
                :responses {200 {:body {:people [{:id s/Uuid}]}}}}}]

    ["/:id" {:get {:summary "Get meta-key by id"
                   :description "Get meta-key by id. Returns 404, if no such person exists. TODO query params."
                   :swagger {:produces "application/json"}
                   :content-type "application/json"
                   :accept "application/json"
                   :handler meta-key/get-meta-key
                   :coercion reitit.coercion.schema/coercion
                   :parameters {:path {:id s/Str}} ; TODO test valid id
                   :responses {200 {:body s/Str}
                               404 {:body {:message s/Str}}
                               422 {:body {:message s/Str}}}}}]]

   ;people/ring-routes
   ["/people"
    ["/" {:get {:summary "Get all people ids"
                :description "Get list of peoples ids. Paging is used as you get a limit of 100 entries."
                :handler people/index
                :swagger {:produces "application/json"}
                :parameters {:query {(s/optional-key :page) s/Int}}
                ;:content-type "application/json"
                ;:accept "application/json"
                :coercion reitit.coercion.schema/coercion
                :responses {200 {:body {:people [{:id s/Uuid}]}}}}

          :post {:summary "Create a person"
                 :description "Create a person.\n The \nThe [subtype] has to be one of [Person, ...]. \nAt least one of [first_name, last_name, description] must have a value."
                 :handler people/handle_create-person
                 :middleware [auth/wrap-authorize-admin!]
                 :swagger {:produces "application/json" :consumes "application/json"}
                 :content-type "application/json"
                 :accept "application/json"
                 :coercion reitit.coercion.schema/coercion
                 :parameters {:body people/schema_import_person}
                 :responses {201 {:body people/schema_import_person_result} ;{:id s/Uuid}} ; api1 returns created data
                             500 {:body {:msg s/Any}} ; TODO error handling
                             400 {:body {:msg s/Any}}
                             401 {:body {:msg s/Any}}
                             403 {:body {:msg s/Any}}}}}]

    ["/:id" {:get {:summary "Get person by id"
                   :description "Get person by id. Returns 404, if no such person exists. TODO query params."
                   :swagger {:produces "application/json"}
                   :content-type "application/json"
                   :accept "application/json"
                   :handler people/handle_get-person
                   :coercion reitit.coercion.schema/coercion
                   :parameters {:path {:id s/Str}}
                   :responses {200 {:body people/schema_export_person}
                               404 {:body s/Str}}}

             :patch {:summary "Updates entities fields"
                     :description "Updates the entities fields"
                     :swagger {:consumes "application/json" :produces "application/json"}
                     :content-type "application/json"
                     :accept "application/json"
                     :handler people/handle_patch-person
                     :coercion reitit.coercion.schema/coercion
                     :parameters {:path {:id s/Str} :body people/schema_update_person}
                     :responses {200 {:body s/Any} ;people/schema_export_person}
                                 404 {:body s/Str}}}

             :delete {:summary "Deletes a person by id"
                      :description "Delete a person by id"
                      :swagger {:produces "application/json"}
                      :content-type "application/json"
                      :handler people/handle_delete-person
                      :middleware [auth/wrap-authorize-admin!]
                      :coercion reitit.coercion.schema/coercion
                      :parameters {:path {:id s/Uuid}}
                      :responses {403 {:body s/Any}
                                  204 {:body s/Any}}}}]]

    ; preview
   ["/previews"
    ["/:preview_id" {:get {:summary "Get preview for id."
                           :swagger {:produces "application/json"}
                           :content-type "application/json"
                           :handler preview/get-preview
                           :middleware [previews/ring-wrap-find-and-add-preview
                                        ring-wrap-add-media-resource-preview 
                                        ring-wrap-authorization
                                        ]
                           :coercion reitit.coercion.schema/coercion
                           :parameters {:path {:preview_id s/Str}}
                           }}]

    ["/:preview_id/data-stream" {:get {:summary "Get preview data-stream for id."
                                       :handler preview/get-preview-file-data-stream
                                       :middleware [previews/ring-wrap-find-and-add-preview
                                                    ring-wrap-add-media-resource-preview
                                                    ring-wrap-authorization
                                                    ]
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:preview_id s/Uuid}}}}]
    ]

    ; roles/ring-routes
    ; TODO coercion 
   ["/roles"
    ["/" {:get {:summary "Get list of roles."
                :description "Get list of roles."
                :handler roles-index/get-index
                :swagger {:produces "application/json"}
                :parameters {:query {(s/optional-key :page) s/Int}}
                :content-type "application/json"
                ;:accept "application/json"
                :coercion reitit.coercion.schema/coercion}}]
                ;:responses {200 {:body {:people [{:id s/Uuid}]}}}

    ["/:id" {:get {:summary "Get role by id"
                   :description "Get a role by id. Returns 404, if no such role exists."
                   :swagger {:produces "application/json"}
                   :content-type "application/json"
                   ;:accept "application/json"
                   :handler roles-role/handle_get-role
                   :coercion reitit.coercion.schema/coercion
                   :parameters {:path {:id s/Str}}
                   :responses {200 {:body roles-role/schema_export-role}
                               404 {:body s/Str}}}}]]

    ; users/ring-routes
   ["/users"
    ["/" {:get {
                :summary "Get list of users ids."
                :description "Get list of users ids."
                :swagger {:produces "application/json"}
                :parameters {:query {(s/optional-key :page) s/Int}}
                :content-type "application/json"
                :handler users/index
                :middleware [auth/wrap-authorize-admin!]
                :coercion reitit.coercion.schema/coercion
                :responses {200 {:body {:users [{:id s/Uuid}]}}}
                }
          :post {:summary "Get list of users ids."
                 :description "Get list of users ids."
                 :swagger {:consumes "application/json" :produces "application/json"}
                 :content-type "application/json"
                 :accept "application/json"
                 :handler users/handle_create-user
                 :middleware [auth/wrap-authorize-admin!]
                 :coercion reitit.coercion.schema/coercion
                 :parameters {:body users/schema_update_user}
                 :responses {201 {:body users/schema_create_user_result}
                             406 {:body s/Any} ; TODO error handling
                             }
                 }
          }
     ]
    ["/:id" {:get {:middleware [auth/wrap-authorize-admin!]
                   :summary "Get user by id"
                   :description "Get a user by id. Returns 404, if no such users exists."
                   :swagger {:produces "application/json"}
                   :coercion reitit.coercion.schema/coercion
                   :content-type "application/json"
                   :parameters {:path {:id s/Any}}
                   :handler users/handle_get-user
                   :responses {200 {:body s/Any} ; TODO coercion
                               404 {:body s/Any}} ; TODO coercion
                   }
             :delete {:middleware [auth/wrap-authorize-admin!]
                      :summary "Delete user by id"
                      :description "Delete a user by id. Returns 404, if no such user exists."
                      :swagger {:produces "application/json"}
                      :coercion reitit.coercion.schema/coercion
                      :content-type "application/json"
                      :parameters {:path {:id s/Str}}
                      :handler users/handle_delete-user
                      :responses {204 {:body s/Any} ; TODO coercion
                                  404 {:body s/Any}}} ; TODO coercion
             :patch {:middleware [auth/wrap-authorize-admin!]
                     :summary "Patch user with id"
                     :description "Patch a user with id. Returns 404, if no such user exists."
                     :swagger {:consumes "application/json" :produces "application/json"}
                     :coercion reitit.coercion.schema/coercion
                     :content-type "application/json"
                     :accept "application/json"

                     :parameters {:path {:id s/Str}
                                  :body users/schema_update_user}
                     :handler users/handle_patch-user
                     :responses {204 {:body users/schema_export_user}
                                 404 {:body s/Any}}}}]] ; TODO coercion
    ; vocabularies/ring-routes
    ; TODO export schema
   ["/vocabularies"
    ["/" {:get {:summary "Get list of vocabularies ids."
                :description "Get list of vocabularies ids."

                :handler vocabularies-index/get-index
                :swagger {:produces "application/json"}
                :parameters {:query {(s/optional-key :page) s/Int}}
                :content-type "application/json"
                ;:accept "application/json"
                :coercion reitit.coercion.schema/coercion
                :responses {200 {:body {:vocabularies [{:id s/Uuid}]}}}}}]
    ["/:id" {:get {:summary "Get vocabulary by id."
                   :description "Get a vocabulary by id. Returns 404, if no such vocabulary exists."
                   :swagger {:produces "application/json"}
                   :content-type "application/json"
                   :handler vocabularies-get/handle_get-vocabulary
                    ;:middleware [wrap-req-paramters-path-2-params]
                    ;:handler vocabularies-get/get-vocabulary
                   :coercion reitit.coercion.schema/coercion
                   :parameters {:path {:id s/Str}}
                   :responses {200 {:body vocabularies-get/schema_export-vocabulary}
                               404 {:body s/Any}}}}]]]) ; TODO coercion

;### Debug ####################################################################
(debug/debug-ns *ns*)
