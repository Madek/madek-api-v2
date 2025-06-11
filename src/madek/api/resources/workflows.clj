(ns madek.api.resources.workflows
  (:require [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.authorization :as authorization]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.resources.shared.json_query_param_helper :as jqh]
            [madek.api.utils.helper :refer [to-jsonb-stm normalize-fields]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [schema.core :as s]
            [taoensso.timbre :refer [info]]))

(defn handle_list-workflows
  [req]
  (let [fields (normalize-fields req :workflows)
        qd (if (empty? fields) [:workflows.id] fields)
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
      (let [data (-> req :parameters :body
                     (assoc :creator_id (-> req :authenticated-entity :id)))
            ins-data (if (contains? data :configuration)
                       (assoc data :configuration (-> (jqh/map-as-json! (:configuration data) :configuration)
                                                      (to-jsonb-stm)))
                       data)
            sql-query (-> (sql/insert-into :workflows)
                          (sql/values [ins-data])
                          (sql/returning :*)
                          sql-format)
            ins-res (jdbc/execute! (:tx req) sql-query)]
        (info "handle_create-workflow: "
              "\ndata:\n" ins-data
              "\nresult:\n" ins-res)
        (if (dbh/has-one! ins-res)
          (sd/response_ok (first ins-res))
          (sd/response_failed "Could not create workflow." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_update-workflow [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :parameters :path :id)
            data (if (contains? data :configuration)
                   (assoc data :configuration (-> (jqh/map-as-json! (:configuration data) :configuration)
                                                  (to-jsonb-stm)))
                   data)
            dwid (assoc data :id id)
            sql-query (-> (sql/update :workflows)
                          (sql/set dwid)
                          (dbh/sql-update-fnc-clause "id" id)
                          (sql/returning :*)
                          sql-format)
            upd-result (jdbc/execute! (:tx req) sql-query)]
        (info "handle_update-workflow: " "\nid\n" id "\ndwid\n" dwid "\nupd-result:" upd-result)
        (if (dbh/has-one! upd-result)
          (sd/response_ok (first upd-result))
          (sd/response_failed "Could not update workflow." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_delete-workflow [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)
            sql-query (-> (sql/delete-from :workflows)
                          (sql/where [:= :id id])
                          (sql/returning :*)
                          sql-format)
            delresult (jdbc/execute! (:tx req) sql-query)]
        (if (dbh/has-one! delresult)
          (sd/response_ok (first delresult))
          (sd/response_failed "Could not delete workflow." 422))))
    (catch Exception e (sd/response_exception e))))

(defn wwrap-find-workflow [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :workflows :id
                                    :workflow true))))

(def schema_create_workflow
  {:name s/Str
   (s/optional-key :is_active) s/Bool
   ; TODO docu is json
   (s/optional-key :configuration) s/Any})

(def schema_update_workflow
  {(s/optional-key :name) s/Str
   (s/optional-key :is_active) s/Bool
   ; TODO docu is json
   (s/optional-key :configuration) s/Any})

; TODO Inst coercion
(def schema_export_workflow
  {(s/optional-key :id) s/Uuid
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
           :parameters {:query {(s/optional-key :fields) [(s/enum :id :name :is_active :configuration
                                                                  :creator_id :created_at :updated_at)]}}
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
