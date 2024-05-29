(ns madek.api.resources.admins
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

(defn handle_list-admin
  [req]
  (let [qd (if (true? (-> req :parameters :query :full_data))
             :admins.*
             :admins.id)
        db-result (sd/query-find-all :admins qd (:tx req))]
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

;;### swagger io schema ########################################################
;
;(def :schema_export-admin
;  {:id s/Uuid
;   (s/optional-key :user_id) s/Uuid
;   (s/optional-key :updated_at) s/Any
;   (s/optional-key :created_at) s/Any})

;### wrappers #################################################################

; TODO docu
(def ring-routes
  ["/admins"
   {:swagger {:tags ["admin/admins"] :security [{"auth" []}]}}
   ["/"
    {:get
     {:summary (sd/sum_adm "List admin users.")
      :handler handle_list-admin
      :middleware [wrap-authorize-admin!]
      :coercion reitit.coercion.schema/coercion
      :parameters {:query {(s/optional-key :full_data) s/Bool}}
      :responses {200 {:body {:admins [(get-schema :admins.schema_export-admin)]}}}}}]
   ; edit admin
   ["/:id"
    {:get
     {:summary (sd/sum_adm "Get admin by id.")
      :handler handle_get-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :id :id true)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:body (get-schema :admins.schema_export-admin)}
                  404 {:body s/Any}}}

     :delete
     {:summary (sd/sum_adm "Delete admin by id.")
      :coercion reitit.coercion.schema/coercion
      :handler handle_delete-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :id :id true)]
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:body (get-schema :admins.schema_export-admin)}
                  404 {:body s/Any}
                  406 {:body s/Any}}}}]

   ; access via user
   ["/:user_id/user"
    {:post
     {:summary (sd/sum_adm "Create admin for user with id.")
      :handler handle_create-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-user :user_id)
                   (wwrap-find-admin :user_id :user_id false)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:user_id s/Uuid}}
      :responses {200 {:body (get-schema :admins.schema_export-admin)}
                  404 {:body s/Any}
                  406 {:body s/Any}}}

     :get
     {:summary (sd/sum_adm "Get admin for user.")
      :handler handle_get-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :user_id :user_id true)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:user_id s/Uuid}}
      :responses {200 {:body (get-schema :admins.schema_export-admin)}
                  404 {:body s/Any}
                  406 {:body s/Any}}}

     :delete
     {:summary (sd/sum_adm "Delete admin for user.")
      :coercion reitit.coercion.schema/coercion
      :handler handle_delete-admin
      :middleware [wrap-authorize-admin!
                   (wwrap-find-admin :user_id :user_id true)]
      :parameters {:path {:user_id s/Uuid}}
      :responses {200 {:body (get-schema :admins.schema_export-admin)}
                  404 {:body s/Any}
                  406 {:body s/Any}}}}]])