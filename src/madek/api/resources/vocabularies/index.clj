(ns madek.api.resources.vocabularies.index
  (:require
   [clojure.string :as str]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [madek.api.utils.pagination :refer [pagination-handler]]
   [taoensso.timbre :refer [debug]]))

(defn- where-clause
  [user-id tx]
  (let [vocabulary-ids (permissions/accessible-vocabulary-ids user-id tx)]
    (if (empty? vocabulary-ids)
      [:= :vocabularies.enabled_for_public_view true]
      [:or
       [:= :vocabularies.enabled_for_public_view true]
       [:in :vocabularies.id vocabulary-ids]])))

(defn- base-query
  ([user-id query-params tx]
   (-> (sql/select :*)
       (sql/from :vocabularies)
       (sql/order-by [:position :asc])
       (sql/where (where-clause user-id tx))))
  ([user-id query-params request tx]
   (let [is_admin_endpoint (str/includes? (-> request :uri) "/admin/")
         select (if is_admin_endpoint
                  (sql/select :*)
                  (sql/select :id :admin_comment :position :labels :descriptions))]
     (-> select
         (sql/from :vocabularies)
         (sql/order-by [:position :asc])
         (sql/where (where-clause user-id tx))))))

(defn transform_ml [vocab]
  (assoc vocab
         :labels (sd/transform_ml (:labels vocab))
         :descriptions (sd/transform_ml (:descriptions vocab))))

(defn- query-index-resources [request]
  (let [user-id (-> request :authenticated-entity :id)
        tx (:tx request)
        qparams (-> request :query-params)
        query (base-query user-id qparams request tx)
        after-fnc (fn [res] (map transform_ml res))
        result (pagination-handler request query :vocabularies after-fnc)]
    (debug 'vocabularies result)
    result))

(defn get-index [request]
  (catcher/with-logging {}
    (let [db-result (query-index-resources request)]
      (sd/response_ok db-result))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
