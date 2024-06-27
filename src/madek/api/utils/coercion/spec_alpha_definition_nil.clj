(ns madek.api.utils.coercion.spec-alpha-definition-nil
  (:require
   [clojure.spec.alpha :as sa]
   ;[reitit.coercion.schema]
   [spec-tools.core :as st]))

;(def schema_export_keyword_usr
;  {:id s/Uuid
;   :meta_key_id s/Str
;   :term s/Str
;   :description (s/maybe s/Str)
;   :position (s/maybe s/Int)
;   :external_uris [s/Any]
;   :external_uri (s/maybe s/Str)
;   :rdf_class s/Str})

(sa/def ::description
  (sa/or :nil nil? :string string?))

(sa/def ::position
  (sa/or :nil nil? :int int?))

(sa/def ::external_uri
  (sa/or :nil nil? :string string?))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
