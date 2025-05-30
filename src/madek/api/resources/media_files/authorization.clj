(ns madek.api.resources.media-files.authorization
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.media-entries.permissions :as me-permissions]
   [next.jdbc :as jdbc]
   [taoensso.timbre :refer [info]]))

(defn- media-file-authorize [request handler scope]
  (let [media-entry-id (get-in request [:media-file :media_entry_id])
        tx (:tx request)
        media-entry (-> (jdbc/execute-one! (:tx request) (-> (sql/select :*)
                                                             (sql/from :media_entries)
                                                             (sql/where [:= :id media-entry-id])
                                                             sql-format)))]
    (info "authorize" "\nmedia-entry-id\n" media-entry-id "\nmedia-entry\n" media-entry)
    (if (get media-entry scope)
      (handler request)
      (if-let [auth-entity (:authenticated-entity request)]
        (if (case scope
              :get_full_size (me-permissions/downloadable-by-auth-entity?
                              media-entry auth-entity tx)
              :get_metadata_and_previews (me-permissions/viewable-by-auth-entity?
                                          media-entry auth-entity tx))
          (handler request)
          {:status 403})
        {:status 401}))))

(defn wrap-auth-media-file-metadata-and-previews [handler]
  (fn [request] (media-file-authorize request handler :get_metadata_and_previews)))

(defn wrap-auth-media-file-full_size [handler]
  (fn [request] (media-file-authorize request handler :get_full_size)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
