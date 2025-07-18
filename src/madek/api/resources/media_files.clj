(ns madek.api.resources.media-files
  (:require
   [madek.api.resources.media-files.authorization :as media-files.authorization]
   [madek.api.resources.media-files.media-file :as media-file]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [reitit.coercion.schema]
   [schema.core :as s]))

;##############################################################################

(defn- query-media-file [media-file-id tx]
  (dbh/query-eq-find-one :media_files :id media-file-id tx))

(defn query-media-file-by-media-entry-id [media-entry-id tx]
  (dbh/query-eq-find-one :media_files :media_entry_id media-entry-id tx))

(defn query-media-files-by-media-entry-id [media-entry-id tx]
  (dbh/query-eq-find-all :media_files :media_entry_id media-entry-id tx))

(defn wrap-find-and-add-media-file
  "Extracts path parameter media_entry_id,
   adds queried media-file and its media_file_id to the request data."
  [handler]
  (fn [request]
    (when-let [media-file-id (-> request :parameters :path :media_file_id)]
      (if-let [media-file (query-media-file media-file-id (:tx request))]
        (handler (assoc request :media-file media-file))
        (sd/response_not_found "No media-file for media_file_id")))))

(defn nil-or-empty? [value]
  (or (nil? value) (empty? value)))

(defn wrap-find-and-add-media-file-by-media-entry-id
  "Extracts path parameter media_entry_id,
   adds queried media-file and its media_file_id to the request data."
  [handler]
  (fn [request]
    (when-let [media-entry-id (-> request :parameters :path :media_entry_id)]
      (let [media-files (query-media-files-by-media-entry-id media-entry-id (:tx request))]
        (if (nil-or-empty? media-files)
          (sd/response_not_found "No media-file for media_entry_id")
          (handler (assoc request :media-file (first media-files))))))))

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
   :updated_at s/Any})

;##############################################################################

(def media-file-routes
  ["/media-files"
   {:openapi {:tags ["api/media-files"]}}
   ["/:media_file_id"
    {:get {:summary (sd/sum_usr_pub "Get media-file for id.")
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler media-file/get-media-file
           :middleware [wrap-find-and-add-media-file
                        media-files.authorization/wrap-auth-media-file-metadata-and-previews]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_file_id s/Str}}
           :responses {200 {:description "Returns the media-file for id."
                            :body schema_export-media-file}
                       404 {:description "Not found."
                            :body s/Any}}}}]

   ["/:media_file_id/data-stream/"
    {:get {:summary (sd/sum_usr_pub "Get media-file data-stream for id.")
           :handler media-file/get-media-file-data-stream
           :middleware [wrap-find-and-add-media-file
                        media-files.authorization/wrap-auth-media-file-full_size]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_file_id s/Str}}
           :responses {200 {:description "Returns the media-file data-stream for id."
                            :body s/Any}
                       404 {:description "Not found."
                            :body s/Any}}}}]])
(def media-entry-routes
  ["/media-entries"
   {:openapi {:tags ["api/media-entries"]}}
   ["/:media_entry_id/media-files/"
    {:get
     {:summary (sd/sum_usr_pub "Get media-file for media-entry id.")
      :handler media-file/get-media-file
      :middleware [wrap-find-and-add-media-file-by-media-entry-id
                   jqh/ring-wrap-add-media-resource
                   jqh/ring-wrap-authorization-view]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Str}}
      :responses {200 {:description "Returns the media-file for media-entry id."
                       :body schema_export-media-file}
                  404 {:description "Not found."
                       :body s/Any}}}}]

   ["/:media_entry_id/media-files/data-stream/"
    {:get
     {:summary (sd/sum_usr_pub "Get media-file data-stream for media-entry id.")
      :handler media-file/get-media-file-data-stream
      :middleware [wrap-find-and-add-media-file-by-media-entry-id
                   jqh/ring-wrap-add-media-resource
                   jqh/ring-wrap-authorization-download]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:media_entry_id s/Str}}
      :responses {200 {:description "Returns the media-file data-stream for media-entry id."
                       :body s/Any}
                  404 {:description "Not found."
                       :body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
