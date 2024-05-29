(ns madek.api.db.dyn_schema.schemas
  (:require [madek.api.db.dyn_schema.db :refer [metadata-fetcher]]
             ;[madek.api.db :refer [get-schema-from-db]]
             [schema.core :as s]
            )
  )

(defn keyword-query-schema []
  ;(s/defschema KeywordQuery
    ;(get-schema-from-db "keywords")

    (let [
          ;meta ((@fetch-table-metadata) "keywords")

          meta (metadata-fetcher)

          p (println "\n>o> meta.from.db=" meta "\n")


          res {:id s/Uuid
               :meta_key_id s/Str
               :term s/Str
               :description (s/maybe s/Str)
               :position (s/maybe s/Int)
               :external_uris [s/Any]
               :external_uri (s/maybe s/Str)
               :rdf_class s/Str}



          p (println "\n>o> returned.res=" res "\n")

          ]res )

    ;)
)
