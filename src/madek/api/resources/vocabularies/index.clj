(ns madek.api.resources.vocabularies.index
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.pagination :refer [add-offset-for-honeysql]]
   [madek.api.resources.shared :as sd]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [next.jdbc :as jdbc]))

(defn- where-clause
  [user-id ds]
  (let [vocabulary-ids (permissions/accessible-vocabulary-ids user-id ds)]
    (if (empty? vocabulary-ids)
      [:= :vocabularies.enabled_for_public_view true]
      [:or
       [:= :vocabularies.enabled_for_public_view true]
       [:in :vocabularies.id vocabulary-ids]])))

(defn- base-query
  ([user-id query-params ds]
   (-> (sql/select :*)
       (sql/from :vocabularies)
       (sql/where (where-clause user-id ds))
       (add-offset-for-honeysql query-params)
       sql-format))

  ([user-id query-params request ds]
   (let [is_admin_endpoint (str/includes? (-> request :uri) "/admin/")
         select (if is_admin_endpoint
                  (sql/select :*)
                  (sql/select :id :admin_comment :position :labels :descriptions))]
     (-> select
         (sql/from :vocabularies)
         (sql/where (where-clause user-id ds))
         (add-offset-for-honeysql query-params)
         sql-format))))

(defn- query-index-resources [request]
  (let [user-id (-> request :authenticated-entity :id)
        ds (:tx request)
        p (println ">o> 3ds=" ds)
        qparams (-> request :query-params)
        query (base-query user-id qparams request ds)]

;(info "query-index-resources: " query)
    (jdbc/execute! ds query)))

(defn transform_ml [vocab]
  (assoc vocab
         :labels (sd/transform_ml (:labels vocab))
         :descriptions (sd/transform_ml (:descriptions vocab))))

(defn get-index [request]
  (catcher/with-logging {}
    (let [db-result (query-index-resources request)

          p (println ">o> db-result=" db-result)

          result (map transform_ml db-result)]
      (sd/response_ok {:vocabularies result}))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
