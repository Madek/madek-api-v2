(ns madek.api.resources.vocabularies
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.resources.vocabularies.delete :as delete]
   [madek.api.resources.vocabularies.get :as get]
   [madek.api.resources.vocabularies.post :as post]
   [madek.api.resources.vocabularies.put :as put]
   [madek.api.utils.helper :refer [cast-to-hstore convert-map-if-exist]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [taoensso.timbre :refer [info]]))

; TODO logwrite

(defn handle_create-vocab [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            sql-query (-> (sql/insert-into :vocabularies)
                          (sql/values [(convert-map-if-exist (cast-to-hstore data))])
                          (sql/returning :*)
                          sql-format)
            ins-res (jdbc/execute-one! (:tx req) sql-query)
            ins-res (sd/transform_ml_map ins-res)]

        (if ins-res
          (sd/response_ok ins-res)
          (sd/response_failed "Could not create vocabulary." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-vocab [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :path-params :id)
            dwid (assoc data :id id)
            tx (:tx req)
            dwid (convert-map-if-exist (cast-to-hstore dwid))
            old-data (dbh/query-eq-find-one :vocabularies :id id tx)]

        (if old-data
          (let [is_admin_endpoint (str/includes? (-> req :uri) "/admin/")
                cols (if is_admin_endpoint [:*] [:id :position :labels :descriptions :admin_comment])
                sql-query (-> (sql/update :vocabularies)
                              (sql/set dwid)
                              (sql/where [:= :id id]))
                sql-query (apply sql/returning sql-query cols)
                sql-query (-> sql-query
                              sql-format)
                upd-res (jdbc/execute-one! tx sql-query)
                upd-res (sd/transform_ml_map upd-res)]

            (if upd-res
              (do
                (info "handle_update-vocab" "\nid: " id "\nnew-data:\n" upd-res)
                (sd/response_ok upd-res))
              (sd/response_failed "Could not update vocabulary." 406)))
          (sd/response_not_found "No such vocabulary."))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-vocab [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)
            tx (:tx req)]
        (if-let [old-data (dbh/query-eq-find-one :vocabularies :id id tx)]
          (let [sql-query (-> (sql/delete-from :vocabularies)
                              (sql/where [:= :id id])
                              (sql/returning :*)
                              sql-format)
                db-result (jdbc/execute-one! tx sql-query)]

            (if db-result
              (sd/response_ok (sd/transform_ml_map old-data))
              (sd/response_failed "Could not delete vocabulary." 406)))
          (sd/response_not_found "No such vocabulary."))))
    (catch Exception ex (sd/parsed_response_exception ex))))

; TODO vocab permission
(def admin-routes
  ["/vocabularies"
   {:swagger {:tags ["admin/vocabularies"]}}
   ["/"
    {:get get/admin.vocabularies

     :post post/admin.vocabularies}]

   ["/:id"
    {:get get/admin.vocabularies.id

     :put put/admin.vocabularies.id

     :delete delete/admin.vocabularies.id}]

   ["/:id/perms"
    ["/"
     {:get get/admin.vocabularies.id.perms

      :put put/admin.vocabularies.id.perms}]

    ["/users"
     {:get get/admin.vocabularies.users}]

    ["/user/:user_id"
     {:get get/admin.vocabularies.users.user_id

      :post post/admin.vocabularies.users.user_id

      :put put/admin.vocabularies.users.user_id

      :delete delete/admin.vocabularies.user.user_id}]

    ["/groups"
     {:get get/admin.vocabularies.groups}]

    ["/group/:group_id"
     {:get get/admin.vocabularies.group.group_id

      :post post/admin.vocabularies.group.group_id

      :put put/admin.vocabularies.group.group_id

      :delete delete/admin.vocabularies.group.group_id}]]])

(def user-routes
  ["/vocabularies"
   {:swagger {:tags ["vocabulary"]}}
   ["/" {:get get/user.vocabularies}]

   ["/:id" {:get get/user.vocabularies.id}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
