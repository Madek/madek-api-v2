(ns madek.api.utils.coercion.spec-alpha-definition-str
  (:require
   [clojure.spec.alpha :as sa]
   [spec-tools.core :as st]))

;### required fields ####################################################################

(sa/def ::id (st/spec {:spec string?}))
(sa/def ::text_type (st/spec {:spec string?}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
