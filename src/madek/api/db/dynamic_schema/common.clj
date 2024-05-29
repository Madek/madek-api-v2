(ns madek.api.db.dynamic_schema.common
  (:require
   [madek.api.db.dynamic_schema.schema_logger :refer [slog]]
   [schema.core :as s]

   [taoensso.timbre :refer [error]]))

(def schema-cache (atom {}))
(def enum-cache (atom {}))
(def validation-cache (atom []))

(defn get-schema [key & [default]]
  (let [val (or (get @schema-cache key default) s/Any)
        _ (if (= val s/Any)
            (swap! validation-cache conj "ERROR: no schema for key=" key))]
    (slog (str "[get-schema] " key "=" val))
    val))

(defn set-schema [key value]
  (slog (str "[set-schema] (" key ") ->" value))
  (swap! schema-cache assoc key (into {} value)))

(defn get-enum [key & [default]]
  (let [val (get @enum-cache key default)] val))

(defn set-enum [key value]
  (swap! enum-cache assoc key value))

(defn get-validation-cache []
  @validation-cache)

(defn add-to-validation-cache [new-element]
  (error "[add-to-validation-cache]" new-element)
  (swap! validation-cache conj new-element))