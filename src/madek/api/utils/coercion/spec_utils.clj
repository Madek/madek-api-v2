(ns madek.api.utils.coercion.spec-utils
  (:require
   [clojure.spec.alpha :as s]))

(defn string->vec
  [x]
  (cond
    (empty? x) []
    (string? x) [x]
    (sequential? x) x
    :else ::s/invalid))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
