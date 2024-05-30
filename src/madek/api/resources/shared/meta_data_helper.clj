(ns madek.api.resources.shared.meta_data_helper
  (:require [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.utils.helper :refer [to-uuid]]
            [madek.api.resources.shared.media_resource_helper :as mrh]
            [next.jdbc :as jdbc]
            [taoensso.timbre :refer [info]]))

; begin meta-data helpers

(defn query-meta-datum [request]
  (let [id (-> request :parameters :path :meta_datum_id)]
    #_(info "query-meta-datum" "\nid\n" id)
    (or
      (jdbc/execute-one! (:tx request)
        (-> (sql/select :*)
            (sql/from :meta_data)
            (sql/where [:= :id (to-uuid id)])
            sql-format))

      (throw (IllegalStateException. (str "We expected to find a MetaDatum for "
                                          id " but did not."))))))

(defn query-media-resource-for-meta-datum [meta-datum tx]
  (or (when-let [id (:media_entry_id meta-datum)]
        (mrh/get-media-resource {:parameters {:path {:media_entry_id id}}}
          :media_entry_id "media_entries" "MediaEntry" tx))
    (when-let [id (:collection_id meta-datum)]
      (mrh/get-media-resource {:parameters {:path {:collection_id id}}}
        :collection_id "collections" "Collection" tx))
    (throw (IllegalStateException. (str "Getting the resource for "
                                        meta-datum "
                                          is not implemented yet.")))))

(defn ring-add-meta-datum-with-media-resource [request handler]
  (if-let [meta-datum (query-meta-datum request)]
    (let [media-resource (query-media-resource-for-meta-datum meta-datum (:tx request))]
      ;(info "add-meta-datum-with-media-resource" "\nmeta-datum\n" meta-datum "\nmedia-resource\n" media-resource)
      (handler (assoc request
                      :meta-datum meta-datum
                      :media-resource media-resource)))
    (handler request)))

; end meta-data helpers

;(debug/debug-ns *ns*)