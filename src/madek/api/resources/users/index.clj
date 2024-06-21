(ns madek.api.resources.users.index
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.users.common :as common]

   [madek.api.utils.pagination :refer [pagination-handler ItemQueryParams swagger-ui-pagination create-swagger-ui-param]]

   [madek.api.resources.users.get :as get-user]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [f]]
   [madek.api.utils.pagination :as pagination]
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

;; TODO: not in use
(def query-schema
  {
;    (s/optional-key :count) s/Int
;   (s/optional-key :page) s/Int
;  :email s/Str
  :test-uuid s/Uuid
   })

(def route
  {:summary (sd/sum_adm (f "Get list of users ids." "no-list"))
   :description "Get list of users ids."
   ;:swagger {:produces "application/json"
   ;          :parameters [{:name "email"
   ;                        :in "query"
   ;                        :description "Filter admin by email, e.g.: mr-test@zhdk.ch"
   ;                        :type "string"
   ;                        :pattern "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$"}
   ;                       {:name "page"
   ;                        :in "query"
   ;                        :description "Page number, defaults to 0 (zero-based-index)"
   ;                        :required true
   ;                        :default 0
   ;                        :minimum 0
   ;                        :type "number"
   ;                        ;:type "integer"
   ;                        ;:type "long"
   ;                        ;:pattern "^([1-9][0-9]*|0)$"
   ;                        }
   ;                       {:name "count"
   ;                        :in "query"
   ;                        :description "Number of items per page (1-100), defaults to 100"
   ;                        :required true
   ;                        :minimum 1
   ;                        :maximum 100
   ;                        :value 100
   ;                        :default 100
   ;                        ;:type "integer"
   ;                        :type "number"
   ;                        ;:type "long"
   ;                        }]}

   ;:swagger ( swagger-ui-pagination {} [{:name "email"
   ;                                         :in "query"
   ;                                         :description "Filter admin by email, e.g.: mr-test@zhdk.ch"
   ;                                         :type "string"
   ;                                      :value "fjdksla@fjdk.at"
   ;                                         :pattern "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$"}])


   ;:parameters {}
   ;:parameters {:query {}}
   ;:parameters {:query query-schema}

   ;:parameters {:query { 'test-uuid' s/Uuid}} ;;broken
   ;:parameters {:query { :test-uuid s/Uuid}} ;;broken

   :content-type "application/json"
   :handler handler
   :middleware [
                wrap-authorize-admin!

                ;(pagination-handler (merge ItemQueryParams schema_query_media_entries))
                ;(pagination-handler (merge ItemQueryParams query-schema) )
                ;(pagination-handler ItemQueryParams )

                ;(pagination-handler ItemQueryParams {:test-uuid "uuid"})
                ;(pagination-handler (merge ItemQueryParams query-schema) {:test-uuid "uuid"} )

                (pagination-handler  ItemQueryParams  )
                ]

   :swagger ( swagger-ui-pagination )
   :parameters {:query {}}
   :coercion reitit.coercion.schema/coercion

   :responses {200 {:body {:users [get-user/schema]}}}})
