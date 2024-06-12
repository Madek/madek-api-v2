(ns madek.api.resources.users.index
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.users.common :as common]
   [madek.api.resources.users.get :as get-user]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [f]]
   [madek.api.utils.pagination :as pagination
    :refer [optional-pagination-params pagination-validation-handler swagger-ui-pagination]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

(defn handle-email-clause [thread-obj params]
  (if-let [email (:email params)]
    (-> thread-obj
        (sql/where [:= :email email]))
    thread-obj))

(defn handler
  "Get an index of the users. Query parameters are pending to be implemented."
  [{params :params tx :tx :as req}]
  (let [query (-> common/base-query
                  (pagination/sql-offset-and-limit params)
                  (handle-email-clause params)
                  (sql-format :inline false))
        res (->> query
                 (jdbc/execute! tx)
                 (assoc {} :users))
        res (sd/transform_ml_map res)]
    (sd/response_ok res)))

(def query-schema
  {(s/optional-key :email) s/Str})

(def route
  {:summary (sd/sum_adm (f "Get list of users ids." "no-list"))
   :description "Get list of users ids."
   :handler handler
   :middleware [wrap-authorize-admin!
                (pagination-validation-handler (merge optional-pagination-params query-schema))]
   :swagger (swagger-ui-pagination)
   :parameters {:query query-schema}
   :coercion reitit.coercion.schema/coercion
   :responses {200 {:body {:users [get-user/schema]}}}})
