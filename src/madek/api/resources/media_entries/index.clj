(ns madek.api.resources.media-entries.index
  (:refer-clojure :exclude [keyword str])
  (:require
   [clojure.set :refer [rename-keys]]
   [logbug.catcher :as catcher]
   [madek.api.anti-csrf.core :refer [keyword str]]
   [madek.api.resources.media-entries.permissions :as media-entry-perms]
   [madek.api.resources.media-entries.query :refer [query-index-resources build-query]]
   [madek.api.resources.media-files :as media-files]
   [madek.api.resources.meta-data.index :as meta-data.index]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.utils.helper :refer [normalize-fields]]
   [madek.api.utils.pagination :refer [pagination-handler is-with-pagination?]]))

;### index ####################################################################

(defn qualify-fields [parent fields]
  (let [fields (if (sequential? fields) fields [fields])]
    (mapv (fn [f]
            (keyword (str (name parent) "_" (name f))))
          fields)))

(defn- get-me-list [req data]
  (let [fields (normalize-fields req)
        me-list (if (empty? fields)
                  (->> data
                       (map #(select-keys % [:media_entry_id]))
                       (map #(rename-keys % {:media_entry_id :id})))
                  (->> data
                       (map #(select-keys % (qualify-fields :media_entry fields)))
                       (map #(rename-keys % {:media_entry_id :id
                                             :media_entry_created_at :created_at
                                             :media_entry_updated_at :updated_at
                                             :media_entry_edit_session_updated_at :edit_session_updated_at
                                             :media_entry_meta_data_updated_at :meta_data_updated_at
                                             :media_entry_creator_id :creator_id
                                             :media_entry_responsible_user_id :responsible_user_id
                                             :media_entry_is_published :is_published
                                             :media_entry_get_metadata_and_previews :get_metadata_and_previews
                                             :media_entry_get_full_size :get_full_size}))))]

;(info "get-me-list: fd: " full-data " list:" me-list)
    me-list))

(defn get-arc-list [data]
  (->> data
       (map #(select-keys % [:arc_id
                             :media_entry_id
                             :arc_order
                             :arc_position
                             :arc_created_at
                             :arc_updated_at]))
       (map #(rename-keys % {:arc_id :id
                             :arc_order :order
                             :arc_position :position
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

(defn build-result [collection-id req data]
  (let [me-list (get-me-list req data)
        result (merge
                {:media_entries me-list}
                (when collection-id
                  {:col_arcs (get-arc-list data)}))]
    result))

(defn build-result-related-data
  "Builds all the query result related data into the response:
  files, previews, meta-data for entries and a collection"
  [collection-id auth-entity data {:keys [tx] :as request}]
  (let [fields (normalize-fields request)
        request (assoc-in request [:parameters :query :fields] [])
        me-list (get-me-list [] data)
        selected-or-all? (fn [key fields] (or (some #{key} fields) (empty? fields)))
        result-me-list (when (selected-or-all? :media_entries fields)
                         (get-me-list request data))
        user-id (:id auth-entity)
        files (when (selected-or-all? :media_files fields)
                (get-files4me-list me-list auth-entity tx))
        previews (when (selected-or-all? :previews fields)
                   (get-preview-list me-list auth-entity tx))
        me-md (when (selected-or-all? :meta_data fields)
                (get-md4me-list me-list auth-entity tx))
        col-md (meta-data.index/get-collection-meta-data collection-id user-id tx)

        result (-> {}
                   (cond-> result-me-list (assoc :media_entries result-me-list))
                   (cond-> me-md (assoc :meta_data me-md))
                   (cond-> files (assoc :media_files files))
                   (cond-> previews (assoc :previews previews))
                   (cond-> collection-id
                     (assoc :col_meta_data col-md
                            :col_arcs (get-arc-list data))))]
    result))

(defn get-index [{{{collection-id :collection_id} :query} :parameters :as request}]
  (catcher/with-logging {}
    (let [is-with-pagination (is-with-pagination? request)
          after-fnc (if is-with-pagination
                      (fn [data] (:media_entries (build-result collection-id request data)))
                      (fn [data] (build-result collection-id request data)))
          result (pagination-handler request (build-query request) (if is-with-pagination :media_entries nil) after-fnc)]
      (sd/response_ok result))))

(defn get-index_related_data [{{{collection-id :collection_id full-data :full_data} :query} :parameters :as request}]
  (catcher/with-logging {}
    (let [auth-entity (-> request :authenticated-entity)
          data (query-index-resources request)
          result (build-result-related-data collection-id auth-entity data request)]
      (sd/response_ok result))))
;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'filter-by-permissions)
;(debug/wrap-with-log-debug #'set-order)
