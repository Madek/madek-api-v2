(ns madek.api.resources.previews
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [madek.api.pagination :as pagination]
    [madek.api.resources.previews.preview :as preview]
    [madek.api.resources.shared :as sd]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    [reitit.coercion.schema]
    [schema.core :as s]
    
    [madek.api.resources.media-files :as media-files]

    ))

(defn- query-preview [preview-id]
  ; we wrap this since badly formated media-file-id strings can cause an
  ; exception, note that 404 is in that case a correct response
  (catcher/snatch {}
    (-> (jdbc/query
          (get-ds)
          ["SELECT * FROM previews WHERE id = ?" preview-id])
        first)))

(defn- wrap-find-and-add-preview
  ([handler] #(wrap-find-and-add-preview % handler))
  ([request handler]
   (when-let [preview-id (-> request :route-params :preview_id)]
     (when-let [preview (query-preview preview-id)]
       (handler (assoc request :preview preview))))))

(def routes
  (-> (cpj/routes
        (cpj/GET "/previews/:preview_id" _ preview/get-preview)
        (cpj/GET "/previews/:preview_id/data-stream" _ preview/get-preview-file-data-stream)
        (cpj/ANY "*" _ sd/dead-end-handler))
      wrap-find-and-add-preview))

(defn ring-wrap-find-and-add-preview
  ([handler] #(ring-wrap-find-and-add-preview % handler))
  ([request handler]
   (when-let [preview-id (-> request :parameters :path :preview_id)]
     ;(logging/info "ring-wrap-find-and-add-preview" "\npreview-id\n" preview-id)
     (when-let [preview (query-preview preview-id)]
       (logging/info "ring-wrap-find-and-add-preview" "\npreview-id\n" preview-id "\npreview\n" preview)
       (handler (assoc request :preview preview))))))

(defn handle_get-preview
  [req]
  (let [media-file (-> req :media-file)
        id (:id media-file)
        previews (sd/query-eq-find-all  "previews" "media_file_id" id)
        pfirst (first previews)]
    (logging/info "handle_get-preview" "\nid\n" id "\nmf\n" media-file "\npreviews\n" pfirst)
    (sd/response_ok pfirst)
    ))

(defn add-preview-for-media-file [handler request]
  (let [media-file (-> request :media-file)
        id (:id media-file)
        previews (sd/query-eq-find-all  "previews" "media_file_id" id)
        pfirst (first previews)]
      (handler (assoc request :preview pfirst))
    ))

(defn wrap-add-preview-for-media-file [handler]
  (fn [request] (add-preview-for-media-file handler request)))


(def ring-routes
  [
   ["/previews"
   ["/:preview_id" 
    {:get {:summary "Get preview for id."}
     :swagger {:produces "application/json"}
     :content-type "application/json"
     :handler preview/get-preview
     :middleware [ring-wrap-find-and-add-preview
                  sd/ring-wrap-add-media-resource-preview
                  sd/ring-wrap-authorization]
     :coercion reitit.coercion.schema/coercion
     :parameters {:path {:preview_id s/Uuid}}}]

   ["/:preview_id/data-stream"
    {:get {:summary "Get preview data-stream for id."
           :handler preview/get-preview-file-data-stream
           :middleware [ring-wrap-find-and-add-preview
                        sd/ring-wrap-add-media-resource-preview
                        sd/ring-wrap-authorization]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:preview_id s/Uuid}}}}]
   ]
   ; TODO auth
   ["/media-entry/:media_entry_id/preview"
    {:get {:summary "Get preview for media-entry id."
           :handler handle_get-preview
           :middleware [media-files/wrap-find-and-add-media-file-by-media-entry-id
           ;            media-files.authorization/ring-wrap-authorize-metadata-and-previews
                        ]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :query {(s/optional-key :size) s/Str}}}}]
   
   ["/media-entry/:media_entry_id/preview/data-stream"
    {:get {:summary "Get preview for media-entry id."
           :handler preview/get-preview-file-data-stream
           :middleware [media-files/wrap-find-and-add-media-file-by-media-entry-id
                        wrap-add-preview-for-media-file
          ;             media-files.authorization/ring-wrap-authorize-metadata-and-previews
                        ]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :query {(s/optional-key :size) s/Str}}}}]
   ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
