(ns madek.api.resources.keywords.keyword
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    [madek.api.utils.sql :as sql]
    [madek.api.resources.shared :as sd]
    
    [madek.api.pagination :as pagination]))


(defn- base-query [full-data]
  (let [sel (if (true? full-data)
              (sql/select :*)
              (sql/select :id :meta_key_id :term)
              )]
    (-> sel (sql/from :keywords)))
  )

(defn find-keyword-sql
  [id]
  (->
    (base-query true)
    (sql/merge-where [:= :keywords.id id])
    sql/format))

(defn get-keyword
  [request]
  (let [id (-> request :params :id)
        keyword (first (jdbc/query (rdbms/get-ds) (find-keyword-sql id)))]
    {:body
       (->
         keyword
         (select-keys
           [:id :meta_key_id :term :description :external_uris :rdf_class
            :created_at])
         (assoc ; support old (singular) version of field
           :external_uri (first (keyword :external_uris))))}))

; TODO test query and paging
(defn query-keywords-sql
  [query]
  (let [full-data (-> query :full-data)]
    (->
     (base-query full-data)
     (sd/build-query-param-like query :meta_key_id)
     (sd/build-query-param-like query :term)
     (sd/build-query-param-like query :description)
     (pagination/add-offset-for-honeysql query)
     sql/format)
    )
  )

(defn db-keywords-get-one [id]
  (first (jdbc/query (get-ds) (find-keyword-sql id))))

(defn db-keywords-query [query]
  (let [dbq (query-keywords-sql query)]
    ; (logging/info "db-keywords-query" dbq)
    (jdbc/query (get-ds) dbq)))

(defn export-keyword [keyword]
  (->
   keyword
   (select-keys
    [:id :meta_key_id :term :description :external_uris :rdf_class
     :created_at])
   (assoc ; support old (singular) version of field
    :external_uri (first (keyword :external_uris)))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
