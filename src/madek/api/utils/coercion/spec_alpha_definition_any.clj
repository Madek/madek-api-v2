(ns madek.api.utils.coercion.spec-alpha-definition-any
  (:require
   [clojure.spec.alpha :as sa]
   [spec-tools.core :as st]))

;### required fields ####################################################################

(sa/def ::labels (st/spec {:spec any?}))
(sa/def ::descriptions (st/spec {:spec any?}))
(sa/def ::hints (st/spec {:spec any?}))
(sa/def ::documentation_urls (st/spec {:spec any?}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
