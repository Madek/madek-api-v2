(ns madek.api.utils.auth
  (:require
   [clj-uuid]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc :as jdbc]))

(def ADMIN_AUTH_METHODS [{"apiAuth" []} {"csrfToken" []}])

;### admin check ##############################################################

(defn admin-route-authorized?
  "True only within routes guarded by `wrap-authorize-admin!`. Being an admin
  (:is_admin) alone grants nothing on user routes; there admins need the same
  permissions as every other user."
  [request]
  (true? (:admin-route-authorized request)))

(defn authorize-admin! [request handler]
  "Checks if the authenticated-entity is an admin by either
  checking (-> request :authenticated-entity :is_admin) if present or performing
  an db query.  If so adds {:is_admin true :admin-route-authorized true} to the
  request and calls handler.
  Throws a ExceptionInfo with status 403 otherwise. "
  (handler
   (or
    (some-> (if (contains? request :is_admin)
              (when (-> request :is_admin) request)
              (when (->> (-> (sql/select [true :is_admin])
                             (sql/from :admins)
                             (sql/where [:= :admins.user_id (-> request :authenticated-entity :id)])
                             (sql/order-by [:admins.user_id :asc])
                             sql-format)
                         (jdbc/execute! (:tx request))
                         first :is_admin)
                (assoc-in request [:is_admin] true)))
            (assoc :admin-route-authorized true))
    (throw
     (ex-info
      "Only administrators are allowed to access this resource."
      {:status 403
       :body {:msg "Only administrators are allowed to access this resource."}})))))

(defn wrap-authorize-admin! [handler]
  (fn [req]
    (authorize-admin! req handler)))
