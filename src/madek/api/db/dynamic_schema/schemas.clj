(ns madek.api.db.dynamic_schema.schemas
  (:require [clojure.walk :refer [postwalk]]
   ;[madek.api.db :refer [get-schema-from-db]]

            [madek.api.db.dynamic_schema.common :refer [get-schema has-schema]]
            [madek.api.db.dynamic_schema.core :refer [create-dynamic-schema]]

            )
  )



(def TYPE_MAYBE "maybe")

(def keywords-schema [{:raw [{:keywords {}}
                             {:_additional [{:column_name "external_uri", :data_type "str"}]}],
                       :raw-schema-name :keywords-extended-raw
                       :schemas [{:keywords.schema_export_keyword_usr {:alias "mar.keywords/schema_export_keyword_usr"
                                                                       :types [{:description {:value-type TYPE_MAYBE}}
                                                                               {:position {:value-type TYPE_MAYBE}}
                                                                               {:external_uri {:value-type TYPE_MAYBE}}]

                                                                       :bl [:created_at :updated_at :creator_id]}}
                                                                       ;:bl [:created_at :updated_at :creator_id :not-existing-field-test]}} ;; TODO: test validation

                                 {:keywords.schema_export_keyword_adm {:alias "mar.keywords/schema_export_keyword_adm"
                                                                       :types [{:description {:value-type TYPE_MAYBE}}
                                                                               {:position {:value-type TYPE_MAYBE}}
                                                                               {:external_uri {:value-type TYPE_MAYBE}}
                                                                               {:creator_id {:value-type TYPE_MAYBE}}]}}]}])

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




(defn keyword-query-schema
  ;([]
  ; (keyword-query-schema [nil])
  ; )


  ([key]
   ;(s/defschema KeywordQuery
   ;(get-schema-from-db "keywords")

   (let [

         ;p (println ">o> ... process [keyword-query-schema]")
         ;meta ((@fetch-table-metadata) "keywords")

         ;;; if schema is not found in cache, create it
         ;res (get-schema key)
         ;res (if (nil? res)
         ;      (do
         ;        p (println ">o> >>> schema_cache.get-schema -> not found!! .. continue with create schema")
         ;        (create-dynamic-schema keywords-schema)
         ;        (get-schema key))
         ;      res)


         res (has-schema key)
         p (println ">o> has-schema?" res)

         ;res (if (nil? res)
         res (if res
               (do
                 p (println ">o> >>> LOADING-STATUS: schema_cache.get-schema -> not found!! .. continue with create schema")
                 (create-dynamic-schema keywords-schema))
               (do
                 p (println ">o> >>> LOADING-STATUS: schema_cache.get-schema -> found")
                 (get-schema key)))

         p (println "\n>o> final.schema\nkey=" key "\n" res "\n")


         ;meta (metadata-fetcher)
         ;
         ;p (println "\n>o> meta.from.db=" meta "\n")
         ;
         ;
         ;res {:id s/Uuid
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
         ;p (println "\n>o> returned.res=" res "\n")

         ] res))

  ;)
  )
