(ns madek.api.resources.app-settings
  (:require [clojure.java.jdbc :as jdbc]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.auth :refer [wrap-authorize-admin!]]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [madek.api.utils.sql :as sql]
            [reitit.coercion.schema]
            [schema.core :as s]
            [logbug.catcher :as catcher]))

(defn transform_ml [data]
  (assoc data
         :about_pages (sd/transform_ml (:about_pages data))

         :brand_texts (sd/transform_ml (:brand_texts data))
         :catalog_titles (sd/transform_ml (:catalog_titles data))
         :catalog_subtitles (sd/transform_ml (:catalog_subtitles data))

         :featured_set_titles (sd/transform_ml (:featured_set_titles data))
         :featured_set_subtitles (sd/transform_ml (:featured_set_subtitles data))
         :provenance_notices (sd/transform_ml (:provenance_notices data))
         :site_titles (sd/transform_ml (:site_titles data))
         :support_urls (sd/transform_ml (:support_urls data))
         :welcome_titles (sd/transform_ml (:welcome_titles data))
         :welcome_texts (sd/transform_ml (:welcome_texts data))))
(defn db_get-app-settings []
  (let [query (->
               (sql/select :*)
               (sql/from :app_settings)
               (sql/format))
        result (first (jdbc/query (get-ds) query))]
    result))

(defn handle_get-app-settings
  [req]
  (sd/response_ok (transform_ml (db_get-app-settings))))

(defn- set-sql-type [data key type]
  (if-let [value (key data)]
    (assoc data key (with-meta value {:pgtype type}))
    data))


(defn handle_update-app-settings
  [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            ins-data (-> data (set-sql-type :sitemap "json"))
            upd-clause (sd/sql-update-clause "id" 0)
            upd-result (jdbc/update! (rdbms/get-ds)
                                     :app_settings
                                     ins-data upd-clause)]



        (sd/logwrite req (str "handle_update-app-settings:"
                              "\ndata\n" data
                              "\nresult\n" upd-result))
        (if (= 1 (first upd-result))
          (sd/response_ok (transform_ml (db_get-app-settings)))
          (sd/response_failed "Could not update app-settings." 406))))
    (catch Exception ex (sd/response_exception ex))))



(def schema_update-app-settings
  {(s/optional-key :featured_set_id) (s/maybe s/Uuid)
   (s/optional-key :splashscreen_slideshow_set_id) (s/maybe s/Uuid)
   (s/optional-key :teaser_set_id) (s/maybe s/Uuid)

   (s/optional-key :brand_logo_url) (s/maybe s/Str)
   (s/optional-key :sitemap) (s/maybe s/Any) ;jsonb


   (s/optional-key :contexts_for_list_details) [s/Str]
   (s/optional-key :contexts_for_entry_validation) [s/Str]
   (s/optional-key :contexts_for_dynamic_filters) [s/Str]

   (s/optional-key :context_for_entry_summary) (s/maybe s/Str)
   (s/optional-key :context_for_collection_summary) (s/maybe s/Str)

   (s/optional-key :catalog_context_keys) [s/Str]

   (s/optional-key :contexts_for_entry_edit) [s/Str]
   (s/optional-key :contexts_for_collection_edit) [s/Str]

   (s/optional-key :contexts_for_entry_extra) [s/Str]
   (s/optional-key :contexts_for_collection_extra) [s/Str]
   (s/optional-key :media_entry_default_license_id) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_meta_key) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_usage_text) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_usage_meta_key) (s/maybe s/Str)
   (s/optional-key :ignored_keyword_keys_for_browsing) (s/maybe s/Str)
   (s/optional-key :default_locale) (s/maybe s/Str)
   (s/optional-key :available_locales) [s/Str]
   (s/optional-key :site_titles) sd/schema_ml_list
   (s/optional-key :brand_texts) sd/schema_ml_list
   (s/optional-key :welcome_titles) sd/schema_ml_list
   (s/optional-key :welcome_texts) sd/schema_ml_list
   (s/optional-key :featured_set_titles) sd/schema_ml_list
   (s/optional-key :featured_set_subtitles) sd/schema_ml_list
   (s/optional-key :catalog_titles) sd/schema_ml_list
   (s/optional-key :catalog_subtitles) sd/schema_ml_list
   (s/optional-key :about_pages) sd/schema_ml_list
   (s/optional-key :support_urls) sd/schema_ml_list
   (s/optional-key :provenance_notices) sd/schema_ml_list
   (s/optional-key :time_zone) s/Str
   (s/optional-key :copyright_notice_templates) [s/Str]
   (s/optional-key :copyright_notice_default_text) (s/maybe s/Str)
   (s/optional-key :section_meta_key_id) (s/maybe s/Str)

   (s/optional-key :edit_meta_data_power_users_group_id) (s/maybe s/Str)
   })

(def schema_export-app-settings
  {:id s/Int
   (s/optional-key :featured_set_id) (s/maybe s/Uuid)
   (s/optional-key :splashscreen_slideshow_set_id) (s/maybe s/Uuid)
   (s/optional-key :teaser_set_id) (s/maybe s/Uuid)


   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any

   (s/optional-key :brand_logo_url) (s/maybe s/Str)
   (s/optional-key :sitemap) (s/maybe s/Any) ;jsonb


   (s/optional-key :contexts_for_list_details) [s/Str]
   (s/optional-key :contexts_for_entry_validation) [s/Str]
   (s/optional-key :contexts_for_dynamic_filters) [s/Str]

   (s/optional-key :context_for_entry_summary) (s/maybe s/Str)
   (s/optional-key :context_for_collection_summary) (s/maybe s/Str)

   (s/optional-key :catalog_context_keys) [s/Str]

   (s/optional-key :contexts_for_entry_edit) [s/Str]
   (s/optional-key :contexts_for_collection_edit) [s/Str]

   (s/optional-key :contexts_for_entry_extra) [s/Str]
   (s/optional-key :contexts_for_collection_extra) [s/Str]
   (s/optional-key :media_entry_default_license_id) (s/maybe s/Uuid)
   (s/optional-key :media_entry_default_license_meta_key) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_usage_text) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_usage_meta_key) (s/maybe s/Str)
   (s/optional-key :ignored_keyword_keys_for_browsing) (s/maybe s/Str)
   (s/optional-key :default_locale) (s/maybe s/Str)
   (s/optional-key :available_locales) [s/Str]
   (s/optional-key :site_titles) (s/maybe sd/schema_ml_list)
   (s/optional-key :brand_texts) (s/maybe sd/schema_ml_list)
   (s/optional-key :welcome_titles) (s/maybe sd/schema_ml_list)
   (s/optional-key :welcome_texts) (s/maybe sd/schema_ml_list)
   (s/optional-key :featured_set_titles) (s/maybe sd/schema_ml_list)
   (s/optional-key :featured_set_subtitles) (s/maybe sd/schema_ml_list)
   (s/optional-key :catalog_titles) (s/maybe sd/schema_ml_list)
   (s/optional-key :catalog_subtitles) (s/maybe sd/schema_ml_list)
   (s/optional-key :about_pages) (s/maybe sd/schema_ml_list)
   (s/optional-key :support_urls) (s/maybe sd/schema_ml_list)
   (s/optional-key :provenance_notices) sd/schema_ml_list
   (s/optional-key :time_zone) s/Str
   (s/optional-key :copyright_notice_templates) [s/Str]
   (s/optional-key :copyright_notice_default_text) (s/maybe s/Str)
   (s/optional-key :section_meta_key_id) (s/maybe s/Str)

   (s/optional-key :edit_meta_data_power_users_group_id) (s/maybe s/Str)
   })

(def admin-routes
  [["/app-settings"
    {:get {:summary (sd/sum_adm "Get App Settings.")
           :handler handle_get-app-settings
           :middleware [wrap-authorize-admin!]
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:body s/Any}}}

     :put {:summary (sd/sum_adm "Update App Settings.")
           :handler handle_update-app-settings
           :middleware [wrap-authorize-admin!]
           :swagger {:produces "application/json"
                     :consumes  "application/json"}
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           :parameters {:body schema_update-app-settings}
           :responses {200 {:body schema_export-app-settings}
                       406 {:body s/Any}}}}]])

(def user-routes
  [["/app-settings"
    {:get {:summary (sd/sum_pub "Get App Settings.")
           :handler handle_get-app-settings
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:body schema_export-app-settings}}}}]])