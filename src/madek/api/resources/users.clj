(ns madek.api.resources.users
  (:require
    [clj-uuid]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.constants :refer [presence]]
    [madek.api.pagination :as pagination]
    [madek.api.utils.auth :refer [wrap-authorize-admin!]]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    [ring.util.codec :refer [url-decode]]
    [schema.core :as s]
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

(defn create-user [request]
  (let [params (as-> (:body request) params
                 (or params {})
                 (assoc params :id (or (:id params) (clj-uuid/v4))))]
    ;(logging/info "create-user" "\nparams\n" params)
    (when-let [dbresult (->> (jdbc/insert! (rdbms/get-ds) :users params) first)]
      ;(logging/info "create-user" "\nparams\n" params "\ndbresult\n" dbresult) 
      (let [result (dissoc dbresult :previous_id :searchable)] ; TODO hide password in result
        {:body result :status 201}))))

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
    {:status 200 :body user}
    {:status 404 :body "No such user found"}))


;### delete user ##############################################################

(defn delete-user [id]
  (if (= 1 (first (jdbc/delete! (rdbms/get-ds)
                                :users (jdbc-id-where-clause id))))
    {:status 204}
    {:status 404}))


;### patch user ##############################################################

(defn patch-user [{body :body {id :id} :params}]
  ;((logging/info "patch-user " "\nbody:\n" body "\nid:\n" id)
   (if (= 1 (first (jdbc/update! (rdbms/get-ds) :users body (jdbc-id-where-clause id))))
    {:body (find-user id)}
    {:status 404}))


;### index ####################################################################

(defn build-index-query [{query-params :query-params}]
  (-> (sql/select :id)
      (sql/from :users)
      (sql/merge-where [:= :is_deactivated false])
      (sql/order-by [:id :asc])
      (pagination/add-offset-for-honeysql query-params)
      sql/format))

(defn index [request]
  {:body
   {:users (jdbc/query (rdbms/get-ds) (build-index-query request))}})


;### routes ###################################################################

(def routes
  (-> (cpj/routes
        (cpj/GET "/users/" [] index)
        (cpj/POST "/users/" [] create-user)
        (cpj/GET "/users/:id" [id] (get-user id))
        (cpj/DELETE "/users/:id" [id] (delete-user id))
        (cpj/PATCH "/users/:id" [] patch-user))
      wrap-authorize-admin!))


(def schema_update_user 
  {
   (s/optional-key :id) s/Uuid
   (s/optional-key :email) s/Str
   (s/optional-key :institutional_id) s/Str
   (s/optional-key :login) s/Str
   (s/optional-key :person_id) s/Uuid
   })

(def schema_create_user_result
  {:id s/Uuid
   :email (s/maybe s/Str)
   :institutional_id (s/maybe s/Str)
   :login (s/maybe s/Str)
   :person_id s/Uuid
   :created_at s/Any ; TODO Inst
   :updated_at s/Any ; TODO Inst
   :settings s/Any ; TODO
   :accepted_usage_terms_id (s/maybe s/Any) ; TODO
   :is_deactivated s/Bool
   :notes (s/maybe s/Any) ; TODO
   :password_digest (s/maybe s/Any) ; TODO
   :last_signed_in_at (s/maybe s/Any) ; TODO
   :autocomplete s/Str
   })

(def schema_export_user
  {
   :id s/Uuid
   :email s/Str
   :institutional_id s/Str
   :login s/Str
   :person_id s/Uuid
   :created_at s/Inst
   :updated_at s/Inst})

(defn handle_get-user [req]
  (let [id (-> req :parameters :path :id)]
    (logging/info "handle_get-user" "\nid\n" id)
    (get-user id)))

(defn handle_delete-user [req]
  (delete-user (-> req :parameters :path :id)))

(defn handle_patch-user [req]
  (let [id (-> req :parameters :path :id)
        body (-> req :parameters :body)
        ireq (assoc-in req [:params :id] id)
        breq (assoc-in ireq [:body] body)]
    (patch-user breq)))

(defn handle_create-user [req]
  (let [body (-> req :parameters :body)
        breq (assoc-in req [:body] body)]
    (create-user breq)
    ))
;### Debug ####################################################################
;(debug/debug-ns *ns*)
