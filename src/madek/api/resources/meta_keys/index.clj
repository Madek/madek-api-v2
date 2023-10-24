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
  [user-id scope]
  (let [vocabulary-ids (permissions/accessible-vocabulary-ids user-id scope)
        perm-kw (keyword (str "vocabularies.enabled_for_public_" scope))]
    (logging/info "vocabs where clause: " vocabulary-ids " for user " user-id " and " scope)
    (if (empty? vocabulary-ids)
      [:= perm-kw true]
      [:or 
       [:= perm-kw true]
       [:in :vocabularies.id vocabulary-ids]])))
      ;[:= :vocabularies.enabled_for_public_view true]
      ;[:or
      ;  [:= :vocabularies.enabled_for_public_view true]
      ;  [:in :vocabularies.id vocabulary-ids]])))



(defn- base-query
  [user-id scope]
  (-> (sql/select :*); :meta_keys.id :meta_keys.vocabulary_id)
      (sql/from :meta_keys)
      (sql/merge-join :vocabularies
                      [:= :meta_keys.vocabulary_id :vocabularies.id])
      (sql/merge-where (where-clause user-id scope))))


(defn- build-query [request]
  (let [qparams (-> request :parameters :query)
        scope (or (:scope qparams) "view") 
        user-id (-> request :authenticated-entity :id)]
    (-> (base-query user-id scope)
        (sd/build-query-param qparams :vocabulary_id)
        (sd/build-query-param-like qparams :id :meta_keys.id)
        (sd/build-query-param qparams :meta_datum_object_type)
        (sd/build-query-param qparams :is_enabled_for_collections)
        (sd/build-query-param qparams :is_enabled_for_media_entries)
        sql/format)))


(defn db-query-meta-keys [request]
  (catcher/with-logging {}
    (let [query (build-query request)]
      (logging/info "db-query-meta-keys: query: " query)
      (jdbc/query (rdbms/get-ds) query))))

;(defn get-index [request]
;  (catcher/with-logging {}
;    {:body
;     {:meta-keys
;      (query-index-resources request)}}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
