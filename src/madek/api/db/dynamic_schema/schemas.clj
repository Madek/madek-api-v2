(ns madek.api.db.dynamic_schema.schemas
  (:require [clojure.walk :refer [postwalk]]
   ;[madek.api.db :refer [get-schema-from-db]]

            [madek.api.db.dynamic_schema.common :refer [get-schema has-schema]]
            [madek.api.db.dynamic_schema.core :refer [create-dynamic-schema]]
   ;[madek.api.db.dynamic_schema.schema_definitions :as defs]
            )
  )



(def TYPE_MAYBE "maybe")

;(def keywords-schema [{:raw [{:keywords {}}
;                             {:_additional [{:column_name "external_uri", :data_type "str"}]}],
;                       :raw-schema-name :keywords-extended-raw
;                       :schemas [{:keywords.schema_export_keyword_usr {:alias "mar.keywords/schema_export_keyword_usr"
;                                                                       :types [{:description {:value-type TYPE_MAYBE}}
;                                                                               {:position {:value-type TYPE_MAYBE}}
;                                                                               {:external_uri {:value-type TYPE_MAYBE}}]
;
;                                                                       :bl [:created_at :updated_at :creator_id]}}
;                                 ;:bl [:created_at :updated_at :creator_id :not-existing-field-test]}} ;; TODO: test validation
;
;                                 {:keywords.schema_export_keyword_adm {:alias "mar.keywords/schema_export_keyword_adm"
;                                                                       :types [{:description {:value-type TYPE_MAYBE}}
;                                                                               {:position {:value-type TYPE_MAYBE}}
;                                                                               {:external_uri {:value-type TYPE_MAYBE}}
;                                                                               {:creator_id {:value-type TYPE_MAYBE}}]}}]}])

(defn find-schema-element [key data]
  (let [result (atom nil)]
    (postwalk
      (fn [x]
        (when (and (map? x) (contains? x key))
          (reset! result {key (get x key)}))
        x)
      data)
    @result))

(comment

  (let [

        res nil




        key :keywords.schema_export_keyword_usr

        ;res (get-schema key)
        res (has-schema key)
        p (println ">o> has-schema?" res)

        ;res (if (nil? res)
        res (if res
              (do
                p (println ">o> >>> schema_cache.get-schema -> not found!! .. continue with create schema")
                (create-dynamic-schema keywords-schema))
              (get-schema key))

        ;key :nix
        ;res (find-schema-element key keywords-schema)
        ;res (key res)

        ]
    res)

  )





(defn fetch-definition [definition-name]
  (let [definition (ns-resolve 'madek.api.db.dynamic_schema.schema_definitions (symbol definition-name))]
    (when (fn? @definition)
      @definition)))

(defn get-var-value [namespace var-name]
  (let [ns-symbol (symbol namespace)
        var-symbol (symbol var-name)]
    (require ns-symbol)
    (let [resolved-var (ns-resolve ns-symbol var-symbol)]
      (when resolved-var
        (deref resolved-var)))))

(defn get-fn-value [namespace fn-name]
  (let [ns-symbol (symbol namespace)
        fn-symbol (symbol fn-name)

        _ (println ">o> get-fn-value.ns-symbol=" ns-symbol)
        _ (println ">o> get-fn-value.fn-symbol=" fn-symbol)
        ]
    (require ns-symbol)
    (let [resolved-fn (ns-resolve ns-symbol fn-symbol)
          p (println ">o> resolved-fn?=" resolved-fn)
          ]
      (when (fn? resolved-fn)
        resolved-fn))))

(defn get-fn-value [namespace fn-name]
  (let [ns-symbol (symbol namespace)
        fn-symbol (symbol fn-name)
        _ (println ">o> get-fn-value.ns-symbol=" ns-symbol)
        _ (println ">o> get-fn-value.fn-symbol=" fn-symbol)]
    (require ns-symbol)
    (let [resolved-fn (ns-resolve ns-symbol fn-symbol)
          p (println ">o> resolved-fn?=" resolved-fn)]
      (when (and resolved-fn (fn? @resolved-fn))
        @resolved-fn))))


(defn query-schema
  "key               .. schemas-key OR raw-schema-name
   schema-def-prefix .. to fetch def/defn by prefix (<schema-def-prefix>-cfg OR <schema-def-prefix>-fnc)"
  [key schema-def-prefix]
  (let [

        p (println "\n\n>o> query-schema >>>>!!!!!!!>>>>>" key)

        namespace "madek.api.db.dynamic_schema.schema_definitions"

        ;; fetch schema-def
        schema-def (let [
                  namespace (symbol namespace)
                  var-name (symbol (str schema-def-prefix "-cfg"))

                  res (get-var-value namespace var-name)
                  ]
              res)
        p (println ">o> has-schema-def?" schema-def)

        ;; fetch schema-fnc
        schema-fnc (let [
                  namespace (symbol namespace)
                  ;var-name (symbol (str schema-def-prefix "-fnc"))
                  var-name (str schema-def-prefix "-fnc")
                  res (get-fn-value namespace var-name)
                  ]
              res)

        _ (if (nil? schema-fnc)
            (do
              p (println ">o> >>> no schema-fnc available")
              ) (do
                  p (println ">o> >>> schema-fnc available")
                  p (println ">o> >>> schema-fnc=" (schema-fnc))
                  ))
        ;p (println ">o> has-schema-fnc?" schema-fnc)

        ;_ (System/exit 0)


        res (has-schema key)
        p (println ">o> has-schema?" res)

        ;qualified-symbol (symbol (str "madek.api.db.dynamic_schema.schema_definitions" schema-def-prefix))
        ;
        ;
        ;
        ;p (println ">o> qualified-symbol=" qualified-symbol)
        ;schema-def (resolve qualified-symbol)
        ;
        ;p (println ">o> schema-def=" schema-def)


        ;schema-fnc (if (nil? schema-fnc)
        res (if res
              (do
                (println ">o> >>> LOADING-STATUS: schema_cache.get-schema -> found -> " key)
                (get-schema key)

                )
              (do
                (println ">o> >>> LOADING-STATUS: schema_cache.get-schema -> not found!! .. continue with create schema\n key ->" key)
                ;(create-dynamic-schema keywords-schema))
                (create-dynamic-schema schema-def)

                (when-not (nil? schema-fnc)
                    (do
                      (println ">o> >>> EXECUTE TOP-LEVEL-FNC, key=" key)
                      (schema-fnc)
                      ))

                (get-schema key)

                )
           )

        p (println "\n>o> final.schema\nkey=" key "\n" res "\n")


        ;meta (metadata-fetcher)
        ;
        ;p (println "\n>o> meta.from.db=" meta "\n")
        ;
        ;
        ;schema-fnc {:id s/Uuid
        ;     :meta_key_id s/Str
        ;     :term s/Str
        ;     :description (s/maybe s/Str)
        ;     :position (s/maybe s/Int)
        ;     :external_uris [s/Any]
        ;     :external_uri (s/maybe s/Str)
        ;     :rdf_class s/Str}
        ;
        ;
        ;
        ;p (println "\n>o> returned.schema-fnc=" schema-fnc "\n")

        p (println ">o> >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n\n" )
        ] res)
  )

;)
;)
