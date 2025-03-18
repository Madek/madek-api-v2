(ns madek.api.resources.media-entries.query
  (:refer-clojure :exclude [keyword str])
  (:require
   [cheshire.core :as json]
   [clojure.core.match :refer [match]]
   [clojure.string :as str :refer [blank?]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.media-entries.advanced-filter :as advanced-filter]
   [madek.api.resources.media-entries.advanced-filter.permissions :as permissions]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [madek.api.utils.core :refer [keyword str]]
   [madek.api.utils.helper :refer [to-uuid]]
   [madek.api.utils.soft-delete :refer [non-soft-deleted soft-deleted]]
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

(defn- base-query [me-query softdelete-mode]
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
        where4 (cond
                 (= softdelete-mode :deleted) (soft-deleted where3 "media_entries")
                 (or (nil? softdelete-mode) (= softdelete-mode :not-deleted)) (non-soft-deleted where3 "media_entries"))

; TODO updated/created after
        from (sql/from where4 :media_entries)]
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

(defn- find-collection-default-sorting [collection-id tx]
  (let [query (-> (sql/select :sorting)
                  (sql/from :collections)
                  (sql/where [:= :collections.id collection-id])
                  sql-format)]
    (:sorting (jdbc/execute-one! tx query))))

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
    "last_change_desc" (order-by-media-entry-attribute query [:edit_session_updated_at :desc])
    "last_change_asc" (order-by-media-entry-attribute query [:edit_session_updated_at :asc])
    "manual_asc" (handle-missing-collection-id collection-id (order-by-arc-attribute query [:position :asc]))
    "manual_desc" (handle-missing-collection-id collection-id (order-by-arc-attribute query [:position :desc]))))

(def ^:private available-sortings '("desc" "asc" "title_asc" "title_desc"
                                           "last_change_desc" "last_change_asc" "manual_asc" "manual_desc"))

(defn- default-order [query]
  (sql/order-by query [:media_entries.created_at :asc]))

(defn- order-by-collection-sorting [query collection-id tx]
  (handle-missing-collection-id collection-id
                                (if-let [sorting (find-collection-default-sorting collection-id tx)]
                                  (let [prepared-sorting (->> (str/split (str/replace sorting "created_at " "") #" ")
                                                              (str/join "_") str/lower-case)]
                                    (order-by-string query prepared-sorting collection-id))
                                  (sql/order-by query [:media_entries.created_at :asc]))))

(def ^:private not-allowed-order-param-message
  (str "only the following values are allowed as order parameter: "
       (str/join ", " available-sortings) " and stored_in_collection"))

(defn- set-order [query query-params tx]
  (-> (let [qorder (-> query-params :order)
            order (jqh/try-as-json qorder)
            collection-id (-> query-params :collection_id)
            result (cond
                     (nil? order) (default-order query)
                     (string? order) (cond
                                       (some #(= order %) available-sortings) (order-by-string query order collection-id)
                                       (= order "stored_in_collection") (order-by-collection-sorting query collection-id tx)
                                       :else (throw (ex-info not-allowed-order-param-message
                                                             {:status 422})))
                     (seq? order) (reduce order-reducer query order)
                     :else (default-order query))]
        (info "set-order" "\norder\n" order)
        result)
      (sql/order-by :media_entries.id)))

; TODO test query and paging
(defn build-query [request]
  (let [query-params (-> request :parameters :query)
        filter-by (json/decode (:filter_by query-params) true)
        props-by (:media_entry filter-by)
        softdelete-mode (:filter_softdelete query-params)
        tx (:tx request)
        authenticated-entity (:authenticated-entity request)
        query-res (-> (base-query props-by softdelete-mode)
                      (set-order query-params tx)
                      (filter-by-collection-id query-params)
                      (permissions/filter-by-query-params query-params authenticated-entity)
                      (advanced-filter/filter-by filter-by tx))]

    ;    (info "build-query"
    ;                  "\nquery-params:\n" query-params
    ;                  "\nfilter-by json:\n" filter-by
    ;                  "\nquery-res:\n" query-res)
    query-res))

(defn query-index-resources [request]
  (jdbc/execute! (:tx request) (-> (build-query request) sql-format)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'filter-by-permissions)
;(debug/wrap-with-log-debug #'set-order)
