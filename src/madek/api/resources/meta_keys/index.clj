(ns madek.api.resources.meta-keys.index
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [madek.api.resources.shared :as shared]
    [madek.api.resources.vocabularies.permissions :as permissions]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    [madek.api.resources.shared :as sd]
    ))

(defn- where-clause
  [user-id]
  (let [vocabulary-ids (permissions/accessible-vocabulary-ids user-id)]
    (if (empty? vocabulary-ids)
      [:= :vocabularies.enabled_for_public_view true]
      [:or
        [:= :vocabularies.enabled_for_public_view true]
        [:in :vocabularies.id vocabulary-ids]])))


(defn- base-query
  [user-id ]
  (-> (sql/select :*); :meta_keys.id :meta_keys.vocabulary_id)
      (sql/from :meta_keys)
      (sql/merge-join :vocabularies
                      [:= :meta_keys.vocabulary_id :vocabularies.id])
      (sql/merge-where (where-clause user-id))))


;(defn- filter-by-vocabulary [query request]
;  (if-let [vocabulary-id (-> request :parameters :query :vocabulary_id)]
;    (-> query
;        (sql/merge-where [:= :vocabulary_id vocabulary-id]))
;    query))


(defn- build-query [request]
  (let [qparams (-> request :parameters :query)
        user-id (-> request :authenticated-entity :id)]
    (-> (base-query user-id)
        ;(filter-by-vocabulary request)
        (sd/build-query-param qparams :vocabulary_id)
        (sd/build-query-param-like qparams :id)
        (sd/build-query-param qparams :meta_datum_object_type)
        (sd/build-query-param qparams :is_enabled_for_collections)
        (sd/build-query-param qparams :is_enabled_for_media_entries)

        sql/format)))


(defn db-query-meta-keys [request]
  (let [query (build-query request)]
    (logging/info "db-query-meta-keys: query: " query)
    (jdbc/query (rdbms/get-ds) query))
  )

;(defn get-index [request]
;  (catcher/with-logging {}
;    {:body
;     {:meta-keys
;      (query-index-resources request)}}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
