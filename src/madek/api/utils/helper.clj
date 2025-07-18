(ns madek.api.utils.helper
  (:require [cheshire.core :as json]
            [honey.sql :refer [format] :rename {format sql-format}]
            [madek.api.anti-csrf.core :refer [keyword str]]
            [pghstore-clj.core :refer [to-hstore THstorable]]
            [taoensso.timbre :refer [warn]])
  (:import (clojure.lang IPersistentMap)
           (java.util UUID)
           (org.postgresql.util PGobject)))

(def LOAD-SWAGGER-DESCRIPTION-FROM-FILE true)

(defn strip-prefixes
  "Strips namespace prefixes from all keyword keys in a map."
  [m]
  (when (map? m)
    (into {}
          (map (fn [[k v]]
                 [(if (keyword? k)
                    (keyword (name k))
                    k)
                  v])
               m))))

(defn normalize-fields
  "Converts query attributes into a vector of keywords."
  ([req]
   (normalize-fields req nil))
  ([req prefix]
   (let [raw (get-in req [:parameters :query :fields])
         attrs (cond
                 (empty? raw) []
                 (string? raw) [raw]
                 :else raw)]
     (mapv (fn [attr]
             (let [name-str (name attr)
                   full (if prefix
                          (str (name prefix) "." name-str)
                          name-str)]
               (keyword full)))
           attrs))))

(defn strip-prefixes-generic
  "Strips table/namespace prefixes from keyword keys.
   - If given a vector of maps, returns a vector of modified maps.
   - If given a single map, returns a modified map.
   - If nil or empty, returns [] or {} accordingly."
  [input]
  (cond
    (nil? input) input
    (map? input) (strip-prefixes input)
    (and (vector? input) (every? map? input))
    (mapv strip-prefixes input)
    :else input))

; [madek.api.utils.helper :refer [t d]]
(defn t [s] (str s ".. MANUALLY TESTED"))
(defn d [s] (str s " / doc-example"))
(defn v [s] (str s " / working validation"))
(defn i [s] (str s " / INFO"))
(defn fv [s] (str s " / validation FAILS"))

(defn f
  ([s] (str s " / ToFix"))
  ([s text] (str s " / ToFix: " text)))

; [madek.api.utils.helper :refer [mslurp]]
(defn mslurp [file-path]
  (if LOAD-SWAGGER-DESCRIPTION-FROM-FILE
    (slurp file-path)
    "DESCRIPTION DEACTIVATED"))

(defn- quote-names-if-reserved-keywords [sql-query] "Used to quote column-names if they are reserved keywords (sql)"
  (let [first-sql (first sql-query)
        modified-sql (clojure.string/replace first-sql "order," "\"order\",")
        modified-sql (clojure.string/replace modified-sql "order =" "\"order\" =")]
    (assoc sql-query 0 modified-sql)))

(defn sql-format-quoted [sql-query]
  (quote-names-if-reserved-keywords (sql-format sql-query)))

; [madek.api.utils.helper :refer [to-uuid]]
(defn to-uuid
  ([value]
   (try
     (let [result (if (instance? String value) (UUID/fromString value) value)]
       result)
     (catch Exception e
       (warn ">>> DEV-ERROR in to-uuid[value], value=" value ", exception=" (.getMessage e))
       value)))

  ([value key]
   (def keys-to-cast-to-uuid #{:user_id :id :group_id :person_id :collection_id :media_entry_id :accepted_usage_terms_id :delegation_id
                               :uploader_id :created_by_id :media_resource_id
                               :keyword_id})
   (let [res (try
               (if (and (contains? keys-to-cast-to-uuid (keyword key)) (instance? String value))
                 (UUID/fromString value)
                 value)
               (catch Exception e
                 (warn ">>> DEV-ERROR in to-uuid[value key], value=" value ", key=" key " exception=" (.getMessage e)
                       ", continue with original value")
                 value))] res))

  ([value key table]
   (def blacklisted-tables #{"meta_keys" "vocabularies"})

   ;; XXX: To fix db-exceptions of io_interfaces
   (if (or (contains? blacklisted-tables (name table)) (and (= table :io_interfaces) (= key :id)))
     value
     (to-uuid value key))))

(defn modify-if-exists [m k f]
  (if (contains? m k)
    (update m k f)
    m))

(defn to-jsonb-stm
  ([value]
   [:cast (json/generate-string value) :jsonb])
  ([map key]
   (let [value (get map key)
         value (if (nil? value) "'{}'" (json/generate-string value))]
     (assoc map key [:cast value :jsonb]))))

(defn convert-map-if-exist [m]
  (-> m
      (modify-if-exists :deleted_at #(vector :cast % ::date))
      (modify-if-exists :layout #(vector :cast % :public.collection_layout))
      (modify-if-exists :default_resource_type #(vector :cast % :public.collection_default_resource_type))
      (modify-if-exists :sorting #(vector :cast % :public.collection_sorting))
      (modify-if-exists :json #(vector :cast (json/generate-string %) :jsonb))
      (modify-if-exists :configuration #(vector :cast (json/generate-string %) :jsonb))
      (modify-if-exists :institutional_directory_inactive_since #(when % [:cast % ::timestamptz]))
      (modify-if-exists :active_until #(when % [:cast % ::timestamptz]))

      ;; uuid
      (modify-if-exists :id #(to-uuid % :id))
      (modify-if-exists :media_entry_default_license_id to-uuid)
      (modify-if-exists :edit_meta_data_power_users_group_id to-uuid)
      (modify-if-exists :creator_id to-uuid)
      (modify-if-exists :person_id to-uuid)
      (modify-if-exists :user_id to-uuid)
      (modify-if-exists :accepted_usage_terms_id to-uuid)
      (modify-if-exists :created_by_id to-uuid)
      (modify-if-exists :uploader_id to-uuid)
      (modify-if-exists :media_entry_id to-uuid)

      ;; jsonb / character varying
      (modify-if-exists :settings #(vector :cast (json/generate-string (or % {})) :jsonb))
      (modify-if-exists :external_uris #(vector :array (or % []) :text))
      (modify-if-exists :sitemap #(vector :cast (json/generate-string (or % {})) :jsonb))
      (modify-if-exists :available_locales #(vector :array (or % []) :text))
      (modify-if-exists :institutional_directory_infos #(vector :array (or % []) :text))

      ;; text[]
      (modify-if-exists :contexts_for_entry_extra #(vector :array (or % []) :text))
      (modify-if-exists :contexts_for_list_details #(vector :array (or % []) :text))
      (modify-if-exists :contexts_for_entry_validation #(vector :array (or % []) :text))
      (modify-if-exists :contexts_for_dynamic_filters #(vector :array (or % []) :text))
      (modify-if-exists :contexts_for_collection_edit #(vector :array (or % []) :text))
      (modify-if-exists :contexts_for_collection_extra #(vector :array (or % []) :text))
      (modify-if-exists :contexts_for_entry_edit #(vector :array (or % []) :text))
      (modify-if-exists :contexts_for_context_keys #(vector :array (or % []) :text))
      (modify-if-exists :catalog_context_keys #(vector :array (or % []) :text))
      (modify-if-exists :copyright_notice_templates #(vector :array (or % []) :text))
      (modify-if-exists :allowed_people_subtypes #(vector :array (or % []) :text))
      (modify-if-exists :person_info_fields #(vector :array (or % []) :text))))

(extend-protocol THstorable
  IPersistentMap
  (to-hstore [this]
    (let [pgobj (doto (PGobject.)
                  (.setType "hstore")
                  (.setValue
                   (->> this
                        (map (fn [[k v]]
                               (str "\"" (name k) "\"=>\"" (str v) "\"")))
                        (clojure.string/join ", "))))]
      pgobj)))

; [madek.api.utils.helper :refer [cast-to-hstore]]
(defn cast-to-hstore [data]
  (let [keys [:labels :descriptions :contents :hints :documentation_urls
              :site_titles :brand_texts :welcome_titles :welcome_texts
              :featured_set_titles :featured_set_subtitles
              :catalog_subtitles :catalog_titles
              :about_pages :support_urls :provenance_notices]]
    (reduce (fn [acc key]
              (if (contains? acc key)
                (let [field-value (get acc key)
                      transformed-value (to-hstore field-value)]
                  (assoc acc key transformed-value))
                acc))
            data
            keys)))

;; =================================================================
;; TODO: replace-java-hashmap
;; convert java.*.HashMap to ClolureMap
(defn replace-java-hashmap [v]
  (if (instance? java.util.HashMap v)
    (into {} (for [[k v] v]
               [(keyword k) v]))
    v))

; [madek.api.utils.helper :refer [replace-java-hashmaps]]
(defn replace-java-hashmaps [m]
  (reduce-kv (fn [acc k v]
               (assoc acc k (replace-java-hashmap v)))
             {}
             m))

(def email-regex #"^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$")

; [madek.api.utils.helper :refer [convert-groupid-userid]]
(defn convert-groupid-userid [group-id user-id]
  (let [is_uuid (boolean (re-matches
                          #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
                          group-id))
        group-id (if is_uuid (to-uuid group-id) group-id)
        is_email (re-matches email-regex user-id)
        is_uuid (boolean (re-matches
                          #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
                          user-id))
        user-id (if is_uuid (to-uuid user-id) user-id)
        is_userid_valid (or is_email is_uuid)
        res {:group-id group-id
             :user-id user-id
             :is_userid_valid is_userid_valid}]
    res))

; [madek.api.utils.helper :refer [convert-userid]]
(defn convert-userid [user-id]
  (let [is_email (boolean (re-matches email-regex (str user-id)))
        is_uuid (boolean (re-matches
                          #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
                          user-id))
        user-id (if is_uuid (to-uuid user-id) user-id)
        is_userid_valid (or is_email is_uuid)
        res {:user-id user-id
             :is_userid_valid is_userid_valid
             :is_valid_email is_email
             :is_valid_uuid is_uuid}]
    res))
