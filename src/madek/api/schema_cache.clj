(ns madek.api.schema_cache
  (:require
   ;; all needed imports
   ;[leihs.core.db :as db]

   [clojure.string :as str]
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

(defn pr [key fnc]
  (println ">oo> HELPER / " key "=" fnc)
  fnc
  )


(def enum-cache (atom {}))
(def schema-cache (atom {}))

(defn get-enum [key & [default]]

  (println ">oo> get-enum.key=" key)

  (pr key (get @enum-cache key default)))


(defn set-enum [key value]
  (println ">oo> set-enum.key=" key)
  (swap! enum-cache assoc key value))


(defn get-schema [key & [default]]
  (pr key (get @schema-cache key default)))

(defn set-schema [key value]
  (swap! schema-cache assoc key value))







(defn fetch-table-metadata [table-name]
  (println ">o> fetch-table-metadata by DB!!!!!!!!" table-name)
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

  (println ">o> fetch-enum by DB!!!!!!!!")

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

        ] res)

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
                   "jsonb" s/Any
                   "character varying" s/Str
                   "timestamp with time zone" s/Any
                   ;; helper
                   "str" s/Str
                   }
  ;"groups.type"
  )

;(def schema_raw_pagination [{:column_name "full_data", :data_type "boolean" :required true}
;                            {:column_name "page", :data_type "int4" :required true}
;                            {:column_name "count", :data_type "int4" :required true}])

;(def schema_full_data_raw [{:column_name "full_data", :data_type "boolean" :required true}])
;(def schema_pagination_raw [{:column_name "page", :data_type "int4" :required true}
;                            {:column_name "count", :data_type "int4" :required true}])

(def schema_full_data_raw [{:column_name "full_data", :data_type "boolean" :required false}])
(def schema_pagination_raw [{:column_name "page", :data_type "int4" :required false}
                            {:column_name "count", :data_type "int4" :required false}])

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


(defn is-db-enum? [data_type]
  (str/starts-with? data_type "enum::")
  )
(defn extract-db-enum-key [data_type]
  (keyword (str/replace data_type #"enum::" ""))
  )

(defn postgres-cfg-to-schema [metadata]
  (into {}
    (map (fn [{:keys [column_name data_type is_nullable required]}]
           (println ">o> postgres-cfg-to-schema =>" column_name data_type is_nullable required)

           (let [keySection (if (true? required)
                              (s/required-key (keyword column_name))
                              (s/optional-key (keyword column_name))
                              )

                 ;_ (println ">o> isEnum=" data_type "| bool?=" (if (str/starts-with? data_type "enum::") (get-enum (str/replace data_type #"enum::" ""))))

                 _ (println ">o> type/type-mapping=" data_type "|" (type-mapping data_type))
                 type-mapping-res (type-mapping data_type)

                 ;valueSection (cond (str/starts-with? data_type "enum::") (get-enum (keyword (str/replace data_type #"enum::" "")))
                 valueSection (cond (is-db-enum? data_type) (get-enum (keyword (extract-db-enum-key data_type)))
                                    (not (nil? type-mapping-res)) (if (= is_nullable "YES")
                                                                    (s/maybe (type-mapping data_type))
                                                                    (type-mapping data_type)
                                                                    )

                                    :else
                                    (do
                                      (error ">o> ERROR: no valid type-mapping found for: >" data_type "<, add definition to schema_cache.type-mapping\nDefault <s/Any> used.\"")
                                      (println ">o> ERROR: no valid type-mapping found for: >" data_type "<, add definition to schema_cache.type-mapping\nDefault <s/Any> used.")
                                      s/Any) )
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

  ([maps target-values]
   (remove-maps-by-entry-values maps :column_name target-values)
   )


  ([maps entry-key target-values]

   (if (empty? target-values)
     maps
     ; else
     (remove #(some #{(entry-key %)} target-values) maps)))

  )
;(remove #(some #{(entry-key %)} target-values) maps))


(defn keep-maps-by-entry-values
  "Keeps only maps from a list where the specified entry key matches any of the values in the provided list."


  ([maps target-values]
   (keep-maps-by-entry-values maps :column_name target-values)
   )


  ([maps entry-key target-values]

   (if (empty? target-values)
     maps
     ; else
     (filter #(some #{(entry-key %)} target-values) maps)))

  )



;(filter #(some #{(entry-key %)} target-values) maps))


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


(defn fetch-table-meta-raw

  ([table-name]
   (fetch-table-meta-raw table-name [])
   )

  ([table-name update-data]
   (let [
         res (fetch-table-metadata table-name)
         p (println ">o> 1res=" res)

         res (map normalize-map res)
         p (println ">o> 2res=" res)


         res (replace-elem res update-data :column_name)    ;;??

         ] res))

  )




(defn create-schema
  ([table-name additional-schema-list-raw] "Prepare schema for a table."
   (create-schema table-name additional-schema-list-raw [] []))

  ([table-name additional-schema-list-raw blacklist-key-names update-schema-list-raw] "Prepare schema for a table."
   (let [

         ;res (fetch-table-metadata table-name)
         ;p (println ">o> 1res=" res)
         ;
         ;res (map normalize-map res)
         ;p (println ">o> 2res=" res)

         res (fetch-table-meta-raw table-name)

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


(defn create-schema-by-data
  ([table-meta-raw] "Prepare schema for a table."
   (create-schema-by-data table-meta-raw [] [] [] []))

  ([table-meta-raw additional-schema-list-raw] "Prepare schema for a table."
   (create-schema-by-data table-meta-raw additional-schema-list-raw [] [] []))

  ([table-meta-raw additional-schema-list-raw blacklist-key-names update-schema-list-raw whitelist-key-names] "Prepare schema for a table."
   (let [
         res table-meta-raw

         ; remove all entries which are not in the whitelist by column_name
         res (keep-maps-by-entry-values res :column_name whitelist-key-names)


         p (println ">o> debug2")
         res (concat res additional-schema-list-raw)
         p (println "\n\n>o> 3res=" res)

         res (remove-maps-by-entry-values res :column_name blacklist-key-names)
         p (println "\n\n>o> 4res=" res)
         p (println "\n\n>o> 4res.keys=" (fetch-column-names res))

         p (println ">o> debug3")
         res (replace-elem res update-schema-list-raw :column_name) ;;TODO: dont replace just update
         p (println "\n\n>o> 5res=" res)

         res (convert-raw-into-postgres-cfg res)
         p (println "\n\n>o> 6res=" res)
         p (println ">o> debug4")

         res (postgres-cfg-to-schema res)
         p (println "\n>o> 7res=" res)

         p (println ">o> debug5")

         ] res)))


(defn update-column-value [data column-name new-value]
  (map (fn [row]
         (if (= (row :column_name) column-name)
           (assoc row :column_name new-value)
           row))
    data))


(defn create-groups-schema []
  (let [
        ;; :groups-schema-raw
        groups-meta-raw (fetch-table-meta-raw "groups" [{:column_name "type" :data_type "enum::groups.type" :is_nullable "NO"}])
        _ (set-schema :groups-schema-raw groups-meta-raw)

        ;; :users-schema-raw
        users-meta-raw (fetch-table-meta-raw "users" [])
        _ (set-schema :users-schema-raw users-meta-raw)

        ;; :groups-schema-with-pagination
        additional-schema-list-raw (concat schema_pagination_raw schema_full_data_raw)
        p (println ">o> debug1")
        res (set-schema :groups-schema-with-pagination (create-schema-by-data groups-meta-raw additional-schema-list-raw))

        ;; :groups-schema-response
        update-schema-list-raw [{:column_name "id", :data_type "uuid" :is_nullable "NO" :required true}]
        res (set-schema :groups-schema-response (create-schema-by-data groups-meta-raw [] [] update-schema-list-raw []))

        ;; :groups-schema-response-put
        whitelist-key-names ["name" "type" "institution" "institutional_id" "institutional_name" "created_by_user_id"]
        res (set-schema :groups-schema-response-put (create-schema-by-data groups-meta-raw [] [] [] whitelist-key-names))


        ;; :groups-schema-response-put-users
        ;; example how to extract & merge meta-data-infos (PUT "/:group-id/users/")
        groups-users-meta-raw (concat (keep-maps-by-entry-values users-meta-raw ["email" "person_id"])
                                (keep-maps-by-entry-values groups-meta-raw ["id" "institutional_id"]))

        res (set-schema :groups-schema-response-user-simple (create-schema-by-data groups-users-meta-raw))

        ;; TODO: needed renaming of keys, fix handler to get rid of this workaround
        groups-users-meta-raw (update-column-value groups-users-meta-raw "person_id" "person-id")
        groups-users-meta-raw (update-column-value groups-users-meta-raw "institutional_id" "institutional-id")

        res (set-schema :groups-schema-response-put-users (create-schema-by-data groups-users-meta-raw)) ;; TODO: name of keys
        ]))


(defn create-users-schema []
  (let [
        ;; :users-schema-raw
        users-meta-raw (fetch-table-meta-raw "users" [])
        _ (set-schema :users-schema-raw users-meta-raw)

        ;; :groups-schema-response-put
        whitelist-key-names ["id" "institutional_id" "email"]
        _ (set-schema :users-schema-payload (create-schema-by-data users-meta-raw [] [] [] whitelist-key-names))
        ]))

(defn create-admins-schema []
  (let [
        ;; :users-schema-raw
        admins-meta-raw (fetch-table-meta-raw "admins" [])
        _ (set-schema :admins-schema-raw admins-meta-raw)

        _ (set-schema :admins-schema (create-schema-by-data admins-meta-raw))
        ]))


(defn create-workflows-schema []
  (let [
        ;; :workflows-schema-raw
        workflows-meta-raw (fetch-table-meta-raw "workflows" [])
        p (println ">o> workflows-meta-raw=" workflows-meta-raw)
        _ (set-schema :workflows-schema-raw workflows-meta-raw)


        _ (set-schema :workflows-schema (create-schema-by-data workflows-meta-raw))

        whitelist-key-names ["name" "is_active" "configuration"]
        _ (set-schema :workflows-schema-min (create-schema-by-data workflows-meta-raw [] [] [] whitelist-key-names))
        ]))


(defn create-collections-schema []
  (let [
        ;; :workflows-schema-raw
        collections-meta-raw (fetch-table-meta-raw "collections" [])
        p (println ">o> workflows-meta-raw=" collections-meta-raw)
        _ (set-schema :collections-schema-raw collections-meta-raw)


        _ (set-schema :collections-schema (create-schema-by-data collections-meta-raw))

        ;whitelist-key-names ["name" "is_active" "configuration"]
        ;_ (set-schema :workflows-schema-min (create-schema-by-data collections-meta-raw [] [] [] whitelist-key-names))
        ]))


(comment
  (let [
        ;res (create-groups-schema)
        ;;res (get-schema :groups-schema-response)
        ;res (get-schema :groups-schema-raw)
        ;;res (get-schema :groups-schema-with-pagination)
        ;res (get-schema :groups-schema-with-pagination)
        ;res (get-enum :groups.type)
        ;res (get-schema :groups-schema-response-put)


        table-meta-raw (get-schema :groups-schema-raw)
        res table-meta-raw

        p (println ">o> table-meta-raw=" table-meta-raw)

        whitelist-key-names ["name" "type" "institution" "institutional_id" "institutional_name" "created_by_user_id"]
        ;whitelist-key-names ["name" "type" "institution"]
        res (keep-maps-by-entry-values res :column_name whitelist-key-names)




        ;p (println ">o> whitelist-keys=" whitelist-keys)
        ;
        ;;res (set-schema :groups-schema-response-put (create-schema-by-data table-meta-raw [] [] update-schema-list-raw))
        ;res (set-schema :groups-schema-response-put (create-schema-by-data table-meta-raw [] [] [] whitelist-keys))

        ]
    res)
  )



(defn init-schema-by-db []

  (println ">o> before db-fetch")
  ;(fetch-table-metadata "groups")
  ;(prepare-schema "groups")

  (let [
        ;; init enums
        _ (set-enum :collection_sorting (create-enum-spec "collection_sorting"))
        _ (set-enum :collection_layout (create-enum-spec "collection_layout"))
        _ (set-enum :collection_default_resource_type (create-enum-spec "collection_default_resource_type"))
        ;; TODO: revise db-ddl to use enum
        _ (set-enum :groups.type (s/enum "AuthenticationGroup" "InstitutionalGroup" "Group"))

        _ (create-groups-schema)
        _ (create-users-schema)
        _ (create-admins-schema)
        _ (create-workflows-schema)
        _ (create-collections-schema)

        ]) )


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