(ns madek.api.db.dynamic_schema.core
  (:require
   [clojure.string :as str]
   [madek.api.db.dynamic_schema.common :refer [get-enum get-schema set-enum set-schema]]
   [madek.api.db.dynamic_schema.db :refer [fetch-enum fetch-table-metadata]]
   [madek.api.db.dynamic_schema.schema_definitions :refer [type-mapping type-mapping-enums]]
   [madek.api.db.dynamic_schema.schema_logger :refer [slog]]
   [madek.api.db.dynamic_schema.statics :refer [TYPE_EITHER TYPE_MAYBE TYPE_NOTHING TYPE_OPTIONAL TYPE_REQUIRED]]
   [madek.api.db.dynamic_schema.validation :refer [validate-keys]]
   [schema.core :as s]))

(require '[schema.core :as schema])

(defn convert-to-enum-spec
  "Creates a Spec enum definition from a sequence of maps with namespaced keys."
  [enum-data]
  (apply s/enum (mapv #(:enumlabel %) enum-data)))

(defn create-enum-spec [table-name]
  (let [enum (fetch-enum table-name)]
    (convert-to-enum-spec enum)))

(defn init-enums-by-db []
  (let [_ (set-enum :collections_sorting (create-enum-spec "collection_sorting"))
        _ (set-enum :collections_layout (create-enum-spec "collection_layout"))
        _ (set-enum :collections_default_resource_type (create-enum-spec "collection_default_resource_type"))

        ;;;; TODO: revise db-ddl to use enum
        _ (set-enum :groups.type (s/enum "AuthenticationGroup" "InstitutionalGroup" "Group"))]))

(defn remove-maps-by-entry-values
  "Removes maps from a list where the specified entry key matches any of the values in the provided list."

  ([maps target-values]
   (remove-maps-by-entry-values maps :column_name target-values))

  ;; TODO: fix this to handle [:foo :bar]
  ([maps entry-key target-values]
   (if (empty? target-values)
     maps
     (remove #(some #{(entry-key %)} target-values) maps))))

(defn keep-maps-by-entry-values
  "Keeps only maps from a list where the specified entry key matches any of the values in the provided list."
  ([maps target-values]
   (keep-maps-by-entry-values maps :column_name target-values))

  ;; TODO: fix this to handle [:foo :bar]
  ([maps entry-key target-values]
   (if (empty? target-values)
     maps
     (filter #(some #{(entry-key %)} target-values) maps))))

(defn fetch-table-meta-raw
  ([table-name]
   (fetch-table-meta-raw table-name []))

  ([table-name update-data]
   (let [res (fetch-table-metadata table-name)
         res (concat res update-data)]
     res)))

(defn postgres-cfg-to-schema [table-name metadata]
  (into {}
        (map (fn [{:keys [column_name data_type key-type value-type]}]
               (let [keySection (cond (= key-type TYPE_REQUIRED) (s/required-key (keyword column_name))
                                      (= key-type TYPE_OPTIONAL) (s/optional-key (keyword column_name))
                                      :else (keyword column_name))

                 ;; FYI, expected: <table>.<column> eg.: "groups.type"
                     type-mapping-key (str (name table-name) "." column_name)
                     type-mapping-enums-res (type-mapping-enums type-mapping-key get-enum)
                     type-mapping-res (type-mapping data_type)

                     valueSection (cond
                                    (not (nil? type-mapping-enums-res)) (if (= value-type TYPE_MAYBE)
                                                                          (s/maybe type-mapping-enums-res)
                                                                          type-mapping-enums-res)
                                    (not (nil? type-mapping-res)) (if (= value-type TYPE_MAYBE)
                                                                    (s/maybe type-mapping-res)
                                                                    type-mapping-res)
                                    (= value-type TYPE_MAYBE) (s/maybe s/Any)
                                    :else s/Any)

                     _ (slog (str "[postgres-cfg-to-schema] table= " table-name ", final-result =>> " {keySection valueSection}))]
                 {keySection valueSection}))
             metadata)))

(defn rename-column-names
  [maps key-map]
  (map
   (fn [m]
     (if-let [new-col-name (get key-map (:column_name m))]
       (assoc m :column_name new-col-name)
       m))
   maps))

(defn create-raw-schema
  "Creates a schema by :raw-config and fetched meta-config from the database.
  Expects an array of maps like this:
[{
  :raw [{:<table-name> {}}]
  :raw-schema-name <name-how-it-should-be-saved-within-cache>
}]"
  [data]
  (let [raw (data :raw)
        raw-schema-name (data :raw-schema-name)
        result []

        res (reduce
             (fn [acc item]
               (reduce (fn [inner-acc [key value]]
                         (cond (not (str/starts-with? (name key) "_"))
                               (let [table-name key
                                     wl-attr (:wl value)
                                     bl-attr (:bl value)
                                     rename-attr (:rename value)
                                     key (name key)
                                     db-meta (fetch-table-metadata key)

                                     _ (slog (str "[handle not-additional] \n
                                  table-name=" table-name "\n
                                  wl-attr=" wl-attr "\n
                                  bl-attr=" bl-attr "\n
                                  rename-attr=" rename-attr "\n
                                  key=" key "\n
                                  db-data=" db-meta "\n"))

                                     db-meta (if (nil? rename-attr)
                                               db-meta
                                               (rename-column-names db-meta rename-attr))

                                     _ (validate-keys table-name db-meta wl-attr bl-attr "RAW-DEFINITION")

                                     res-wl-bl (cond
                                                 (not (nil? bl-attr)) (remove-maps-by-entry-values db-meta :column_name bl-attr)
                                                 (not (nil? wl-attr)) (keep-maps-by-entry-values db-meta :column_name wl-attr)
                                                 :else db-meta)
                                     res3 (postgres-cfg-to-schema table-name res-wl-bl)]
                                 (into inner-acc res3))

                               (= (name key) "_additional") (let [table-name key
                                                                  res2 (postgres-cfg-to-schema table-name value)]
                                                              (into inner-acc res2))
                               :else inner-acc))
                       acc item))
             result raw)

        _ (set-schema raw-schema-name res)]
    res))

(defn rename-by-keys
  [maps key-map]
  (map
   (fn [m]
     (reduce
      (fn [acc [old-key new-key]]
        (if (contains? m old-key)
          (assoc acc new-key (m old-key))
          acc))
      (apply dissoc m (keys key-map))
      key-map))
   maps))

(defn fetch-value-by-key
  [maps key]
  (some (fn [m] (get m key)) maps))

(defn revise-schema-types [table-name column_name column_type type-spec types key-types value-types]
  (let [val (fetch-value-by-key types column_name)
        key-type (get val :key-type)
        value-type (get val :value-type)
        either-condition (get val :either-condition)

        key-type (if (not (nil? key-types))
                   (if (nil? key-type) key-types key-type)
                   key-type)
        value-type (if (not (nil? value-types))
                     (if (nil? value-type) value-types value-type)
                     value-type)

        keySection (cond (= key-type TYPE_REQUIRED) (s/required-key (keyword column_name))
                         (= key-type TYPE_OPTIONAL) (s/optional-key (keyword column_name))
                         (= key-type TYPE_NOTHING) (keyword column_name)
                         :else (keyword column_name))

        ;; revise schema types by mapping (TODO: raw-handling is already doing this, to remove?)
        type-mapping-key (str (name table-name) "." (name column_name))
        type-mapping-enums-res (type-mapping-enums type-mapping-key get-enum)
        valueSection (cond
                       (not (nil? type-mapping-enums-res)) (if (= value-type TYPE_MAYBE)
                                                             (s/maybe type-mapping-enums-res)
                                                             type-mapping-enums-res)
                       (and (= value-type TYPE_EITHER) (not (nil? either-condition))) (s/->Either either-condition)
                       (= value-type TYPE_MAYBE) (s/maybe column_type)
                       :else column_type)

        _ (slog (str "[revise-schema-types] <<<<<<<<<<<<<<< before <<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n
        type-mapping-key=" type-mapping-key "\n
        column_name=" column_name "\n
        key-type=" key-type "\n
        value-type=" value-type "\n"))

        ;; TODO: quiet helpful for debugging
        _ (slog "??? [set-schema] =>> " {keySection valueSection})]
    {keySection valueSection}))

(defn process-revision-of-schema-types
  [table-name list-of-maps types key-types value-types]
  (map
   (fn [[col-name col-type]]
     (let [type-spec (some (fn [type-map]
                             (get type-map col-name))
                           types)]
       (revise-schema-types table-name col-name col-type type-spec types key-types value-types)))
   list-of-maps))

(defn remove-by-keys [data keys-to-remove]
  (remove (fn [[key _]]
            (some #{key} keys-to-remove)) data))

(defn keep-by-keys [data keys-to-keep]
  (filter (fn [[key _]]
            (some #{key} keys-to-keep)) data))

(defn create-schemas-by-config
  "Creates a specific schema by already existing schema (:raw-schema-name).
Expects an array of maps like this:
[{
:raw-schema-name <name-how-it-should-be-saved-within-cache>
:schemas [{:<name-how-it-should-be-saved-within-cache> {:wl [] :bl [] :cache-as [] :types [] :key-types [] :value-types []}}]
}]"
  [data]
  (let [schema-def (:schemas data)
        raw-schema-name (:raw-schema-name data)

        _ (if (nil? schema-def)
            (throw (Exception. "[create-schemas-by-config()] No data.schemas definition found")))

        _ (if (nil? raw-schema-name)
            (throw (Exception. "[create-schemas-by-config()] No data.raw-schema-name definition found")))

        result []
        res (reduce
             (fn [acc item]
               (reduce (fn [inner-acc [key value]]
                         (let [schema-raw (get-schema raw-schema-name)
                               table-name key
                               wl-attr (:wl value)
                               bl-attr (:bl value)
                               cache-as-attr (:cache-as value)

                               types-attr (:types value)
                               key-types-attr (:key-types value)
                               value-types-attr (:value-types value)

                               _ (validate-keys table-name schema-raw wl-attr bl-attr "SCHEMAS-DEFINITION")

                               schema-raw (cond
                                            (not (nil? bl-attr)) (remove-by-keys schema-raw bl-attr)
                                            (not (nil? wl-attr)) (keep-by-keys schema-raw wl-attr)
                                            :else schema-raw)

                               result (process-revision-of-schema-types table-name schema-raw types-attr key-types-attr value-types-attr)

                               _ (set-schema (keyword key) result)
                               _ (when (not (nil? cache-as-attr))
                                   (doseq [kw cache-as-attr]
                                     (set-schema (keyword kw) result)))]

                           (into inner-acc result)))
                       acc item))
             result schema-def)] res))

(defn create-dynamic-schema
  "Expects an array of maps like this: (:schemas is optional)
  [{
    :raw [{:<table-name> {}}]
    :raw-schema-name <name-how-it-should-be-saved-within-cache>

    :schemas [{:<name-how-it-should-be-saved-within-cache> {:wl [] :bl [] :cache-as [] :types [] :key-types [] :value-types []}}]
  }]
  "
  [cfg-array]
  (doseq [c cfg-array]
    (let [_ (create-raw-schema c)
          _ (when (contains? c :schemas)
              (create-schemas-by-config c))])))
