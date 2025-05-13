(ns madek.api.resources.previews.preview
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.constants]
   [madek.api.data-streaming :as data-streaming]
   [next.jdbc :as jdbc]
   [taoensso.timbre :refer [info]]))

(defn db-get-preview [id tx]
  (let [query (-> (sql/select :*)
                  (sql/from :previews)
                  (sql/order-by [:id :asc])
                  (sql/where [:= :previews.id id])
                  sql-format)]
    (jdbc/execute-one! tx query)))

(defn get-preview [request]
  (let [id (-> request :parameters :path :preview_id)
        tx (:tx request)
        result (db-get-preview id tx)]
    (info "get-preview" "\nid\n" id "\nresult\n" result)
    {:body result}))

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
                    (info "get-preview-file-ds" "\npreview\n" preview)
                    (when-let [file-path (preview-file-path preview)]
                      (info "get-preview-file-ds" "\nfilepath\n" file-path)
                      (data-streaming/respond-with-file file-path
                                                        (:content_type preview))))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
