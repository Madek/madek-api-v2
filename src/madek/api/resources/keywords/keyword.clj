(ns madek.api.resources.keywords.keyword
  (:require
    [clojure.java.jdbc :as jdbc]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    [madek.api.utils.sql :as sql]
    [madek.api.resources.shared :as sd] 
    [madek.api.pagination :as pagination]
    ))


(defn db-keywords-get-one [id]
  (sd/query-eq-find-one :keywords :id id))

(defn db-keywords-query [query]
  (let [dbq (->
             (sql/select :*)
             (sql/from :keywords)
             (sd/build-query-param query :id)
             (sd/build-query-param query :rdf_class)
             (sd/build-query-param-like query :meta_key_id)
             (sd/build-query-param-like query :term)
             (sd/build-query-param-like query :description)
             (pagination/add-offset-for-honeysql query)
             sql/format)]
    ; (logging/info "db-keywords-query" dbq)
    (jdbc/query (get-ds) dbq)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
