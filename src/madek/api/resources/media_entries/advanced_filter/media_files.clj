(ns madek.api.resources.media-entries.advanced-filter.media-files
  (:require
   [honey.sql.helpers :as sql]
   [madek.api.utils.helper :refer [to-uuid]]))

(def ^:private safe-identifier-pattern #"^[a-zA-Z_][a-zA-Z0-9_]*$")

(defn- sql-merge-where-media-file-spec [sqlmap media-file-spec]
  (let [k (:key media-file-spec)]
    (if (and (string? k) (re-matches safe-identifier-pattern k))
      (let [key-prefixed (keyword (str "media_files." k))
            val (to-uuid (:value media-file-spec) k)]
        (-> sqlmap
            (sql/where [:= key-prefixed val])))
      (throw (ex-info (str "Invalid media_files filter key: " k)
                      {:status 422})))))

(defn sql-filter-by [sqlmap media-file-specs]
  (if-not (empty? media-file-specs)
    (reduce sql-merge-where-media-file-spec
            (-> sqlmap
                (sql/join
                 :media_files
                 [:= :media_files.media_entry_id :media_entries.id]))
            media-file-specs)
    sqlmap))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'filter-by-permissions)
;(debug/wrap-with-log-debug #'build-query)
