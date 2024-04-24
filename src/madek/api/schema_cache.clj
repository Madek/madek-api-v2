(ns madek.api.schema_cache
  (:require
   ;; all needed imports
   ;[leihs.core.db :as db]

   [madek.api.db.core :refer [get-ds]]

   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]




   ;[madek.api.utils.helper :refer [merge-query-parts to-uuids]]

   [next.jdbc :as jdbc]
   ; [taoensso.timbre :as timbre]
   ; [clojure.core.cache :as cache]
   [schema.core :as s])
  )



(def cache (atom {}))

(defn get-schema [key & [default]]
  (get @cache key default))

(defn set-schema [key value]
  (swap! cache assoc key value))

(defn fetch-table-metadata [table-name]
  (let [ds (get-ds)]
    (try (jdbc/execute! ds
           (-> (sql/select :column_name :data_type :is_nullable)
               (sql/from :information_schema.columns)
               (sql/where [:= :table_name table-name])
               sql-format))
         (catch Exception e
           (println ">o> ERROR: fetch-table-metadata" (.getMessage e))
           (throw (Exception. "Unable to establish a database connection"))))))

(require '[schema.core :as schema])

(def type-mapping {"varchar" s/Str
                   "int4" s/Int
                   "boolean" s/Bool
                   "uuid" s/Uuid
                   "text" s/Str
                   "character varying" s/Str
                   "timestamp with time zone" s/Any})

;(def schema_raw_pagination [{:column_name "full_data", :data_type "boolean" :required true}
;                            {:column_name "page", :data_type "int4" :required true}
;                            {:column_name "count", :data_type "int4" :required true}])

(def schema_raw_pagination [{:column_name "full_data", :data_type "boolean" :required true}])
(def schema_raw_pagination [{:column_name "page", :data_type "int4" :required true}
                            {:column_name "count", :data_type "int4" :required true}])

(defn ensure-required-attr [entries]
  (let [entries (map (fn [entry]
                       (if (and (map? entry) (not (contains? entry :required)))
                         (assoc entry :required false)
                         entry))
                  entries)

        entries (map (fn [entry]
                       (if (and (map? entry) (not (contains? entry :is_nullable)))
                         (assoc entry :is_nullable "NO")
                         entry))
                  entries)] entries))

(defn postgres-to-schema [metadata]
  (into {}
    (map (fn [{:keys [column_name data_type is_nullable required]}]
           (println ">o> =>" column_name data_type is_nullable required)

           


           (if (true? required)
             {(s/required-key (keyword column_name)) (type-mapping data_type)}
             {(s/optional-key (keyword column_name)) (if (= is_nullable "YES")
                                                       (s/maybe (type-mapping data_type))
                                                       (type-mapping data_type))}))
      metadata)))

(defn normalize-map [namespaced-map]
  (into {} (map (fn [[k v]] [(keyword (name k)) v]) namespaced-map)))

(defn prepare-schema [table-name]
  (let [res (fetch-table-metadata "groups")
        p (println ">o> 1res=" res)

        res (map normalize-map res)
        p (println ">o> 2res=" res)

        res (concat res schema_raw_pagination)
        p (println "\n\n>o> 3res=" res)

        res (ensure-required-attr res)
        p (println "\n\n>o> 4res=" res)

        res (postgres-to-schema res)
        p (println "\n>o> 5res=" res)] res))


(defn init-schema-by-db []

  (println ">o> before db-fetch")
  ;(fetch-table-metadata "groups")
  ;(prepare-schema "groups")

  (set-schema :test (prepare-schema "groups"))

  (println ">o> after db-fetch")

  )