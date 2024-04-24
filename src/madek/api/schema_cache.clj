(ns madek.api.schema_cache
  (:require
   ;; all needed imports
   ;[leihs.core.db :as db]

   [honey.sql :refer [format] :rename {format sql-format}]

   [honey.sql.helpers :as sql]
   [madek.api.db.core :refer [get-ds]]



   [taoensso.timbre :refer [info warn debug error spy]]



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
               sql-format
               spy
               ))
         (catch Exception e
           (println ">o> ERROR: fetch-table-metadata" (.getMessage e))
           (throw (Exception. "Unable to establish a database connection"))))))



;
;SELECT enumlabel
;FROM pg_enum
;JOIN pg_type ON pg_enum.enumtypid = pg_type.oid
;WHERE pg_type.typname = 'collection_sorting';



;SELECT enumlabel
;FROM pg_enum
;JOIN pg_type ON pg_enum.enumtypid = pg_type.oid
;WHERE pg_type.typname = 'collection_sorting';

(defn fetch-enum [enum-name]
  (let [ds (get-ds)

        ;;; TODO: FIXME: use get-ds
        ;ds {:dbtype "postgresql"
        ;              :dbname "madek_test"
        ;              :user "madek_sql"
        ;              :port 5415
        ;              :password "madek_sql"}
        ]
    (try (jdbc/execute! ds
           (-> (sql/select :enumlabel)
               (sql/from :pg_enum)
               (sql/join :pg_type [:= :pg_enum.enumtypid :pg_type.oid])
               (sql/where [:= :pg_type.typname enum-name])
               sql-format))

         (catch Exception e
           (println ">o> ERROR: fetch-table-metadata" (.getMessage e))
           (throw (Exception. "Unable to establish a database connection"))))))


(defn create-enum-spec
  "Creates a Spec enum definition from a sequence of maps with namespaced keys."
  [enum-data]
  ;(apply s/enum (mapv #(-> % :pg_enum :enumlabel) enum-data)))
  (apply s/enum (mapv #(-> % :pg_enum/enumlabel) enum-data)))


(defn create-enum-spec
  "Creates a Spec enum definition from a sequence of maps with namespaced keys."
  [enum-data]
  (let [enum-labels (mapv #(-> % :pg_enum :enumlabel) enum-data)
        filtered-enum-labels (remove nil? enum-labels)]
    (apply s/enum filtered-enum-labels)))


(defn convert-to-enum-spec
  "Creates a Spec enum definition from a sequence of maps with namespaced keys."
  [enum-data]

  (println ">o> enum-data=" enum-data)

  (apply s/enum (mapv #(:enumlabel %) enum-data)))
  ;(apply s/enum (mapv #(:pg_enum/enumlabel %) enum-data)))


(defn create-enum-spec [table-name]


  (let [
        res (fetch-enum table-name)
        p (println ">o> 1ares=" res)
        res (convert-to-enum-spec res)
        p (println ">o> 2ares=" res)

        ]res)

  ;(convert-to-enum-spec (fetch-enum table-name))
  )


(comment
  (let [
        res (create-enum-spec "collection_sorting")
        p (println ">o> 1res=" res)
        res (create-enum-spec "collection_layout")
        p (println ">o> 1res=" res)
        res (create-enum-spec "collection_default_resource_type")
        p (println ">o> 1res=" res)

        ;res (fetch-enum "collection_sorting")
        ;p (println ">o> 1res=" res)
        ;res (fetch-enum "collection_layout")
        ;p (println ">o> 1res=" res)
        ;res (fetch-enum "collection_default_resource_type")
        ;p (println ">o> 1res=" res)




        ;p (println ">o> 1??=" (:enumlabel (first res)))
        ;p (println ">o> 2??=" (class (:enumlabel (:pg_enum (first res)))))
        ;p (println ">o> 3??=" (first res))
        ;p (println ">o> 3??=" (:pg_enum (first res)))
        ;p (println ">o> 3??=" (:enumlabel (:pg_enum (first res))))
        ;p (println ">o> 3??=" (:pg_enum/enumlabel (first res)))
        ;
        ;;[#:pg_enum{:enumlabel "created_at ASC"}
        ;
        ;res (create-enum-spec res)
        ;
        ;
        ;p (println ">o> 2res=" res)

        ]
    res
    )
  )


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

(def schema_full_data_raw [{:column_name "full_data", :data_type "boolean" :required true}])
(def schema_pagination_raw [{:column_name "page", :data_type "int4" :required true}
                            {:column_name "count", :data_type "int4" :required true}])

(defn convert-raw-into-postgres-cfg [entries] "Ensures that all entries have all required keys. (turn *-raw elements into *-entries)"
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

(defn postgres-cfg-to-schema [metadata]
  (into {}
    (map (fn [{:keys [column_name data_type is_nullable required]}]
           (println ">o> =>" column_name data_type is_nullable required)

           (let [keySection (if (true? required)
                              (s/required-key (keyword column_name))
                              (s/optional-key (keyword column_name))
                              )
                 valueSection (if (= is_nullable "YES")
                                (s/maybe (type-mapping data_type))
                                (type-mapping data_type)
                                )
                 ]
             {keySection valueSection}
             ))

      metadata)))

(defn normalize-map [namespaced-map]
  (into {} (map (fn [[k v]] [(keyword (name k)) v]) namespaced-map)))

;(def keys-to-remove [:city])
;
;(defn contains-any-key?
;  "Checks if any of the keys in `keys-to-remove` are present in the map `m`."
;  [m keys-to-remove]
;  (some #(contains? m %) keys-to-remove))
;
;(defn filtered-data [data keys-to-remove]
;  (remove contains-any-key? data))
;
;(defn filter-maps
;  "Filters out any maps containing any of the keys specified in `keys-to-remove`."
;  [maps keys-to-remove]
;  (println ">o> 1keys-to-remove.m=" maps)
;  (println ">o> 1keys-to-remove=" keys-to-remove)
;
;  (remove (fn [m] (
;                   (println ">o> 2keys-to-remove.fnc=" some #(contains? m %) keys-to-remove)
;
;                   some #(contains? m %) keys-to-remove)
;
;            ) maps))
;
;(defn remove-columns
;  "Removes maps with specific column_name values from a list of maps."
;  [maps column-name-to-remove values-to-remove]
;  (remove #(some (set values-to-remove) [(% column-name-to-remove)]) maps))
;
;(defn remove-maps-by-entry
;  "Removes maps from a list where the specified entry key has the specified value."
;  [maps entry-key target-value]
;  (remove #(= (entry-key %) target-value) maps))


(defn remove-maps-by-entry-values
  "Removes maps from a list where the specified entry key matches any of the values in the provided list."
  [maps entry-key target-values]
  (remove #(some #{(entry-key %)} target-values) maps))

(defn fetch-column-names
  "Extracts the values of :column_name from a list of maps."
  [maps]
  (map :column_name maps))

(defn replace-elem
  "Replaces an element in the vector of maps with a new map where the key matches."
  [data new-list-of-maps key]
  (mapv (fn [item]
          (println ">o> item=" item)
          (println ">o> item=" item)

          (if-let [replacement (first (filter #(= (key %) (key item)) new-list-of-maps))]
            replacement
            item))
    data))

(defn create-schema
  ([table-name additional-schema-list-raw blacklist-key-names update-schema-list-raw] "Prepare schema for a table."
   (let [res (fetch-table-metadata table-name)
         p (println ">o> 1res=" res)

         res (map normalize-map res)
         p (println ">o> 2res=" res)

         res (concat res additional-schema-list-raw)
         p (println "\n\n>o> 3res=" res)

         res (remove-maps-by-entry-values res :column_name blacklist-key-names)
         p (println "\n\n>o> 4res=" res)
         p (println "\n\n>o> 4res.keys=" (fetch-column-names res))

         res (replace-elem res update-schema-list-raw :column_name)
         p (println "\n\n>o> 5res=" res)

         res (convert-raw-into-postgres-cfg res)
         p (println "\n\n>o> 6res=" res)

         res (postgres-cfg-to-schema res)
         p (println "\n>o> 7res=" res)] res)))


(defn init-schema-by-db []

  (println ">o> before db-fetch")
  ;(fetch-table-metadata "groups")
  ;(prepare-schema "groups")

  (let [

        ;; seems to work
        res (create-enum-spec "collection_sorting")
        p (println ">o> 1abres=" res)
        res (create-enum-spec "collection_layout")
        p (println ">o> 1abres=" res)
        res (create-enum-spec "collection_default_resource_type")
        p (println ">o> 1abres=" res)


        ;; create schema for groups
        ;blacklist-key-names [:created_at :updated_at]
        blacklist-key-names ["created_at" "updated_at"]
        ;blacklist-key-names ["created_at" "updated_at" "page" "count"]

        additional-schema-list-raw (concat schema_pagination_raw schema_full_data_raw)


        update-schema-list-raw [{:column_name "full_data", :data_type "varchar" :is_nullable "NO" :required false}]
        ;update-schema-list-raw [{:column_name "full_data", :data_type "varchar" :is_nullable "NO" :required true}]

        p (println ">o> additional-schema-list-raw=" additional-schema-list-raw)

        res (set-schema :test (create-schema "groups" additional-schema-list-raw blacklist-key-names update-schema-list-raw))

        ;p (println ">o> keys:" (keys res))

        ] res)

  ;(println ">o> after db-fetch")

  )


;SELECT enumlabel
;FROM pg_enum
;JOIN pg_type ON pg_enum.enumtypid = pg_type.oid
;WHERE pg_type.typname = 'collection_default_resource_type';
;
;
;
;SELECT enumlabel
;FROM pg_enum
;JOIN pg_type ON pg_enum.enumtypid = pg_type.oid
;WHERE pg_type.typname = 'collection_layout';;
;
;
;SELECT enumlabel
;FROM pg_enum
;JOIN pg_type ON pg_enum.enumtypid = pg_type.oid
;WHERE pg_type.typname = 'collection_sorting';





(comment
  (let [






        res (init-schema-by-db)

        ;blacklist [:created_at :updated_at]
        blacklist ["created_at" "updated_at"]
        ;blacklist ["created_at" "updated_at" "page" "count"]

        additional-attr (concat schema_pagination_raw schema_full_data_raw)


        update-elem-list [{:column_name "full_data", :data_type "varchar" :is_nullable "NO" :required false}]
        update-elem-list [{:column_name "full_data", :data_type "varchar" :is_nullable "NO" :required true}]

        p (println ">o> additional-attr=" additional-attr)

        res (set-schema :test (create-schema "groups" additional-attr blacklist update-elem-list))

        ;p (println ">o> keys:" (keys res))

        ]
    res
    )
  )