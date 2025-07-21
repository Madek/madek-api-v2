(ns madek.api.utils.coercion.spec-utils
  (:require
   [clojure.spec.alpha :as sa]
   [schema.core :as s])
  (:import [java.time OffsetDateTime]
           [java.time.format DateTimeParseException]))

(defn string->vec
  [x]
  (cond
    (empty? x) []
    (string? x) [x]
    (sequential? x) x
    :else ::sa/invalid))

(defn offset-date-time-string? [^String s]
  (try
    (OffsetDateTime/parse s)
    true
    (catch DateTimeParseException _ false)))

;; IsoOffsetDateTimeString for schema.spec, see: 'Timestamp-formats'
(def IsoOffsetDateTimeString
  (s/constrained
   s/Str
   offset-date-time-string?
   'IsoOffsetDateTimeString))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
