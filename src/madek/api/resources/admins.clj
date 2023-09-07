(ns madek.api.resources.admins
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [logbug.catcher :as catcher]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.auth :refer [wrap-authorize-admin!]]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [reitit.coercion.schema]
            [schema.core :as s]))




(defn handle_list-admin
  [req]
  (let [qd (if (true? (-> req :parameters :query :full_data))
             :admins.*
             :admins.id)
        db-result (sd/query-find-all :admins qd)]
    ;(logging/info "handle_list-admin" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok {:admins db-result})
    )
  )

(defn handle_get-admin
  [req]
  (let [admin (-> req :admin)]
    ;(logging/info "handle_get-admin" admin)
    (sd/response_ok admin)))

(defn handle_create-admin
  [req]
  (catcher/with-logging {}
    (if-let [admin (-> req :admin)]
      ; already has admin
      (sd/response_ok admin)
      ; create admin entry
      (let [user (-> req :user)
            id (:id user)
            data {:user_id id}
            ins-res (jdbc/insert! (get-ds) :admins data)]
        (sd/logwrite req (str "handle_create-admin:" " user-id: " id " result: " ins-res))
        (if-let [result (first ins-res)]
          (sd/response_ok result)
          (sd/response_failed "Could not create admin." 406))))))


(defn handle_delete-admin
  [req]
  (catcher/with-logging {}
    (let [admin (-> req :admin)
          admin-id (-> req :admin :id)
          del-result (jdbc/delete! (get-ds) :admins ["id = ?" admin-id])]
      (sd/logwrite req (str "handle_delete-admin: " " data:\n" admin "\nresult: " del-result))
      (if (= 1 (first del-result))
        (sd/response_ok admin)
        (sd/response_failed "Could not delete admin." 406)))))

(defn wwrap-find-admin [param colname send404]
  (fn [handler] 
    (fn [request] (sd/req-find-data
                   request handler param
                   :admins colname :admin send404))))

(defn wwrap-find-user [param]
  (fn [handler]
    (fn [request] (sd/req-find-data
                   request handler param
                   :users :id :user true))))

(def schema_export-admin
  {
   :id s/Uuid
   :user_id s/Uuid
   :updated_at s/Any
   :created_at s/Any
  })

; TODO response coercion
; TODO docu
; TODO tests
(def ring-routes
  [["/admins"
    {:get
     {:summary  (sd/sum_adm "List admin users.")
      :handler handle_list-admin
      :middleware [wrap-authorize-admin!]
      :coercion reitit.coercion.schema/coercion
      :parameters {:query {(s/optional-key :full_data) s/Bool}}
      :responses {200 {:body {:admins [schema_export-admin]}}}}}]
    ; edit admin
   ["/admins/:id"
    {:get
     {:summary (sd/sum_adm "Get admin by id.")
      :handler handle_get-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :id :id true)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:body schema_export-admin}
                  404 {:body s/Any}}}

     :delete
     {:summary (sd/sum_adm "Delete admin by id.")
      :coercion reitit.coercion.schema/coercion
      :handler handle_delete-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :id :id true)]
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:body schema_export-admin}
                  404 {:body s/Any}
                  406 {:body s/Any}}}}]

   ; access via user
   ["/admins/:user_id/user"
    {:post
     {:summary (sd/sum_adm "Create admin for user with id.")
      :handler handle_create-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-user :user_id)
                   (wwrap-find-admin :user_id :user_id false)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:user_id s/Uuid}}
      :responses {200 {:body schema_export-admin}
                  404 {:body s/Any}
                  406 {:body s/Any}}}

     :get
     {:summary (sd/sum_adm "Get admin for user.")
      :handler handle_get-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :user_id :user_id true)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:user_id s/Uuid}}
      :responses {200 {:body schema_export-admin}
                  404 {:body s/Any}
                  406 {:body s/Any}}}

     :delete
     {:summary (sd/sum_adm "Delete admin for user.")
      :coercion reitit.coercion.schema/coercion
      :handler handle_delete-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :user_id :user_id true)]
      :parameters {:path {:user_id s/Uuid}}
      :responses {200 {:body schema_export-admin}
                  404 {:body s/Any}
                  406 {:body s/Any}}}}]])