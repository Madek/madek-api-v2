(ns madek.api.resources.delegations-users
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.shared :as sd]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [error info]]))

(def res-req-name :delegation_user)
(def res-table-name "delegations_users")
(def res-col-name :delegation_id)

(defn handle_list-delegations_users
  [req]
  (let [delegation_id (-> req :parameters :query :delegation_id)
        user_id (-> req :parameters :query :user_id)
        col-sel (if (true? (-> req :parameters :query :full-data))
                  (sql/select :*)
                  (sql/select :user_id))
        base-query (-> col-sel (sql/from :delegations_users))
        query (cond-> base-query
                delegation_id (sql/where [:= :delegation_id delegation_id])
                user_id (sql/where [:= :user_id user_id]))
        db-result (jdbc/execute! (:tx req) (sql-format query))]

    ;(->> db-result (map :id) set)
    (info "handle_list-delegations_user" "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_list-delegations_users-by-user
  [req]
  (let [;full-data (true? (-> req :parameters :query :full-data))
        user-id (-> req :authenticated-entity :id)
        db-result (sd/query-eq-find-all :delegations_users :user_id user-id (:tx req))
        id-set (map :delegation_id db-result)]
    (info "handle_list-delegations_user" "\nresult\n" db-result "\nid-set\n" id-set)
    (sd/response_ok {:delegation_ids id-set})
    ;(if full-data (sd/response_ok db-result) (sd/response_ok {:delegation_ids id-set})) 
    ))

(defn handle_get-delegations_user
  [req]
  (let [favorite_collection (-> req res-req-name)]
    ;(info "handle_get-favorite_collection" favorite_collection)
    ; TODO hide some fields
    (sd/response_ok favorite_collection)))

(defn handle_create-delegations_user
  [req]
  (let [user (or (-> req :user) (-> req :authenticated-entity))
        delegation (-> req :delegation)
        data {:user_id (:id user) :delegation_id (:id delegation)}
        sql-query (-> (sql/insert-into :delegations_users)
                      (sql/values [data])
                      (sql/returning :*)
                      sql-format)
        ins-res (jdbc/execute-one! (:tx req) sql-query)]
    (if ins-res
      (sd/response_ok ins-res)
      (sd/response_failed "Could not create delegations_user." 406))))

(defn handle_delete-delegations_user
  [req]
  (let [delegations_user (-> req res-req-name)
        user-id (:user_id delegations_user)
        delegation-id (res-col-name delegations_user)
        sql-query (-> (sql/delete-from :delegations_users)
                      (sql/where [:= :user_id user-id] [:= :delegation_id delegation-id])
                      (sql/returning :*)
                      sql-format)
        res (jdbc/execute-one! (:tx req) sql-query)]
    (if res
      (sd/response_ok delegations_user)
      (error "Failed delete delegations_user "
             "user-id: " user-id "delegation-id: " delegation-id))))

(defn wwrap-find-delegations_user [send404]
  (fn [handler]
    (fn [request]
      (sd/req-find-data2
       request handler
       :user_id :delegation_id
       :delegations_users
       :user_id :delegation_id
       res-req-name
       send404))))

(defn wwrap-find-delegations_user-by-auth [send404]
  (fn [handler]
    (fn [request]
      (let [user-id (-> request :authenticated-entity :id str)
            del-id (-> request :parameters :path :delegation_id str)]
        (info "uid\n" user-id "del-id\n" del-id)
        (sd/req-find-data-search2
         request handler
         user-id del-id
         :delegations_users
         :user_id :delegation_id
         res-req-name
         send404)))))

(defn wwrap-find-user [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :users :id
                                    :user true))))

(defn wwrap-find-delegation [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :delegations
                                    :id :delegation true))))

;(def schema_delegations_users_export
;  {:user_id s/Uuid
;   :delegation_id s/Uuid
;   :updated_at s/Any
;   :created_at s/Any})

; TODO response coercion
; TODO docu
; TODO tests
; user self edit favorites 
(def query-routes
  ["/delegation/users"
   {:swagger {:tags ["delegation/users"]}}
   {:get
    {:summary (sd/sum_adm "Query delegation users.")
     :handler handle_list-delegations_users-by-user
     :swagger {:produces "application/json"}
     :coercion reitit.coercion.schema/coercion
     :parameters {:query {(s/optional-key :delegation_id) s/Uuid
                          (s/optional-key :user_id) s/Uuid}}
     :responses {200 {:body {:delegation_ids [s/Uuid]}}}}}])

;; TODO: no usage??
(def user-routes
  ["/delegation/:delegation_id/user"
   {:swagger {:tags ["delegation/users"]}}
   ["/"
    {:post {:summary (sd/sum_cnv "Create delegations_user for authed user and media-entry.")
            :handler handle_create-delegations_user
            :middleware [(wwrap-find-delegation :delegation_id)
                         (wwrap-find-delegations_user-by-auth false)]
            :swagger {:produces "application/json"}
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:delegation_id s/Uuid}}
            :responses {200 {:body (get-schema :delegations-users.schema_delegations_users_export)}
                        404 {:body s/Any}
                        406 {:body s/Any}}}

     :get {:summary (sd/sum_cnv "Get delegations_user for authed user and media-entry.")
           :handler handle_get-delegations_user
           :middleware [(wwrap-find-delegation :delegation_id)
                        (wwrap-find-delegations_user-by-auth true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:delegation_id s/Uuid}}
           :responses {200 {:body (get-schema :delegations-users.schema_delegations_users_export)}
                       404 {:body s/Any}
                       406 {:body s/Any}}}

     :delete {:summary (sd/sum_cnv "Delete delegations_user for authed user and media-entry.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-delegations_user
              :middleware [(wwrap-find-delegation :delegation_id)
                           (wwrap-find-delegations_user-by-auth true)]
              :parameters {:path {:delegation_id s/Uuid}}
              :responses {200 {:body (get-schema :delegations-users.schema_delegations_users_export)}
                          404 {:body s/Any}
                          406 {:body s/Any}}}}]])

(def admin-routes
  [["/delegation/users"
    {:swagger {:tags ["admin/delegation/users"] :security [{"auth" []}]}}
    ["/"
     {:get
      {:summary (sd/sum_adm "Query delegations_users.")
       :handler handle_list-delegations_users
       :coercion reitit.coercion.schema/coercion
       :parameters {:query {(s/optional-key :user_id) s/Uuid
                            (s/optional-key :delegation_id) s/Uuid
                            (s/optional-key :full-data) s/Bool}}}}]
    ["/:delegation_id/:user_id"
     {:post
      {:summary (sd/sum_adm "Create delegations_user for user and delegation.")
       :handler handle_create-delegations_user
       :middleware [(wwrap-find-user :user_id)
                    (wwrap-find-delegation :delegation_id)
                    (wwrap-find-delegations_user false)]
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:user_id s/Uuid
                           :delegation_id s/Uuid}}}

      :get
      {:summary (sd/sum_adm "Get delegations_user for user and delegation.")
       :handler handle_get-delegations_user
       :middleware [(wwrap-find-delegations_user true)]
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:user_id s/Uuid
                           :delegation_id s/Uuid}}}

      :delete
      {:summary (sd/sum_adm "Delete delegations_user for user and delegation.")
       :coercion reitit.coercion.schema/coercion
       :handler handle_delete-delegations_user
       :middleware [(wwrap-find-delegations_user true)]
       :parameters {:path {:user_id s/Uuid
                           :delegation_id s/Uuid}}}}]]])
