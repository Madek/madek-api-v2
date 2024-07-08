(ns madek.api.utils.coercion.spec-alpha-definition-nil
  (:require
   [clojure.spec.alpha :as sa]))

(defn nil-or [pred]
  (sa/or :nil nil? :value pred))

(sa/def ::descriptions (nil-or string?))
(sa/def ::description (nil-or string?))
(sa/def ::first_name (nil-or string?))
(sa/def ::institutional_id (nil-or string?))
(sa/def ::institutional_name (nil-or string?))
(sa/def ::accepted_usage_terms_id (nil-or uuid?))
(sa/def ::institution (nil-or string?))
(sa/def ::created_by_user_id (nil-or string?))
(sa/def ::position (nil-or int?))
(sa/def ::external_uri (nil-or string?))
(sa/def ::length_max (nil-or int?))
(sa/def ::labels (nil-or string?))
(sa/def ::length_min (nil-or int?))
(sa/def ::documentation_urls (nil-or any?))
(sa/def ::last_signed_in_at (nil-or any?))
(sa/def ::responsible_user_id (nil-or uuid?))
(sa/def ::responsible_delegation_id (nil-or uuid?))
(sa/def ::hints (nil-or map?))
(sa/def ::last_name (nil-or string?))
(sa/def ::login (nil-or string?))
(sa/def ::admin_comment (nil-or string?))
(sa/def ::notes (nil-or string?))
(sa/def ::pseudonym (nil-or string?))
(sa/def ::default_context_id (nil-or string?))

(sa/def ::order (nil-or string?))
(sa/def ::position (nil-or any?))
(sa/def ::created_at (nil-or any?))
(sa/def ::updated_at (nil-or any?))

;### Debug ####################################################################
;(debug/debug-ns *ns*)