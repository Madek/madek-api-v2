(ns madek.api.utils.coercion.spec-alpha-definition-nil
  (:require
   [clojure.spec.alpha :as sa])
  (:import [java.time OffsetDateTime]
           [java.time.format DateTimeParseException]))

(defn nil-or [pred]
  (sa/or :nil nil? :value pred))

(sa/def ::accepted_usage_terms_id (nil-or uuid?))
(sa/def :nil-str/active_until (nil-or any?))
(sa/def ::admin_comment (nil-or string?))
(sa/def ::allowed_rdf_class (nil-or string?))
(sa/def ::clipboard_user_id (nil-or uuid?))
(sa/def ::collection_id (nil-or uuid?))
(sa/def ::created_at (nil-or any?))
(sa/def ::media_entry_id (nil-or uuid?))
(sa/def ::created_by_user_id (nil-or string?))
(sa/def ::creator_id (nil-or uuid?))
(sa/def ::default_context_id (nil-or string?))
(sa/def ::description (nil-or string?))
(sa/def ::descriptions (nil-or string?))
(sa/def ::descriptions_2 (nil-or string?))
(sa/def ::documentation_urls (nil-or any?))
(sa/def ::external_uri (nil-or string?))
(sa/def ::first_name (nil-or string?))
(sa/def ::email (nil-or string?))
(sa/def ::highlight (nil-or boolean?))
(sa/def ::hints (nil-or map?))
(sa/def ::identification_info (nil-or string?))
(sa/def ::institution (nil-or string?))
(sa/def ::institutional_id (nil-or string?))
(sa/def ::institutional_name (nil-or string?))
(sa/def ::institutional_directory_inactive_since (nil-or any?))
(sa/def ::labels (nil-or string?))
(sa/def ::labels_2 (nil-or string?))
(sa/def ::last_name (nil-or string?))
(sa/def ::last_signed_in_at (nil-or any?))
(sa/def ::length_max (nil-or int?))
(sa/def ::length_min (nil-or int?))
(sa/def ::login (nil-or string?))
(sa/def ::notes (nil-or string?))
(sa/def ::order (nil-or string?))
(sa/def ::position (nil-or any?))
(sa/def ::position (nil-or int?))
(sa/def ::pseudonym (nil-or string?))
(sa/def ::responsible_delegation_id (nil-or uuid?))
(sa/def ::responsible_user_id (nil-or uuid?))
(sa/def ::updated_at (nil-or any?))
(sa/def ::updator_id (nil-or uuid?))

;; IsoOffsetDateTimeString for schema.spec, see: 'Timestamp-formats'
(defn offset-date-time-string? [^String s]
  (try
    (OffsetDateTime/parse s)
    true
    (catch DateTimeParseException _ false)))

(sa/def ::deleted_at
  (sa/nilable
   (sa/and string? offset-date-time-string?)))

(sa/def ::active_until
  (sa/nilable
   (sa/and string? offset-date-time-string?)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
