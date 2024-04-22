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
    (println ">o> wrap-authorize-admin!")

    (authorize-admin! req handler)))
