(ns madek.api.utils.coercion.spec-alpha-definition-map
  (:require
   [clojure.spec.alpha :as sa]
   [spec-tools.core :as st]))

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

(sa/def ::iso8601-date-time
  (st/spec
   {:spec (sa/and string? #(re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z" %))
    :description "An ISO 8601 formatted date-time string"}))

(sa/def ::deleted_at
  (st/spec
   {:spec ::iso8601-date-time
    :description "Timestamp when the resource was deleted, in ISO 8601 format"}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
