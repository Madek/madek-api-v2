(ns madek.api.resources.coercion-spec2
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

;### required fields ####################################################################

(sa/def ::id (st/spec {:spec uuid?}))
(sa/def ::meta_key_id (st/spec {:spec string?}))
(sa/def ::term (st/spec {:spec string?}))

(sa/def ::description
  (sa/or :nil nil? :string string?))

;(sa/def ::description (st/spec {:spec string?}))

(sa/def ::creator_id (st/spec {:spec uuid?}))
(sa/def ::created_at (st/spec {:spec any?}))
(sa/def ::updated_at (st/spec {:spec any?}))

(sa/def ::position-nil
  (sa/or :nil nil? :int int?))

(sa/def ::position (st/spec {:spec int?}))

(sa/def ::external_uris (st/spec {:spec (sa/coll-of any?)
                                  :description "An array of any types"}))

(sa/def ::external_uri
  (sa/or :nil nil? :string string?))

(sa/def ::rdf_class (st/spec {:spec string?}))

;(sa/def ::basic (st/spec {:spec (sa/coll-of any?)
;                                  :description "An array of any types"}))

;(def schema_export_keyword_usr
;  {:id s/Uuid
;   :meta_key_id s/Str
;   :term s/Str
;   :description (s/maybe s/Str)
;   :position (s/maybe s/Int)
;   :external_uris [s/Any]
;   :external_uri (s/maybe s/Str)
;   :rdf_class s/Str})

;(sa/def ::person (s/keys :opt-un [::id ::meta_key_id ::term ::description ::rdf_class]))
(sa/def ::person (sa/keys :req-un [::id ::meta_key_id ::term ::description ::position ::external_uris ::external_uri ::rdf_class]))

(def schema_query_pagination2
  (sa/keys :req-un [::id ::meta_key_id ::term ::description ::position ::external_uris ::external_uri ::rdf_class]))

(def schema_pagination
  (sa/keys
   :opt-un [::page ::size]))

;(def schema_query_pagination
;  (sa/keys
;    :opt-un [::id ::meta_key_id ::term ::description ::rdf_class ::page ::size]))

;(def schema_query_keyword
;  {(s/optional-key :id) s/Uuid
;   (s/optional-key :meta_key_id) s/Str
;   (s/optional-key :term) s/Str
;   (s/optional-key :description) s/Str
;   (s/optional-key :rdf_class) s/Str})
;

;(sa/def ::response-body (sa/keys :req-un [::keywords]))
;
;(sa/def ::keywords (st/spec {:spec (sa/coll-of ::person)
;                            :description "A list of persons"}))
;
;
;

;
;(sa/def ::person (sa/keys :req-un [::id ::meta_key_id ::term ::description ::position ::external_uris ::external_uri ::rdf_class]))
;
;(sa/def ::response-body (sa/keys :req-un [::keywords]))
;
;(sa/def ::keywords (st/spec {:spec (sa/coll-of ::person)
;                             :description "A list of persons"}))
;

;### Debug ####################################################################
;(debug/debug-ns *ns*)
