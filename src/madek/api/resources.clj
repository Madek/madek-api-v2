(ns madek.api.resources
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            ;[logbug.catcher :as catcher]
            [logbug.debug :as debug]
            [madek.api.authentication :as authentication]
            [madek.api.resources.admins :as admins]
            [madek.api.resources.app-settings :as app-settings]
            [madek.api.resources.collection-collection-arcs :as collection-collection-arcs]
            [madek.api.resources.collection-media-entry-arcs :as collection-media-entry-arcs]
            [madek.api.resources.collections :as collections]
            [madek.api.resources.confidential-links :as confidential-links]
            [madek.api.resources.context-keys :as context_keys]
            [madek.api.resources.contexts :as contexts]
            [madek.api.resources.custom-urls :as custom-urls]
            [madek.api.resources.delegations :as delegations]
            [madek.api.resources.delegations-users :as delegations_users]
            [madek.api.resources.delegations-groups :as delegations_groups]
            [madek.api.resources.edit-sessions :as edit-sessions]
            [madek.api.resources.favorite-collections :as favorite-collections]
            [madek.api.resources.favorite-media-entries :as favorite-media-entries]
            [madek.api.resources.full-texts :as full-texts]
            [madek.api.resources.groups :as groups]
            [madek.api.resources.io-interfaces :as io-interfaces]
            [madek.api.resources.io-mappings :as io-mappings]
            [madek.api.resources.keywords :as keywords]
            [madek.api.resources.media-entries :as media-entries]
            [madek.api.resources.media-files :as media-files]
            [madek.api.resources.meta-data :as meta-data]
            [madek.api.resources.meta-keys :as meta-keys]
            [madek.api.resources.people :as people]
            [madek.api.resources.permissions :as permissions]
            [madek.api.resources.previews :as previews]
            [madek.api.resources.roles :as roles]
            [madek.api.resources.shared :as sd]
            [madek.api.resources.static-pages :as static-pages]
            [madek.api.resources.usage-terms :as usage-terms]
            [madek.api.resources.users :as users]
            [madek.api.resources.vocabularies :as vocabularies]
            [madek.api.resources.workflows :as workflows]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [reitit.coercion.schema]))
            


;### wrap media resource ######################################################


;### wrap meta-datum with media-resource#######################################




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


(def api2-routes
  ["/" {:get (constantly sd/root)}
   ; admin routes

   ; TODO api-tokens
   ;["api-tokens/" {:post (constantly sd/no_impl)
   ;                :get (constantly sd/no_impl)
   ;                :put (constantly sd/no_impl)
   ;                :delete (constantly sd/no_impl)}]


   ; TODO confidential-links post, get, patch, delete

   ; TODO Frage: delegations workflows post, get, patch, delete

   

   ])

(def admin-routes
  ; TODO use wrap admin
  ["/api/admin" ;{:middleware [
                ;              authentication/wrap
                              ;wrap-authorize-admin!
                ;              ]}

   admins/ring-routes
   app-settings/admin-routes

   context_keys/admin-routes
   contexts/admin-routes

   ; TODO Frage: wird das noch gebraucht
   delegations/ring-routes
   delegations_users/admin-routes
   delegations_groups/admin-routes


   edit-sessions/admin-routes
   favorite-collections/admin-routes
   favorite-media-entries/admin-routes

   full-texts/edit-routes

   groups/ring-routes
   io-interfaces/admin-routes
   io-mappings/admin-routes
   keywords/admin-routes

   meta-keys/admin-routes

   people/admin-routes
   roles/admin-routes
   usage-terms/admin-routes

   users/admin-routes
   ; TODO static pages
   static-pages/admin-routes
   vocabularies/admin-routes]) 

(def user-routes
  ["/api" {:middleware [authentication/wrap]}

   app-settings/user-routes
   context_keys/user-routes
   contexts/user-routes
   keywords/query-routes
   meta-keys/query-routes
   people/user-routes

   ; collections
   collections/ring-routes
   meta-data/collection-routes
   ;media-entries/collection-routes
   custom-urls/collection-routes
   edit-sessions/collection-routes
   favorite-collections/collection-routes

   permissions/collection-routes

   collection-media-entry-arcs/collection-routes
   collection-collection-arcs/collection-routes

   collection-media-entry-arcs/ring-routes
   collection-collection-arcs/ring-routes

   full-texts/collection-routes
   confidential-links/user-col-routes
   custom-urls/query-routes


   edit-sessions/query-routes

   ; favorites
   favorite-media-entries/favorite-routes
   favorite-collections/favorite-routes

   full-texts/query-routes
   ;media_entries
   media-entries/ring-routes
   media-entries/media-entry-routes
   previews/media-entry-routes
   meta-data/media-entry-routes
   confidential-links/user-me-routes
   custom-urls/media-entry-routes
   edit-sessions/media-entry-routes
   favorite-media-entries/media-entry-routes
   media-files/media-entry-routes
   permissions/media-entry-routes
   full-texts/entry-routes
   ;media_files
   media-files/media-file-routes

   ;meta_data
   meta-data/meta-data-routes
   meta-data/role-routes

   previews/preview-routes

   roles/user-routes

   users/user-routes
   groups/user-routes
   usage-terms/user-routes

   vocabularies/user-routes
   workflows/user-routes
   ]) 


;### Debug ####################################################################
(debug/debug-ns *ns*)
