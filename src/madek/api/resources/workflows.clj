(ns madek.api.resources.workflows
  (:require [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.authorization :as authorization]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.resources.shared.json_query_param_helper :as jqh]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [schema.core :as s]
            [taoensso.timbre :refer [info]]))

(defn handle_list-workflows
  [req]
  (let [qd (if (true? (-> req :parameters :query :full_data))
             :workflows.*
             :workflows.id)
        tx (:tx req)
        db-result (dbh/query-find-all :workflows qd tx)]
    ;(info "handle_list-workflows" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-workflow
  [req]
  (let [workflow (-> req :workflow)]
    (info "handle_get-workflow" workflow)
    ; TODO hide some fields
    (sd/response_ok workflow)))

(defn handle_create-workflow [req]

  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            conf-data-or-str (:configuration data)
            conf-data (jqh/try-as-json conf-data-or-str)
            uid (-> req :authenticated-entity :id)
            ins-data (assoc data :creator_id uid :configuration (with-meta conf-data {:pgtype "jsonb"}))
            sql-query (-> (sql/insert-into :workflows)
                          (sql/values [ins-data])
                          sql-format)
            ins-res (jdbc/execute-one! (:tx req) sql-query)]

        (info "handle_create-workflow: "
              "\ndata:\n" ins-data
              "\nresult:\n" ins-res)

        (if-let [result (::jdbc/update-count ins-res)]
          (sd/response_ok result)
          (sd/response_failed "Could not create workflow." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_update-workflow [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :parameters :path :id)
            tx (:tx req)
            dwid (assoc data :id id)
            upd-query (dbh/sql-update-clause "id" (str id))
            sql-query (-> (sql/update :workflows)
                          (sql/set dwid)
                          (sql/where upd-query)
                          sql-format)
            upd-result (jdbc/execute! (:tx req) sql-query)]

        (info "handle_update-workflow: " "\nid\n" id "\ndwid\n" dwid "\nupd-result:" upd-result)

        (if (= 1 (::jdbc/update-count upd-result))
          (sd/response_ok (dbh/query-eq-find-one :workflows :id id tx))
          (sd/response_failed "Could not update workflow." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_delete-workflow [req]
  (try
    (catcher/with-logging {}
      (let [olddata (-> req :workflow)
            id (-> req :parameters :path :id)
            sql-query (-> (sql/delete-from :workflows)
                          (sql/where [:= :id id])
                          sql-format)
            delresult (jdbc/execute! (:tx req) sql-query)]

        (if (= 1 (::jdbc/update-count delresult))
          (sd/response_ok olddata)
          (sd/response_failed "Could not delete workflow." 422))))
    (catch Exception e (sd/response_exception e))))

(defn wwrap-find-workflow [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :workflows :id
                                    :workflow true))))

(def schema_create_workflow
  {;:id is db assigned or optional
   :name s/Str
   ;:creator_id s/Uuid
   (s/optional-key :is_active) s/Bool
   ; TODO docu is json
   (s/optional-key :configuration) s/Any})

(def schema_update_workflow
  {;:id s/Uuid
   (s/optional-key :name) s/Str
   (s/optional-key :is_active) s/Bool
   ; TODO docu is json
   (s/optional-key :configuration) s/Any})

; TODO Inst coercion
(def schema_export_workflow
  {:id s/Uuid
   (s/optional-key :name) s/Str
   (s/optional-key :is_active) s/Bool
   ; TODO docu is json
   (s/optional-key :configuration) s/Any
   (s/optional-key :creator_id) s/Uuid
   (s/optional-key :created_at) s/Any ; TODO as Inst
   (s/optional-key :updated_at) s/Any})

; TODO response coercion
; TODO docu
; TODO tests
(def user-routes

  ["/"
   {:openapi {:tags ["workflows"]}}
   ["workflows"
    {:post {:summary (sd/sum_auth "Create workflow.")
            :handler handle_create-workflow
            :middleware [authorization/wrap-authorized-user]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_create_workflow}
            :responses {200 {:description "Returns the created workflow."
                             :body schema_export_workflow}
                        406 {:description "Could not create workflow."
                             :body s/Any}}}

     :get {:summary (sd/sum_auth "List workflows.")
           :handler handle_list-workflows
           :middleware [authorization/wrap-authorized-user]
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {;(s/optional-key :name) s/Str ; TODO query by name
                                (s/optional-key :full_data) s/Bool}}
           :responses {200 {:description "Returns the workflows."
                            :body [schema_export_workflow]}
                       406 {:description "Could not list workflows."
                            :body s/Any}}}}]

   ["workflows/:id"
    {:get {:summary (sd/sum_auth "Get workflow by id.")
           :handler handle_get-workflow
           :middleware [authorization/wrap-authorized-user
                        (wwrap-find-workflow :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:description "Returns the workflow."
                            :body schema_export_workflow}
                       404 {:description "Workflow not found."
                            :body s/Any}}}

     :put {:summary (sd/sum_auth "Update workflow with id.")
           :handler handle_update-workflow
           :middleware [authorization/wrap-authorized-user
                        (wwrap-find-workflow :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body schema_update_workflow}
           :responses {200 {:description "Returns the updated workflow."
                            :body schema_export_workflow}
                       404 {:description "Workflow not found."
                            :body s/Any}
                       406 {:description "Could not update workflow."
                            :body s/Any}}}

     :delete {:summary (sd/sum_auth "Delete workflow by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-workflow
              :middleware [authorization/wrap-authorized-user
                           (wwrap-find-workflow :id)]
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:description "Returns the deleted workflow."
                               :body schema_export_workflow}
                          404 {:description "Workflow not found."
                               :body s/Any}}}}]])
