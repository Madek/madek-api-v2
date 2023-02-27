(ns madek.api.resources.media-files
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]
    [madek.api.resources.media-files.authorization :as media-files.authorization]
    [madek.api.resources.media-files.media-file :as media-file]
    [madek.api.resources.shared :as shared]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    ))

;##############################################################################

(defn- query-media-file [media-file-id]
  ; we wrap this since badly formated media-file-id strings can cause an
  ; exception, note that 404 is in that case a correct response
  (catcher/snatch {}
    (-> (jdbc/query
          (get-ds)
          ["SELECT * FROM media_files WHERE id = ?" media-file-id])
        first)))

(defn wrap-find-and-add-media-file
  ([handler] #(wrap-find-and-add-media-file % handler))
  ([request handler]
   (when-let [media-file-id (or (-> request :route-params :media_file_id) (-> request :parameters :path :media_file_id))]
     (if-let [media-file (query-media-file media-file-id)]
        ;(
          ;(logging/info "wrap-find-and-add-media-file" "\nid\n" media-file-id "\nmedia file\n" media-file)
          (handler (assoc request :media-file media-file)) 
        ;)
        ;(
          ;(logging/info "wrap-find-and-add-media-file" "not found" "\nid\n" media-file-id)
          {:status 404}
         ;)
        ))))
     ;(when-let [media-file (query-media-file media-file-id)]
     ;  (handler (assoc request :media-file media-file))))))

;##############################################################################

(def routes
  (I>  wrap-handler-with-logging
      (cpj/routes
        (cpj/GET "/media-files/:media_file_id" _
                 #'media-file/get-media-file)
        (cpj/GET "/media-files/:media_file_id/data-stream" _
                 (media-files.authorization/wrap-authorize
                   #'media-file/get-media-file-data-stream :get_full_size))
        (cpj/ANY "*" _ shared/dead-end-handler))
      (media-files.authorization/wrap-authorize :get_metadata_and_previews)
      wrap-find-and-add-media-file))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
