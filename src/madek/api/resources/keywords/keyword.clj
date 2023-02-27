(ns madek.api.resources.keywords.keyword
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    [madek.api.utils.sql :as sql]
    [madek.api.resources.shared :as shared]
    [schema.core :as s]
    ))

(defn find-keyword-sql
  [id]
  (->
    (sql/select :*)
    (sql/from :keywords)
    (sql/merge-where [:= :keywords.id id])
    sql/format))

(defn get-keyword
  [request]
  (let [id
          (->
            request
            :params
            :id)
        keyword (first (jdbc/query (rdbms/get-ds) (find-keyword-sql id)))]
    {:body
       (->
         keyword
         (select-keys
           [:id :meta_key_id :term :description :external_uris :rdf_class
            :created_at])
         (assoc ; support old (singular) version of field
           :external_uri (first (keyword :external_uris))))}))

(defn query-keywords-sql
  []
  (->
   (sql/select :*)
   (sql/from :keywords)
   sql/format))

(defn db-keywords-get-one [id]
  (first (jdbc/query (rdbms/get-ds) (find-keyword-sql id))))

(defn db-keywords-query [query]
  (jdbc/query (rdbms/get-ds) (query-keywords-sql) ))

(defn export-keyword [keyword]
  (->
   keyword
   (select-keys
    [:id :meta_key_id :term :description :external_uris :rdf_class
     :created_at])
   (assoc ; support old (singular) version of field
    :external_uri (first (keyword :external_uris)))))

(def schema_export_keyword
  {:id s/Uuid
   :meta_key_id s/Str
   :term s/Str
   :description (s/maybe s/Str)
   :external_uris s/Any
   :external_uri s/Str
   :rdf_class s/Str
   :created_at s/Any}) ; TODO use s/Inst

(defn handle_get-keyword
  [request]
  (let [id (shared/get-path-params request :id) 
        keyword (db-keywords-get-one id)]
    (if (= keyword nil) 
      {:status 404 :body {:msg (apply str ["No such keyword (" id ")"])}}
      {:status 200 :body (export-keyword keyword)})))

(defn handle_query-keywords [request]
  (let [ds (:db request)
        result (db-keywords-query ds)]
    {:status 200 :body { :keywords result}}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
