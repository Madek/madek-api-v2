(ns madek.api.resources.keywords.keyword
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
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
             (sd/build-query-param-like query :meta_key_id)
             (sd/build-query-param-like query :term)
             (sd/build-query-param-like query :description)
             (pagination/add-offset-for-honeysql query)
             sql/format)]
    ; (logging/info "db-keywords-query" dbq)
    (jdbc/query (get-ds) dbq)))

(defn db-keywords-create [data]
  (->> (jdbc/insert! (get-ds) :keywords data) first))

(defn db-keywords-update [id data]
  (if-let [upd-res (jdbc/update!
                    (get-ds) :keywords data
                    (sd/sql-update-clause "id" id))]
    (db-keywords-get-one id)
    nil))

(defn db-keywords-delete [id]
  (if-let [data (db-keywords-get-one id)]
    (if-let [del-res (jdbc/delete!
                      (get-ds) :keywords
                      (sd/sql-update-clause "id" id))]
      data
      nil)
    nil))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
