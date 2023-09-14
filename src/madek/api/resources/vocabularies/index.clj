(ns madek.api.resources.vocabularies.index
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [madek.api.resources.vocabularies.permissions :as permissions]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
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
  [user-id]
  (-> (sql/select :*);:id)
      (sql/from :vocabularies)
      (sql/merge-where (where-clause user-id))
      sql/format))

(defn- query-index-resources [request]
  (let [user-id (-> request :authenticated-entity :id)
        query (base-query user-id)]
    ;(logging/info "query-index-resources: " query)
    (jdbc/query (get-ds) query)))

(defn transform_ml [vocab]
  (assoc vocab
         :labels (sd/transform_ml (:labels vocab))
         :descriptions (sd/transform_ml (:descriptions vocab))))

(defn get-index [request]
  (catcher/with-logging {}
    (let [db-result (query-index-resources request)
          result (->> db-result 
                      (map transform_ml)
                      (map sd/remove-internal-keys)
                      )]
      (sd/response_ok {:vocabularies result}))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
