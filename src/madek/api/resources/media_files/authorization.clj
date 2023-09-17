(ns madek.api.resources.media-files.authorization
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [madek.api.resources.media-entries.permissions :as me-permissions]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    ))

(defn- media-file-authorize [request handler scope]
  (let [media-entry-id (get-in request [:media-file :media_entry_id])
        media-entry (-> (jdbc/query (get-ds)
                                    [(str "SELECT * FROM media_entries WHERE id = ?")
                                     media-entry-id]) first)]
    (logging/info "authorize" "\nmedia-entry-id\n" media-entry-id "\nmedia-entry\n" media-entry)
    (if (get media-entry scope)
      (handler request)
      (if-let [auth-entity (:authenticated-entity request)]
        (if (case scope
              :get_full_size (me-permissions/downloadable-by-auth-entity?
                               media-entry auth-entity)
              :get_metadata_and_previews (me-permissions/viewable-by-auth-entity?
                                           media-entry auth-entity))
          (handler request)
          {:status 403})
        {:status 401}))))


(defn wrap-auth-media-file-metadata-and-previews [handler]
  (fn [request] (media-file-authorize request handler :get_metadata_and_previews)))

(defn wrap-auth-media-file-full_size [handler]
  (fn [request] (media-file-authorize request handler :get_full_size)))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
