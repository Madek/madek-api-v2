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

(defn get-media-file-data-stream [request]
  (catcher/snatch {}
                  (when-let [media-file (:media-file request)]
                    (when-let [file-path (media-file-path media-file)]
                      (data-streaming/respond-with-file file-path
                                                        (:content_type media-file))))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
