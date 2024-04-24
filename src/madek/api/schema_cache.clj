(ns madek.api.schema_cache
  (:require
   ;; all needed imports
   ;[leihs.core.db :as db]

   [honey.sql :refer [format] :rename {format sql-format}]

   [honey.sql.helpers :as sql]
   [madek.api.db.core :refer [get-ds]]




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

(def schema_full_data_raw [{:column_name "full_data", :data_type "boolean" :required true}])
(def schema_pagination_raw [{:column_name "page", :data_type "int4" :required true}
                            {:column_name "count", :data_type "int4" :required true}])

(defn ensure-required-attr [entries] "Ensures that all entries have all required keys. (turn *-raw elements into *-entries)"
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



(def keys-to-remove [:city])

(defn contains-any-key?
  "Checks if any of the keys in `keys-to-remove` are present in the map `m`."
  [m keys-to-remove]
  (some #(contains? m %) keys-to-remove))

(defn filtered-data [data keys-to-remove]
  (remove contains-any-key? data))



(defn filter-maps
  "Filters out any maps containing any of the keys specified in `keys-to-remove`."
  [maps keys-to-remove]
  (println ">o> 1keys-to-remove.m=" maps)
  (println ">o> 1keys-to-remove=" keys-to-remove)

  (remove (fn [m] (
                   (println ">o> 2keys-to-remove.fnc=" some #(contains? m %) keys-to-remove)

                   some #(contains? m %) keys-to-remove)

            ) maps))

(defn remove-columns
  "Removes maps with specific column_name values from a list of maps."
  [maps column-name-to-remove values-to-remove]
  (remove #(some (set values-to-remove) [(% column-name-to-remove)]) maps))


(defn remove-maps-by-entry
  "Removes maps from a list where the specified entry key has the specified value."
  [maps entry-key target-value]
  (remove #(= (entry-key %) target-value) maps))


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


;(defn replace-elem
;  "Replaces an element in the vector of maps with a new map where all keys match."
;  [data new-list-of-maps list-of-keys]
;  (mapv (fn [item]
;          (if-let [replacement (first (filter #(every? (fn [k] (= (k %) (k item))) list-of-keys) new-list-of-maps))]
;            replacement
;            item))
;    data))


(defn create-schema
  ;([table-name]
  ; (prepare-schema table-name [] [])
  ; )
  ;
  ;([table-name additional-attrs]
  ; (prepare-schema table-name additional-attrs [])
  ; )


  ([table-name additional-schema-list-raw blacklist-key-names update-schema-list-raw] "Prepare schema for a table."
   (let [res (fetch-table-metadata table-name)
         p (println ">o> 1res=" res)

         res (map normalize-map res)
         p (println ">o> 2res=" res)

         ;res (concat res schema_raw_pagination)
         res (concat res additional-schema-list-raw)
         p (println "\n\n>o> 3res=" res)

         ;res (remove contains-any-key? res blacklist-key-names)

         ;res (filter-maps res blacklist-key-names)

         ;res (remove-maps-by-entry res :column_name "city")
         ;res (remove-maps-by-entry-values res :column_name ["city" "name"])
         res (remove-maps-by-entry-values res :column_name blacklist-key-names)
         p (println "\n\n>o> 4res=" res)
         p (println "\n\n>o> 4res.keys=" (fetch-column-names res))


         res (replace-elem res update-schema-list-raw :column_name)
         ;res (replace-elem res update-schema-list-raw [:column_name :])
         p (println "\n\n>o> 5res=" res)

         res (ensure-required-attr res)
         p (println "\n\n>o> 6res=" res)

         res (postgres-to-schema res)
         p (println "\n>o> 7res=" res)] res)))


(defn init-schema-by-db []

  (println ">o> before db-fetch")
  ;(fetch-table-metadata "groups")
  ;(prepare-schema "groups")

  (let [

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