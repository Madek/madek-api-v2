(ns madek.api.resources.previews.index
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc :as jdbc]))

(defn- get-first-or-30-percent [list]
  (if (> (count list) 1)
    (nth list (min (Math/ceil (* (/ (count list) 10.0) 3)) (- (count list) 1)))
    (first list)))

(defn- detect-ui-preview-id [sqlmap media-type tx]
  (if (= media-type "video")
    (let [query (-> sqlmap
                    (sql/where [:= :media_type "image"]
                               [:= :thumbnail "large"])
                    (sql/order-by [:previews.filename :asc] [:previews.created_at :desc])
                    sql-format)]
      (let [previews (jdbc/execute! tx query)]
        (:id (get-first-or-30-percent previews))))
    nil))

(defn- add-preview-pointer-to [previews detected-id]
  (map #(if (= (:id %) detected-id) (assoc % :used_as_ui_preview true) %) previews))

(defn get-index [media-file tx]
  (let [sqlmap (-> (sql/select :previews.*)
                   (sql/from [:previews :previews])
                   (sql/join [:media_files :media_files] [:= :previews.media_file_id :media_files.id])
                   (sql/join [:media_entries :media_entries] [:= :media_entries.id :media_files.media_entry_id])
                   (sql/where [:= :previews.media_file_id (:id media-file)])
                   (sql/order-by [:previews.created_at :desc]))]
    (let [detected-id (detect-ui-preview-id sqlmap (:media_type media-file) tx)]
      (add-preview-pointer-to
       (jdbc/execute! tx (-> sqlmap sql-format))
       detected-id))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
