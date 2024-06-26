(ns madek.api.resources.keywords.keyword
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.pagination :as pagination]
   [madek.api.resources.shared.db_helper :as dbh]
   [next.jdbc :as jdbc]))

(defn db-keywords-get-one [id tx]
  (dbh/query-eq-find-one :keywords :id id tx))

(defn db-keywords-query [query tx]
  (let [dbq (->
             (sql/select :*)
             (sql/from :keywords)
             (dbh/build-query-param query :id)
             (dbh/build-query-param query :rdf_class)
             (dbh/build-query-param-like query :meta_key_id)
             (dbh/build-query-param-like query :term)
             (dbh/build-query-param-like query :description)
             (pagination/sql-offset-and-limit query)
             sql-format)]
    ; (info "db-keywords-query" dbq)
    (jdbc/execute! tx dbq)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
