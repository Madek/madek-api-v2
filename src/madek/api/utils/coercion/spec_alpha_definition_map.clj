(ns madek.api.utils.coercion.spec-alpha-definition-map
  (:require
   [clojure.spec.alpha :as sa]
   [schema.core :as s]
   [spec-tools.core :as st])
  (:import [java.time OffsetDateTime]
           [java.time.format DateTimeParseException]))

(defn nil-or [pred]
  (sa/or :nil nil? :value pred))

(sa/def ::de (nil-or string?))
(sa/def ::en (nil-or string?))

(sa/def ::labels
  (st/spec
   {:spec (sa/keys :opt-un [::de ::en])
    :description "A map of en/de-entries"}))

(sa/def ::descriptions
  (st/spec
   {:spec (sa/keys :opt-un [::de ::en])
    :description "A map of en/de-entries"}))

(sa/def ::hints
  (st/spec
   {:spec (sa/keys :opt-un [::de ::en])
    :description "A map of en/de-entries"}))

(sa/def ::documentation_urls
  (st/spec
   {:spec (sa/keys :opt-un [::de ::en])
    :description "A map of en/de-entries"}))

(def valid-enum-values #{:deleted :not-deleted :all})

(sa/def ::enum-spec (sa/and keyword? valid-enum-values))

(sa/def ::filter_softdelete
  (st/spec
   {:spec ::enum-spec
    :description "An enum of :option1, :option2, or :option3"
    :json-schema {:enum ["not-deleted" "deleted" "all"]}}))

(defn offset-date-time-string? [^String s]
  (try
    (OffsetDateTime/parse s)
    true
    (catch DateTimeParseException _ false)))

;; IsoOffsetDateTimeString for schema.spec, see: 'Timestamp-formats'
(def iso-offset-date-time-string
  (s/constrained string? offset-date-time-string? 'iso-offset-date-time-string))

(sa/def ::deleted_at
  (st/spec
   {:spec iso-offset-date-time-string
    :description "Timestamp when the resource was deleted, e.g:
    ISO_OFFSET_DATE_TIME: \"2025-06-26T16:30:46.926173+02:00"}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
