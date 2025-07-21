(ns madek.api.resources.users.index
  (:require
   [clojure.spec.alpha :as sa]
   [honey.sql.helpers :as sql]
   [logbug.debug :as debug]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.users.common :as common]
   [madek.api.resources.users.get :as get-user]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.helper :refer [f]]
   [madek.api.utils.pagination :refer [pagination-handler]]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]))

(defn email-filter [sql-query params]
  (if-let [email (:email params)]
    (-> sql-query
        (sql/where [:= :email email]))
    sql-query))

(defn institution-filter [sql-query params]
  (if-let [institution (:institution params)]
    (-> sql-query
        (sql/where [:= :institution institution]))
    sql-query))

(defn filter-query [sql-query query-params]
  (-> sql-query
      (email-filter query-params)
      (institution-filter query-params)))

(defn users-index-handler
  [{{params :query} :parameters tx :tx :as req}]
  (let [query (-> common/base-query
                  (filter-query params))
        after-fnc (fn [res] (sd/transform_ml_map res))
        res (pagination-handler req query :users after-fnc)]
    (sd/response_ok res)))

(sa/def ::users-query-def
  (sa/keys :opt-un [::sp/email ::sp/institution ::sp/page ::sp/size]))

(def route
  {:summary (sd/sum_adm (f "Get list of users ids." "no-list"))
   :description "Get list of users ids."
   :handler users-index-handler
   :middleware [wrap-authorize-admin!]
   :parameters {:query ::users-query-def}
   :coercion spec/coercion
   :responses {200 {:description "List of users ids."
                    :body ::get-user/users-body-resp-def}}})

;(debug/debug-ns *ns*)
