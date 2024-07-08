(ns madek.api.resources.users.index
  (:require
   [clojure.spec.alpha :as sa]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.pagination :as pagination]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.users.common :as common]
   [madek.api.resources.users.get :as get-user]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.helper :refer [f]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]))

(defn handle-email-clause [thread-obj params]
  (if-let [email (:email params)]
    (-> thread-obj
        (sql/where [:= :email email]))
    thread-obj))

(defn handler
  "Get an index of the users. Query parameters are pending to be implemented."
  [{{params :query} :parameters tx :tx :as req}]
  (let [query (-> common/base-query
                  (pagination/sql-offset-and-limit params)
                  (handle-email-clause params)
                  (sql-format :inline false))
        res (->> query
                 (jdbc/execute! tx)
                 (assoc {} :users))
        res (sd/transform_ml_map res)]
    (sd/response_ok res)))

(sa/def ::users-query-def (sa/keys :opt-un [::sp/email ::sp/page ::sp/size]))

(def route
  {:summary (sd/sum_adm (f "Get list of users ids." "no-list"))
   :description "Get list of users ids."
   :handler handler
   :middleware [wrap-authorize-admin!]
   :parameters {:query ::users-query-def}
   :coercion spec/coercion
   :responses {200 {:description "List of users ids."
                    :body ::get-user/users-body-resp-def}}})
