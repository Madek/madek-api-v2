(ns madek.api.resources.keywords.keyword
  (:require
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.db_helper :as dbh]
               [madek.api.utils.helper :refer [gen-from-order-by]]
[madek.api.utils.order-by :refer [->lookup-order-by]]
))

(defn db-keywords-get-one [id tx]
  (dbh/query-eq-find-one :keywords :id id tx))

(defn db-keywords-query [query tx]
  (let [dbq (->
             (sql/select :*)

             ;(sql/from :keywords)
             ;(gen-from-order-by :keywords)
             (->lookup-order-by :keywords)

             (dbh/build-query-param query :id)
             (dbh/build-query-param query :rdf_class)
             (dbh/build-query-param-like query :meta_key_id)
             (dbh/build-query-param-like query :term)
             (dbh/build-query-param-like query :description))]
    dbq))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
