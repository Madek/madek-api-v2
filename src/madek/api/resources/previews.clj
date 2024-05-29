(ns madek.api.resources.previews
  (:require
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.media-entries.media-entry :refer [get-media-entry-for-preview]]
   [madek.api.resources.media-files :as media-files]
   [madek.api.resources.previews.preview :as preview]
   [madek.api.resources.shared :as sd]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [info]]))

(defn ring-wrap-find-and-add-preview
  ([handler] #(ring-wrap-find-and-add-preview % handler))
  ([request handler]
   (when-let [preview-id (-> request :parameters :path :preview_id)]
     (info "ring-wrap-find-and-add-preview" "\npreview-id\n" preview-id)
     (when-let [preview (first (sd/query-eq-find-all :previews :id preview-id (:tx request)))]
       (info "ring-wrap-find-and-add-preview" "\npreview-id\n" preview-id "\npreview\n" preview)
       (handler (assoc request :preview preview))))))

(defn handle_get-preview
  [req]
  (let [media-file (-> req :media-file)
        id (:id media-file)
        size (or (-> req :parameters :query :size) "small")]
    (if-let [preview (sd/query-eq-find-one :previews :media_file_id id :thumbnail size (:tx req))]
      (sd/response_ok preview)
      (sd/response_not_found "No such preview file"))
    ;(info "handle_get-preview" "\nid\n" id "\nmf\n" media-file "\npreviews\n" preview)
    ))
(defn add-preview-for-media-file [handler request]
  (let [media-file (-> request :media-file)
        id (:id media-file)
        previews (sd/query-eq-find-all :previews :media_file_id id (:tx request))
        pfirst (first previews)]
    (handler (assoc request :preview pfirst))))

(defn wrap-add-preview-for-media-file [handler]
  (fn [request] (add-preview-for-media-file handler request)))

(defn- ring-add-media-resource-preview [request handler]
  (if-let [media-resource (get-media-entry-for-preview request)]
    (let [mmr (assoc media-resource :type "MediaEntry" :table-name "media_entries")
          request-with-media-resource (assoc request :media-resource mmr)]
      (handler request-with-media-resource))
    (sd/response_not_found "No media-resource for preview")))

(defn ring-wrap-add-media-resource-preview [handler]
  (fn [request]
    (ring-add-media-resource-preview request handler)))

;(def schema_export_preview
;  {:id s/Uuid
;   :media_file_id s/Uuid
;   :media_type s/Str
;   :content_type s/Str
;   ;(s/enum "small" "small_125" "medium" "large" "x-large" "maximum")
;   :thumbnail s/Str
;   :width (s/maybe s/Int)
;   :height (s/maybe s/Int)
;   :filename s/Str
;   :conversion_profile (s/maybe s/Str)
;   :updated_at s/Any
;   :created_at s/Any})

; TODO tests
(def preview-routes
  ["/previews"
   {:swagger {:tags ["api/previews"]}}
   ["/:preview_id"
    {:get {:summary "Get preview for id."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler preview/get-preview
           :middleware [ring-wrap-find-and-add-preview
                        ring-wrap-add-media-resource-preview
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:preview_id s/Uuid}}}}]

   ["/:preview_id/data-stream"
    {:get {:summary "Get preview data-stream for id."
           :handler preview/get-preview-file-data-stream
           :middleware [ring-wrap-find-and-add-preview
                        ring-wrap-add-media-resource-preview
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:preview_id s/Uuid}}}}]])

; TODO auth
; TODO tests
(def media-entry-routes
  ["/media-entry"
   {:swagger {:tags ["api/media-entry"]}}
   ; TODO media-entry preview auth
   ["/:media_entry_id/preview"
    {:get {:summary "Get preview for media-entry id."
           :handler handle_get-preview
           :middleware [media-files/wrap-find-and-add-media-file-by-media-entry-id
                        ;            media-files.authorization/ring-wrap-authorize-metadata-and-previews
                        ]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :query {(s/optional-key :size) s/Str}}
           :responses {200 {:body (get-schema :previews.schema_export_preview)}
                       404 {:body s/Any}}}}]
   ; TODO media-entry preview auth
   ["/:media_entry_id/preview/data-stream"
    {:get {:summary "Get preview for media-entry id."
           :handler preview/get-preview-file-data-stream
           :middleware [media-files/wrap-find-and-add-media-file-by-media-entry-id
                        wrap-add-preview-for-media-file
                        ;             media-files.authorization/ring-wrap-authorize-metadata-and-previews
                        ]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :query {(s/optional-key :size) s/Str}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
