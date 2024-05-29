(ns madek.api.db.dynamic_schema.schema_definitions
  (:require
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.db.dynamic_schema.statics :refer [TYPE_EITHER TYPE_MAYBE TYPE_NOTHING TYPE_OPTIONAL]]
   [madek.api.utils.validation :refer [vector-or-hashmap-validation]]
   [schema.core :as s]))

(def type-mapping {"varchar" s/Str
                   "int4" s/Int
                   "integer" s/Int
                   "boolean" s/Bool
                   "uuid" s/Uuid
                   "text" s/Str
                   "jsonb" s/Any
                   "character varying" s/Str
                   "timestamp with time zone" s/Any
                   ;; helper
                   "str" s/Str
                   "any" s/Any})

;; ### TODO: not yet defined by db? ###################################################
(def schema_full_data_raw [{:column_name "full_data", :data_type "boolean"}])

(def schema_pagination_raw [{:column_name "page", :data_type "int4"}
                            {:column_name "count", :data_type "int4"}])

(def schema-de-en {(s/optional-key :de) (s/maybe s/Str)
                   (s/optional-key :en) (s/maybe s/Str)})

(def schema-allowed_people_subtypes (s/enum "People" "PeopleGroup"))

(def schema-subtype (s/enum "Person" "PeopleGroup" "PeopleInstitutionalGroup"))

(def schema-meta_datum (s/enum "MetaDatum::Text"
                               "MetaDatum::TextDate"
                               "MetaDatum::JSON"
                               "MetaDatum::Keywords"
                               "MetaDatum::People"
                               "MetaDatum::Roles"))

(def schema-allowed_people_subtypes (s/enum "People" "PeopleGroup"))

(def schema-scope (s/enum "view" "use"))

(def schema_sorting_types
  (s/enum "created_at ASC"
          "created_at DESC"
          "title ASC"
          "title DESC"
          "last_change"
          "manual ASC"
          "manual DESC"))

;; ####################################################################################

(defn type-mapping-enums [key get-enum] "Maps a <table>.<key> to a Spec type, eg.: enum OR schema-definition "
  (let [enum-map {"collections.default_resource_type" (get-enum :collections_default_resource_type)
                  "collections.layout" (get-enum :collections_layout)
                  "collections.sorting" (get-enum :collections_sorting)

                  "groups.type" (get-enum :groups.type)

                  "users.settings" vector-or-hashmap-validation

                  "app_settings.about_pages" schema-de-en
                  "app_settings.brand_texts" schema-de-en
                  "app_settings.catalog_subtitles" schema-de-en
                  "app_settings.catalog_titles" schema-de-en
                  "app_settings.featured_set_subtitles" schema-de-en
                  "app_settings.featured_set_titles" schema-de-en
                  "app_settings.provenance_notices" schema-de-en
                  "app_settings.site_titles" schema-de-en
                  "app_settings.support_urls" schema-de-en
                  "app_settings.welcome_texts" schema-de-en
                  "app_settings.welcome_titles" schema-de-en

                  "app_settings.available_locales" [s/Str]
                  "app_settings.contexts_for_entry_extra" [s/Str]
                  "app_settings.contexts_for_entry_validation" [s/Str]
                  "app_settings.catalog_context_keys" [s/Str]
                  "app_settings.contexts_for_dynamic_filters" [s/Str]
                  "app_settings.contexts_for_collection_extra" [s/Str]
                  "app_settings.copyright_notice_templates" [s/Str]
                  "app_settings.contexts_for_entry_edit" [s/Str]
                  "app_settings.contexts_for_collection_edit" [s/Str]
                  "app_settings.contexts_for_list_details" [s/Str]

                  "context_keys.labels" schema-de-en
                  "context_keys.descriptions" schema-de-en
                  "context_keys.hints" schema-de-en
                  "context_keys.documentation_urls" schema-de-en

                  "contexts.labels" schema-de-en
                  "contexts.descriptions" schema-de-en

                  "vocabularies.descriptions" schema-de-en
                  "vocabularies.labels" schema-de-en

                  "static-pages.contents" schema-de-en

                  "roles.labels" schema-de-en

                  "people.external_uris" [s/Str]
                  "people.subtype" schema-subtype
                  "keywords.external_uris" [s/Str]

                  ;; TODO: check if this is correct, should be "meta_keys .."
                  "meta-keys.meta_datum_object_type" schema-meta_datum
                  "meta-keys.allowed_people_subtypes" schema-allowed_people_subtypes
                  "meta-keys.scope" schema-scope
                  ;"meta_keys.meta_datum_object_type" schema-meta_datum
                  ;"meta_keys.allowed_people_subtypes" schema-allowed_people_subtypes
                  ;"meta_keys.scope" schema-scope

                  "meta_keys.labels" schema-de-en
                  "meta_keys.descriptions" schema-de-en
                  "meta_keys.hints" schema-de-en
                  "meta_keys.documentation_urls" schema-de-en

                  "media_files.conversion_profiles" [s/Any]}

        res (get enum-map key nil)]
    res))

(def create-groups-schema [{:raw [{:groups {}}
                                  {:_additional (concat schema_pagination_raw schema_full_data_raw)}]
                            :raw-schema-name :groups-schema-with-pagination-raw
                            :schemas [{:groups.schema-query-groups {:key-types "optional"
                                                                    :alias "schema_query-groups"}}]}

                           {:raw [{:groups {}}],
                            :raw-schema-name :groups-schema-raw
                            :schemas [{:groups.schema-update-group {:alias "schema_update-group"
                                                                    :key-types "optional"
                                                                    :value-types TYPE_MAYBE
                                                                    :types [{:name {:value-type TYPE_NOTHING}} {:type {:value-type TYPE_NOTHING}}]
                                                                    :wl [:name :type :institution :institutional_id :institutional_name :created_by_user_id]}}

                                      {:groups.schema-export-group {:alias "schema_export-group"
                                                                    :key-types TYPE_OPTIONAL
                                                                    :types [{:id {:key-type TYPE_NOTHING}}
                                                                            {:created_by_user_id {:value-type TYPE_MAYBE}}
                                                                            {:institutional_id {:value-type TYPE_MAYBE}}
                                                                            {:institutional_name {:value-type TYPE_MAYBE}}
                                                                            {:institution {:value-type TYPE_MAYBE}}]}}

                                      {:groups.schema-import-group {:alias "schema_import-group"
                                                                    :key-types TYPE_OPTIONAL
                                                                    :value-types TYPE_MAYBE
                                                                    :types [;{:id {:key-type TYPE_NOTHING}}
                                                                            {:name {:key-type TYPE_NOTHING :value-type TYPE_NOTHING}}
                                                                            {:type {:value-type TYPE_NOTHING}}
                                                                            {:created_by_user_id {:value-type TYPE_MAYBE}}
                                                                            {:institutional_id {:value-type TYPE_MAYBE}}
                                                                            {:institutional_name {:value-type TYPE_MAYBE}}
                                                                            {:institution {:value-type TYPE_MAYBE}}]}}]}

                           {:raw [{:users {:wl ["id" "email" "institutional_id" "login" "created_at" "updated_at" "person_id"]}}],
                            :raw-schema-name :groups-schema-response-user-simple-raw

                            :schemas [{:groups.schema-response-user-simple {:alias "schema_response-user-simple"
                                                                            :value-types TYPE_MAYBE
                                                                            :types [{:id {:value-type TYPE_NOTHING}}]
                                                                            :bl [:login :created_at :updated_at]}}

                                      {:groups.schema-update-group-user-list {:alias "schema_update-group-user-list"
                                                                              :key-types "optional"
                                                                              :types [{:id {:key-type TYPE_NOTHING}}]
                                                                              :bl [:login :created_at :updated_at :person_id]}}]}])

(def create-users-schema [{:raw [{:users {}}
                                 {:_additional [{:column_name "is_admin", :data_type "boolean"}]}],
                           :raw-schema-name :users-schema-raw
                           :schemas [{:users-schema-payload {:alias "maru.update/schema"
                                                             :key-types "optional"
                                                             :types [{:accepted_usage_terms_id {:value-type TYPE_MAYBE}} {:notes {:value-type TYPE_MAYBE}}]
                                                             :wl [:accepted_usage_terms_id :autocomplete :email :institution :first_name :last_name :login :notes :searchable]}}

                                     {:get.users-schema-payload {:alias "mar.users.get/schema"
                                                                 :value-types TYPE_MAYBE
                                                                 :types [{:created_at {:value-type TYPE_NOTHING}}
                                                                         {:email {:key-type TYPE_OPTIONAL :value-type TYPE_NOTHING}}
                                                                         {:id {:value-type TYPE_NOTHING}}
                                                                         {:person_id {:value-type TYPE_NOTHING}}
                                                                         {:is_admin {:value-type TYPE_NOTHING}}
                                                                         {:updated_at {:value-type TYPE_NOTHING}}
                                                                         {:settings {:key-type TYPE_OPTIONAL :value-type TYPE_NOTHING}}]
                                                                 :bl [:searchable :active_until :autocomplete]}}

                                     {:create.users-schema-payload {:alias "mar.users.create/schema"
                                                                    :key-types "optional"
                                                                    :types [{:person_id {:key-type TYPE_NOTHING}}
                                                                            {:accepted_usage_terms_id {:value-type TYPE_MAYBE}}
                                                                            {:notes {:value-type TYPE_MAYBE}}]
                                                                    :wl [:person_id :accepted_usage_terms_id :email :institution :institutional_id :first_name :last_name :login :notes :settings]}}]}])

(def create-admins-schema [{:raw [{:admins {}}],
                            :raw-schema-name :groups-schema-raw
                            :schemas [{:admins.schema_export-admin {:alias "mar.admin/schema_export-admin"
                                                                    :key-types "optional"
                                                                    :types [{:id {:key-type TYPE_NOTHING}}]}}]}])

(def create-workflows-schema [{:raw [{:workflows {}}],
                               :raw-schema-name :workflows-schema-raw
                               :schemas [{:workflows.schema_create_workflow {:alias "mar.workflow/schema_create_workflow"
                                                                             :key-types "optional"
                                                                             :types [{:name {:key-type TYPE_NOTHING}}]
                                                                             :wl [:name :is_active :configuration]}}

                                         {:workflows.schema_update_workflow {:alias "mar.workflow/schema_update_workflow"
                                                                             :key-types "optional"
                                                                             :wl [:name :is_active :configuration]}}

                                         {:workflows.schema_export_workflow {:alias "mar.workflow/schema_export_workflow"
                                                                             :key-types "optional"
                                                                             :types [{:id {:key-type TYPE_NOTHING}}]
                                                                             :wl [:name :is_active :configuration :creator_id :created_at :updated_at]}}]}])

(def create-collections-schema [{:raw [{:collections {}}],
                                 :raw-schema-name :collections-schema-raw
                                 :schemas [{:collections.schema_collection-import {:alias "mar.collections/schema_collection-import"
                                                                                   :key-types "optional"
                                                                                   :types [{:default_context_id {:value-type TYPE_MAYBE}}
                                                                                           {:workflow_id {:value-type TYPE_MAYBE}}]}}

                                           {:collections.schema_collection-export {:alias "mar.collections/schema_collection-export"
                                                                                   :key-types "optional"
                                                                                   :types [{:id {:key-type TYPE_NOTHING}}
                                                                                           {:responsible_user_id {:value-type TYPE_MAYBE}}
                                                                                           {:default_context_id {:value-type TYPE_MAYBE}}
                                                                                           {:clipboard_user_id {:value-type TYPE_MAYBE}}
                                                                                           {:workflow_id {:value-type TYPE_MAYBE}}
                                                                                           {:responsible_delegation_id {:value-type TYPE_MAYBE}}]}}

                                           {:collections.schema_collection-update {:alias "mar.collections/schema_collection-update"
                                                                                   :key-types "optional"
                                                                                   :types [{:default_context_id {:value-type TYPE_MAYBE}}
                                                                                           {:workflow_id {:value-type TYPE_MAYBE}}]
                                                                                   :wl [:layout :is_master :sorting :default_context_id :workflow_id :default_resource_type]}}]}

                                {:raw [{:collections {:wl ["collection_id" "creator_id" "responsible_user_id" "clipboard_user_id" "workflow_id" "responsible_delegation_id" "public_get_metadata_and_previews"]
                                                      :_additional [{:column_name "order", :data_type "any"}]
                                                      :rename {"get_metadata_and_previews" "public_get_metadata_and_previews"
                                                               "id" "collection_id"}}}
                                       {:collection_user_permissions {:wl ["me_get_metadata_and_previews" "me_edit_permission" "me_edit_metadata_and_relations"]
                                                                      :rename {"get_metadata_and_previews" "me_get_metadata_and_previews"
                                                                               "edit_permissions" "me_edit_permission"
                                                                               "edit_metadata_and_relations" "me_edit_metadata_and_relations"}}}
                                       {:_additional (concat schema_pagination_raw schema_full_data_raw)}],
                                 :raw-schema-name :collections-collection_user_permission-schema-raw
                                 :schemas [{:collections.schema_collection-query {:alias "mar.collections/schema_collection-query"
                                                                                  :key-types "optional"}}]}])

(def create-collection-media-entry-schema [{:raw [{:collection_media_entry_arcs {}}],
                                            :raw-schema-name :collection-media-entry-arcs-schema-raw

                                            :schemas [{:collection_mea.schema_collection-media-entry-arc-export {:alias "mar.collection-meida-entry-arcs/schema_collection-media-entry-arc-export"
                                                                                                                 :types [{:cover {:value-type TYPE_MAYBE}}
                                                                                                                         {:order {:value-type TYPE_MAYBE}}
                                                                                                                         {:position {:value-type TYPE_MAYBE}}]}}

                                                      {:collection_mea.schema_collection-media-entry-arc-update {:alias "mar.collection-media-entry-arcs/schema_collection-media-entry-arc-update"
                                                                                                                 :key-types "optional"
                                                                                                                 :wl [:highlight :cover :order :position]}}
                                                      {:collection_mea.schema_collection-media-entry-arc-create {:alias "mar.collection-media-entry-arcs/schema_collection-media-entry-arc-create"
                                                                                                                 :key-types "optional"
                                                                                                                 :wl [:highlight :cover :order :position]}}]}])

(def create-collection-collection-arcs-schema [{:raw [{:collection_collection_arcs {}}],
                                                :raw-schema-name :collection_collection_arcs-raw

                                                :schemas [{:collection_carcs.schema_collection-collection-arc-export {:alias "mar.collection-collection-arcs/schema_collection-collection-arc-export"}}

                                                          {:collection_carcs.schema_collection-collection-arc-update {:alias "mar.collection-collection-arcs/schema_collection-collection-arc-update"
                                                                                                                      :key-types "optional"
                                                                                                                      :wl [:highlight :order :position]}}

                                                          {:collection_carcs.schema_collection-collection-arc-create {:alias "mar.collection-collection-arcs/schema_collection-collection-arc-create"
                                                                                                                      :key-types "optional"
                                                                                                                      :wl [:highlight :order :position]}}]}])

(def create-app-settings-schema [{:raw [{:app_settings {}}],
                                  :raw-schema-name :app_settings-raw

                                  :schemas [{:app_settings-raw.schema_update-app-settings {:alias "mar.app-settings/schema_update-app-settings"
                                                                                           :key-types "optional"
                                                                                           :bl ["id"]
                                                                                           :types [{:brand_logo_url {:value-type TYPE_MAYBE}}
                                                                                                   {:context_for_collection_summary {:value-type TYPE_MAYBE}}
                                                                                                   {:context_for_entry_summary {:value-type TYPE_MAYBE}}
                                                                                                   {:copyright_notice_default_text {:value-type TYPE_MAYBE}}
                                                                                                   {:default_locale {:value-type TYPE_MAYBE}}
                                                                                                   {:edit_meta_data_power_users_group_id {:value-type TYPE_MAYBE}}
                                                                                                   {:ignored_keyword_keys_for_browsing {:value-type TYPE_MAYBE}}
                                                                                                   {:media_entry_default_license_id {:value-type TYPE_MAYBE}}
                                                                                                   {:media_entry_default_license_meta_key {:value-type TYPE_MAYBE}}
                                                                                                   {:media_entry_default_license_usage_meta_key {:value-type TYPE_MAYBE}}
                                                                                                   {:media_entry_default_license_usage_text {:value-type TYPE_MAYBE}}

                                                                                                   {:provenance_notices {:value-type TYPE_MAYBE}}

                                                                                                   {:section_meta_key_id {:value-type TYPE_MAYBE}}
                                                                                                   {:sitemap {:value-type TYPE_MAYBE}}
                                                                                                   {:splashscreen_slideshow_set_id {:value-type TYPE_MAYBE}}
                                                                                                   {:teaser_set_id {:value-type TYPE_MAYBE}}
                                                                                                   {:featured_set_id {:value-type TYPE_MAYBE}}]}}

                                            {:app_settings-raw.schema_export-app-settings {:alias "mar.app-settings/schema_export-app-settings"
                                                                                           :key-types "optional"
                                                                                           :value-types TYPE_MAYBE
                                                                                           :types [{:available_locales {:value-type TYPE_NOTHING}}
                                                                                                   {:catalog_context_keys {:value-type TYPE_NOTHING}}
                                                                                                   {:contexts_for_collection_edit {:value-type TYPE_NOTHING}}
                                                                                                   {:contexts_for_collection_extra {:value-type TYPE_NOTHING}}
                                                                                                   {:contexts_for_dynamic_filters {:value-type TYPE_NOTHING}}
                                                                                                   {:contexts_for_entry_edit {:value-type TYPE_NOTHING}}
                                                                                                   {:contexts_for_entry_extra {:value-type TYPE_NOTHING}}
                                                                                                   {:contexts_for_entry_validation {:value-type TYPE_NOTHING}}
                                                                                                   {:contexts_for_list_details {:value-type TYPE_NOTHING}}
                                                                                                   {:copyright_notice_templates {:value-type TYPE_NOTHING}}
                                                                                                   {:created_at {:value-type TYPE_NOTHING}}
                                                                                                   {:id {:value-type TYPE_NOTHING}}
                                                                                                   {:time_zone {:value-type TYPE_NOTHING}}]}}]}])

(def create-confidential-links-schema [{:raw [{:confidential_links {}}],
                                        :raw-schema-name :confidential_links-raw

                                        :schemas [{:confidential_links.schema_export_conf_link {:alias "mar.confidential_links/schema_export_conf_link"
                                                                                                :types [{:description {:value-type TYPE_MAYBE}}
                                                                                                        {:expires_at {:value-type TYPE_MAYBE}}]}}

                                                  {:confidential_links.schema_update_conf_link {:alias "mar.confidential_links/schema_update_conf_link"
                                                                                                :key-types "optional"
                                                                                                :value-types TYPE_MAYBE
                                                                                                :wl [:revoked :description :expires_at]
                                                                                                :types [{:revoked {:value-type TYPE_NOTHING}}]}}

                                                  {:confidential_links.schema_import_conf_link {:alias "mar.confidential_links/schema_import_conf_link"
                                                                                                :key-types "optional"
                                                                                                :value-types TYPE_MAYBE
                                                                                                :wl [:revoked :description :expires_at]
                                                                                                :types [{:revoked {:key-type TYPE_NOTHING :value-type TYPE_NOTHING}}]}}]}])

(def create-context-keys-schema [{:raw [{:context_keys {}}],
                                  :raw-schema-name :context_keys-raw

                                  :schemas [{:context_keys.schema_export_context_key_admin {:alias "mar.context_keys/schema_export_context_key_admin"
                                                                                            :types [{:length_max {:value-type TYPE_MAYBE}}
                                                                                                    {:length_min {:value-type TYPE_MAYBE}}
                                                                                                    {:labels {:value-type TYPE_MAYBE}}
                                                                                                    {:descriptions {:value-type TYPE_MAYBE}}
                                                                                                    {:hints {:value-type TYPE_MAYBE}}
                                                                                                    {:documentation_urls {:value-type TYPE_MAYBE}}
                                                                                                    {:admin_comment {:value-type TYPE_MAYBE}}]}}

                                            {:context_keys.schema_export_context_key {:alias "mar.context_keys/schema_export_context_key"
                                                                                      :types [{:length_max {:value-type TYPE_MAYBE}}
                                                                                              {:length_min {:value-type TYPE_MAYBE}}
                                                                                              {:labels {:value-type TYPE_MAYBE}}
                                                                                              {:descriptions {:value-type TYPE_MAYBE}}
                                                                                              {:hints {:value-type TYPE_MAYBE}}
                                                                                              {:documentation_urls {:value-type TYPE_MAYBE}}]
                                                                                      :bl [:admin_comment :updated_at :created_at]}}

                                            {:context_keys.schema_update_context_keys {:alias "mar.context_keys/schema_update_context_keys"
                                                                                       :key-types "optional"
                                                                                       :value-types TYPE_MAYBE
                                                                                       :types [{:is_required {:value-type TYPE_NOTHING}}
                                                                                               {:position {:value-type TYPE_NOTHING}}]
                                                                                       :bl [:id :context_id :meta_key_id :admin_comment :updated_at :created_at]}}

                                            {:context_keys.schema_import_context_keys {:alias "mar.context_keys/schema_import_context_keys"
                                                                                       :key-types "optional"
                                                                                       :value-types TYPE_MAYBE
                                                                                       :types [{:context_id {:key-type TYPE_NOTHING :value-type TYPE_NOTHING}}
                                                                                               {:meta_key_id {:key-type TYPE_NOTHING :value-type TYPE_NOTHING}}
                                                                                               {:is_required {:key-type TYPE_NOTHING :value-type TYPE_NOTHING}}
                                                                                               {:position {:key-type TYPE_NOTHING :value-type TYPE_NOTHING}}]
                                                                                       :bl [:id]}}]}])

(def create-context-schema [{:raw [{:contexts {}}],
                             :raw-schema-name :contexts-raw

                             :schemas [{:contexts.schema_import_contexts {:alias "mar.contexts/schema_import_contexts"
                                                                          :cache-as [:contexts.schema_export_contexts_adm]
                                                                          :value-types TYPE_MAYBE
                                                                          :types [{:id {:value-type TYPE_NOTHING}}]}}

                                       {:contexts.schema_update_contexts {:alias "mar.contexts/schema_update_contexts"
                                                                          :value-types TYPE_MAYBE
                                                                          :key-types "optional"
                                                                          :types [{:id {:value-type TYPE_NOTHING}}]}}

                                       {:contexts.schema_export_contexts_usr {:alias "mar.contexts/schema_export_contexts_usr"
                                                                              :bl [:admin_comment]
                                                                              :types [{:labels {:value-type TYPE_MAYBE}}
                                                                                      {:descriptions {:value-type TYPE_MAYBE}}]}}]}])

(def create-custom-urls-schema [{:raw [{:custom_urls {}}],
                                 :raw-schema-name :custom_urls-raw

                                 :schemas [{:custom_urls.schema_export_custom_url {:alias "mar.custom_urls/schema_export_custom_url"
                                                                                   :types [{:media_entry_id {:value-type TYPE_MAYBE}}
                                                                                           {:collection_id {:value-type TYPE_MAYBE}}]}}

                                           {:custom_urls.schema_update_custom_url {:alias "mar.custom_urls/schema_update_custom_url"
                                                                                   :key-types "optional"
                                                                                   :wl [:id :is_primary]}}
                                           {:custom_urls.schema_create_custom_url {:alias "mar.custom_urls/schema_create_custom_url"
                                                                                   :wl [:id :is_primary]}}]}])

(def create-delegation-schema [{:raw [{:delegations {}}],
                                :raw-schema-name :delegations-raw

                                :schemas [{:delegations.schema_export_delegations {:alias "mar.delegations/schema_export_delegations"
                                                                                   :types [{:admin_comment {:value-type TYPE_MAYBE}}]}}

                                          {:delegations.schema_get_delegations {:alias "mar.delegations/schema_get_delegations"
                                                                                :key-types "optional"
                                                                                :types [{:admin_comment {:value-type TYPE_MAYBE}}
                                                                                        {:id {:key-type TYPE_NOTHING}}]}}

                                          {:delegations.schema_update_delegations {:alias "mar.delegations/schema_update_delegations"
                                                                                   :key-types "optional"
                                                                                   :types [{:admin_comment {:value-type TYPE_MAYBE}}]
                                                                                   :bl [:id]}}

                                          {:delegations.schema_import_delegations {:alias "mar.delegations/schema_import_delegations"
                                                                                   :types [{:admin_comment {:value-type TYPE_MAYBE}}]
                                                                                   :bl [:id]}}]}])

(def create-edit_session-schema [{:raw [{:edit_sessions {}}],
                                  :raw-schema-name :edit_sessions-raw

                                  :schemas [{:edit_sessions.schema_export_edit_session {:alias "mar.edit_sessions/schema_export_edit_session"
                                                                                        :types [{:media_entry_id {:value-type TYPE_MAYBE}}
                                                                                                {:collection_id {:value-type TYPE_MAYBE}}]}}]}

                                 {:raw [{:edit_sessions {}}
                                        {:_additional (concat schema_pagination_raw schema_full_data_raw)}],
                                  :raw-schema-name :edit_sessions-with-pagination-raw

                                  :schemas [{:edit_sessions.schema_adm_query_edit_session {:alias "mar.edit_sessions/schema_adm_query_edit_session"
                                                                                           :key-types "optional"}}

                                            {:edit_sessions.schema_usr_query_edit_session {:alias "mar.edit_sessions/schema_usr_query_edit_session"
                                                                                           :key-types "optional"
                                                                                           :bl [:created_at]}}]}])

(def create-usage_terms-schema [{:raw [{:usage_terms {}}],
                                 :raw-schema-name :usage_terms-raw

                                 :schemas [{:usage_terms.schema_export_usage_term {:alias "mar.usage-terms/schema_export_usage_term"
                                                                                   :key-types "optional"
                                                                                   :types [{:id {:key-type TYPE_NOTHING}}]}}

                                           {:usage_terms.schema_update_usage_terms {:alias "mar.usage-terms/schema_update_usage_terms"
                                                                                    :key-types "optional"
                                                                                    :wl [:title :version :intro :body]}}

                                           {:usage_terms.schema_import_usage_terms {:alias "mar.usage-terms/schema_import_usage_terms"
                                                                                    :wl [:title :version :intro :body]}}]}])

(def create-static_pages-schema [{:raw [{:static_pages {}}],
                                  :raw-schema-name :static_pages-raw

                                  :schemas [{:static_pages.schema_export_static_page {:alias "mar.static-pages/schema_export_static_page"}}

                                            {:static_pages.schema_update_static_page {:alias "mar.static-pages/schema_update_static_page"
                                                                                      :key-types "optional"
                                                                                      :wl [:name :contents]}}

                                            {:static_pages.schema_create_static_page {:alias "mar.static-pages/schema_create_static_page"
                                                                                      :wl [:name :contents]}}]}])

(def create-roles-schema [{:raw [{:roles {}}],
                           :raw-schema-name :roles-raw

                           :schemas [{:roles.schema_export-role {:alias "mar.roles/schema_export-role"
                                                                 :types [{:creator_id {:key-type TYPE_OPTIONAL}}
                                                                         {:created_at {:key-type TYPE_OPTIONAL}}
                                                                         {:updated_at {:key-type TYPE_OPTIONAL}}]}}

                                     {:roles.schema_update-role {:alias "mar.roles/schema_update-role"
                                                                 :wl [:labels]}}
                                     {:roles.schema_create-role {:alias "mar.roles/schema_create-role"
                                                                 :wl [:meta_key_id :labels]}}]}])

(def create-previews-schema [{:raw [{:previews {}}],
                              :raw-schema-name :previews-raw

                              :schemas [{:previews.schema_export_preview {:alias "mar.previews/schema_export_preview"
                                                                          :types [{:width {:value-type TYPE_MAYBE}}
                                                                                  {:height {:value-type TYPE_MAYBE}}
                                                                                  {:conversion_profile {:value-type TYPE_MAYBE}}]}}]}])

(def create-delegations_users-schema [{:raw [{:delegations_users {}}
                                             {:users {:wl ["updated_at" "created_at"]}}],
                                       :raw-schema-name :delegations-users-raw

                                       :schemas [{:delegations-users.schema_delegations_users_export {:alias "mar.delegations-users/schema_delegations_users_export"}}]}])

(def create-io_interfaces-schema [{:raw [{:io_interfaces {}}],
                                   :raw-schema-name :io_interfaces-raw
                                   :schemas [{:io_interfaces.schema_export_io_interfaces {:alias "mar.io_interfaces/schema_export_io_interfaces"
                                                                                          :types [{:description {:value-type TYPE_MAYBE}}]}}

                                             {:io_interfaces.schema_export_io_interfaces_opt {:alias "mar.io_interfaces/schema_export_io_interfaces_opt"
                                                                                              :key-types "optional"
                                                                                              :types [{:id {:key-type TYPE_NOTHING}}
                                                                                                      {:description {:value-type TYPE_MAYBE}}]}}

                                             {:io_interfaces.schema_update_io_interfaces {:alias "mar.io_interfaces/schema_update_io_interfaces"
                                                                                          :key-types "optional"
                                                                                          :wl [:description]}}

                                             {:io_interfaces.schema_import_io_interfaces {:alias "mar.io_interfaces/schema_import_io_interfaces"
                                                                                          :wl [:id :description]}}]}])

(def create-favorite_collections-schema [{:raw [{:favorite_collections {}}],
                                          :raw-schema-name :favorite_collections-raw

                                          :schemas [{:favorite_collections.schema_favorite_collection_export {:alias "mar.favorite_collections/schema_favorite_collection_export"
                                                                                                              :key-types "optional"
                                                                                                              :types [{:user_id {:key-type TYPE_NOTHING}}]}}]}])

(def create-media-files-schema [{:raw [{:media_files {:wl ["id" "media_entry_id" "media_type" "content_type" "filename" "size" "updated_at" "created_at"]}}
                                       {:_additional [{:column_name "previews", :data_type "any"}]}],
                                 :raw-schema-name :media_files-raw
                                 :schemas [{:media_files.schema_export-media-file {:alias "mar.media-files/schema_export-media-file"
                                                                                   :types [{:media_type {:value-type TYPE_MAYBE}}]}}]}])

(def create-meta-data-schema [{:raw [{:meta_data {}}
                                     {:_additional [{:column_name "value", :data_type "str"}]}],
                               :raw-schema-name :meta_data-schema-raw

                               :schemas [{:meta-data-schema.schema_export_meta-datum {:alias "mar.meta-data/schema_export_meta-datum"
                                                                                      :wl [:id :meta_key_id :type :value :media_entry_id :collection_id]
                                                                                      :types [{:value {:value-type TYPE_EITHER
                                                                                                       :either-condition [[{:id s/Uuid}] s/Str]}}
                                                                                              {:media_entry_id {:key-type TYPE_OPTIONAL}}
                                                                                              {:collection_id {:key-type TYPE_OPTIONAL}}]}}]}])

(def create-meta-data-role-schema [{:raw [{:meta_data_roles {}}],
                                    :raw-schema-name :meta_data_roles-schema-raw

                                    :schemas [{:meta-data-role-schema.schema_export_mdrole {:alias "mar.meta-data/schema_export_mdrole"
                                                                                            :types [{:role_id {:value-type TYPE_MAYBE}}]}}]}])

(def create-favorite-media-entries-schema [{:raw [{:favorite-media-entries {}}],
                                            :raw-schema-name :favorite-media-entries-raw

                                            :schemas [{:favorite-media-entries-raw.schema_favorite_media_entries_export {:alias "mar.favorite-media-entries-raw/schema_favorite_media_entries_export"}}]}])

(def create-people-schema [{:raw [{:people {}}],
                            :raw-schema-name :people-raw

                            :schemas [{:people.schema {:alias "marp.create/schema"
                                                       :key-types "optional"
                                                       :value-types TYPE_MAYBE

                                                       :types [{:subtype {:key-type TYPE_NOTHING}}]
                                                       :bl [:id :created_at :updated_at :searchable]}}

                                      {:people.get.schema {:alias "marp.get/schema"
                                                           :value-types TYPE_MAYBE
                                                           :types [{:description {:value-type TYPE_MAYBE}}
                                                                   {:first_name {:value-type TYPE_MAYBE}}
                                                                   {:institutional_id {:value-type TYPE_MAYBE}}
                                                                   {:last_name {:value-type TYPE_MAYBE}}
                                                                   {:admin_comment {:value-type TYPE_MAYBE}}
                                                                   {:pseudonym {:value-type TYPE_MAYBE}}]
                                                           :bl [:searchable]}}]}])

(def create-media_entries-schema [{:raw [{:media_entries {}}],
                                   :raw-schema-name :media_entries-raw

                                   :schemas [{:media-entries.schema_export_media_entry {:alias "mar.media-entries/schema_export_media_entry"
                                                                                        :key-types "optional"
                                                                                        :types [{:id {:key-type TYPE_NOTHING}}
                                                                                                {:responsible_delegation_id {:value-type TYPE_MAYBE}}]}}]}

                                  {:raw [{:collection_media_entry_arcs {}}],
                                   :raw-schema-name :collection_media_entry_arcs-raw
                                   :schemas [{:media-entries.schema_export_col_arc {:alias "mar.media-entries/schema_export_col_arc"
                                                                                    :types [{:order {:value-type TYPE_MAYBE}}
                                                                                            {:position {:value-type TYPE_MAYBE}}]
                                                                                    :wl [:media_entry_id :id :order :position :created_at :updated_at]}}

                                             {:media-entries.schema_export_preview {:alias "mar.media-entries/schema_export_preview"
                                                                                    :raw-schema-name :preview-raw
                                                                                    :types [{:width {:value-type TYPE_MAYBE}}
                                                                                            {:height {:value-type TYPE_MAYBE}}
                                                                                            {:conversion_profile {:value-type TYPE_MAYBE}}]}}]}

                                  {:raw [{:meta_data {}}],
                                   :raw-schema-name :meta_data-raw
                                   :schemas [{:media-entries.schema_export_meta_data {:alias "mar.media-entries/schema_export_meta_data"
                                                                                      :types [{:media_entry_id {:value-type TYPE_MAYBE}}
                                                                                              {:collection_id {:value-type TYPE_MAYBE}}
                                                                                              {:string {:value-type TYPE_MAYBE}}
                                                                                              {:json {:value-type TYPE_MAYBE}}
                                                                                              {:other_media_entry_id {:value-type TYPE_MAYBE}}]
                                                                                      :bl [:created_by_id]}}]}

                                  {:raw [{:media_entries {:wl ["public_get_metadata_and_previews" "public_get_full_size"]
                                                          :rename {"get_metadata_and_previews" "public_get_metadata_and_previews"
                                                                   "get_full_size" "public_get_full_size"}}}

                                         {:collection_media_entry_arcs {:wl ["collection_id" "order"]}}

                                         {:media_entry_user_permissions {:wl ["me_get_metadata_and_previews" "me_get_full_size" "me_edit_metadata" "me_edit_permissions"]
                                                                         :rename {"get_metadata_and_previews" "me_get_metadata_and_previews"
                                                                                  "get_full_size" "me_get_full_size"
                                                                                  "edit_metadata" "me_edit_metadata"
                                                                                  "edit_permissions" "me_edit_permissions"}}}

                                         {:_additional (concat schema_pagination_raw schema_full_data_raw [{:column_name "filter_by", :data_type "str"}])}],
                                   :raw-schema-name :media_entries-media_entry_user_permission-collection_media_entry_arcs-raw

                                   :schemas [{:media-entries.schema_query_media_entries {:alias "mar.media-entries/schema_query_media_entries"
                                                                                         :key-types "optional"}}]}

                                  {:raw [{:media_files {}}],
                                   :raw-schema-name :media_files-raw

                                   :schemas [{:media-files.schema_export_media_file {:alias "mar.media-entries/schema_export_media_file"
                                                                                     :types [{:media_type {:value-type TYPE_MAYBE}}
                                                                                             {:width {:value-type TYPE_MAYBE}}
                                                                                             {:height {:value-type TYPE_MAYBE}}
                                                                                             {:meta_data {:value-type TYPE_MAYBE}}]}}]}])

(defn top-level-media_entries-schema [] {:media-entries-schema-schema_publish_failed {:message {:is_publishable s/Bool
                                                                                                :media_entry_id s/Uuid
                                                                                                :has_meta_data [{s/Any s/Bool}]}}

                                         :media-entries-schema-schema_query_media_entries_related_result {:media_entries [(get-schema :media-entries.schema_export_media_entry)]
                                                                                                          :meta_data [[(get-schema :media-entries.schema_export_meta_data)]]
                                                                                                          :media_files [(s/maybe (get-schema :media-files.schema_export_media_file))]
                                                                                                          :previews [[(s/maybe (get-schema :media-entries.schema_export_preview))]]
                                                                                                          (s/optional-key :col_arcs) [(get-schema :media-entries.schema_export_col_arc)]
                                                                                                          (s/optional-key :col_meta_data) [(get-schema :media-entries.schema_export_meta_data)]}})

(def create-meta_keys-schema [{:raw [{:meta_keys {}}],
                               :raw-schema-name :meta_keys-raw

                               :schemas [{:meta-keys.schema_create-meta-key {:alias "mar.meta-keys/schema_create-meta-key"
                                                                             :key-types "optional"
                                                                             :types [{:id {:key-type TYPE_NOTHING}}
                                                                                     {:is_extensible_list {:key-type TYPE_NOTHING}}
                                                                                     {:meta_datum_object_type {:key-type TYPE_NOTHING}}
                                                                                     {:is_enabled_for_media_entries {:key-type TYPE_NOTHING}}
                                                                                     {:is_enabled_for_collections {:key-type TYPE_NOTHING}}
                                                                                     {:vocabulary_id {:key-type TYPE_NOTHING}}

                                                                                     {:allowed_rdf_class {:value-type TYPE_MAYBE}}
                                                                                     {:labels {:value-type TYPE_MAYBE}}
                                                                                     {:descriptions {:value-type TYPE_MAYBE}}
                                                                                     {:hints {:value-type TYPE_MAYBE}}
                                                                                     {:documentation_urls {:value-type TYPE_MAYBE}}
                                                                                     {:admin_comment {:value-type TYPE_MAYBE}}]}}

                                         {:meta-keys.schema_update-meta-key {:alias "mar.meta-keys/schema_update-meta-key"
                                                                             :key-types "optional"
                                                                             :types [{:id {:key-type TYPE_NOTHING}}
                                                                                     {:is_extensible_list {:key-type TYPE_NOTHING}}
                                                                                     {:meta_datum_object_type {:key-type TYPE_NOTHING}}
                                                                                     {:is_enabled_for_media_entries {:key-type TYPE_NOTHING}}
                                                                                     {:is_enabled_for_collections {:key-type TYPE_NOTHING}}
                                                                                     {:vocabulary_id {:key-type TYPE_NOTHING}}

                                                                                     {:allowed_rdf_class {:value-type TYPE_MAYBE}}
                                                                                     {:labels {:value-type TYPE_MAYBE}}
                                                                                     {:descriptions {:value-type TYPE_MAYBE}}
                                                                                     {:hints {:value-type TYPE_MAYBE}}
                                                                                     {:documentation_urls {:value-type TYPE_MAYBE}}
                                                                                     {:admin_comment {:value-type TYPE_MAYBE}}]

                                                                             :bl [:id :vocabulary_id :meta_datum_object_type]}}]}

                              {:raw [{:meta_keys {}}
                                     {:_additional [{:column_name "scope", :data_type "any"}]}],
                               :raw-schema-name :meta_keys-scope-raw

                               :schemas [{:meta-keys.schema_query-meta-key {:alias "mar.meta-keys/schema_query-meta-key"
                                                                            :key-types "optional"
                                                                            :wl [:id :vocabulary_id :meta_datum_object_type :is_enabled_for_collections :is_enabled_for_media_entries :scope]}}]}

                              {:raw [{:meta_keys {}}
                                     {:vocabularies {:rename {"id" "id_2"
                                                              "enabled_for_public_use" "enabled_for_public_use_2"
                                                              "enabled_for_public_view" "enabled_for_public_view_2"
                                                              "position" "position_2"
                                                              "labels" "labels_2"
                                                              "descriptions" "descriptions_2"
                                                              "admin_comment" "admin_comment_2"}}}

                                     {:_additional [{:column_name "io_mappings", :data_type "any"}]}]

                               :raw-schema-name :meta_keys-vocabularies-raw

                               :schemas [{:meta-keys.schema_export-meta-key-usr {:alias "mar.meta-keys/schema_export-meta-key-usr"
                                                                                 :key-types "optional"
                                                                                 :types [{:id {:key-type TYPE_NOTHING}}
                                                                                         {:labels {:key-type TYPE_NOTHING}}
                                                                                         {:descriptions {:key-type TYPE_NOTHING}}
                                                                                         {:hints {:key-type TYPE_NOTHING}}
                                                                                         {:documentation_urls {:key-type TYPE_NOTHING}}
                                                                                         {:vocabulary_id {:key-type TYPE_NOTHING}}

                                                                                         {:allowed_rdf_class {:value-type TYPE_MAYBE}}
                                                                                         {:labels {:value-type TYPE_MAYBE}}
                                                                                         {:descriptions {:value-type TYPE_MAYBE}}
                                                                                         {:hints {:value-type TYPE_MAYBE}}
                                                                                         {:documentation_urls {:value-type TYPE_MAYBE}}]
                                                                                 :bl [:admin_comment]}}

                                         {:meta-keys.schema_export-meta-key-adm {:alias "mar.meta-keys/schema_export-meta-key-adm"
                                                                                 :key-types "optional"
                                                                                 :types [{:id {:key-type TYPE_NOTHING}}
                                                                                         {:labels {:key-type TYPE_NOTHING}}
                                                                                         {:descriptions {:key-type TYPE_NOTHING}}
                                                                                         {:hints {:key-type TYPE_NOTHING}}
                                                                                         {:documentation_urls {:key-type TYPE_NOTHING}}
                                                                                         {:vocabulary_id {:key-type TYPE_NOTHING}}

                                                                                         {:admin_comment {:key-type TYPE_NOTHING :value-type TYPE_MAYBE}}

                                                                                         {:allowed_rdf_class {:value-type TYPE_MAYBE}}
                                                                                         {:labels {:value-type TYPE_MAYBE}}
                                                                                         {:descriptions {:value-type TYPE_MAYBE}}
                                                                                         {:hints {:value-type TYPE_MAYBE}}
                                                                                         {:documentation_urls {:value-type TYPE_MAYBE}}
                                                                                         {:admin_comment_2 {:value-type TYPE_MAYBE}}]}}]}])

(def create-keywords-schema [{:raw [{:keywords {}}],
                              :raw-schema-name :keywords-raw
                              :schemas [{:keywords.schema_create_keyword {:alias "mar.keywords/schema_create_keyword"
                                                                          :key-types "optional"
                                                                          :types [{:meta_key_id {:key-type TYPE_NOTHING}}
                                                                                  {:term {:key-type TYPE_NOTHING}}

                                                                                  {:description {:value-type TYPE_MAYBE}}
                                                                                  {:position {:value-type TYPE_MAYBE}}]
                                                                          :wl [:meta_key_id :term :description :position :external_uris :rdf_class]}}
                                        {:keywords.schema_update_keyword {:alias "mar.keywords/schema_update_keyword"
                                                                          :key-types "optional"

                                                                          :types [{:description {:value-type TYPE_MAYBE}}]

                                                                          :wl [:term :description :position :external_uris :rdf_class]}}]}

                             {:raw [{:keywords {}}
                                    {:_additional [{:column_name "external_uri", :data_type "str"}]}],
                              :raw-schema-name :keywords-extended-raw
                              :schemas [{:keywords.schema_export_keyword_usr {:alias "mar.keywords/schema_export_keyword_usr"
                                                                              :types [{:description {:value-type TYPE_MAYBE}}
                                                                                      {:position {:value-type TYPE_MAYBE}}
                                                                                      {:external_uri {:value-type TYPE_MAYBE}}]

                                                                              :bl [:created_at :updated_at :creator_id]}}
                                        {:keywords.schema_export_keyword_adm {:alias "mar.keywords/schema_export_keyword_adm"
                                                                              :types [{:description {:value-type TYPE_MAYBE}}
                                                                                      {:position {:value-type TYPE_MAYBE}}
                                                                                      {:external_uri {:value-type TYPE_MAYBE}}
                                                                                      {:creator_id {:value-type TYPE_MAYBE}}]}}]}

                             {:raw [{:keywords {:wl [:id :meta_key_id :term :description :rdf_class]}}
                                    {:_additional schema_pagination_raw}],
                              :raw-schema-name :keywords-raw
                              :schemas [{:keywords.schema_query_keyword {:alias "mar.keywords/schema_query_keyword"
                                                                         :key-types "optional"}}]}])

(def create-permissions-schema [{:raw [{:collection_user_permissions {}}],
                                 :raw-schema-name :collection_user_permissions-raw
                                 :schemas [{:collection_user_permissions.schema_export-collection-user-permission {:alias "mar.permissions/schema_export-collection-user-permission"
                                                                                                                   :types [{:updator_id {:value-type TYPE_MAYBE}}
                                                                                                                           {:delegation_id {:value-type TYPE_MAYBE}}]}}

                                           {:collection_user_permissions.schema_create-collection-user-permission {:alias "mar.permissions/schema_create-collection-user-permission"
                                                                                                                   :wl [:get_metadata_and_previews :edit_metadata_and_relations :edit_permissions]}}]}

                                {:raw [{:collection_group_permissions {}}],
                                 :raw-schema-name :collection_group_permissions-raw
                                 :schemas [{:collection_group_permissions.schema_export-collection-group-permission {:alias "mar.permissions/schema_export-collection-group-permission"
                                                                                                                     :types [{:updator_id {:value-type TYPE_MAYBE}}]}}

                                           {:collection_group_permissions.schema_create-collection-group-permission {:alias "mar.permissions/schema_create-collection-group-permission"
                                                                                                                     :wl [:get_metadata_and_previews :edit_metadata_and_relations]}}]}

                                {:raw [{:media_entry_user_permissions {}}],
                                 :raw-schema-name :media_entry_user_permissions-raw

                                 :schemas [{:media_entry_user_permissions.schema_export-media-entry-user-permission {:alias "mar.permissions/schema_export-media-entry-user-permission"
                                                                                                                     :types [{:updator_id {:value-type TYPE_MAYBE}}
                                                                                                                             {:delegation_id {:value-type TYPE_MAYBE}}]}}

                                           {:media_entry_user_permissions.schema_create-media-entry-user-permission {:alias "mar.permissions/schema_create-media-entry-user-permission"
                                                                                                                     :wl [:get_metadata_and_previews :get_full_size :edit_metadata :edit_permissions]}}]}

                                {:raw [{:media_entries {}}],
                                 :raw-schema-name :media_entries-raw
                                 :schemas [{:media_entries.schema_export-media-entry-perms {:alias "mar.permissions/schema_export-media-entry-perms"
                                                                                            :types [{:id {:key-type TYPE_NOTHING}}
                                                                                                    {:creator_id {:key-type TYPE_NOTHING}}
                                                                                                    {:is_published {:key-type TYPE_NOTHING}}

                                                                                                    {:responsible_user_id {:value-type TYPE_MAYBE}}
                                                                                                    {:responsible_delegation_id {:value-type TYPE_MAYBE}}]
                                                                                            :key-types "optional"
                                                                                            :wl [:id :creator_id :is_published :get_metadata_and_previews :get_full_size :responsible_user_id :responsible_delegation_id]}}
                                           {:media_entries.schema_update-media-entry-perms {:alias "mar.permissions/schema_update-media-entry-perms"
                                                                                            :types [{:responsible_user_id {:value-type TYPE_MAYBE}}
                                                                                                    {:responsible_delegation_id {:value-type TYPE_MAYBE}}]
                                                                                            :key-types "optional"
                                                                                            :wl [:get_metadata_and_previews :get_full_size :responsible_user_id :responsible_delegation_id]}}]}

                                {:raw [{:collections {}}],
                                 :raw-schema-name :collections-perms-raw
                                 :schemas [{:collections-perms.schema_update-collection-perms {:alias "mar.permissions/schema_update-collection-perms"
                                                                                               :types [{:get_metadata_and_previews {:value-type TYPE_NOTHING}}]

                                                                                               :key-types "optional"
                                                                                               :value-types TYPE_MAYBE
                                                                                               :wl [:get_metadata_and_previews :responsible_user_id :clipboard_user_id :workflow_id :responsible_delegation_id]}}
                                           {:collections-perms.schema_export-collection-perms {:alias "mar.permissions/schema_export-collection-perms"
                                                                                               :types [{:id {:key-type TYPE_NOTHING :value-type TYPE_NOTHING}}
                                                                                                       {:creator_id {:key-type TYPE_NOTHING :value-type TYPE_NOTHING}}
                                                                                                       {:get_metadata_and_previews {:value-type TYPE_NOTHING}}]
                                                                                               :key-types "optional"
                                                                                               :value-types TYPE_MAYBE
                                                                                               :wl [:id :creator_id :get_metadata_and_previews :responsible_user_id :clipboard_user_id :workflow_id :responsible_delegation_id]}}]}

                                {:raw [{:media_entry_group_permissions {}}],
                                 :raw-schema-name :media_entry_group_permissions-raw
                                 :schemas [{:media_entry_group_permissions.schema_export-media-entry-group-permission {:alias "mar.permissions/schema_export-media-entry-group-permission"
                                                                                                                       :types [{:updator_id {:value-type TYPE_MAYBE}}]}}

                                           {:media_entry_group_permissions.schema_create-media-entry-group-permission {:alias "mar.permissions/schema_create-media-entry-group-permission"
                                                                                                                       :wl [:get_metadata_and_previews :get_full_size :edit_metadata]}}]}])

(defn top-level-permissions-schema [] {:media_entry_user_permissions.schema_export_media-entry-permissions-all {:media_entry (get-schema :media_entries.schema_export-media-entry-perms)
                                                                                                                :users [(get-schema :media_entry_user_permissions.schema_export-media-entry-user-permission)]
                                                                                                                :groups [(get-schema :media_entry_group_permissions.schema_export-media-entry-group-permission)]}

                                       :collection_permissions-all.schema_export-collection-permissions-all {:media-resource (get-schema :collections-perms.schema_export-collection-perms)
                                                                                                             :users [(get-schema :collection_user_permissions.schema_export-collection-user-permission)]
                                                                                                             :groups [(get-schema :collection_group_permissions.schema_export-collection-group-permission)]}})

(def create-vocabularies-schema [{:raw [{:vocabularies {}}],
                                  :raw-schema-name :vocabularies-raw

                                  :schemas [{:vocabularies.schema_export-vocabulary {:alias "mar.vocabularies/schema_export-vocabulary"
                                                                                     :types [{:labels {:value-type TYPE_MAYBE}}
                                                                                             {:descriptions {:value-type TYPE_MAYBE}}
                                                                                             {:admin_comment {:key-type TYPE_OPTIONAL :value-type TYPE_MAYBE}}]
                                                                                     :bl [:enabled_for_public_view :enabled_for_public_use]}}

                                            {:vocabularies.schema_export-vocabulary-admin {:alias "mar.vocabularies/schema_export-vocabulary-admin"
                                                                                           :types [{:labels {:value-type TYPE_MAYBE}}
                                                                                                   {:descriptions {:value-type TYPE_MAYBE}}
                                                                                                   {:admin_comment {:key-type TYPE_OPTIONAL :value-type TYPE_MAYBE}}]}}

                                            {:vocabularies.schema_import-vocabulary {:alias "mar.vocabularies/schema_import-vocabulary"
                                                                                     :types [{:labels {:key-type TYPE_OPTIONAL :value-type TYPE_MAYBE}}
                                                                                             {:descriptions {:key-type TYPE_OPTIONAL :value-type TYPE_MAYBE}}
                                                                                             {:admin_comment {:key-type TYPE_OPTIONAL :value-type TYPE_MAYBE}}]}}

                                            {:vocabularies.schema_update-vocabulary {:alias "mar.vocabularies/schema_update-vocabulary"
                                                                                     :key-types "optional"
                                                                                     :value-types TYPE_MAYBE
                                                                                     :types [{:position {:value-type TYPE_NOTHING}}]
                                                                                     :bl [:id :enabled_for_public_view :enabled_for_public_use]}}

                                            {:vocabularies.schema_perms-update {:alias "mar.vocabularies/schema_perms-update"
                                                                                :key-types "optional"
                                                                                :wl [:enabled_for_public_view :enabled_for_public_use]}}

                                            {:vocabularies.schema_export-perms_all_vocabulary {:alias "mar.vocabularies/schema_export-perms_all"
                                                                                               :wl [:id :enabled_for_public_view :enabled_for_public_use]}}]}

                                 {:raw [{:vocabulary_group_permissions {}}],
                                  :raw-schema-name :vocabulary_group_permissions-raw

                                  :schemas [{:vocabularies.schema_export-group-perms {:alias "mar.vocabularies/schema_export-group-perms"}}]}

                                 {:raw [{:vocabulary_user_permissions {}}],
                                  :raw-schema-name :vocabulary_user_permissions-raw

                                  :schemas [{:vocabularies.vocabulary_user_permissions {:alias "mar.vocabularies/vocabulary_user_permissions"}}

                                            {:vocabularies.schema_perms-update-user-or-group {:alias "mar.vocabularies/schema_perms-update-user-or-group"
                                                                                              :wl [:use :view]}}]}])

(defn top-level-vocabularies-schema [] {:vocabularies.schema_export-perms_all
                                        {:vocabulary (get-schema :vocabularies.schema_export-perms_all_vocabulary)
                                         :users [(get-schema :vocabularies.vocabulary_user_permissions)]
                                         :groups [(get-schema :vocabularies.schema_export-group-perms)]}})