(ns madek.api.db.dynamic_schema.schemas
  (:require [madek.api.db.dynamic_schema.common :refer [get-schema has-schema set-schema]]
            [madek.api.db.dynamic_schema.core :refer [create-dynamic-schema init-enums-by-db]]))

(defn get-var-value [namespace var-name]
  (let [ns-symbol (symbol namespace)
        var-symbol (symbol var-name)]
    (require ns-symbol)
    (let [resolved-var (ns-resolve ns-symbol var-symbol)]
      (when resolved-var
        (deref resolved-var)))))

(defn get-fn-value [namespace fn-name]
  (let [ns-symbol (symbol namespace)
        fn-symbol (symbol fn-name)]
    (require ns-symbol)
    (let [resolved-fn (ns-resolve ns-symbol fn-symbol)]
      (when (and resolved-fn (fn? @resolved-fn))
        @resolved-fn))))


(defonce init-enums (do
                      (init-enums-by-db)
                      true))  ;; The `true` value is just a placeholder to indicate initialization
(defn query-schema
  "key               .. schemas-key OR raw-schema-name
   schema-def-prefix .. to fetch def/defn by prefix (<schema-def-prefix>-cfg OR <schema-def-prefix>-fnc)"
  [key schema-def-prefix]
  (let [

        ;_ (init-enums-by-db)                                ;; init enums only once

        namespace "madek.api.db.dynamic_schema.schema_definitions"
        schema-def (get-var-value namespace (str schema-def-prefix "-cfg"))
        schema-fnc (get-fn-value namespace (str schema-def-prefix "-fnc"))]
    (if (has-schema key)
      (get-schema key)
      (do
        (create-dynamic-schema schema-def)
        (when schema-fnc
          (doseq [[k v] (seq (schema-fnc))]
            (set-schema k v)))
        (get-schema key)))))