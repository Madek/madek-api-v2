(ns madek.api.resources.groups.shared
  (:require
    [clj-uuid]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    ))


(defn sql-merge-where-id
  ([group-id] (sql-merge-where-id {} group-id))
  ([sql-map group-id]
   (if (re-matches
         #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
         group-id)
     (sql/merge-where sql-map [:or
                               [:= :groups.id group-id]
                               [:= :groups.institutional_id group-id]])
     (sql/merge-where sql-map [:= :groups.institutional_id group-id]))))

(defn jdbc-update-group-id-where-clause [id]
  (-> id sql-merge-where-id sql/format
      (update-in [0] #(clojure.string/replace % "WHERE" ""))))

(defn find-group-sql [id]
  (-> (sql-merge-where-id id)
      (sql/select :*)
      (sql/from :groups)
      sql/format))

(defn find-group [id]
  (->> id find-group-sql
       (jdbc/query (rdbms/get-ds)) first))

