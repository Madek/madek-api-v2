(ns madek.api.resources.media-files
  (:require
   [clojure.tools.logging :as logging]
   [compojure.core :as cpj]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug :refer [I>]]
   [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]
   [madek.api.resources.media-files.authorization :as media-files.authorization]
   [madek.api.resources.media-files.media-file :as media-file] 
   [reitit.coercion.schema]
   [schema.core :as s]
   [madek.api.resources.shared :as sd]
   ))

;##############################################################################

(defn- query-media-file [media-file-id]
  (sd/query-eq-find-one "media_files" "id" media-file-id))
  ; we wrap this since badly formated media-file-id strings can cause an
  ; exception, note that 404 is in that case a correct response
  ;(catcher/snatch {}
  ;  (-> (jdbc/query
  ;        (get-ds)
  ;        ["SELECT * FROM media_files WHERE id = ?" media-file-id])
  ;      first)))

(defn- query-media-file-by-media-entry-id [media-entry-id]
  (sd/query-eq-find-one "media_files" "media_entry_id" media-entry-id))
  ; we wrap this since badly formated media-file-id strings can cause an
  ; exception, note that 404 is in that case a correct response
  ;(catcher/snatch {} 
  ;  (-> (jdbc/query
  ;       (get-ds)
  ;       ["SELECT * FROM media_files WHERE media_entry_id = ?" media-entry-id])
  ;      first)))

(defn find-and-add-media-file
  "Extracts path parameter media_entry_id,
   adds queried media-file and its media_file_id to the request data."
  [request handler]
  (when-let [media-file-id (or (-> request :route-params :media_file_id) (-> request :parameters :path :media_file_id))]
    (if-let [media-file (query-media-file media-file-id)]
      (handler (assoc request :media-file media-file))
      (sd/response_not_found "No media-file for media_file_id"))))

(defn wrap-find-and-add-media-file
  "Extracts path parameter media_entry_id,
   adds queried media-file and its media_file_id to the request data."
  [handler]
  (fn [request] (find-and-add-media-file request handler)))

(defn find-and-add-media-file-by-media-entry-id 
  "Extracts path parameter media_entry_id,
   adds queried media-file and its media_file_id to the request data."
  [request handler]
  (when-let [media-entry-id (or (-> request :route-params :media_entry_id) (-> request :parameters :path :media_entry_id))]
     (if-let [media-file (query-media-file-by-media-entry-id media-entry-id)]
       (handler (assoc request :media-file media-file :media_file_id (:id media-file)))
       (sd/response_not_found "No media-file for media_entry_id"))))

(defn wrap-find-and-add-media-file-by-media-entry-id
  "Extracts path parameter media_entry_id,
   adds queried media-file and its media_file_id to the request data."
  [handler]
  (fn [request] (find-and-add-media-file-by-media-entry-id request handler)))
     


;##############################################################################

(def routes
  (I>  wrap-handler-with-logging
      (cpj/routes
        (cpj/GET "/media-files/:media_file_id" _
                 #'media-file/get-media-file)
        (cpj/GET "/media-files/:media_file_id/data-stream" _
                 (media-files.authorization/wrap-authorize
                   #'media-file/get-media-file-data-stream :get_full_size))
        (cpj/ANY "*" _ sd/dead-end-handler))
      (media-files.authorization/wrap-authorize :get_metadata_and_previews)
      wrap-find-and-add-media-file))


(def ring-routes
  [
   ["/media-files"
   ["/:media_file_id" {:get {:summary "Get media-file for id."
                             :swagger {:produces "application/json"}
                             :content-type "application/json"
                             :handler media-file/get-media-file
                             :middleware [wrap-find-and-add-media-file
                                          media-files.authorization/ring-wrap-authorize-metadata-and-previews]
                             :coercion reitit.coercion.schema/coercion
                             :parameters {:path {:media_file_id s/Str}}}}]

   ["/:media_file_id/data-stream" {:get {:summary "Get media-file data-stream for id."
                                         :handler media-file/get-media-file-data-stream
                                         :middleware [wrap-find-and-add-media-file
                                                      media-files.authorization/ring-wrap-authorize-full_size]
                                         :coercion reitit.coercion.schema/coercion
                                         :parameters {:path {:media_file_id s/Str}}}}]
   
   ["/:media_entry_id/for-media-entry" {:get {:summary "Get media-file for media-entry id."
                                              :handler media-file/get-media-file
                                              :middleware [wrap-find-and-add-media-file-by-media-entry-id
                                                           media-files.authorization/ring-wrap-authorize-metadata-and-previews]
                                              :coercion reitit.coercion.schema/coercion
                                              :parameters {:path {:media_entry_id s/Str}}}}]
   ]
   
   ["/media-file/for-media-entry/:media_entry_id" {:get {:summary "Get media-file for media-entry id."
                                                         :handler media-file/get-media-file
                                                         :middleware [wrap-find-and-add-media-file-by-media-entry-id
                                                                      media-files.authorization/ring-wrap-authorize-metadata-and-previews]
                                                         :coercion reitit.coercion.schema/coercion
                                                         :parameters {:path {:media_entry_id s/Str}}}}]
   
   ["/media-entry/:media_entry_id/media-files" {:get {:summary "Get media-file for media-entry id."
                                                      :handler media-file/get-media-file
                                                      :middleware [wrap-find-and-add-media-file-by-media-entry-id
                                                                   media-files.authorization/ring-wrap-authorize-metadata-and-previews]
                                                      :coercion reitit.coercion.schema/coercion
                                                      :parameters {:path {:media_entry_id s/Str}}}}]


   ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
