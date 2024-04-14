(ns madek.api.resources.media-entries.index
  (:refer-clojure :exclude [keyword str])
  (:require
   [cheshire.core :as json]
   [clojure.core.match :refer [match]]
   [clojure.set :refer [rename-keys]]
   [clojure.string :as str :refer [blank?]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [logbug.debug :refer [I> identity-with-logging]]
   [madek.api.db.core :refer [get-ds]]
   [madek.api.pagination :as pagination]
   [madek.api.resources.media-entries.advanced-filter :as advanced-filter]
   [madek.api.resources.media-entries.advanced-filter.permissions :as permissions]
   [madek.api.resources.media-entries.permissions :as media-entry-perms]
   [madek.api.resources.media-files :as media-files]
   [madek.api.resources.meta-data.index :as meta-data.index]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.core :refer [keyword str]]
   [madek.api.utils.helper :refer [to-uuid]]
   [next.jdbc :as jdbc]
   [taoensso.timbre :refer [info]]))

;### collection_id ############################################################

(defn- filter-by-collection-id [sqlmap {:keys [collection_id] :as query-params}]
  (if-not collection_id
    sqlmap
    (-> sqlmap
        (sql/join [:collection_media_entry_arcs :arcs]
                  [:= :arcs.media_entry_id :media_entries.id])
        (sql/where [:= :arcs.collection_id (to-uuid collection_id)])
        (sql/select
         [:arcs.created_at :arc_created_at]
         [:arcs.order :arc_order]
         [:arcs.position :arc_position]
         [:arcs.created_at :arc_created_at]
         [:arcs.updated_at :arc_updated_at]
         [:arcs.id :arc_id]))))

;### query ####################################################################

(defn ^:private base-query [me-query]
  ; TODO make full-data selectable
  (let [sel (sql/select [:media_entries.id :media_entry_id]
                        [:media_entries.created_at :media_entry_created_at]
                        [:media_entries.updated_at :media_entry_updated_at]
                        [:media_entries.edit_session_updated_at :media_entry_edit_session_updated_at]
                        [:media_entries.meta_data_updated_at :media_entry_meta_data_updated_at]
                        [:media_entries.is_published :media_entry_is_published]
                        [:media_entries.get_metadata_and_previews :media_entry_get_metadata_and_previews]
                        [:media_entries.get_full_size :media_entry_get_full_size]
                        [:media_entries.creator_id :media_entry_creator_id]
                        [:media_entries.responsible_user_id :media_entry_responsible_user_id])
        is-pub (:is_published me-query)
        where1 (if (nil? is-pub)
                 sel
                 (sql/where sel [:= :media_entries.is_published (= true is-pub)]))
        creator-id (:creator_id me-query)
        where2 (if (blank? creator-id) ; or not uuid
                 where1
                 (sql/where where1 [:= :media_entries.creator_id creator-id]))
        ru-id (:responsible_user_id me-query)
        where3 (if (blank? ru-id) ; or not uuid
                 where2
                 (sql/where where2 [:= :media_entries.responsible_user_id ru-id]))

        ; TODO updated/created after
        from (sql/from where3 :media_entries)
;        orig-query (-> (sql/select [:media_entries.id :media_entry_id]
;                                   [:media_entries.created_at :media_entry_created_at])
;                       (sql/from :media_entries))
        ]
;    (info "base-query"
;                  "\nme-query:\n" me-query
;                  "\nfrom:\n" sel
;                  "\nwhere1:\n" where1
;                  "\nwhere2:\n" where2
;                  "\nwhere3:\n" where3
;                  "\nresult:\n" from
;                  ;"\norig:\n" orig-query
;                  )
    from))

(defn- order-by-media-entry-attribute [query [attribute order]]
  (let [order-by-arg (match [(keyword attribute) (keyword order)]
                       [:created_at :desc] [:media-entries.created_at :desc-nulls-last]
                       [:created_at _] [:media-entries.created_at]
                       [:edit_session_updated_at _] [:media_entries.edit_session_updated_at])]
    (sql/order-by query order-by-arg)))

(defn- order-by-arc-attribute [query [attribute order]]
  (let [order-by-arg (match [(keyword attribute) (keyword order)]
                       [:order :desc] [:arcs.order :desc-nulls-last]
                       [:order _] [:arcs.order]
                       [:position :asc] [:arcs.position :asc]
                       [:position :desc] [:arcs.position :desc-nulls-last]
                       [:created_at :desc] [:arcs.created_at :desc-nulls-last]
                       [:created_at _] [:arcs.created_at])]
    (sql/order-by query order-by-arg)))

(defn- order-by-meta-datum-text [query [meta-key-id order]]
  (let [from-name (-> meta-key-id
                      (clojure.string/replace #"\W+" "_")
                      clojure.string/lower-case
                      (#(str "meta_data_" %)))
        keyword1 (keyword (str from-name ".meta_key_id"))
        keyword2 (keyword (str from-name ".media_entry_id"))]
    (-> query
        (sql/left-join [:meta_data from-name]
                       [:= keyword1 meta-key-id])
        (sql/order-by [(-> from-name (str ".string") keyword)
                       (case (keyword order)
                         :asc :asc-nulls-last
                         :desc :desc-nulls-last
                         :asc-nulls-last)])
        (sql/where [:= keyword2 :media_entries.id]))))

(defn- order-reducer [query [scope & more]]
  (case scope
    "media_entry" (order-by-media-entry-attribute query more)
    "arc" (order-by-arc-attribute query more)
    "MetaDatum::Text" (order-by-meta-datum-text query more)))

(defn- order-by-title [query order]
  (let [direction (-> (str/split order #"_") (last))]
    (reduce order-reducer [query ["MetaDatum::Text" "madek_core:title" direction]])))

(defn- find-collection-default-sorting [collection-id]
  (let [query (-> (sql/select :sorting)
                  (sql/from :collections)
                  (sql/where [:= :collections.id collection-id])
                  sql-format)]
    (:sorting (jdbc/execute-one! (get-ds) query))))

(defn- handle-missing-collection-id [collection-id code-to-run]
  (if (or (not collection-id) (nil? collection-id))
    (throw (ex-info "collection_id param must be given" {:status 422}))
    code-to-run))

(defn- order-by-string [query order collection-id]
  (case order
    "asc" (sql/order-by query [:media_entries.created_at (keyword order)])
    "desc" (sql/order-by query [:media_entries.created_at (keyword order)])
    "title_asc" (order-by-title query order)
    "title_desc" (order-by-title query order)
    "last_change" (order-by-media-entry-attribute query [:edit_session_updated_at :asc])
    "manual_asc" (handle-missing-collection-id collection-id (order-by-arc-attribute query [:position :asc]))
    "manual_desc" (handle-missing-collection-id collection-id (order-by-arc-attribute query [:position :desc]))))

(def ^:private available-sortings '("desc" "asc" "title_asc" "title_desc"
                                           "last_change" "manual_asc" "manual_desc"))

(defn- default-order [query]
  (sql/order-by query [:media_entries.created_at :asc]))

(defn- order-by-collection-sorting [query collection-id]
  (handle-missing-collection-id collection-id
                                (if-let [sorting (find-collection-default-sorting collection-id)]
                                  (let [prepared-sorting (->> (str/split (str/replace sorting "created_at " "") #" ") (str/join "_") str/lower-case)]
                                    (order-by-string query prepared-sorting collection-id))
                                  (sql/order-by query [:media_entries.created_at :asc]))))

(def ^:private not-allowed-order-param-message
  (str "only the following values are allowed as order parameter: "
       (str/join ", " available-sortings) " and stored_in_collection"))

(defn- set-order [query query-params]
  (-> (let [qorder (-> query-params :order)
            order (sd/try-as-json qorder)
            collection-id (-> query-params :collection_id)
            result (cond
                     (nil? order) (default-order query)
                     (string? order) (cond
                                       (some #(= order %) available-sortings) (order-by-string query order collection-id)
                                       (= order "stored_in_collection") (order-by-collection-sorting query collection-id)
                                       :else (throw (ex-info not-allowed-order-param-message
                                                             {:status 422})))
                     (seq? order) (reduce order-reducer query order)
                     :else (default-order query))]
        (info "set-order" "\norder\n" order)
        result)
      (sql/order-by :media_entries.id)))

; example queries
;{"meta_data": [{"key": "madek_core:title", "match": "Bildshirmfoto"}],
; "permissions": [{"key":"public","value":"false"}]
; "media-entry": [{"key":"is_published", "value": "true"}
;                 {"key":"creator-id", "value": "" }
;                 {"key": "responsible_user_id", "value":""}]
;}
; test {"meta_data":[{"key":"any","match":"nitai"}]}
; test2 {"meta_data":[{"key":"test:string","match":"par tial"},
;                     {"key":"filter:7cq5ila0xxqlrc7wod2g","value":"3768574c-d4d8-4fac-ad73-0e2dbd4cc443"},
;                     {"key":"test:licenses"},
;                     {"not_key":"filter:1vq1h2t25y92yq8ojg11"},
;                     {"key":"test:people","value":"9f70e42c-8d01-4b2b-8f10-2719921797fc"},
;                     {"key":"any","type":"MetaDatum::Keywords","value":"694b858d-e8eb-4e51-8bee-f55fd8a0491b"}],
;        "media_files":[{"key":"content_type","value":"image/jpeg"},
;                     {"key":"uploader_id","value":"7bf54b03-42e5-4dc8-8a36-309ca9b1563f"},
;                     {"key":"extension","value":"jpg"}],
;        "permissions":[{"key":"responsible_user","value":"935e9257-b7a7-4783-bb11-553907ca67f6"},
;                     {"key":"public","value":"true"},
;                     {"key":"entrusted_to_user","value":"73fbb710-eedc-481d-a411-692705decd09"},
;                     {"key":"entrusted_to_group","value":"e8b962f6-df73-4b6f-b2b6-3f71230cd0aa"}]}

; TODO test query and paging
(defn- build-query [request]
  (let [query-params (-> request :parameters :query)
        filter-by (json/decode (:filter_by query-params) true)
        props-by (:media_entry filter-by)
        authenticated-entity (:authenticated-entity request)
        query-res (I> identity-with-logging
                      (base-query props-by)
                      (set-order query-params)
                      (filter-by-collection-id query-params)
                      (permissions/filter-by-query-params query-params
                                                          authenticated-entity)
                      (advanced-filter/filter-by filter-by)
                      (pagination/add-offset-for-honeysql query-params))
        query-res (-> query-res sql-format)]

;    (info "build-query"
;                  "\nquery-params:\n" query-params
;                  "\nfilter-by json:\n" filter-by
;                  "\nquery-res:\n" query-res)
    query-res))

(defn- query-index-resources [request]
  (jdbc/execute! (get-ds) (build-query request)))

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
                             :arc_created_at
                             :arc_updated_at]))
       (map #(rename-keys % {:arc_id :id
                             :arc_order :order
                             :arc_position :position
                             :arc_created_at :created_at
                             :arc_updated_at :updated_at}))))

(defn- get-files4me-list [melist auth-entity]
  (let [auth-list (remove nil? (map #(when (true? (media-entry-perms/downloadable-by-auth-entity? % auth-entity))
                                       (media-files/query-media-file-by-media-entry-id (:id %))) melist))]
    ;(info "get-files4me-list: \n" auth-list)
    auth-list))

(defn get-preview-list [melist auth-entity]
  (let [auth-list (map #(when (true? (media-entry-perms/viewable-by-auth-entity? % auth-entity))
                          (sd/query-eq-find-all :previews :media_file_id
                                                (:id (media-files/query-media-file-by-media-entry-id (:id %))))) melist)]
    ;(info "get-preview-list" auth-list)
    auth-list))

(defn get-md4me-list [melist auth-entity]
  (let [user-id (:id auth-entity)
        auth-list (map #(when (true? (media-entry-perms/viewable-by-auth-entity? % auth-entity))
                          (meta-data.index/get-media-entry-meta-data (:id %) user-id)) melist)]
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
  [collection-id auth-entity full-data data]
  (let [me-list (get-me-list true data)
        result-me-list (get-me-list full-data data)
        user-id (:id auth-entity)
        ; TODO compute only on demand
        files (get-files4me-list me-list auth-entity)
        previews (get-preview-list me-list auth-entity)
        me-md (get-md4me-list me-list auth-entity)
        col-md (meta-data.index/get-collection-meta-data collection-id user-id)
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
          result (build-result-related-data collection-id auth-entity full-data data)]
      (sd/response_ok result)))
     ;(catch Exception e (sd/response_exception e)))
  )
;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'filter-by-permissions)
;(debug/wrap-with-log-debug #'set-order)
