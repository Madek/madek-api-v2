(ns madek.api.resources.groups.shared
  (:require
   [clj-uuid]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.utils.helper :refer [to-uuid]]
   [next.jdbc :as jdbc]))

(defn sql-merge-where-id
  ([group-id] (sql-merge-where-id {} group-id))
  ([sql-map group-id]
   (if (instance? java.util.UUID group-id)
     (sql/where sql-map [:or
                         [:= :groups.id (to-uuid group-id)]
                         [:= :groups.institutional_id (str group-id)]])
     (sql/where sql-map [:= :groups.institutional_id (str group-id)]))))

(defn jdbc-update-group-id-where-clause [id]
  (-> id sql-merge-where-id))

(defn find-group-sql [id]
  (-> (sql-merge-where-id id)
      (sql/select :*)
      (sql/from :groups)
      sql-format))

(defn find-group [id tx]
  (jdbc/execute-one! tx (find-group-sql id)))
