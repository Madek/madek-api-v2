(ns madek.api.resources.contexts
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [cast-to-hstore t]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [error]]))

(defn- context_transform_ml [context]
  (assoc context
         :labels (sd/transform_ml (:labels context))
         :descriptions (sd/transform_ml (:descriptions context))))

(defn handle_adm-list-contexts
  [req]
  (let [db-query (-> (sql/select :*)
                     (sql/from :contexts)
                     sql-format)
        db-result (jdbc/execute! (:tx req) db-query)
        result (map context_transform_ml db-result)]
    ;(info "handle_adm-list-context" "\nquery\n" db-query "\nresult\n" result)
    (sd/response_ok result)))

(defn handle_usr-list-contexts
  [req]
  (let [db-query (-> (sql/select :id :labels :descriptions)
                     (sql/from :contexts)
                     sql-format)
        db-result (jdbc/execute! (:tx req) db-query)
        result (map context_transform_ml db-result)]
    ;(info "handle_usr-list-context" "\nquery\n" db-query "\nresult\n" result)
    (sd/response_ok result)))

(defn handle_adm-get-context
  [req]
  (let [context (-> req :context context_transform_ml)]
    ;(info "handle_adm-get-context" context)
    (sd/response_ok context)))

(defn handle_usr-get-context
  [req]
  (let [context (-> req :context context_transform_ml sd/remove-internal-keys)]
    ;(info "handle_usr-get-context" context)
    (sd/response_ok context)))

(defn handle_create-contexts
  [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            sql (-> (sql/insert-into :contexts)
                    (sql/values [(cast-to-hstore data)])
                    (sql/returning :*)
                    sql-format)
            res (jdbc/execute-one! (:tx req) sql)]

        (if res
          ; TODO clean result
          (sd/response_ok (context_transform_ml res))
          (sd/response_failed "Could not create context." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-contexts
  [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :parameters :path :id)
            dwid (assoc data :id id)
            fir (-> (sql/update :contexts)
                    (sql/set (cast-to-hstore dwid))
                    (sql/where [:= :id id])
                    (sql/returning :*)
                    sql-format)
            upd-result (jdbc/execute-one! (:tx req) fir)]

        (if upd-result
          (sd/response_ok (context_transform_ml upd-result))
          (sd/response_failed "Could not update context." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-context
  [req]
  (try
    (catcher/with-logging {}

      (let [id (-> req :context :id)
            sql-query (-> (sql/delete-from :contexts)
                          (sql/where [:= :id id])
                          (sql/returning :*)
                          sql-format)
            del-result (jdbc/execute-one! (:tx req) sql-query)]

        (if del-result
          (sd/response_ok (context_transform_ml del-result))
          (error "Could not delete context " id))))
    (catch Exception ex (sd/response_exception ex))))

(defn wwrap-find-context [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler
                                    param
                                    :contexts colname
                                    :context send404))))

(def schema_import_contexts
  {:id s/Str
   :admin_comment (s/maybe s/Str)
   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)})

(def schema_update_contexts
  {;(s/optional-key :id) s/Str
   (s/optional-key :admin_comment) (s/maybe s/Str)
   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)})

(def schema_export_contexts_usr
  {:id s/Str
   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)})

(def schema_export_contexts_adm
  {:id s/Str
   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)
   :admin_comment (s/maybe s/Str)})

; TODO more checks
; TODO response coercion
; TODO docu
; TODO tests
(def admin-routes

  ["/contexts"
   {:swagger {:tags ["admin/contexts"] :security [{"auth" []}]}}
   ["/"
    {:post {:summary (sd/sum_adm_todo "Create contexts.")
            :handler handle_create-contexts
            :middleware [wrap-authorize-admin!]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_import_contexts}
            :responses {200 {:body schema_export_contexts_adm}
                        406 {:body s/Any}}}
     ; context list / query
     :get {:summary (sd/sum_adm "List contexts.")
           :handler handle_adm-list-contexts
           :middleware [wrap-authorize-admin!]
           :coercion reitit.coercion.schema/coercion
           ;:parameters {:query {(s/optional-key :full-data) s/Bool}}
           :responses {200 {:body [schema_export_contexts_adm]}
                       406 {:body s/Any}}}}]
   ; edit context
   ["/:id"
    {:get {:summary (sd/sum_adm "Get contexts by id.")
           :handler handle_adm-get-context
           :middleware [wrap-authorize-admin!
                        (wwrap-find-context :id :id true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}
           :responses {200 {:body schema_export_contexts_adm}
                       404 {:body s/Any}}}

     :put {:summary (sd/sum_adm "Update contexts with id.")
           :handler handle_update-contexts
           :middleware [wrap-authorize-admin!
                        (wwrap-find-context :id :id true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}
                        :body schema_update_contexts}
           :responses {200 {:body schema_export_contexts_adm}
                       404 {:body s/Any}
                       406 {:body s/Any}
                       500 {:body s/Any}}}

     :delete {:summary (sd/sum_adm_todo "Delete context by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-context
              :middleware [wrap-authorize-admin!
                           (wwrap-find-context :id :id true)]
              :parameters {:path {:id s/Str}}
              :responses {200 {:body schema_export_contexts_adm}
                          404 {:body s/Any}
                          406 {:body s/Any}
                          500 {:body s/Any}}}}]])

; TODO docu and tests
(def user-routes

  ["/contexts"
   {:swagger {:tags ["contexts"]}}
   ["/"
    {:get {:summary (sd/sum_usr "List contexts.")
           :handler handle_usr-list-contexts
           :coercion reitit.coercion.schema/coercion
           ;:parameters {:query {(s/optional-key :full-data) s/Bool}}
           :responses {200 {:body [schema_export_contexts_usr]}
                       406 {:body s/Any}}}}]
   ; edit context
   ["/:id"
    {:get {:summary (sd/sum_usr "Get contexts by id.")
           :handler handle_usr-get-context
           :middleware [(wwrap-find-context :id :id true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}
           :responses {200 {:body schema_export_contexts_usr}
                       404 {:body s/Any}}}}]])