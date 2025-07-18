(ns madek.api.resources.delegations
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [normalize-fields]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [info]]))

(defn handle_list-delegations
  [req]
  (let [fields (normalize-fields req)
        qd (if (empty? fields) [:delegations.id] fields)
        db-result (dbh/query-find-all :delegations qd (:tx req))]
    ;(info "handle_list-delegation" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-delegation
  [req]
  (let [delegation (-> req :delegation)]
    (info "handle_get-delegation" delegation)
    ; TODO hide some fields
    (sd/response_ok delegation)))

(defn handle_create-delegations
  [req]
  (let [data (-> req :parameters :body)
        sql-query (-> (sql/insert-into :delegations) (sql/values [data]) (sql/returning :*) sql-format)
        ins-res (jdbc/execute! (:tx req) sql-query)
        auth-entity (:authenticated-entity req)
        user (:id auth-entity)
        data {:delegation_id (-> (first ins-res) :id)
              :user_id user}
        sql-query (-> (sql/insert-into :delegations_supervisors) (sql/values [data]) (sql/returning :*) sql-format)]
    (jdbc/execute! (:tx req) sql-query)
    (if ins-res
      (sd/response_ok (first ins-res))
      (sd/response_failed "Could not create delegation." 406))))

(defn handle_update-delegations
  [req]
  (let [data (-> req :parameters :body)
        id (-> req :parameters :path :id)
        dwid (assoc data :id id)
        old-data (-> req :delegation)
        upd-query (dbh/sql-update-clause "id" (str id))
        tx (:tx req)
        sql-query (-> (sql/update :delegations)
                      (sql/set dwid)
                      (sql/where [:= :id id])
                      sql-format)]
    ; create delegation entry
    (info "handle_update-delegations: " "\nid\n" id "\ndwid\n" dwid
          "\nold-data\n" old-data
          "\nupd-query\n" upd-query)

    (if-let [ins-res (first (jdbc/execute! tx sql-query))]
      (let [new-data (dbh/query-eq-find-one :delegations :id id tx)]
        (info "handle_update-delegations:" "\nnew-data\n" new-data)
        (sd/response_ok new-data))
      (sd/response_failed "Could not update delegation." 406))))

(defn handle_delete-delegation [req]
  (let [tx (:tx req)
        delegation (-> req :delegation)
        id (-> delegation :id)
        sql-query (-> (sql/delete-from :delegations)
                      (sql/where [:= :id id])
                      (sql/returning :*)
                      sql-format)]
    (try
      (if (dbh/query-eq-find-one :delegation :id id tx)
        (if (jdbc/execute-one! tx sql-query)
          (sd/response_ok delegation)
          (sd/response_failed "Could not delete delegation." 406))
        (sd/response_not_found "No such delegation found."))
      (catch Exception ex (sd/parsed_response_exception ex)))))

(defn wwrap-find-delegation [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :delegations colname :delegation send404))))

(def schema_import_delegations
  {;:id s/Str
   :name s/Str
   :description s/Str
   :admin_comment (s/maybe s/Str)})

(def schema_update_delegations
  {;(s/optional-key :id) s/Str
   (s/optional-key :name) s/Str
   (s/optional-key :description) s/Str
   (s/optional-key :admin_comment) (s/maybe s/Str)})

(def schema_get_delegations
  {(s/optional-key :id) s/Uuid
   (s/optional-key :name) s/Str
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :admin_comment) (s/maybe s/Str)
   (s/optional-key :notifications_email) (s/maybe s/Str)
   (s/optional-key :notify_all_members) s/Bool
   (s/optional-key :beta_tester_notifications) s/Bool})

; TODO Inst coercion
(def schema_export_delegations
  {:id s/Uuid
   :name s/Str
   :description s/Str
   :admin_comment (s/maybe s/Str)
   (s/optional-key :notifications_email) (s/maybe s/Str)
   (s/optional-key :notify_all_members) s/Bool
   (s/optional-key :beta_tester_notifications) s/Bool})

; TODO more checks
; TODO response coercion
; TODO docu
; TODO tests
(def ring-routes

  ["/"
   {:openapi {:tags ["admin/delegations"] :security ADMIN_AUTH_METHODS}}
   ["delegations/"
    {:post {:summary (sd/sum_adm_todo "Create delegations.")
            ; TODO labels and descriptions
            :handler handle_create-delegations
            :middleware [wrap-authorize-admin!]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_import_delegations}
            :responses {200 {:description "Returns the created delegation."
                             :body schema_export_delegations}
                        406 {:description "Could not create delegation."
                             :body s/Any}}}
     :get {:summary (sd/sum_adm "List delegations.")
           :handler handle_list-delegations
           :middleware [wrap-authorize-admin!]
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :fields) [(s/enum :id :name :description :admin_comment
                                                                  :notifications_email :notify_all_members
                                                                  :beta_tester_notifications)]}}
           :responses {200 {:description "Returns the list of delegations."
                            :body [schema_get_delegations]}}}}]

   ; edit delegation
   ["delegations/:id"
    {:get {:summary (sd/sum_adm "Get delegations by id.")
           :handler handle_get-delegation
           :middleware [(wwrap-find-delegation :id :id true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:description "Returns the delegation."
                            :body schema_export_delegations}
                       404 (sd/create-error-message-response "Not Found." "No such entity in :delegations as :id with <id>")}}

     :put {:summary (sd/sum_adm "Update delegations with id.")
           :handler handle_update-delegations
           :middleware [(wwrap-find-delegation :id :id true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body schema_update_delegations}
           :responses {200 {:description "Returns the updated delegation."
                            :body schema_export_delegations}
                       404 (sd/create-error-message-response "Not Found." "No such entity in :delegations as :id with <id>")
                       406 (sd/create-error-message-response "Not Acceptable." "Could not update delegation.")}}

     :delete {:summary (sd/sum_adm_todo "Delete delegation by id.")
              :handler handle_delete-delegation
              :middleware [(wwrap-find-delegation :id :id true)]
              :parameters {:path {:id s/Uuid}}
              :coercion reitit.coercion.schema/coercion
              :responses {200 {:description "Returns the deleted delegation."
                               :body schema_export_delegations}
                          404 (sd/create-error-message-response "Not Found." "No such delegation found.")
                          406 (sd/create-error-message-response "Not Acceptable." "Could not delete delegation.")}}}]])
