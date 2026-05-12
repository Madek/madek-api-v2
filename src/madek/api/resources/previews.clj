(ns madek.api.resources.previews
  (:require
   [madek.api.resources.media-entries.media-entry :refer [get-media-entry-for-preview]]
   [madek.api.resources.media-files :as media-files]
   [madek.api.resources.previews.index :as previews.index]
   [madek.api.resources.previews.preview :as preview]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [debug]]))

(defn ring-wrap-find-and-add-preview
  ([handler] #(ring-wrap-find-and-add-preview % handler))
  ([request handler]
   (when-let [preview-id (-> request :parameters :path :preview_id)]
     (debug "ring-wrap-find-and-add-preview" "\npreview-id\n" preview-id)
     (when-let [preview (first (dbh/query-eq-find-all :previews :id preview-id (:tx request)))]
       (debug "ring-wrap-find-and-add-preview" "\npreview-id\n" preview-id "\npreview\n" preview)
       (handler (assoc request :preview preview))))))

(defn ring-add-media-resource-preview [request handler]
  (if-let [media-resource (get-media-entry-for-preview request)]
    (let [mmr (assoc media-resource :type "MediaEntry" :table-name "media_entries")
          request-with-media-resource (assoc request :media-resource mmr)]
      (handler request-with-media-resource))
    (sd/response_not_found "No media-resource for preview")))

(defn ring-wrap-add-media-resource-preview [handler]
  (fn [request]
    (ring-add-media-resource-preview request handler)))

(def schema_export_preview
  {:id s/Uuid
   :media_file_id s/Uuid
   :media_type s/Str
   :content_type s/Str
   :thumbnail s/Str
   :width (s/maybe s/Int)
   :height (s/maybe s/Int)
   :filename s/Str
   :conversion_profile (s/maybe s/Str)
   :updated_at s/Any
   :created_at s/Any})

(def schema-preview-summary
  {:id s/Uuid
   :thumbnail s/Str})

(defn handle-get-previews-index [req]
  (let [media-file (:media-file req)
        tx (:tx req)
        previews (previews.index/get-index media-file tx)]
    (->> previews
         (map #(select-keys % [:id :thumbnail]))
         sd/response_ok)))

; TODO tests
(def preview-routes
  ["/previews"
   {:openapi {:tags ["api/previews"]}}
   ["/:preview_id"
    {:get {:summary "Get preview for id."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler preview/get-preview
           :middleware [ring-wrap-find-and-add-preview
                        ring-wrap-add-media-resource-preview
                        jqh/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:description "Returns the preview."
                            :body schema_export_preview}
                       404 {:description "Not found."
                            :body s/Any}}
           :parameters {:path {:preview_id s/Uuid}}}}]

   ["/:preview_id/data-stream"
    {:get {:summary "Get preview data-stream for id."
           :handler preview/get-preview-file-data-stream
           :middleware [ring-wrap-find-and-add-preview
                        ring-wrap-add-media-resource-preview
                        jqh/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:description "Returns the preview."
                            :body s/Any}
                       404 {:description "Not found."
                            :body s/Any}}
           :parameters {:path {:preview_id s/Uuid}}}}]])

; TODO auth
(def media-entry-routes
  ["/media-entries"
   {:openapi {:tags ["api/media-entries"]}}
   ; TODO media-entry preview auth
   ["/:media_entry_id/previews/"
    {:get {:summary "Get all previews for media-entry id."
           :handler handle-get-previews-index
           :middleware [media-files/wrap-find-and-add-media-file-by-media-entry-id]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}}
           :responses {200 {:description "Returns the list of previews."
                            :body [schema-preview-summary]}
                       404 {:description "Not found."
                            :body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
