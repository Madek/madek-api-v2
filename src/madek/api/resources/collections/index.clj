(ns madek.api.resources.collections.index
  (:require
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.collections.advanced-filter.permissions :as permissions]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.utils.helper :refer [normalize-fields]]
   [madek.api.utils.pagination :refer [pagination-handler]]
   [madek.api.utils.soft-delete :refer [non-soft-deleted soft-deleted]]))

;### collection_id ############################################################

(defn- filter-by-collection-id [sqlmap {:keys [collection_id] :as query-params}]
  (cond-> sqlmap
    (seq (str collection_id))
    (-> (sql/join [:collection_collection_arcs :cca]
                  [:= :cca.child_id :collections.id])
        (sql/where [:= :cca.parent_id collection_id]))))

;### query ####################################################################

(defn- base-query [fields softdelete-mode]
  (let [toselect (if (empty? fields)
                   (sql/select :collections.id :collections.created_at :collections.deleted_at)
                   (apply sql/select fields))]
    (-> toselect
        (sql/from :collections)
        (sql/order-by [:id :asc])
        (cond-> (= softdelete-mode :deleted) (soft-deleted "collections"))
        (cond-> (or (nil? softdelete-mode) (= softdelete-mode :not-deleted)) (non-soft-deleted "collections")))))

(defn- set-order [query query-params]
  (if (some #{"desc"} [(-> query-params :order)])
    (-> query (sql/order-by [:collections.id :desc]))
    (-> query (sql/order-by [:collections.id :asc]))))

; TODO test query and paging
(defn- build-query [request]
  (let [query-params (:query-params request)
        authenticated-entity (:authenticated-entity request)
        fields (normalize-fields request)
        softdelete-mode (:filter_softdelete query-params)
        sql-query (-> (base-query fields softdelete-mode)
                      (set-order query-params)
                      (dbh/build-query-param query-params :creator_id)
                      (dbh/build-query-param query-params :responsible_user_id)
                      (filter-by-collection-id query-params)
                      (permissions/filter-by-query-params query-params
                                                          authenticated-entity))]
    ;(logging/info "build-query"
    ;              "\nquery\n" query-params
    ;              "\nsql query:\n" sql-query)
    sql-query))

(defn- query-index-resources [request]
  (pagination-handler request (build-query request) :collections))

;### index ####################################################################

(defn get-index [request]
  (catcher/with-logging {}
    {:body
     (query-index-resources request)}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'filter-by-permissions)
;(debug/wrap-with-log-debug #'build-query)
