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
    
    [logbug.catcher :as catcher]))


(defn sql-select
  ([] (sql-select {}))
  ([sql-map]
   (sql/select sql-map :*
               ;:users.id :users.email :users.institutional_id :users.login
               ;:users.created_at :users.updated_at
               ;:users.person_id
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

;### index ####################################################################

(defn build-index-query [query-params]
  
  (-> (if (true? (:full_data query-params))
        (sql/select :*)
        (sql/select :id))
      (sql/from :users)
      (sql/merge-where [:= :is_deactivated false])
      (sd/build-query-param query-params :person_id)
      (sd/build-query-param query-params :institution)
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

;### swagger io schema #############################################################

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
   :last_signed_in_at (s/maybe s/Any) ; TODO
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
   (s/optional-key :updated_at) s/Any})

(def schema_query_user
  {(s/optional-key :full_data) s/Bool
   (s/optional-key :page) s/Int
   (s/optional-key :count) s/Int

   (s/optional-key :person_id) s/Uuid
   (s/optional-key :institution) s/Str
   (s/optional-key :institutional_id) s/Uuid
   (s/optional-key :accepted_usage_terms_id) s/Uuid
   (s/optional-key :is_deactivated) s/Bool

   (s/optional-key :email) s/Str
   (s/optional-key :login) s/Str
   (s/optional-key :notes) s/Str
   (s/optional-key :autocomplete) s/Str
   (s/optional-key :searchable) s/Str})

(def schema_usr_query_user
  {(s/optional-key :full_data) s/Bool
   (s/optional-key :page) s/Int
   (s/optional-key :count) s/Int

   (s/optional-key :person_id) s/Uuid
   (s/optional-key :institution) s/Str
   (s/optional-key :institutional_id) s/Uuid
   
   (s/optional-key :email) s/Str
   (s/optional-key :notes) s/Str
   (s/optional-key :autocomplete) s/Str
   (s/optional-key :searchable) s/Str})

;### handlers #############################################################

(defn handle_get-user [req]
  (let [user (-> req :user)]
    (sd/response_ok user)))

(defn handle_delete-user [req]
  (try
    (catcher/with-logging {}
      (let [old-data (-> req :user)
            id (-> req :parameters :path :id)
            del-result (jdbc/delete! (rdbms/get-ds)
                                     :users (jdbc-id-where-clause id))]
        (if (= 1 (first del-result))
          (sd/response_ok old-data)
          (sd/response_failed "Could not delete user." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_patch-user [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)
            body (-> req :parameters :body)
            upd-result (jdbc/update! (rdbms/get-ds)
                                     :users body (jdbc-id-where-clause id))]

        (if (= 1 (first upd-result))
          (sd/response_ok (find-user id) 200)
          (sd/response_failed "Could not update user." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_create-user [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            ins-result (jdbc/insert! (rdbms/get-ds) :users data)]
        (if-let [result (first ins-result)]
          (sd/response_ok (dissoc result :password_digest) 201)
          (sd/response_failed "Could not create user." 406))))
    (catch Exception ex (sd/response_exception ex))))


(defn wwrap-find-user [param]
  (fn [handler]
    (fn [request]
      (let [search (-> request :parameters :path param)]
        (if-let [result-db (find-user search)]
          (handler (assoc request :user result-db))
          (sd/response_not_found "No such user."))))))

;### routes ###################################################################

(def admin-routes
  ["/users"
   ["/" 
    {:get {:summary (sd/sum_adm "Get list of users ids.")
           :description "Get list of users ids."
           :swagger {:produces "application/json"}
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
        :middleware [wrap-authorize-admin!
                     (wwrap-find-user :id)]
        :swagger {:produces "application/json"}
        :coercion reitit.coercion.schema/coercion
        :content-type "application/json"
        :parameters {:path {:id s/Str}}

        :responses {200 {:body schema_export_user}
                    404 {:body s/Any}}}

  :delete {:summary (sd/sum_adm "Delete user by id")
           :description "Delete a user by id. Returns 404, if no such user exists."
           :handler handle_delete-user
           :middleware [wrap-authorize-admin!
                        (wwrap-find-user :id)]
           :swagger {:produces "application/json"}
           :coercion reitit.coercion.schema/coercion
           :content-type "application/json"
           :parameters {:path {:id s/Str}}

           :responses {200 {:body s/Any} ; TODO coercion
                       404 {:body s/Any}}} ; TODO coercion

  :put {:summary (sd/sum_adm "Update user with id")
        :description "Patch a user with id. Returns 404, if no such user exists."
        :swagger {:consumes "application/json"
                  :produces "application/json"}
        :coercion reitit.coercion.schema/coercion
        :content-type "application/json"
        :accept "application/json"
        :parameters {:path {:id s/Str}
                     :body schema_update_user}
        :handler handle_patch-user
        :middleware [wrap-authorize-admin!
                     (wwrap-find-user :id)]
        :responses {200 {:body schema_export_user}
                    404 {:body s/Any}}}}]
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
           ; TODO query reduced user query schema
           :parameters {:query schema_usr_query_user}
           
           
           
           :responses {200 {:body {:users [{:id s/Uuid}]}}}}}]
   ["/:id"

    {:get {:summary (sd/sum_usr "Get user by id")
           :description "Get a user by id. Returns 404, if no such users exists."
           :handler handle_get-user
           :middleware [authorization/wrap-authorized-user
                        (wwrap-find-user :id)]
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Any}}
           :responses {200 {:body s/Any} ; TODO coercion
                       404 {:body s/Any}}}
     }]])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
