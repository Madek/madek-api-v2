(ns madek.api.resources.delegations
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.shared :as sd]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [info]]))

(defn handle_list-delegations
  [req]
  (let [full-data (= "true" (get (-> req :query-params) "full-data"))
        qd (if (true? full-data) :* :delegations.id)
        db-result (sd/query-find-all :delegations qd (:tx req))]
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
        sql-query (-> (sql/insert-into :delegations) (sql/values [data]) sql-format)
        ins-res (jdbc/execute! (:tx req) sql-query)]
    (if ins-res
      ; TODO clean result
      (sd/response_ok (first ins-res))
      (sd/response_failed "Could not create delegation." 406))))

(defn handle_update-delegations
  [req]
  (let [data (-> req :parameters :body)
        id (-> req :parameters :path :id)
        dwid (assoc data :id id)
        old-data (-> req :delegation)
        upd-query (sd/sql-update-clause "id" (str id))
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
      (let [new-data (sd/query-eq-find-one :delegations :id id tx)]
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
      (if (sd/query-eq-find-one :delegation :id id tx)
        (if (jdbc/execute-one! tx sql-query)
          (sd/response_ok delegation)
          (sd/response_failed "Could not delete delegation." 406))
        (sd/response_not_found "No such delegation found."))
      (catch Exception ex (sd/parsed_response_exception ex)))))

(defn wwrap-find-delegation [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :delegations colname :delegation send404))))

;(def schema_import_delegations
;  {;:id s/Str
;   :name s/Str
;   :description s/Str
;   :admin_comment (s/maybe s/Str)})
;
;(def schema_update_delegations
;  {;(s/optional-key :id) s/Str
;   (s/optional-key :name) s/Str
;   (s/optional-key :description) s/Str
;   (s/optional-key :admin_comment) (s/maybe s/Str)})
;
;(def schema_get_delegations
;  {:id s/Uuid
;   (s/optional-key :name) s/Str
;   (s/optional-key :description) s/Str
;   (s/optional-key :admin_comment) (s/maybe s/Str)})
;
;; TODO Inst coercion
;(def schema_export_delegations
;  {:id s/Uuid
;   :name s/Str
;   :description s/Str
;   :admin_comment (s/maybe s/Str)})

; TODO more checks
; TODO response coercion
; TODO docu
; TODO tests
(def ring-routes

  ["/delegations"
   {:swagger {:tags ["admin/delegations"] :security [{"auth" []}]}}
   ["/"
    {:post {:summary (sd/sum_adm_todo "Create delegations.")
            ; TODO labels and descriptions
            :handler handle_create-delegations
            :coercion reitit.coercion.schema/coercion
            :parameters {:body (get-schema :delegations.schema_import_delegations)}
            :responses {200 {:body (get-schema :delegations.schema_export_delegations)}
                        406 {:body s/Any}}}
     :get {:summary (sd/sum_adm "List delegations.")
           :handler handle_list-delegations
           :coercion reitit.coercion.schema/coercion
           :swagger {:produces "application/json"
                     :parameters [{:name "full-data"
                                   :in "query"
                                   :description "Request full data."
                                   :required true
                                   :value false
                                   :default false
                                   :type "boolean"}]}
           :responses {200 {:body [(get-schema :delegations.schema_get_delegations)]}}}}]

   ; edit delegation
   ["/:id"
    {:get {:summary (sd/sum_adm "Get delegations by id.")
           :handler handle_get-delegation
           :middleware [(wwrap-find-delegation :id :id true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body (get-schema :delegations.schema_export_delegations)}
                       404 {:description "Not Found."
                            :schema s/Str
                            :examples {"application/json" {:message "No such entity in :delegations as :id with <id>"}}}}}

     :put {:summary (sd/sum_adm "Update delegations with id.")
           :handler handle_update-delegations
           :middleware [(wwrap-find-delegation :id :id true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body (get-schema :delegations.schema_update_delegations)}
           :responses {200 {:body (get-schema :delegations.schema_export_delegations)}
                       404 {:description "Not Found."
                            :schema s/Str
                            :examples {"application/json" {:message "No such entity in :delegations as :id with <id>"}}}
                       406 {:description "Not Acceptable."
                            :schema s/Str
                            :examples {"application/json" {:message "Could not update delegation."}}}}}

     :delete {:summary (sd/sum_adm_todo "Delete delegation by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-delegation
              :middleware [(wwrap-find-delegation :id :id true)]
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:body (get-schema :delegations.schema_export_delegations)}
                          404 {:description "Not Found."
                               :schema s/Str
                               :examples {"application/json" {:message "No such delegation found"}}}
                          406 {:description "Not Acceptable."
                               :schema s/Str
                               :examples {"application/json" {:message "Could not delete delegation."}}}}}}]])