(ns madek.api.resources.users.index
  (:require
   [clojure.spec.alpha :as sa]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.users.common :as common]
   [madek.api.resources.users.get :as get-user]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.helper :refer [f]]
   [madek.api.utils.pagination :refer [pagination-handler]]
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
                  (handle-email-clause params))
        after-fnc (fn [res] (sd/transform_ml_map res))
        res (pagination-handler req query :users after-fnc)]
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
