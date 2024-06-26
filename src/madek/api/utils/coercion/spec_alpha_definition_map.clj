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

;### Debug ####################################################################
;(debug/debug-ns *ns*)
