(ns madek.api.utils.coercion.spec-alpha-definition
  (:require
   [clojure.spec.alpha :as sa]
   [madek.api.pagination :refer [ZERO_BASED_PAGINATION DEFAULT_COUNT_SWAGGER DEFAULT_PAGE_SWAGGER]]
   [spec-tools.core :as st]))

(sa/def ::page (st/spec {:spec int?
                         :description (str "Page number " (when ZERO_BASED_PAGINATION "(zero-based pagination)"))
                         :json-schema/default DEFAULT_PAGE_SWAGGER}))

(sa/def ::size (st/spec {:spec int?
                         :description "Number of items per page"
                         :json-schema/default DEFAULT_COUNT_SWAGGER}))

(def schema_pagination_opt
  (sa/keys
   :opt-un [::page ::size]))

(def schema_pagination_req
  (sa/keys
   :req-un [::page ::size]))

;### required fields ####################################################################

(sa/def ::id (st/spec {:spec uuid?}))
(sa/def ::group-id (st/spec {:spec uuid?}))
(sa/def ::person_id (st/spec {:spec uuid?}))
(sa/def ::media_entry_id (st/spec {:spec uuid?}))
(sa/def ::user_id (st/spec {:spec uuid?}))
(sa/def ::created_by_user_id (st/spec {:spec uuid?}))
(sa/def ::collection_id (st/spec {:spec uuid?}))
(sa/def ::responsible_user_id (st/spec {:spec uuid?}))
(sa/def ::clipboard_user_id (st/spec {:spec uuid?}))
(sa/def ::workflow_id (st/spec {:spec uuid?}))
(sa/def ::responsible_delegation_id (st/spec {:spec uuid?}))

(sa/def ::child_id (st/spec {:spec uuid?}))
(sa/def ::parent_id (st/spec {:spec uuid?}))
(sa/def ::media_resource_id (st/spec {:spec uuid?}))

(sa/def ::email (st/spec {:spec string?}))
(sa/def ::name (st/spec {:spec string?}))
(sa/def ::type (st/spec {:spec string?}))
(sa/def ::institutional_id (st/spec {:spec string?}))
(sa/def ::institutional_name (st/spec {:spec string?}))
(sa/def ::institution (st/spec {:spec string?}))
(sa/def ::meta_key_id (st/spec {:spec string?}))
(sa/def ::term (st/spec {:spec string?}))
(sa/def ::description (st/spec {:spec string?}))
(sa/def ::searchable (st/spec {:spec string?}))
(sa/def ::order (st/spec {:spec string?}))
(sa/def ::filter_by (st/spec {:spec string?}))
(sa/def ::subtype (st/spec {:spec string?}))
(sa/def ::text (st/spec {:spec string?}))

(sa/def ::creator_id (st/spec {:spec uuid?}))
(sa/def ::created_at (st/spec {:spec any?}))
(sa/def ::updated_at (st/spec {:spec any?}))
(sa/def ::deleted_at (st/spec {:spec any?}))

(sa/def ::meta_data_updated_at (st/spec {:spec any?}))
(sa/def ::edit_session_updated_at (st/spec {:spec any?}))

(sa/def ::changed_after (st/spec {:spec instance?}))
(sa/def ::created_after (st/spec {:spec instance?}))
(sa/def ::updated_after (st/spec {:spec instance?}))

(sa/def ::context_id (st/spec {:spec string?}))
(sa/def ::is_required (st/spec {:spec boolean?}))
(sa/def ::full_data (st/spec {:spec boolean?}))
(sa/def ::highlight (st/spec {:spec boolean?}))
(sa/def ::is_master (st/spec {:spec boolean?}))
(sa/def ::get_full_size (st/spec {:spec boolean?}))
(sa/def ::is_admin (st/spec {:spec boolean?}))
(sa/def ::public_get_metadata_and_previews (st/spec {:spec boolean?}))
(sa/def ::me_get_metadata_and_previews (st/spec {:spec boolean?}))
(sa/def ::me_get_full_size (st/spec {:spec boolean?}))
(sa/def ::me_edit_permissions (st/spec {:spec boolean?}))
(sa/def ::public_get_full_size (st/spec {:spec boolean?}))
(sa/def ::me_edit_metadata (st/spec {:spec boolean?}))
(sa/def ::me_edit_permission (st/spec {:spec boolean?}))
(sa/def ::me_edit_metadata_and_relations (st/spec {:spec boolean?}))
(sa/def ::get_metadata_and_previews (st/spec {:spec boolean?}))
(sa/def ::is_published (st/spec {:spec boolean?}))

(sa/def ::position (st/spec {:spec int?}))

(sa/def ::external_uris (st/spec {:spec (sa/coll-of any?)
                                  :description "An array of any types"}))

(sa/def ::rdf_class (st/spec {:spec string?}))

(sa/def ::layout (st/spec {:spec any?}))
(sa/def ::sorting (st/spec {:spec any?}))
(sa/def ::default_resource_type (st/spec {:spec any?}))
(sa/def ::settings (st/spec {:spec any?}))

(sa/def ::media_entries (st/spec {:spec any?}))
(sa/def ::meta_data (st/spec {:spec any?}))
(sa/def ::media_files (st/spec {:spec any?}))
(sa/def ::previews (st/spec {:spec any?}))
(sa/def ::col_arcs (st/spec {:spec any?}))
(sa/def ::col_meta_data (st/spec {:spec any?}))
(sa/def ::labels (st/spec {:spec map?}))
(sa/def ::documentation_urls (st/spec {:spec list?}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
