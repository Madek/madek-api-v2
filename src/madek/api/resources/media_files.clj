(ns madek.api.resources.media-files
  (:require
   [clojure.tools.logging :as logging]
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
  (sd/query-eq-find-one :media_files :id media-file-id))

(defn query-media-file-by-media-entry-id [media-entry-id]
  (sd/query-eq-find-one :media_files :media_entry_id media-entry-id))

(defn query-media-files-by-media-entry-id [media-entry-id]
  (sd/query-eq-find-all :media_files :media_entry_id media-entry-id))


(defn wrap-find-and-add-media-file
  "Extracts path parameter media_entry_id,
   adds queried media-file and its media_file_id to the request data."
  [handler]
  (fn [request]
    (when-let [media-file-id (-> request :parameters :path :media_file_id)]
      (if-let [media-file (query-media-file media-file-id)]
        (handler (assoc request :media-file media-file))
        (sd/response_not_found "No media-file for media_file_id")))))


(defn wrap-find-and-add-media-file-by-media-entry-id
  "Extracts path parameter media_entry_id,
   adds queried media-file and its media_file_id to the request data."
  [handler]
  (fn [request]
    (when-let [media-entry-id (-> request :parameters :path :media_entry_id)]
       (if-let [media-files (query-media-files-by-media-entry-id media-entry-id)]
         (handler (assoc request :media-file (first media-files)))
         (sd/response_not_found "No media-file for media_entry_id")))))


(def schema_export-media-file
  {:id s/Uuid
   :media_entry_id s/Uuid
   :media_type (s/maybe s/Str)
   :content_type s/Str
   :filename s/Str

   :previews s/Any
   :size s/Int
   ;:width s/Int
   ;:height s/Int
   ;:access_hash s/Str
   ;:meta_data s/Str
   ;:uploader_id s/Uuid
   ;:conversion_profiles [s/Str]
   :created_at s/Any
   :updated_at s/Any

   })

;##############################################################################

(def media-file-routes
  ["/media-file"
   ["/:media_file_id"
    {:get {:summary (sd/sum_usr_pub "Get media-file for id.")
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler media-file/get-media-file
           :middleware [wrap-find-and-add-media-file
                        media-files.authorization/wrap-auth-media-file-metadata-and-previews]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_file_id s/Str}}
           :responses {200 {:body schema_export-media-file}
                       404 {:body s/Any}}}}]

   ["/:media_file_id/data-stream"
    {:get {:summary (sd/sum_usr_pub "Get media-file data-stream for id.")
           :handler media-file/get-media-file-data-stream
           :middleware [wrap-find-and-add-media-file
                        media-files.authorization/wrap-auth-media-file-full_size]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_file_id s/Str}}
           ;:responses {:200 {:body s/Any}
           ;            :404 {:body s/Any}}
           }}]
   ])

(def media-entry-routes
  ["/media-entry"
   ["/:media_entry_id/media-file"
    {:get
     {:summary (sd/sum_usr_pub "Get media-file for media-entry id.")
      :handler media-file/get-media-file
      :middleware [wrap-find-and-add-media-file-by-media-entry-id
                   sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Str}}
      :responses {200 {:body schema_export-media-file}
                  404 {:body s/Any}}}}]
   
   ["/:media_entry_id/media-file/data-stream"
    {:get
     {:summary (sd/sum_usr_pub "Get media-file data-stream for media-entry id.")
      :handler media-file/get-media-file-data-stream
      :middleware [wrap-find-and-add-media-file-by-media-entry-id
                   sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-download]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Str}}
      ;:responses {:200 {:body s/Any}
      ;            :404 {:body s/Any}}
      }}]
  ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
