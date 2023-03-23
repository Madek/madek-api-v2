(ns madek.api.resources.collections.index
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [madek.api.pagination :as pagination]
    [madek.api.resources.collections.advanced-filter.permissions :as permissions :refer [filter-by-query-params]]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    ))

;### collection_id ############################################################

(defn- filter-by-collection-id [sqlmap {:keys [collection_id] :as query-params}]
  (cond-> sqlmap
    (seq collection_id)
    (-> (sql/merge-join [:collection_collection_arcs :cca]
                        [:= :cca.child_id :collections.id])
        (sql/merge-where [:= :cca.parent_id collection_id]))))


;### query ####################################################################

(defn ^:private base-query [full-data]
  (let [toselect (if (true? full-data)
                   (sql/select :*)
                   (sql/select :collections.id, :collections.created_at))]
    (-> toselect
        (sql/from :collections)))
  )

(defn- set-order [query query-params]
  (if (some #{"desc"} [(-> query-params :order)])
    (-> query (sql/order-by [:collections.created-at :desc]))
    (-> query (sql/order-by [:collections.created-at :asc]))))

; TODO test query and paging
(defn- build-query [request]
  (let [query-params (:query-params request)
        authenticated-entity (:authenticated-entity request)]
    (logging/info "build-query" "\nquery\n" query-params)
    (-> (base-query (:full_data query-params))
        (set-order query-params)
        (filter-by-collection-id query-params)
        (permissions/filter-by-query-params query-params
                                            authenticated-entity)
        (pagination/add-offset-for-honeysql query-params)
        sql/format)))

(defn- query-index-resources [request]
  (jdbc/query (rdbms/get-ds) (build-query request)))

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
