(ns madek.api.resources.vocabularies.index
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.pagination :refer [add-offset-for-honeysql]]

   [logbug.catcher :as catcher]
   [madek.api.db.core :refer [get-ds]]
   [madek.api.resources.shared :as sd]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [madek.api.utils.helper :refer [str-to-int]]
   [next.jdbc :as jdbc]))

(defn- where-clause
  [user-id]
  (let [vocabulary-ids (permissions/accessible-vocabulary-ids user-id)]
    (if (empty? vocabulary-ids)
      [:= :vocabularies.enabled_for_public_view true]
      [:or
       [:= :vocabularies.enabled_for_public_view true]
       [:in :vocabularies.id vocabulary-ids]])))

(defn- base-query
  ;([user-id size offset]
  ([user-id query-params]
   (-> (sql/select :*)
       (sql/from :vocabularies)
       (sql/where (where-clause user-id))
       (add-offset-for-honeysql query-params)
       ;(sql/offset offset)
       ;(sql/limit size)
       sql-format))

  ;([user-id size offset request]
  ([user-id query-params request]
   (let [is_admin_endpoint (str/includes? (-> request :uri) "/admin/")
         select (if is_admin_endpoint
                  (sql/select :*)
                  (sql/select :id :admin_comment :position :labels :descriptions))]
     (-> select
         (sql/from :vocabularies)
         (sql/where (where-clause user-id))

       (add-offset-for-honeysql query-params)
         ;(sql/offset offset)
         ;(sql/limit size)
         sql-format))))

(defn- query-index-resources [request]
  (let [user-id (-> request :authenticated-entity :id)
        qparams (-> request :query-params)

        ; TODO: unify pagination-handling
        ;page (get qparams "page")
        ;count (get qparams "count")
        ;
        ;offset (str-to-int page 0)
        ;size (str-to-int count 100)
        ;
        ;query (base-query user-id size offset request)]
        query (base-query user-id qparams request)]

;(info "query-index-resources: " query)
    (jdbc/execute! (get-ds) query)))

(defn transform_ml [vocab]
  (assoc vocab
         :labels (sd/transform_ml (:labels vocab))
         :descriptions (sd/transform_ml (:descriptions vocab))))

(defn get-index [request]
  (catcher/with-logging {}
    (let [db-result (query-index-resources request)
          result (map transform_ml db-result)]
      (sd/response_ok {:vocabularies result}))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
