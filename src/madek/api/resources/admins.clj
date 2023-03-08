(ns madek.api.resources.admins
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [madek.api.utils.sql :as sql]
            [reitit.coercion.schema]
            [schema.core :as s]))




(defn handle_list-admin
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data ))
        qd (if (true? full-data) :admins.* :admins.id)
        db-result (sd/query-find-all :admins qd)]
    ;(->> db-result (map :id) set)
    (logging/info "handle_list-admin" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)
    )
  )

(defn handle_get-admin
  [req]
  (let [admin (-> req :admin)]
    (logging/info "handle_get-admin" admin)
    ; TODO hide some fields
    (sd/response_ok admin)))

(defn handle_create-admin
  [req]
  (let [user (-> req :user)
        id (:id user)
        data {:user_id id}]
   (if-let [admin (-> req :admin)]
      ; already has admin
      (sd/response_ok admin)
      ; create admin entry
      (if-let [ins_res (jdbc/insert! (rdbms/get-ds) :admins data)]
        ; TODO clean result
        (sd/response_ok ins_res)
        (sd/response_failed "Could not create admin." 406))
     )
  ))


(defn handle_delete-admin
  [req]
  (let [admin (-> req :admin)
        admin-id (-> req :admin :id)] 
    (if (= 1 (first (jdbc/delete! (rdbms/get-ds) :admins ["id = ?" admin-id])))
      (sd/response_ok admin)
      (logging/error "Failed delete admin " admin-id))
  ))

(defn wwrap-find-admin [param colname send404]
  (fn [handler] 
    (fn [request] (sd/req-find-data request handler param "admins" colname :admin send404))))

(defn wwrap-find-user [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param "users" "id" :user true))))

; TODO response coercion
; TODO docu
; TODO tests
(def ring-routes
  
  [
   ["/admins"
    ["/"
     {:post {:summary (sd/sum_adm_todo "Create admin user.")
             :handler (constantly sd/no_impl)}
    ; admin list / query
      :get {:summary  (sd/sum_adm "List admin users.")
            :handler handle_list-admin
            :coercion reitit.coercion.schema/coercion
            :parameters {:query {(s/optional-key :full-data) s/Bool}}
            }}]
    ; edit admin
    ["/:id" {:post {:summary (sd/sum_adm "Create admin for user with id.")
                    :handler handle_create-admin
                    :middleware [(wwrap-find-user :id)
                                 (wwrap-find-admin :id "user_id" false)]
                    :coercion reitit.coercion.schema/coercion
                    :parameters {:path {:id s/Uuid}}}

             :get {:summary (sd/sum_adm "Get admin by id.")
                   :handler handle_get-admin
                   :middleware [(wwrap-find-admin :id "id" true)]
                   :coercion reitit.coercion.schema/coercion
                   :parameters {:path {:id s/Uuid}}}

             :delete {:summary (sd/sum_adm "Delete admin by id.")
                      :coercion reitit.coercion.schema/coercion
                      :handler handle_delete-admin
                      :middleware [(wwrap-find-admin :id "id" true)]
                      :parameters {:path {:id s/Uuid}}}}]
    ; convenience to access by user id
    ["/by-user/:user_id/"
     {:post {:summary (sd/sum_adm "Create admin for user with id.")
             :handler handle_create-admin
             :middleware [(wwrap-find-user :user_id)
                          (wwrap-find-admin :user_id "user_id" false)]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:id s/Uuid}}}

      :get {:summary (sd/sum_cnv_adm "Get admin for user.")
                                   ;:handler handle_get-admin-by-user
            :handler handle_get-admin
            :middleware [(wwrap-find-admin :user_id "user_id" true)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:user_id s/Uuid}}}

      :delete {:summary (sd/sum_cnv_adm "Delete admin for user.")
               :coercion reitit.coercion.schema/coercion
               :handler handle_delete-admin
               :middleware [(wwrap-find-admin :user_id "user_id" true)]
               :parameters {:path {:user_id s/Uuid}}}}]]
   ; convenience to access via user
   ["/user" 
    ["/:user_id/admin" 
     {:post {:summary (sd/sum_cnv_adm "Create admin for user with id.")
             :handler handle_create-admin
             :middleware [(wwrap-find-user :user_id)
                          (wwrap-find-admin :user_id "user_id" false)]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:user_id s/Uuid}}}

      :get {:summary (sd/sum_cnv_adm "Get admin for user.")
                                   ;:handler handle_get-admin-by-user
            :handler handle_get-admin
            :middleware [(wwrap-find-admin :user_id "user_id" true)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:user_id s/Uuid}}}

      :delete {:summary (sd/sum_cnv_adm "Delete admin for user.")
               :coercion reitit.coercion.schema/coercion
               :handler handle_delete-admin
               :middleware [(wwrap-find-admin :user_id "user_id" true)]
               :parameters {:path {:user_id s/Uuid}}}}]

    ]
   ])