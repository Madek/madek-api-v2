(ns madek.api.resources.app-settings
  (:require
   [clojure.java.io :as io]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [cast-to-hstore convert-map-if-exist]]
   [madek.api.utils.helper :refer [mslurp]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

;;; get ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn transform_ml [data]
  ; ?: this seems to be rather crude, ist there not a way to seq over this with
  ; the help of the schema?
  (assoc data
         :about_pages (sd/transform_ml (:about_pages data))
         :banner_messages (sd/transform_ml (:banner_messages data))
         :brand_texts (sd/transform_ml (:brand_texts data))
         :catalog_subtitles (sd/transform_ml (:catalog_subtitles data))
         :catalog_titles (sd/transform_ml (:catalog_titles data))
         :featured_set_subtitles (sd/transform_ml (:featured_set_subtitles data))
         :featured_set_titles (sd/transform_ml (:featured_set_titles data))
         :provenance_notices (sd/transform_ml (:provenance_notices data))
         :site_titles (sd/transform_ml (:site_titles data))
         :support_urls (sd/transform_ml (:support_urls data))
         :welcome_texts (sd/transform_ml (:welcome_texts data))
         :welcome_titles (sd/transform_ml (:welcome_titles data))))

(defn db-get-app-settings [tx]
  (-> (sql/select :*)
      (sql/from :app_settings)
      sql-format
      ((partial jdbc/execute-one! tx))))

(defn handle_get-app-settings [{tx :tx}]
  (sd/response_ok (transform_ml (db-get-app-settings tx))))

;;; update ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update-app-settings
  "Updates app_settings and returns true if that happened and
  false otherwise"
  [data tx]
  (let [data (convert-map-if-exist (cast-to-hstore data))
        res (-> (sql/update :app_settings)
                (sql/set data)
                (sql-format :inline false)
                (->> (jdbc/execute-one! tx))
                :next.jdbc/update-count
                (= 1))] res))

(defn handle_update-app-settings
  [{{body :body} :parameters tx :tx}]
  (try
    (catcher/with-logging {}
      (if (update-app-settings body tx)
        (sd/response_ok (transform_ml (db-get-app-settings tx)))
        (sd/response_failed "Could not update app-settings." 406)))
    (catch Exception ex (sd/parsed_response_exception ex))))

;;; schema ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def schema_update-app-settings
  {(s/optional-key :about_pages) sd/schema_ml_list
   (s/optional-key :available_locales) [s/Str]
   (s/optional-key :banner_messages) sd/schema_ml_list
   (s/optional-key :brand_logo_url) (s/maybe s/Str)
   (s/optional-key :brand_texts) sd/schema_ml_list
   (s/optional-key :catalog_caching) (s/maybe s/Bool)
   (s/optional-key :catalog_context_keys) [s/Str]
   (s/optional-key :catalog_subtitles) sd/schema_ml_list
   (s/optional-key :catalog_titles) sd/schema_ml_list
   (s/optional-key :context_for_collection_summary) (s/maybe s/Str)
   (s/optional-key :context_for_entry_summary) (s/maybe s/Str)
   (s/optional-key :contexts_for_collection_edit) [s/Str]
   (s/optional-key :contexts_for_collection_extra) [s/Str]
   (s/optional-key :contexts_for_dynamic_filters) [s/Str]
   (s/optional-key :contexts_for_entry_edit) [s/Str]
   (s/optional-key :contexts_for_entry_extra) [s/Str]
   (s/optional-key :contexts_for_entry_validation) [s/Str]
   (s/optional-key :contexts_for_list_details) [s/Str]
   (s/optional-key :copyright_notice_default_text) (s/maybe s/Str)
   (s/optional-key :copyright_notice_templates) [s/Str]
   (s/optional-key :default_locale) (s/maybe s/Str)
   (s/optional-key :edit_meta_data_power_users_group_id) (s/maybe s/Uuid)
   (s/optional-key :featured_set_subtitles) sd/schema_ml_list
   (s/optional-key :featured_set_titles) sd/schema_ml_list
   (s/optional-key :ignored_keyword_keys_for_browsing) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_id) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_meta_key) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_usage_meta_key) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_usage_text) (s/maybe s/Str)
   (s/optional-key :person_info_fields) [s/Str]
   (s/optional-key :provenance_notices) (s/maybe sd/schema_ml_list)
   (s/optional-key :section_meta_key_id) (s/maybe s/Str)
   (s/optional-key :site_titles) sd/schema_ml_list
   (s/optional-key :sitemap) (s/maybe s/Any) ;jsonb
   (s/optional-key :splashscreen_slideshow_set_id) (s/maybe s/Uuid)
   (s/optional-key :support_urls) sd/schema_ml_list
   (s/optional-key :teaser_set_id) (s/maybe s/Uuid)
   (s/optional-key :time_zone) s/Str
   (s/optional-key :welcome_texts) sd/schema_ml_list
   (s/optional-key :welcome_titles) sd/schema_ml_list
   (s/optional-key :featured_set_id) (s/maybe s/Uuid)})

(def schema_export-app-settings
  {(s/optional-key :about_pages) (s/maybe sd/schema_ml_list)
   (s/optional-key :available_locales) [s/Str]
   (s/optional-key :banner_messages) (s/maybe sd/schema_ml_list)
   (s/optional-key :brand_logo_url) (s/maybe s/Str)
   (s/optional-key :brand_texts) (s/maybe sd/schema_ml_list)
   (s/optional-key :catalog_caching) (s/maybe s/Bool)
   (s/optional-key :catalog_context_keys) [s/Str]
   (s/optional-key :catalog_subtitles) (s/maybe sd/schema_ml_list)
   (s/optional-key :catalog_titles) (s/maybe sd/schema_ml_list)
   (s/optional-key :context_for_collection_summary) (s/maybe s/Str)
   (s/optional-key :context_for_entry_summary) (s/maybe s/Str)
   (s/optional-key :contexts_for_collection_edit) [s/Str]
   (s/optional-key :contexts_for_collection_extra) [s/Str]
   (s/optional-key :contexts_for_dynamic_filters) [s/Str]
   (s/optional-key :contexts_for_entry_edit) [s/Str]
   (s/optional-key :contexts_for_entry_extra) [s/Str]
   (s/optional-key :contexts_for_entry_validation) [s/Str]
   (s/optional-key :contexts_for_list_details) [s/Str]
   (s/optional-key :copyright_notice_default_text) (s/maybe s/Str)
   (s/optional-key :copyright_notice_templates) [s/Str]
   (s/optional-key :created_at) s/Any
   (s/optional-key :default_locale) (s/maybe s/Str)
   (s/optional-key :edit_meta_data_power_users_group_id) (s/maybe s/Uuid)
   (s/optional-key :featured_set_id) (s/maybe s/Uuid)
   (s/optional-key :featured_set_subtitles) (s/maybe sd/schema_ml_list)
   (s/optional-key :featured_set_titles) (s/maybe sd/schema_ml_list)
   (s/optional-key :id) s/Int
   (s/optional-key :ignored_keyword_keys_for_browsing) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_id) (s/maybe s/Uuid)
   (s/optional-key :media_entry_default_license_meta_key) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_usage_meta_key) (s/maybe s/Str)
   (s/optional-key :media_entry_default_license_usage_text) (s/maybe s/Str)
   (s/optional-key :person_info_fields) [s/Str]
   (s/optional-key :provenance_notices) (s/maybe sd/schema_ml_list)
   (s/optional-key :section_meta_key_id) (s/maybe s/Str)
   (s/optional-key :site_titles) (s/maybe sd/schema_ml_list)
   (s/optional-key :sitemap) (s/maybe s/Any) ;jsonb
   (s/optional-key :splashscreen_slideshow_set_id) (s/maybe s/Uuid)
   (s/optional-key :support_urls) (s/maybe sd/schema_ml_list)
   (s/optional-key :teaser_set_id) (s/maybe s/Uuid)
   (s/optional-key :time_zone) s/Str
   (s/optional-key :updated_at) s/Any
   (s/optional-key :users_active_until_ui_default) (s/maybe s/Int)
   (s/optional-key :welcome_texts) (s/maybe sd/schema_ml_list)
   (s/optional-key :welcome_titles) (s/maybe sd/schema_ml_list)})

;;; routes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def admin-routes
  [["/"
    {:openapi {:tags ["admin/app-settings"] :security ADMIN_AUTH_METHODS}}
    ["app-settings"
     {:get {:summary (sd/sum_adm "Get App Settings.")
            :handler handle_get-app-settings
            :middleware [wrap-authorize-admin!]
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :coercion reitit.coercion.schema/coercion
            :responses {200 {:body schema_export-app-settings
                             :description "Returns the app settings."}}}

      :put {:summary (sd/sum_adm "Update App Settings.")
            :handler handle_update-app-settings
            :middleware [wrap-authorize-admin!]
            :description (mslurp (io/resource "md/admin-app-settings.md"))
            :swagger {:produces "application/json"
                      :consumes "application/json"}
            :content-type "application/json"
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_update-app-settings}
            :responses {200 {:description "Returns the updated app settings."
                             :body schema_export-app-settings}
                        403 {:description "Only administrators are allowed to access this resource."}
                        404 {:description "<Groups|Meta-Keys> entry does not exist"}}}}]]])

(def user-routes
  [["/"
    {:openapi {:tags ["app-settings"] :security []}}
    ["app-settings"
     {:get {:summary (sd/?no-auth? (sd/sum_pub "Get App Settings."))
            :handler handle_get-app-settings
            :swagger {:produces "application/json"}
            :content-type "application/json"
            :coercion reitit.coercion.schema/coercion
            :responses {200 {:description "Returns the app settings."
                             :body schema_export-app-settings}}}}]]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
