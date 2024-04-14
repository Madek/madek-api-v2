(ns madek.api.utils.helper
  (:require [cheshire.core :as json]
            [pghstore-clj.core :refer [to-hstore]]
            [taoensso.timbre :refer [warn]])
  (:import (java.util UUID)))

(def LOAD-SWAGGER-DESCRIPTION-FROM-FILE true)

; [madek.api.utils.helper :refer [t d]]
(defn t [s] (str s ".. MANUALLY TESTED"))
(defn d [s] (str s " / doc-example"))
(defn v [s] (str s " / working validation"))
(defn i [s] (str s " / INFO"))
(defn fv [s] (str s " / validation FAILS"))

(defn f
  ([s] (str s " / ToFix"))
  ([s text] (str s " / ToFix: " text)))

; [madek.api.utils.helper :refer [str-to-int]]
(defn str-to-int
  "Attempts to convert a string to an integer, returning a default value if conversion fails."
  [value default-value]
  (try
    (Integer/parseInt (str value))
    (catch NumberFormatException e
      default-value)))

; [madek.api.utils.helper :refer [mslurp]]
(defn mslurp [file-path]
  (if LOAD-SWAGGER-DESCRIPTION-FROM-FILE
    (slurp file-path)
    "DESCRIPTION DEACTIVATED"))

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
                               :uploader_id :created_by_id
                               :keyword_id})
   (let [res (try
               (if (and (contains? keys-to-cast-to-uuid (keyword key)) (instance? String value))
                 (UUID/fromString value)
                 value)
               (catch Exception e
                 (warn ">>> DEV-ERROR in to-uuid[value key], value=" value ", key=" key " exception=" (.getMessage e))
                 value))] res))

  ([value key table]
   (def blacklisted-tables #{"meta_keys" "vocabularies"})

   ;; XXX: To fix db-exceptions of io_interfaces
   (if (or (contains? blacklisted-tables (name table)) (and (= table :io_interfaces) (= key :id)))
     value
     (to-uuid value key))))

; [madek.api.utils.helper :refer [urls-to-custom-format]]
;; TODO: maybe possible with json/dump?
(defn convert-to-raw-set [urls]
  (let [transformed-urls urls
        combined-str (str "'{" (clojure.string/join "," transformed-urls) "}'")]
    [:raw combined-str]))

; [madek.api.utils.helper :refer [convert-map]]
(defn convert-map [map]
  (-> map
      (update :external_uris #(if (nil? %)
                                [:raw "'{}'"]
                                (convert-to-raw-set %))) ;;rename to convert-to-raw-set
      (update :creator_id #(if (contains? map :creator_id) (to-uuid % :creator_id)))))

(defn modify-if-exists [m k f]
  (if (contains? m k)
    (update m k f)
    m))

;; Used for columns of jsonb type
; [madek.api.utils.helper :refer [convert-map-if-exist]]
(defn convert-map-if-exist [m]
  (-> m
      (modify-if-exists :layout #(if (contains? m :layout) [:cast % :public.collection_layout]))
      (modify-if-exists :default_resource_type #(if (contains? m :default_resource_type) [:cast % :public.collection_default_resource_type]))
      (modify-if-exists :sorting #(if (contains? m :sorting) [:cast % :public.collection_sorting]))
      (modify-if-exists :json #(if (contains? m :json) [:cast (json/generate-string %) :jsonb]))

      ;; uuid
      (modify-if-exists :id #(if (contains? m :id) (to-uuid % :id)))
      (modify-if-exists :media_entry_default_license_id #(if (contains? m :id) (to-uuid %)))
      (modify-if-exists :edit_meta_data_power_users_group_id #(if (contains? m :edit_meta_data_power_users_group_id) (to-uuid %)))
      (modify-if-exists :creator_id #(if (contains? m :creator_id) (to-uuid %)))
      (modify-if-exists :person_id #(if (contains? m :person_id) (to-uuid %)))
      (modify-if-exists :user_id #(if (contains? m :user_id) (to-uuid %)))
      (modify-if-exists :accepted_usage_terms_id #(if (contains? m :accepted_usage_terms_id) (to-uuid %)))

      (modify-if-exists :created_by_id #(if (contains? m :created_by_id) (to-uuid %)))
      (modify-if-exists :uploader_id #(if (contains? m :uploader_id) (to-uuid %)))
      (modify-if-exists :media_entry_id #(if (contains? m :media_entry_id) (to-uuid %)))

      ;; jsonb / character varying
      (modify-if-exists :settings #(if (nil? %) [:raw "'{}'"] (convert-to-raw-set %)))
      (modify-if-exists :external_uris #(if (nil? %) [:raw "'{}'"] (convert-to-raw-set %)))
      (modify-if-exists :sitemap #(if (nil? %) [:raw "'{}'"] (convert-to-raw-set %)))
      (modify-if-exists :available_locales #(if (nil? %) [:raw "'{}'"] (convert-to-raw-set %)))

      ;; text[]
      (modify-if-exists :contexts_for_entry_extra #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))
      (modify-if-exists :contexts_for_list_details #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))
      (modify-if-exists :contexts_for_entry_validation #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))
      (modify-if-exists :contexts_for_dynamic_filters #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))
      (modify-if-exists :contexts_for_collection_edit #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))
      (modify-if-exists :contexts_for_collection_extra #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))
      (modify-if-exists :contexts_for_entry_edit #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))
      (modify-if-exists :contexts_for_context_keys #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))
      (modify-if-exists :catalog_context_keys #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))
      (modify-if-exists :copyright_notice_templates #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))
      (modify-if-exists :allowed_people_subtypes #(if (nil? %) [:raw "'[]'"] (convert-to-raw-set %)))))

; [madek.api.utils.helper :refer [cast-to-hstore]]
(defn cast-to-hstore [data]
  (let [keys [:labels :descriptions :contents :hints :documentation_urls
              :site_titles :brand_texts :welcome_titles :welcome_texts
              :featured_set_titles :featured_set_subtitles :catalog_subtitles :catalog_titles
              :about_pages :support_urls :provenance_notices]]
    (reduce (fn [acc key]
              (if (contains? acc key)
                (let [field-value (get acc key)
                      transformed-value (to-hstore field-value)]
                  (assoc acc key transformed-value))
                acc))
            data
            keys)))

(defn array-to-map [arr]
  (zipmap arr (range (count arr))))

(defn map-to-array [m]
  (map first (sort-by val m)))

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

;; TODO / remove this fnc, just use to-uuid: if its valid it will be casted
; [madek.api.utils.helper :refer [convert-groupid]]
(defn convert-groupid [group-id]
  (let [is_uuid (boolean (re-matches
                          #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
                          group-id))
        group-id (if is_uuid (to-uuid group-id) group-id)
        res {:group-id group-id}]
    res))

(defn parse-to-int [value default-value]
  (try
    (let [value (if (instance? java.lang.Long value) (str value) value)
          value (if (instance? java.lang.Integer value) (str value) value)]
      (Integer/parseInt value))
    (catch Exception e
      default-value)))

; [madek.api.utils.helper :refer [parse-specific-keys]]
(defn parse-specific-keys [params defaults]
  (into {}
        (map (fn [[k v]]
               [k (if (contains? defaults k)
                    (parse-to-int v (defaults k))
                    v)])
             params)))
