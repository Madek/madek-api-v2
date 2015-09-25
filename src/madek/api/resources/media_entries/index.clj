(ns madek.api.resources.media-entries.index
  (:require
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.ring :refer [wrap-handler-with-logging]]
    [honeysql.sql :refer :all]
    [madek.api.pagination :as pagination]
    [madek.api.resources.media-entries.permissions.filter :as permissions-sql]
    [madek.api.resources.shared :as shared]
    ))

(defn build-index-base-query
  [{:keys [order] :or {order :asc} :as query-params-with-auth-entity}]
  (-> (sql-select :me.id, :me.created_at)
      (sql-merge-modifiers :distinct)
      (sql-from [:media-entries :me])
      (permissions-sql/sql-public-get-metadata-and-previews
        query-params-with-auth-entity)
      (permissions-sql/sql-public-get-full-size
        query-params-with-auth-entity)
      (permissions-sql/sql-me-permission :me_get_metadata_and_previews
                                         query-params-with-auth-entity)
      (permissions-sql/sql-me-permission :me_get_full_size
                                         query-params-with-auth-entity)
      (sql-order-by [:me.created-at (keyword order)])
      (sql-limit 10)))

(defn- index-resources [query-params-with-auth-entity]
  (let [query (-> (build-index-base-query query-params-with-auth-entity)
                  (pagination/add-offset-for-honeysql query-params-with-auth-entity)
                  sql-format)]
    (jdbc/query (rdbms/get-ds) query)))

(defn- wrap-permissions-params-combination-check [handler query-params]
  (letfn [(permissions-params-combined? [query-params]
            (> (count (select-keys query-params [:public_get_metadata_and_previews
                                                 :public_get_full_size
                                                 :me_get_metadata_and_previews
                                                 :me_get_full_size])) 1))]
    (fn [request]
      (if (permissions-params-combined? query-params)
        {:status 422 :body {:message "It is not allowed to combine multiple permission query parameters!"}}
        (handler request)))))

(defn- wrap-permissions-params-false-value-check [handler query-params]
  (letfn [(me-permissions-params-some-false-value? [query-params]
            (not-every? true?
                        (map read-string
                             (vals (select-keys query-params
                                                [:me_get_metadata_and_previews
                                                 :me_get_full_size])))))]
    (fn [request]
      (if (me-permissions-params-some-false-value? query-params)
        {:status 422 :body {:message "True value must be provided for 'me_' permission parameters"}}
        (handler request)))))

(defn- get-index-base [{:keys [query-params authenticated-entity]}]
  (catcher/wrap-with-log-error
    {:body
     {:media-entries
      (index-resources (into query-params
                             {:auth-entity authenticated-entity}))}}))

(defn get-index [request]
  (let [{query-params :query-params} request]
    (-> get-index-base
        (wrap-permissions-params-false-value-check query-params)
        (wrap-permissions-params-combination-check query-params))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
