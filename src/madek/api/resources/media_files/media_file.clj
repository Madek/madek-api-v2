(ns madek.api.resources.media-files.media-file
  (:require
   [logbug.catcher :as catcher]
   [madek.api.constants]
   [madek.api.data-streaming :as data-streaming]
   [madek.api.resources.previews.index :as previews]
   [madek.api.resources.shared.core :as sd]))

(defn get-media-file [request]
  (if (= nil (:media-file request))
    (sd/response_not_found "No media-file for media_entry_id")
    (when-let [media-file (:media-file request)]
      {:status 200
       :body (conj (select-keys media-file [:id :size :created_at :updated_at
                                            :media_type :media_entry_id
                                            :filename :content_type])
                   {:previews (map #(select-keys % [:id :thumbnail :used_as_ui_preview])
                                   (previews/get-index media-file (:tx request)))})})))

(defn- media-file-path [media-file]
  (let [id (:guid media-file)
        [first-char] id]
    (clojure.string/join
     (java.io.File/separator)
     [madek.api.constants/FILE_STORAGE_DIR first-char id])))

(defn- preview-file-path [preview]
  (let [filename (:filename preview)
        [first-char] filename]
    (clojure.string/join
     (java.io.File/separator)
     [madek.api.constants/THUMBNAILS_STORAGE_DIR first-char filename])))

(defn- media-size-query [request]
  (some-> request :parameters :query :media_size str clojure.string/trim not-empty))

(defn- stream-original [media-file]
  (when-let [file-path (media-file-path media-file)]
    (data-streaming/respond-with-file file-path (:content_type media-file))))

(defn- stream-preview [request media-file media-size]
  (if-not (previews/normalize-media-size media-size)
    (sd/response_not_found "Invalid media_size")
    (if-let [preview (first (previews/filter-by-media-size
                             (previews/get-index media-file (:tx request))
                             media-size))]
      (when-let [file-path (preview-file-path preview)]
        (data-streaming/respond-with-file file-path (:content_type preview)))
      (sd/response_not_found "No preview for media_size"))))

(defn get-media-file-data-stream [request]
  (catcher/snatch {}
                  (when-let [media-file (:media-file request)]
                    (if-let [media-size (media-size-query request)]
                      (stream-preview request media-file media-size)
                      (stream-original media-file)))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
