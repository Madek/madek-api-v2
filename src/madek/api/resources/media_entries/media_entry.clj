(ns madek.api.resources.media-entries.media-entry
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.utils.helper :refer [to-uuid]]
   [next.jdbc :as jdbc]))

(def ^:private media-entry-keys
  [:id :created_at
   :creator_id
   :responsible_user_id
   :is_published
   :updated_at :edit_session_updated_at :meta_data_updated_at])

(defn get-media-entry-for-preview [request]
  (let [preview-id (or (-> request :params :preview_id) (-> request :parameters :path :preview_id))
        query (-> (sql/select :media_entries.*)
                  (sql/from :media_entries)
                  (sql/join :media_files [:= :media_entries.id :media_files.media_entry_id])
                  (sql/join :previews [:= :media_files.id :previews.media_file_id])
                  (sql/where [:= :previews.id (to-uuid preview-id)])
                  (sql-format))
        dbresult (jdbc/execute-one! (:tx request) query)]
    ;(info "get-media-entry-for-preview" "\npreview-id\n" preview-id "\ndbresult\n" dbresult)
    dbresult))

(defn get-media-entry [request]
  (when-let [media-entry (:media-resource request)]
    {:body (select-keys media-entry media-entry-keys)}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
