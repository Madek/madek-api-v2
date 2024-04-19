(ns madek.api.resources.collections.index
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.pagination :as pagination]
   [madek.api.resources.collections.advanced-filter.permissions :as permissions]
   [madek.api.resources.shared :as sd]
   [next.jdbc :as jdbc]))

;### collection_id ############################################################

(defn- filter-by-collection-id [sqlmap {:keys [collection_id] :as query-params}]
  (cond-> sqlmap
    (seq (str collection_id))
    (-> (sql/join [:collection_collection_arcs :cca]
                  [:= :cca.child_id :collections.id])
        (sql/where [:= :cca.parent_id collection_id]))))

;### query ####################################################################

(defn ^:private base-query [full-data]
  (let [toselect (if (true? full-data)
                   (sql/select :*)
                   (sql/select :collections.id, :collections.created_at))]
    (-> toselect
        (sql/from :collections))))

(defn- set-order [query query-params]
  (if (some #{"desc"} [(-> query-params :order)])
    (-> query (sql/order-by [:collections.created_at :desc]))
    (-> query (sql/order-by [:collections.created_at :asc]))))

; TODO test query and paging
(defn- build-query [request]
  (let [query-params (:query-params request)
        authenticated-entity (:authenticated-entity request)
        full_data (= true (:full_data query-params))
        sql-query (-> (base-query full_data)
                      (set-order query-params)
                      (sd/build-query-param query-params :creator_id)
                      (sd/build-query-param query-params :responsible_user_id)
                      (filter-by-collection-id query-params)
                      (permissions/filter-by-query-params query-params
                                                          authenticated-entity)
                      (pagination/add-offset-for-honeysql query-params)
                      sql-format)]
    ;(logging/info "build-query"
    ;              "\nquery\n" query-params
    ;              "\nsql query:\n" sql-query)
    sql-query))

(defn- query-index-resources [request]
  (jdbc/execute! (:tx request) (build-query request)))

;### index ####################################################################

(defn get-index [request]
  (catcher/with-logging {}
    {:body
     {:collections
      (query-index-resources request)}}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'filter-by-permissions)
;(debug/wrap-with-log-debug #'build-query)
