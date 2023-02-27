(ns madek.api.resources.roles.role
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    [madek.api.utils.sql :as sql]
    [madek.api.resources.shared :as shared]
    [schema.core :as s]
    ))

(defn get-role [request]
  (let [id (-> request :params :id)
        query (-> (sql/select :*)
                  (sql/from :roles)
                  (sql/merge-where
                    [:= :roles.id id])
                  (sql/format))]
    {:body (select-keys (first (jdbc/query (rdbms/get-ds) query))
                        [:id
                         :labels
                         :created_at])}))

(defn query_role-find-one [id]
  (-> (sql/select :*)
      (sql/from :roles)
      (sql/merge-where
       [:= :roles.id id])
      (sql/format)))

(defn db_role-find-one [id]
  (let [query (query_role-find-one id)
        resultdb (first (jdbc/query (rdbms/get-ds) query))]
    resultdb
    ))

(defn export_role [db-role]
  (select-keys db-role [:id :labels :created_at]))

(def schema_export-role
  {:id s/Uuid
   :labels s/Any;{{s/Any s/Any}}
   :created_at s/Any
   })

(defn handle_get-role [request]
  (let [id (shared/get-path-params request :id)
        resultdb (db_role-find-one id)
        result (export_role resultdb)]
    {:status 200 :body result}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
