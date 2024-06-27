(ns madek.api.resources.media-entries.index
  (:refer-clojure :exclude [keyword str])
  (:require
   [clojure.set :refer [rename-keys]]
   [logbug.catcher :as catcher]
   [madek.api.resources.media-entries.permissions :as media-entry-perms]
   [madek.api.resources.media-entries.query :refer [query-index-resources]]
   [madek.api.resources.media-files :as media-files]
   [madek.api.resources.meta-data.index :as meta-data.index]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]))

;### index ####################################################################

(defn- get-me-list [full-data data]
  (let [me-list (if (true? full-data)
                  (->> data
                       (map #(select-keys % [:media_entry_id
                                             :media_entry_created_at
                                             :media_entry_updated_at
                                             :media_entry_edit_session_updated_at
                                             :media_entry_meta_data_updated_at
                                             :media_entry_creator_id
                                             :media_entry_responsible_user_id
                                             :media_entry_is_published
                                             :media_entry_get_metadata_and_previews
                                             :media_entry_get_full_size]))
                       (map #(rename-keys % {:media_entry_id :id
                                             :media_entry_created_at :created_at
                                             :media_entry_updated_at :updated_at
                                             :media_entry_edit_session_updated_at :edit_session_updated_at
                                             :media_entry_meta_data_updated_at :meta_data_updated_at
                                             :media_entry_creator_id :creator_id
                                             :media_entry_responsible_user_id :responsible_user_id
                                             :media_entry_is_published :is_published
                                             :media_entry_get_metadata_and_previews :get_metadata_and_previews
                                             :media_entry_get_full_size :get_full_size})))
                  ; else get only ids
                  (->> data
                       (map #(select-keys % [:media_entry_id]))
                       (map #(rename-keys % {:media_entry_id :id}))))]
    ;(info "get-me-list: fd: " full-data " list:" me-list)
    me-list))

(defn get-arc-list [data]
  (->> data
       (map #(select-keys % [:arc_id
                             :media_entry_id
                             :arc_order
                             :arc_position
                             :arc_highlight
                             :arc_cover
                             :arc_created_at
                             :arc_updated_at]))
       (map #(rename-keys % {:arc_id :id
                             :arc_order :order
                             :arc_position :position
                             :arc_highlight :highlight
                             :arc_cover :cover
                             :arc_created_at :created_at
                             :arc_updated_at :updated_at}))))

(defn- get-files4me-list [melist auth-entity tx]
  (let [auth-list (remove nil? (map #(when (true? (media-entry-perms/downloadable-by-auth-entity? % auth-entity tx))
                                       (media-files/query-media-file-by-media-entry-id (:id %) tx)) melist))]
    ;(info "get-files4me-list: \n" auth-list)
    auth-list))

(defn get-preview-list [melist auth-entity tx]
  (let [auth-list (map #(when (true? (media-entry-perms/viewable-by-auth-entity? % auth-entity tx))
                          (dbh/query-eq-find-all :previews :media_file_id
                                                 (:id (media-files/query-media-file-by-media-entry-id (:id %) tx)) tx)) melist)]
    ;(info "get-preview-list" auth-list)
    auth-list))

(defn get-md4me-list [melist auth-entity tx]
  (let [user-id (:id auth-entity)
        auth-list (map #(when (true? (media-entry-perms/viewable-by-auth-entity? % auth-entity tx))
                          (meta-data.index/get-media-entry-meta-data (:id %) user-id tx)) melist)]
    auth-list))

(defn build-result [collection-id full-data data]
  (let [me-list (get-me-list full-data data)
        result (merge
                {:media_entries me-list}
                (when collection-id
                  {:col_arcs (get-arc-list data)}))]
    result))

(defn build-result-related-data
  "Builds all the query result related data into the response:
  files, previews, meta-data for entries and a collection"
  [collection-id auth-entity full-data data tx]
  (let [me-list (get-me-list true data)
        result-me-list (get-me-list full-data data)
        user-id (:id auth-entity)
        ; TODO compute only on demand
        files (get-files4me-list me-list auth-entity tx)
        previews (get-preview-list me-list auth-entity tx)
        me-md (get-md4me-list me-list auth-entity tx)
        col-md (meta-data.index/get-collection-meta-data collection-id user-id tx)
        result (merge
                {:media_entries result-me-list
                  ; TODO add only on demand
                 :meta_data me-md
                 :media_files files
                 :previews previews}

                (when collection-id
                  {:col_meta_data col-md
                   :col_arcs (get-arc-list data)}))]
    result))

(defn get-index [{{{collection-id :collection_id full-data :full_data} :query} :parameters :as request}]
  ;(try
  (catcher/with-logging {}
    (let [data (query-index-resources request)
          result (build-result collection-id full-data data)]
      (sd/response_ok result)))
  ;(catch Exception e (sd/response_exception e)))
  )

(defn get-index_related_data [{{{collection-id :collection_id full-data :full_data} :query} :parameters :as request}]
  ;(try
  (catcher/with-logging {}
    (let [auth-entity (-> request :authenticated-entity)
          data (query-index-resources request)
          tx (:tx request)
          result (build-result-related-data collection-id auth-entity full-data data tx)]
      (sd/response_ok result)))
  ;(catch Exception e (sd/response_exception e)))
  )
;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'filter-by-permissions)
;(debug/wrap-with-log-debug #'set-order)
