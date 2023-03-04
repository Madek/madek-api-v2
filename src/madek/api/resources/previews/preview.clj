(ns madek.api.resources.previews.preview
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [madek.api.constants]
    [madek.api.data-streaming :as data-streaming]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    [madek.api.utils.sql :as sql]
    ))

(defn get-preview [request]
  (let [id (or (-> request :params :preview_id) (-> request :parameters :path :preview_id))
        query (-> (sql/select :*)
                  (sql/from :previews)
                  (sql/merge-where
                    [:= :previews.id id])
                  (sql/format))]
                  (logging/info "get-preview" "\nid\n" id)
    {:body (first (jdbc/query (rdbms/get-ds) query))}))

(defn- preview-file-path [preview]
  (let [; TODO why is this needed for compojure
        ;filename (:filename preview)
        ; TODO why is this needed for reitit
         filename (:filename preview)
        [first-char] filename]
    (clojure.string/join
      (java.io.File/separator)
      [madek.api.constants/THUMBNAILS_STORAGE_DIR first-char filename])))

(defn get-preview-file-data-stream [request]
  (catcher/snatch {}
    (when-let [preview (:preview request)]
      (logging/info "get-preview-file-ds" "\npreview\n" preview)
      (when-let [file-path (preview-file-path preview)]
        (logging/info "get-preview-file-ds" "\nfilepath\n" file-path)
        (data-streaming/respond-with-file file-path
                                          (:content_type preview))))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
