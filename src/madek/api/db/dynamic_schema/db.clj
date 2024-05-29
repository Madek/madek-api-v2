(ns madek.api.db.dynamic_schema.db
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.db.core :refer [get-ds]]
   [next.jdbc :as jdbc]))

(defn fetch-enum [enum-name]
  (let [ds (get-ds)]
    (try (jdbc/execute! ds
                        (-> (sql/select :enumlabel)
                            (sql/from :pg_enum)
                            (sql/join :pg_type [:= :pg_enum.enumtypid :pg_type.oid])
                            (sql/where [:= :pg_type.typname enum-name])
                            sql-format))
         (catch Exception e
           (throw (Exception. "Unable to establish a database connection"))))))

(defn fetch-table-metadata [table-name]
  (let [ds (get-ds)]
    (try (jdbc/execute! ds
                        (-> (sql/select :column_name :data_type)
                            (sql/from :information_schema.columns)
                            (sql/where [:= :table_name table-name])
                            sql-format))
         (catch Exception e
           (throw (Exception. "Unable to establish a database connection"))))))