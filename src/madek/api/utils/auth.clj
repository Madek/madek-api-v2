(ns madek.api.utils.auth
  (:require
   [clj-uuid]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc :as jdbc]))

;### admin check ##############################################################

(defn authorize-admin! [request handler]
  "Checks if the authenticated-entity is an admin by either
  checking (-> request :authenticated-entity :is_admin) if present or performing
  an db query.  If so adds {:is_amdin true} to the requests an calls handler.
  Throws a ExceptionInfo with status 403 otherwise. "

     (let [
              p (println ">o> is_admin?1="  request)
              p (println ">o> is_admin?1b="  (:is_admin request))
              p (println ">o> auth.id?2="  (-> request :authenticated-entity))
              p (println ">o> auth.id?2b="  (-> request :authenticated-entity :id))
              p (println ">o> auth.id?3="  (->> (-> (sql/select [true :is_admin])
                                                    (sql/from :admins)
                                                    (sql/where [:= :admins.user_id (-> request :authenticated-entity :id)])
                                                    sql-format)
                                                (jdbc/execute! (:tx request))
                                                first :is_admin))
              ])

  (handler
   (or
      ;(if (contains? (-> request :authenticated-entity) :is_admin)
    (if (contains? request :is_admin)
        ;(when (-> request :authenticated-entity :is_admin) request)
      (when (-> request :is_admin) request)
      (when (->> (-> (sql/select [true :is_admin])
                     (sql/from :admins)
                     (sql/where [:= :admins.user_id (-> request :authenticated-entity :id)])
                     sql-format)
                 (jdbc/execute! (:tx request))
                 first :is_admin)
          ;(assoc-in request [:authenticated-entity :is_admin] true)))
        (assoc-in request [:is_admin] true)))
    (throw
     (ex-info
      "Only administrators are allowed to access this resource."
      {:status 403
       :body {:msg "Only administrators are allowed to access this resource."}})))))

(defn wrap-authorize-admin! [handler]
  (fn [req]
    (authorize-admin! req handler)))
