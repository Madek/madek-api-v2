(ns madek.api.utils.coercion.spec-alpha-definition
  (:require
   [clojure.spec.alpha :as sa]
   ;[reitit.coercion.schema]
   [spec-tools.core :as st]))


(sa/def ::page (st/spec {:spec int?
                         :description "Page number"
                         :json-schema/default 0}))

;; TODO: pos-int? breaks tests
;(defn pos-int? [x]
;  (and (integer? x) (pos? x)))
;
;(sa/def ::page (sa/spec
;                 {:spec (sa/or :pos-int pos-int? :zero? #(= 0 %))
;                  :description "Page number"
;                  :json-schema/default 0}))

(sa/def ::size (st/spec {:spec int?
                         :description "Number of items per page"
                         :json-schema/default 10}))

(def schema_pagination_opt
  (sa/keys
    :opt-un [::page ::size]))

(def schema_pagination_req
  (sa/keys
    :opt-un [::page ::size]))

;### required fields ####################################################################

(sa/def ::id (st/spec {:spec uuid?}))
(sa/def ::group-id (st/spec {:spec uuid?}))
(sa/def ::person_id (st/spec {:spec uuid?}))
(sa/def ::created_by_user_id (st/spec {:spec uuid?}))

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

(sa/def ::creator_id (st/spec {:spec uuid?}))
(sa/def ::created_at (st/spec {:spec any?}))
(sa/def ::updated_at (st/spec {:spec any?}))

;(sa/def ::changed_after (st/spec {:spec instance?}))
;(sa/def ::created_after (st/spec {:spec instance?}))
;(sa/def ::updated_after (st/spec {:spec instance?}))

(sa/def ::changed_after (st/spec {:spec any?}))
(sa/def ::created_after (st/spec {:spec any?}))
(sa/def ::updated_after (st/spec {:spec any?}))

(sa/def ::context_id (st/spec {:spec string?}))
(sa/def ::is_required (st/spec {:spec boolean?}))
(sa/def ::full_data (st/spec {:spec boolean?}))

(sa/def ::position (st/spec {:spec int?}))

(sa/def ::external_uris (st/spec {:spec (sa/coll-of any?)
                                  :description "An array of any types"}))

(sa/def ::rdf_class (st/spec {:spec string?}))

;
;;(sa/def ::person (s/keys :opt-un [::id ::meta_key_id ::term ::description ::rdf_class]))
;(sa/def ::person (sa/keys :req-un [::id ::meta_key_id ::term ::description ::position ::external_uris ::external_uri ::rdf_class]))
;
;
;(def schema_query_pagination2
;  (sa/keys :req-un [::id ::meta_key_id ::term ::description ::position ::external_uris ::external_uri ::rdf_class]))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
