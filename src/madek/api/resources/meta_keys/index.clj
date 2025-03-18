(ns madek.api.resources.meta-keys.index
  (:require
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [taoensso.timbre :refer [info]]))

(defn- where-clause
  [user-id scope tx]
  (let [vocabulary-ids (permissions/accessible-vocabulary-ids user-id scope tx)
        perm-kw (keyword (str "vocabularies.enabled_for_public_" scope))]
    (info "vocabs where clause: " vocabulary-ids " for user " user-id " and " scope)
    (if (empty? vocabulary-ids)
      [:= perm-kw true]
      [:or
       [:= perm-kw true]
       [:in :vocabularies.id vocabulary-ids]])))

(defn- base-query
  [user-id scope tx]
  (-> (sql/select :meta_keys.*)
      (sql/from :meta_keys)
      (sql/join :vocabularies
                [:= :meta_keys.vocabulary_id :vocabularies.id])
      (sql/where (where-clause user-id scope tx))))

(defn build-query [request]
  (let [qparams (-> request :parameters :query)
        tx (:tx request)
        scope (or (:scope qparams) "view")
        user-id (-> request :authenticated-entity :id)]
    (-> (base-query user-id scope tx)
        (dbh/build-query-param qparams :vocabulary_id)
        (dbh/build-query-param-like qparams :id :meta_keys.id)
        (dbh/build-query-param qparams :meta_datum_object_type)
        (dbh/build-query-param qparams :is_enabled_for_collections)
        (dbh/build-query-param qparams :is_enabled_for_media_entries)
        (sql/order-by :meta_keys.id))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
