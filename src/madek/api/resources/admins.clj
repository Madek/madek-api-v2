(ns madek.api.resources.admins
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [normalize-fields]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.spec]
   [schema.core :as s]))

(defn handle_list-admin
  [req]
  (let [fields (normalize-fields req :admins)
        qd (if (empty? fields) [:admins.id] fields)
        db-result (dbh/query-find-all :admins qd (:tx req))]
    (sd/response_ok {:admins db-result})))

(defn handle_get-admin
  [req]
  (let [admin (-> req :admin)]
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
            sql (-> (sql/insert-into :admins)
                    (sql/values [data])
                    (sql/returning :*)
                    sql-format)
            ins-res (jdbc/execute! (:tx req) sql)]
        (sd/logwrite req (str "handle_create-admin:" " user-id: " id " result: " ins-res))
        (if-let [result (first ins-res)]
          (sd/response_ok result)
          (sd/response_failed "Could not create admin." 406))))))

(defn handle_delete-admin
  [req]
  (catcher/with-logging {}
    (let [admin (-> req :admin)
          admin-id (:id admin)
          sql (-> (sql/delete-from :admins)
                  (sql/where [:= :id admin-id])
                  (sql/returning :*)
                  sql-format)
          del-result (jdbc/execute-one! (:tx req) sql)]
      (if del-result
        (sd/response_ok del-result)
        (sd/response_failed "Could not delete admin." 406)))))

;### wrappers #################################################################

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

;### swagger io schema ########################################################

(def schema_export-admin
  {(s/optional-key :id) s/Uuid
   (s/optional-key :user_id) s/Uuid
   (s/optional-key :updated_at) s/Any
   (s/optional-key :created_at) s/Any})

;### wrappers #################################################################

; TODO docu
(def ring-routes
  ["/"
   {:openapi {:tags ["admin/admins/"] :security ADMIN_AUTH_METHODS}}
   ["admins"
    {:get
     {:summary (sd/sum_adm "List admin users.")
      :handler handle_list-admin
      :middleware [wrap-authorize-admin!]
      :coercion reitit.coercion.schema/coercion
      :parameters {:query {(s/optional-key :fields) [(s/enum :id :user_id :updated_at :created_at)]}}
      :responses {200 {:description "Returns the list of admins."
                       :body {:admins [schema_export-admin]}}}}}]
   ; edit admin
   ["admins/:id"
    {:get
     {:summary (sd/sum_adm "Get admin by id.")
      :handler handle_get-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :id :id true)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:description "Returns the admin."
                       :body schema_export-admin}
                  404 {:description "Admin not found."
                       :body s/Any}}}

     :delete
     {:summary (sd/sum_adm "Delete admin by id.")
      :coercion reitit.coercion.schema/coercion
      :handler handle_delete-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :id :id true)]
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:description "Returns the deleted admin."
                       :body schema_export-admin}
                  404 {:description "Admin not found. HERE!!"
                       :body s/Any}
                  406 {:description "Could not delete admin."
                       :body s/Any}}}}]

   ; access via user
   ["admins/:user_id/user"
    {:post
     {:summary (sd/sum_adm "Create admin for user with id.")
      :handler handle_create-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-user :user_id)
                   (wwrap-find-admin :user_id :user_id false)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:user_id s/Uuid}}
      :responses {200 {:description "Returns the created admin."
                       :body schema_export-admin}
                  404 {:description "User not found."
                       :body s/Any}
                  406 {:description "Could not create admin."
                       :body s/Any}}}

     :get
     {:summary (sd/sum_adm "Get admin for user.")
      :handler handle_get-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :user_id :user_id true)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:user_id s/Uuid}}
      :responses {200 {:description "Returns the admin."
                       :body schema_export-admin}
                  404 {:description "Admin not found."
                       :body s/Any}
                  406 {:description "Could not get admin."
                       :body s/Any}}}

     :delete
     {:summary (sd/sum_adm "Delete admin for user.")
      :coercion reitit.coercion.schema/coercion
      :handler handle_delete-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :user_id :user_id true)]
      :parameters {:path {:user_id s/Uuid}}
      :responses {200 {:description "Returns the deleted admin."
                       :body schema_export-admin}
                  404 {:description "Admin not found."
                       :body s/Any}
                  406 {:description "Could not delete admin."
                       :body s/Any}}}}]])
