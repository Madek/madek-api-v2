(ns madek.api.resources.users
  (:require
    [clj-uuid]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug] 
    [madek.api.pagination :as pagination]
    [madek.api.utils.auth :refer [wrap-authorize-admin!]]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    [madek.api.resources.shared :as sd]
    [reitit.coercion.schema]
    [schema.core :as s]
    [madek.api.authorization :as authorization]
    ))


(defn sql-select
  ([] (sql-select {}))
  ([sql-map]
   (sql/select sql-map
               :users.id :users.email :users.institutional_id :users.login
               :users.created_at :users.updated_at
               :users.person_id
               )))

(defn sql-merge-where-id
  ([id] (sql-merge-where-id {} id))
  ([sql-map id]
   (if (re-matches
         #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
         id)
     (sql/merge-where sql-map [:or
                               [:= :users.id id]
                               [:= :users.institutional_id id]
                               [:= :users.email id]])
     (sql/merge-where sql-map [:or
                               [:= :users.institutional_id id]
                               [:= :users.email id]]))))


(defn jdbc-id-where-clause [id]
  (-> id sql-merge-where-id sql/format
      (update-in [0] #(clojure.string/replace % "WHERE" ""))))


;### create user #############################################################

(defn create-user [body]
  (let [params body]
    ;(logging/info "create-user" "\nparams\n" params)
    (when-let [dbresult (->> (jdbc/insert! (rdbms/get-ds) :users params) first)]
      (logging/info "created-user" "\nparams\n" params "\ndbresult\n" dbresult) 
      (let [result (dissoc dbresult :password_digest )] ; hide password in result
        (sd/response_ok result 201)))))

;### get user ################################################################

(defn find-user-sql [some-id]
  (-> (sql-select)
      (sql-merge-where-id some-id)
      (sql/merge-where [:= :is_deactivated false])
      (sql/from :users)
      sql/format))

(defn find-user [some-id]
  (->> some-id find-user-sql
       (jdbc/query (rdbms/get-ds)) first))

(defn get-user [some-id]
  (if-let [user (find-user some-id)]
    (sd/response_ok user)
    {:status 404 :body "No such user found"}))


;### delete user ##############################################################

; TODO return data before
(defn delete-user [id]
  (if (= 1 (first (jdbc/delete! (rdbms/get-ds)
                                :users (jdbc-id-where-clause id))))
    {:status 204}
    {:status 404}))


;### patch user ##############################################################



;### index ####################################################################

(defn build-index-query [query-params]
  
  (-> (if (true? (:full_data query-params))
        (sql/select :*)
        (sql/select :id))
      (sql/from :users)
      (sql/merge-where [:= :is_deactivated false])
      (sd/build-query-param query-params :person_id)
      (sd/build-query-param query-params :institutional_id)
      (sd/build-query-param query-params :accepted_usage_terms_id)
      (sd/build-query-param query-params :is_deactivated)
      (sd/build-query-param-like query-params :email)
      (sd/build-query-param-like query-params :login)
      (sd/build-query-param-like query-params :notes)
      (sd/build-query-param-like query-params :autocomplete)
      (sd/build-query-param-like query-params :searchable)
      (sql/order-by [:id :asc])
      (pagination/add-offset-for-honeysql query-params)
      sql/format))

(defn index [request]
  (let [query-params (-> request :parameters :query)
        sql-query (build-index-query query-params)
        result (jdbc/query (rdbms/get-ds) sql-query)]
    (sd/response_ok {:users result})))


;### routes ###################################################################



(def schema_update_user 
  {
   ;(s/optional-key :id) s/Uuid
   (s/optional-key :email) s/Str
   (s/optional-key :login) s/Str

   ;(s/optional-key :person_id) s/Uuid
   ;(s/optional-key :institutional_id) s/Str
   (s/optional-key :institution) s/Str

   (s/optional-key :accepted_usage_terms_id) (s/maybe s/Uuid) ; TODO
   
   (s/optional-key :notes) (s/maybe s/Str) ; TODO

   (s/optional-key :is_deactivated) s/Bool
   
   ;(s/optional-key :password_digest) (s/maybe s/Any) ; TODO
   ;:last_signed_in_at (s/maybe s/Any) ; TODO
   (s/optional-key :autocomplete) s/Str
   (s/optional-key :searchable) s/Str
   })

(def schema_create_user
  {
   (s/optional-key :id) s/Uuid
   :person_id s/Uuid
   (s/optional-key :institutional_id) s/Str

   (s/optional-key :email) s/Str
   (s/optional-key :login) s/Str

   (s/optional-key :institution) s/Str
   (s/optional-key :accepted_usage_terms_id) (s/maybe s/Uuid) ; TODO

   (s/optional-key :notes) (s/maybe s/Str) ; TODO

   (s/optional-key :is_deactivated) s/Bool

   ;(s/optional-key :password_digest) (s/maybe s/Any) ; TODO
   ;:last_signed_in_at (s/maybe s/Any) ; TODO
   (s/optional-key :autocomplete) s/Str
   (s/optional-key :searchable) s/Str
   (s/optional-key :settings) s/Any ; TODO is json
   })

(def schema_create_user_result
  {
   :id s/Uuid
   :person_id s/Uuid
   :institutional_id (s/maybe s/Str)
   :email (s/maybe s/Str)
   :login (s/maybe s/Str)
   
   (s/optional-key :institution) (s/maybe s/Str)

   :settings s/Any ; TODO is json
   :accepted_usage_terms_id (s/maybe s/Uuid) ; TODO
   :is_deactivated s/Bool
   :notes (s/maybe s/Str) ; TODO
   ;:password_digest (s/maybe s/Any) ; TODO
   ;:last_signed_in_at (s/maybe s/Any) ; TODO
   :autocomplete s/Str
   :searchable (s/maybe s/Str)

   :created_at s/Any
   :updated_at s/Any})

(def schema_export_user
  {:id s/Uuid
   (s/optional-key :email) s/Str
   (s/optional-key :login) s/Str
   (s/optional-key :person_id) s/Uuid

   (s/optional-key :institutional_id) (s/maybe s/Str)
   (s/optional-key :institution) (s/maybe s/Str)

   (s/optional-key :accepted_usage_terms_id) (s/maybe s/Uuid) ; TODO
   (s/optional-key :is_deactivated) s/Bool

   (s/optional-key :notes) (s/maybe s/Str)
   (s/optional-key :settings) s/Any ; TODO is json

   (s/optional-key :last_signed_in_at) (s/maybe s/Any) ; TODO
   (s/optional-key :autocomplete) s/Str
   (s/optional-key :searchable) s/Str

   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any
   })

(defn handle_get-user [req]
  (let [id (-> req :parameters :path :id)]
    ;(logging/info "handle_get-user" "\nid\n" id)
    (get-user id)))

(defn handle_delete-user [req]
  (delete-user (-> req :parameters :path :id)))

(defn handle_patch-user [req]
  (let [id (-> req :parameters :path :id)
        body (-> req :parameters :body)]
    (if-let [old-data (find-user id)]
      (let [upd-result (jdbc/update! (rdbms/get-ds) :users body (jdbc-id-where-clause id))]
        (if (= 1 (first upd-result))
          (sd/response_ok (find-user id) 200)
          (sd/response_failed "Could not create user." 406)))
      (sd/response_not_found "No such user."))))

(defn handle_create-user [req]
  (let [body (-> req :parameters :body)]
    (create-user body)
    ))

(def schema_query_user
  {(s/optional-key :full_data) s/Bool
   (s/optional-key :page) s/Int
   (s/optional-key :count) s/Int
   (s/optional-key :person_id) s/Uuid
   (s/optional-key :institutional_id) s/Uuid
   (s/optional-key :accepted_usage_terms_id) s/Uuid
   (s/optional-key :is_deactivated) s/Bool
   (s/optional-key :email) s/Str
   (s/optional-key :login) s/Str
   (s/optional-key :notes) s/Str
   (s/optional-key :autocomplete) s/Str
   (s/optional-key :searchable) s/Str})

; TODO tests
(def admin-routes
  ["/users"
   ["/" 
    {:get {:summary (sd/sum_adm "Get list of users ids.")
           :description "Get list of users ids."
           :swagger {:produces "application/json"}
           ; TODO query paging count and full-data, more query params
           :parameters {:query schema_query_user}
           :content-type "application/json"
           :handler index
           :middleware [wrap-authorize-admin!]
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:body {:users [schema_export_user]}}}}

     :post {:summary (sd/sum_adm "Create user.")
            :description "Create user."
            :swagger {:consumes "application/json"
                      :produces "application/json"}
            :content-type "application/json"
            :accept "application/json"
            :handler handle_create-user
            :middleware [wrap-authorize-admin!]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_create_user}
            :responses {201 {:body schema_create_user_result}
                        406 {:body s/Any}}}}]

["/:id"
 {:get {:summary (sd/sum_adm "Get user by id")
        :description "Get a user by id. Returns 404, if no such users exists."
        :handler handle_get-user
        :middleware [wrap-authorize-admin!]
        :swagger {:produces "application/json"}
        :coercion reitit.coercion.schema/coercion
        :content-type "application/json"
        :parameters {:path {:id s/Str}}

        :responses {200 {:body s/Any} ; TODO coercion
                    404 {:body s/Any}}}

  :delete {:summary (sd/sum_adm "Delete user by id")
           :description "Delete a user by id. Returns 404, if no such user exists."
           :handler handle_delete-user
           :middleware [wrap-authorize-admin!]
           :swagger {:produces "application/json"}
           :coercion reitit.coercion.schema/coercion
           :content-type "application/json"
           :parameters {:path {:id s/Str}}

           :responses {204 {:body s/Any} ; TODO coercion
                       404 {:body s/Any}}} ; TODO coercion

  :put {:middleware [wrap-authorize-admin!]
        :summary (sd/sum_adm "Update user with id")
        :description "Patch a user with id. Returns 404, if no such user exists."
        :swagger {:consumes "application/json"
                  :produces "application/json"}
        :coercion reitit.coercion.schema/coercion
        :content-type "application/json"
        :accept "application/json"
        :parameters {:path {:id s/Str}
                     :body schema_update_user}
        :handler handle_patch-user
        :responses {204 {:body schema_export_user}
                    404 {:body s/Any}}}}] ; TODO coercion
   ])

;TODO Frage: wer kann die Liste der Benutzer sehen?
; TODO users: update own user
(def user-routes
  ["/users"
   ["/"
    {:get {:summary (sd/sum_usr "Get list of users ids.")
           :description "Get list of users ids."
           :handler index
           :middleware [authorization/wrap-authorized-user]
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           ; TODO query more params
           :parameters {:query {(s/optional-key :page) s/Int
                                (s/optional-key :count) s/Int}}
           
           
           
           :responses {200 {:body {:users [{:id s/Uuid}]}}}}}]
   ["/:id"

    {:get {:summary (sd/sum_usr "Get user by id")
           :description "Get a user by id. Returns 404, if no such users exists."
           :handler handle_get-user
           :middleware [authorization/wrap-authorized-user]
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Any}}
           :responses {200 {:body s/Any} ; TODO coercion
                       404 {:body s/Any}}}
     }]])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
