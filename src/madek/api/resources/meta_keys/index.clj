(ns madek.api.resources.meta-keys.index
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [madek.api.pagination :as pagination]
    [madek.api.resources.shared :as shared]
    [madek.api.resources.vocabularies.permissions :as permissions]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    ))

(defn- where-clause
  [user-id]
  (let [vocabulary-ids (permissions/accessible-vocabulary-ids user-id)]
    (if (empty? vocabulary-ids)
      [:= :vocabularies.enabled_for_public_view true]
      [:or
        [:= :vocabularies.enabled_for_public_view true]
        [:in :vocabularies.id vocabulary-ids]])))

(defn- select_cls [full-data]
  (if (= true full-data)
    :*
    :meta_keys.id))

(defn- base-query
  [user-id full-data]
  (-> (sql/select (select_cls full-data))
      (sql/from :meta_keys)
      (sql/merge-join :vocabularies
                      [:= :meta_keys.vocabulary_id :vocabularies.id])
      (sql/merge-where (where-clause user-id))))

(defn- filter-by-vocabulary [query request]
  (if-let [vocabulary-id (or (-> request :query-params :vocabulary_id) (-> request :parameters :query :vocabulary_id))]
    (-> query
        (sql/merge-where [:= :vocabulary_id vocabulary-id]))
    query))

(defn- build-query [request]
  (let [user-id (-> request :authenticated-entity :id)
        full-data (-> request :parameters :query :full-data)]
    (-> (base-query user-id full-data)
        (filter-by-vocabulary request)
        sql/format)))

(defn- query-index-resources [request]
  (jdbc/query (rdbms/get-ds)
              (build-query request)))

(defn get-index [request]
  (catcher/with-logging {}
    {:body
     {:meta-keys
      (query-index-resources request)}}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
