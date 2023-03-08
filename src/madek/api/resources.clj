(ns madek.api.resources
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [compojure.core :as cpj]
            [logbug.catcher :as catcher]
            [logbug.debug :as debug]
            [madek.api.authentication :as authentication]
            [madek.api.authorization :refer [authorized?]]
            [madek.api.resources.admins :as admins]
            [madek.api.resources.auth-info :as auth-info]
            [madek.api.resources.collection-media-entry-arcs :as collection-media-entry-arcs]
            [madek.api.resources.collections :as collections]
            [madek.api.resources.custom-urls :as custom-urls]
            [madek.api.resources.favorite-collections :as favorite-collections]
            [madek.api.resources.favorite-media-entries :as favorite-media-entries]
            [madek.api.resources.groups :as groups]
            [madek.api.resources.keywords :as keywords]
            [madek.api.resources.media-entries :as media-entries]
            [madek.api.resources.media-entries.media-entry :refer [get-media-entry-for-preview]]
            [madek.api.resources.media-files :as media-files]
            [madek.api.resources.meta-data :as meta-data]
            [madek.api.resources.meta-keys :as meta-keys]
            [madek.api.resources.people :as people]
            [madek.api.resources.previews :as previews]
            [madek.api.resources.roles :as roles]
            [madek.api.resources.shared :as sd]
            [madek.api.resources.usage-terms :as usage-terms]
            [madek.api.resources.io-interfaces :as io-interfaces]
            [madek.api.resources.io-mappings :as io-mappings]
            [madek.api.resources.users :as users]
            [madek.api.resources.vocabularies :as vocabularies]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [reitit.coercion.schema]))
            


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


(defn- wrap-add-media-resource [handler]
  (fn [request]
    (add-media-resource request handler)))



; TODO move to params coercion
(defn- wrap-check-uuid-syntax-conformity [handler]
  (letfn [(return-422-if-not-uuid-conform [request]
            (if (re-find sd/uuid-matcher (-> request :params :resource_id))
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



(defn- wrap-add-meta-datum-with-media-resource [handler]
  (fn [request]
    (add-meta-datum-with-media-resource request handler)))


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
      



(def api2-routes
  ["/" {:get (constantly sd/root)}
   ; admin routes


   ["api-clients/" {:post (constantly sd/no_impl)
                    :get (constantly sd/no_impl)
                    :put (constantly sd/no_impl)
                    :delete (constantly sd/no_impl)}]

   ;["api-tokens/" {:post (constantly sd/no_impl)
   ;                :get (constantly sd/no_impl)
   ;                :put (constantly sd/no_impl)
   ;                :delete (constantly sd/no_impl)}]

   ["app-settings" {;:post {:summary (sd/sum_todo "App Settings") :handler (constantly sd/no_impl)}
                    :get {:summary (sd/sum_todo "App Settings") :handler (constantly sd/no_impl)}
                    :put {:summary (sd/sum_todo "App Settings") :handler (constantly sd/no_impl)}}]
                    ;:delete {:summary (sd/sum_todo "App Settings") :handler (constantly sd/no_impl)}

   ; data-type [collection, media-entry]
   ;["collections/:data-type/arcs/" {:post (constantly sd/no_impl)
   ;                                 :get (constantly sd/no_impl)
   ;                                 :put (constantly sd/no_impl)
   ;                                 :delete (constantly sd/no_impl)}]

   ; convenience access to permissions
   ; perm-type [api-client, group, user] 
   ["collections/:perm-type/perm" {:post (constantly sd/no_impl)
                                   :get (constantly sd/no_impl)
                                   :put (constantly sd/no_impl)
                                   :delete (constantly sd/no_impl)}]

   ;["confidential-links" {:post {:summary (sd/sum_todo "Confidential Links") :handler (constantly sd/no_impl)}
   ;                       :get {:summary (sd/sum_todo "Confidential Links") :handler (constantly sd/no_impl)}
   ;                       :put {:summary (sd/sum_todo "Confidential Links") :handler (constantly sd/no_impl)}
   ;                       :delete {:summary (sd/sum_todo "Confidential Links") :handler (constantly sd/no_impl)}}]

   ;["context-keys/" {:post {:summary (sd/sum_todo "Context Keys") :handler (constantly sd/no_impl)}
   ;                  :get {:summary (sd/sum_todo "Context Keys") :handler (constantly sd/no_impl)}
   ;                  :put {:summary (sd/sum_todo "Context Keys") :handler (constantly sd/no_impl)}
   ;                  :delete {:summary (sd/sum_todo "Context Keys") :handler (constantly sd/no_impl)}}]

   ;["context/" {:post {:summary (sd/sum_todo "Context") :handler (constantly sd/no_impl)}
   ;             :get {:summary (sd/sum_todo "Context") :handler (constantly sd/no_impl)}
   ;             :put {:summary (sd/sum_todo "Context") :handler (constantly sd/no_impl)}
   ;             :delete {:summary (sd/sum_todo "Context") :handler (constantly sd/no_impl)}}]

   ;["custom-urls/" {:post {:summary (sd/sum_todo "Custom URLs") :handler (constantly sd/no_impl)}
   ;                 :get {:summary (sd/sum_todo "Custom URLs") :handler (constantly sd/no_impl)}
   ;                 :put {:summary (sd/sum_todo "Custom URLs") :handler (constantly sd/no_impl)}
   ;                 :delete {:summary (sd/sum_todo "Custom URLs") :handler (constantly sd/no_impl)}}]

   ["delegations/" {:post (constantly sd/no_impl)
                    :get (constantly sd/no_impl)
                    :put (constantly sd/no_impl)
                    :delete (constantly sd/no_impl)}]

   ;; del-type [groups, users, workflows]
   ["delegations/:del-type/" {:post (constantly sd/no_impl)
                              :get (constantly sd/no_impl)
                              :put (constantly sd/no_impl)
                              :delete (constantly sd/no_impl)}]

   ;["edit-session/" {:post (constantly sd/no_impl)
   ;                  :get (constantly sd/no_impl)
   ;                  :put (constantly sd/no_impl)
   ;                  :delete (constantly sd/no_impl)}]

   ; data-type [collections, media-entries]
   ;["favorites/:data-type/" {:post 2(constantly sd/no_impl)
   ;                          :get 2(constantly sd/no_impl)
   ;                          :put 2(constantly sd/no_impl)
   ;                          :delete 2(constantly sd/no_impl)}]
   
   
   ;["full-texts/" {:post {:summary (sd/sum_todo "Full Texts") :handler (constantly sd/no_impl)}
   ;                :get {:summary (sd/sum_todo "Full Texts") :handler (constantly sd/no_impl)}
   ;                :put {:summary (sd/sum_todo "Full Texts") :handler (constantly sd/no_impl)}
   ;                :delete {:summary (sd/sum_todo "Full Texts") :handler (constantly sd/no_impl)}}]

   ;["io-interfaces" {:post {:summary (sd/sum_todo "IO Interfaces") :handler (constantly sd/no_impl)}
   ;                  :get {:summary (sd/sum_todo "IO Interfaces") :handler (constantly sd/no_impl)}
   ;                  :put {:summary (sd/sum_todo "IO Interfaces") :handler (constantly sd/no_impl)}
   ;                  :delete {:summary (sd/sum_todo "IO Interfaces") :handler (constantly sd/no_impl)}}]

   ;["io-mappings" {:post {:summary (sd/sum_todo "IO Mappings") :handler (constantly sd/no_impl)}
   ;                :get {:summary (sd/sum_todo "IO Mappings") :handler (constantly sd/no_impl)}
   ;                :put {:summary (sd/sum_todo "IO Mappings") :handler (constantly sd/no_impl)}
   ;                :delete {:summary (sd/sum_todo "IO Mappings") :handler (constantly sd/no_impl)}}]

   ;["keywords" {:post (constantly sd/no_impl)
   ;;             :get (constantly sd/no_impl)
   ;             :put (constantly sd/no_impl)
   ;             :delete (constantly sd/no_impl)}]

   ;["media-files/" {:post (constantly sd/no_impl)
                    ;:get (constantly sd/no_impl)
   ;                 :put (constantly sd/no_impl)
                    ;:delete (constantly sd/no_impl)
   ;                }]



   ;["meta-data/" {:post (constantly sd/no_impl)
   ;               :get (constantly sd/no_impl)
   ;               :put (constantly sd/no_impl)
   ;               :delete (constantly sd/no_impl)}]

   ; data-type [keywords, meta-terms, people, roles] [text,json]
   ;["meta-data/:data-type/" {:post (constantly sd/no_impl)
;                             :get (constantly sd/no_impl)
   ;                          :put (constantly sd/no_impl)
;                             :delete (constantly sd/no_impl)
   ;                          }
   ; ]

   ;["meta-keys/" {:post (constantly sd/no_impl)
   ;               ;:get (constantly sd/no_impl)
   ;               :put (constantly sd/no_impl)
   ;               :delete (constantly sd/no_impl)}]

   ;["media-entries/" {:post (constantly sd/no_impl)
   ;                   ;:get (constantly sd/no_impl)
   ;                   :put (constantly sd/no_impl)
   ;                   :delete (constantly sd/no_impl)}]

   ;["media-entries/meta-data" {:post (constantly sd/no_impl)
   ;                   ;:get (constantly sd/no_impl)
   ;                            :put (constantly sd/no_impl)
   ;                            :delete (constantly sd/no_impl)}]
   ; redirect to permissions
   ["media-entries/:perm-type/perms" {:post (constantly sd/no_impl)
                                      :get (constantly sd/no_impl)
                                      :put (constantly sd/no_impl)
                                      :delete (constantly sd/no_impl)}]

   ;["static-pages/" {:post {:summary (sd/sum_todo "Static pages") :handler (constantly sd/no_impl)}
   ;                  :get {:summary (sd/sum_todo "Static Pages") :handler (constantly sd/no_impl)}
   ;                  :put {:summary (sd/sum_todo "Static Pages") :handler (constantly sd/no_impl)}
   ;                  :delete {:summary (sd/sum_todo "Static Pages") :handler (constantly sd/no_impl)}}]

   


   ;["workflows/" {:post {:summary (sd/sum_todo "Workflows") :handler (constantly sd/no_impl)}
   ;               :get {:summary (sd/sum_todo "Workflows") :handler (constantly sd/no_impl)}
   ;               :put {:summary (sd/sum_todo "Workflows") :handler (constantly sd/no_impl)}
   ;               :delete {:summary (sd/sum_todo "Workflows") :handler (constantly sd/no_impl)}}]


   ; perm-type [api-client, group, user]
   ["permissions/:data-type/:perm-type/" {:post (constantly sd/no_impl)
                                          :get (constantly sd/no_impl)
                                          :put (constantly sd/no_impl)
                                          :delete (constantly sd/no_impl)}]])
   
   

   

(def ring-routes
  ["/api" {:middleware [authentication/wrap]}
    ;["/" {:get (constantly sd/no_impl)}]
   
   
   admins/ring-routes

   ["/auth-info" {:get {:summary "Authentication help and info."
                        :handler auth-info/auth-info}}]

   ; collections
   collections/ring-routes
   collection-media-entry-arcs/ring-routes


   custom-urls/ring-routes
   custom-urls/media-entry-routes
   custom-urls/collection-routes

   favorite-collections/ring-routes
   favorite-media-entries/ring-routes

   groups/ring-routes

   io-interfaces/ring-routes
   io-mappings/ring-routes

   keywords/ring-routes
   

   ;links

   media-entries/ring-routes
   
  ;media_entries
   

   ;media_files
   media-files/ring-routes

   ;meta_data
   meta-data/ring-routes
   meta-data/collection-routes
   meta-data/media-entry-routes
   
   meta-keys/ring-routes

   people/ring-routes

   previews/preview-routes
   previews/media-entry-routes

   roles/ring-routes
   
   usage-terms/ring-routes

   users/ring-routes
   
   vocabularies/ring-routes

   api2-routes
    ])
   

;### Debug ####################################################################
(debug/debug-ns *ns*)
