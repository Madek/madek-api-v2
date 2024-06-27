(ns madek.api.utils.coercion.spec-alpha-definition
  (:require
   [clojure.spec.alpha :as sa]
   ;[reitit.coercion.schema]
   [spec-tools.core :as st]))

(sa/def ::page (st/spec {:spec pos-int?
                         :description "Page number"
                         :json-schema/default 1}))

(sa/def ::size (st/spec {:spec pos-int?
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
(sa/def ::meta_key_id (st/spec {:spec string?}))
(sa/def ::term (st/spec {:spec string?}))

(sa/def ::creator_id (st/spec {:spec uuid?}))
(sa/def ::created_at (st/spec {:spec any?}))
(sa/def ::updated_at (st/spec {:spec any?}))

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
