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

(sa/def ::descriptions
  (sa/or :nil nil? :string string?))

(sa/def ::description
  (sa/or :nil nil? :string string?))

(sa/def ::position
  (sa/or :nil nil? :int int?))

(sa/def ::external_uri
  (sa/or :nil nil? :string string?))

(sa/def ::length_max
  (sa/or :nil nil? :int int?))

(sa/def ::labels
  (sa/or :nil nil? :string string?))
(sa/def ::length_min
  (sa/or :nil nil? :int int?))

(sa/def ::documentation_urls
  (sa/or :nil nil? :any any?))

(sa/def ::hints
  (sa/or :nil nil? :any any?))                              ;; TODO: MAP OF EN/DE

(sa/def ::admin_comment
  (sa/or :nil nil? :string string?))                           ;;TODO

;(def schema_ml_list
;  {(s/optional-key :de) (s/maybe s/Str)
;   (s/optional-key :en) (s/maybe s/Str)})

;(sa/def ::documentation_urls
;  (sa/or :nil nil? (sa/coll-of any?)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
