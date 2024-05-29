(ns madek.api.db.dynamic_schema.schema_main
  (:require
   [madek.api.db.dynamic_schema.common :refer [get-validation-cache set-schema]]
   [madek.api.db.dynamic_schema.core :refer [create-dynamic-schema init-enums-by-db]]
   [madek.api.db.dynamic_schema.schema_definitions :as d]
   [taoensso.timbre :refer [error info]]))

(defn set-schema-by-map [schema-map]
  (doseq [[k v] (seq schema-map)]
    (set-schema k v)))

(defn init-schema-by-db []
  (let [_ (init-enums-by-db)

        _ (create-dynamic-schema d/create-groups-schema)
        _ (create-dynamic-schema d/create-users-schema)

        _ (create-dynamic-schema d/create-admins-schema)
        _ (create-dynamic-schema d/create-workflows-schema)
        _ (create-dynamic-schema d/create-collections-schema)
        _ (create-dynamic-schema d/create-collection-media-entry-schema)
        _ (create-dynamic-schema d/create-collection-collection-arcs-schema)
        _ (create-dynamic-schema d/create-app-settings-schema)
        _ (create-dynamic-schema d/create-confidential-links-schema)
        _ (create-dynamic-schema d/create-context-keys-schema)
        _ (create-dynamic-schema d/create-context-schema)
        _ (create-dynamic-schema d/create-custom-urls-schema)
        _ (create-dynamic-schema d/create-delegation-schema)
        _ (create-dynamic-schema d/create-edit_session-schema)
        _ (create-dynamic-schema d/create-vocabularies-schema)
        _ (create-dynamic-schema d/create-usage_terms-schema)
        _ (create-dynamic-schema d/create-static_pages-schema)
        _ (create-dynamic-schema d/create-roles-schema)
        _ (create-dynamic-schema d/create-previews-schema)
        _ (create-dynamic-schema d/create-permissions-schema)
        _ (create-dynamic-schema d/create-people-schema)
        _ (create-dynamic-schema d/create-keywords-schema)
        _ (create-dynamic-schema d/create-meta_keys-schema)
        _ (create-dynamic-schema d/create-media_entries-schema)
        _ (create-dynamic-schema d/create-delegations_users-schema)
        _ (create-dynamic-schema d/create-io_interfaces-schema)
        _ (create-dynamic-schema d/create-favorite_collections-schema)
        _ (create-dynamic-schema d/create-media-files-schema)
        _ (create-dynamic-schema d/create-meta-data-schema)
        _ (create-dynamic-schema d/create-meta-data-role-schema)
        _ (create-dynamic-schema d/create-favorite-media-entries-schema)

        _ (set-schema-by-map (d/top-level-vocabularies-schema))
        _ (set-schema-by-map (d/top-level-permissions-schema))
        _ (set-schema-by-map (d/top-level-media_entries-schema))

        _ (let [errors (get-validation-cache)
                _ (if (empty? errors)
                    (info "[init-schema-by-db] Schema-Validation is OK, no differences between db and generated schema-definitions recognized.")
                    (error "[init-schema-by-db] Schema-Validation failed: " (count errors) " erros occured\nDetails:\n" errors))])]))

;;; Example to save/fetch schema-configuration ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;(comment
;  (let [
;        data {
;              :raw [
;                    {:groups {}}
;                    ;{:_additional (concat schema_pagination_raw schema_full_data_raw)}
;                    ]
;              :raw-schema-name :groups-schema-with-pagination-raw
;              :schemas [
;                        {:groups.schema-query-groups {:key-types "optional" :alias "schema_query-groups"}}
;                        ]
;              }
;
;        tx (get-ds)
;
;
;        data [:cast (json/generate-string data) :jsonb]
;        ins-data {:id "abc" :key "test-me" :config data}
;        query (-> (sql/insert-into :schema_definition)
;                  (sql/values [ins-data])
;                  (sql/returning :*)
;                  sql-format
;                  )
;
;        p (println ">o> query=" query)
;
;        res (jdbc/execute! tx query)
;
;
;        p (println "\nquery" res)
;
;        ]
;    res
;    )
;  )

;(comment
;  (let [
;        tx (get-ds)
;
;        query (-> (sql/select :*)
;                  (sql/from :schema_definition)
;                  (sql/where [:= :key "test-me"])
;                  sql-format
;                  )
;
;        p (println ">o> query=" query)
;
;        res (jdbc/execute-one! tx query)
;        p (println ">o> res1=" res)
;        ]
;    res
;    )
;  )

