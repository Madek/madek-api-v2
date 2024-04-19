(ns madek.api.resources.keywords.keyword
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.db.core :refer [get-ds]]
   [madek.api.pagination :as pagination]
   [madek.api.resources.shared :as sd]
   [next.jdbc :as jdbc]))

(defn db-keywords-get-one [id ds]
  (sd/query-eq-find-one :keywords :id id ds))

(defn db-keywords-query [query ds]
  (let [dbq (->
             (sql/select :*)
             (sql/from :keywords)
             (sd/build-query-param query :id)
             (sd/build-query-param query :rdf_class)
             (sd/build-query-param-like query :meta_key_id)
             (sd/build-query-param-like query :term)
             (sd/build-query-param-like query :description)
             (pagination/add-offset-for-honeysql query)
             sql-format)]
    ; (info "db-keywords-query" dbq)
    (jdbc/execute! ds dbq)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
