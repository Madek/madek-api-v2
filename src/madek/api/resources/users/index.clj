(ns madek.api.resources.users.index
  (:require
   [clojure.spec.alpha :as sa]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.users.common :as common]
   [madek.api.resources.users.get :as get-user]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.helper :refer [f]]
   [madek.api.pagination :as pagination]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]

   [schema.core :as s]))

(defn handle-email-clause [thread-obj params]
  (if-let [email (:email params)]
    (-> thread-obj
        (sql/where [:= :email email]))
    thread-obj))

(defn handler
  "Get an index of the users. Query parameters are pending to be implemented."
  ;[{params :params parameters :parameters tx :tx :as req}]
  [{{params :query} :parameters tx :tx :as req}]

  (let [
        ;;; iterate through params and print type
        ;_ (doseq [k (keys params)]
        ;  (println (str k " " (type (get params k))))
        ;)
        ;
        ;
        ;p (println ">o> >> -------- already casted params" )
        ;_ (doseq [k (keys params)]
        ;  (println (str k " " (type (get params k))))
        ;)
        ;



        query (-> common/base-query
                  ;(pagination/sql-offset-and-limit params)
                  (pagination/add-offset-for-honeysql params)
                  (handle-email-clause params)
                  (sql-format :inline false))
        res (->> query
                 (jdbc/execute! tx)
                 (assoc {} :users))
        res (sd/transform_ml_map res)]
    (sd/response_ok res)))

(sa/def ::users-query-def (sa/keys :opt-un [::sp/email ::sp/page ::sp/size]))

(def query-schema
  {(s/optional-key :email) s/Str})

(def route
  {:summary (sd/sum_adm (f "Get list of users ids." "no-list"))
   :description "Get list of users ids."
   :handler handler

   :middleware [wrap-authorize-admin!
                ;(pagination-validation-handler (merge optional-pagination-params query-schema))
                ]
   ;:swagger (swagger-ui-pagination)
   ;:parameters {:query query-schema}

   :parameters {:query ::users-query-def}
   ;:coercion reitit.coercion.schema/coercion
   :coercion spec/coercion

   ;:responses {200 {:body {:users [get-user/schema]}}}})
   ;:responses {200 {:body ::get-user/users-body-resp-def}}}) ;; TODO: fixme
   :responses {200 {:body any?}}})
