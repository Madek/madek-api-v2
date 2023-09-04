(ns madek.api.resources.roles.role
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [madek.api.pagination :as pagination]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [madek.api.utils.sql :as sql]))

(defn export-role [db-role]
  (select-keys db-role [:id :labels :created_at]))

(defn- transform-ml-role [role]
  (assoc role :labels (sd/transform_ml (:labels role))))


(defn- query-index
  [query-params]
  (-> (sql/select :roles.*)
      (sql/from :roles)
      (pagination/add-offset-for-honeysql query-params)
      (sql/format)))

(defn get-index
  [request]
  (let [query-params (-> request :parameters :query)
        dbresult (jdbc/query (rdbms/get-ds) (query-index query-params))
        result (map transform-ml-role dbresult)]
    (sd/response_ok {:roles result})))

(defn query_role-find-one [id]
  (-> (sql/select :*)
      (sql/from :roles)
      (sql/merge-where
       [:= :roles.id id])
      (sql/format)))


(defn db_role-find-one [id]
  (let [query (query_role-find-one id)
        resultdb (first (jdbc/query (rdbms/get-ds) query))]
    resultdb))




(defn handle_get-role-usr [request]
  (let [id (-> request :parameters :path :id)
        resultdb (db_role-find-one id)
        result (-> resultdb
                   export-role
                   transform-ml-role
                   )]
    (sd/response_ok result)))


(defn handle_get-role-admin [request]
  (let [id (-> request :parameters :path :id)
        resultdb (db_role-find-one id)
        result (transform-ml-role resultdb)]
    (sd/response_ok result)))


(defn handle_create-role
  [req]
  (try
    (let [data (-> req :parameters :body)
          auth-id (-> req :authenticated-entity :id)
          ins-data (assoc data :creator_id auth-id)
          ins-res (jdbc/insert! (rdbms/get-ds) :roles ins-data)]
      
      (logging/info "handle_create-role: " "\new-data:\n" data "\nresult:\n" ins-res)

      (if-let [result (first ins-res)]
       (sd/response_ok (transform-ml-role result))
        (sd/response_failed "Could not create role." 406)))
    (catch Exception ex (sd/response_exception ex))))


(defn handle_update-role
  [req]
  (try
    (let [data (-> req :parameters :body)
          id (-> req :parameters :path :id)
          dwid (assoc data :id id)
          upd-query (sd/sql-update-clause "id" (str id))
          upd-result (jdbc/update! (rdbms/get-ds) :roles dwid upd-query)]

      (logging/info "handle_update-role: " id "\nnew-data\n" dwid "\nupd-result\n" upd-result)

      (if (= 1 (first upd-result))
        (sd/response_ok (transform-ml-role
                         (sd/query-eq-find-one :roles :id id)))
        (sd/response_failed "Could not update role." 406))
      )
    (catch Exception ex (sd/response_exception ex)))
  )

(defn handle_delete-role
  [req]
  (try
    (let [id (-> req :parameters :path :id)]
      (if-let [role (sd/query-eq-find-one :roles :id id)]
        
        (let [del-query (sd/sql-update-clause "id" id)
              del-result (jdbc/delete! (rdbms/get-ds) :roles del-query)]
          (logging/info "handle_delete-role: " id " result: " del-result)
          (if (= 1 (first del-result))
            (sd/response_ok (transform-ml-role role))
            (sd/response_failed "Could not delete role." 406)))
        
        (sd/response_failed "No such role." 404)
      ))
    (catch Exception ex (sd/response_exception ex))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
