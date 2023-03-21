(ns madek.api.resources.delegations
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]))


(defn handle_list-delegations
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data))
        qd (if (true? full-data) :* :delegations.id)
        db-result (sd/query-find-all :delegations qd)]
    ;(logging/info "handle_list-delegation" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-delegation
  [req]
  (let [delegation (-> req :delegation)]
    (logging/info "handle_get-delegation" delegation)
    ; TODO hide some fields
    (sd/response_ok delegation)))

(defn handle_create-delegations
  [req]
  (let [data (-> req :parameters :body)
        ; or TODO data with id
        ]
        ; create delegation entry
      (if-let [ins_res (jdbc/insert! (rdbms/get-ds) :delegations data)]
        ; TODO clean result
        (sd/response_ok (first ins_res))
        (sd/response_failed "Could not create delegation." 406))))

(defn handle_update-delegations
  [req]
  (let [data (-> req :parameters :body)
        id (-> req :parameters :path :id)
        dwid (assoc data :id id)
        ;old-data (sd/query-eq-find-one "delegations" "id" id)
        old-data (-> req :delegation)
        upd-query (sd/sql-update-clause "id" (str id))
        ; or TODO data with id
        ]
        ; create delegation entry
    (logging/info "handle_update-delegations: " "\nid\n" id "\ndwid\n" dwid
                  "\nold-data\n" old-data
                  "\nupd-query\n" upd-query)
    (if-let [ins-res (jdbc/update! (rdbms/get-ds) :delegations dwid upd-query)]
        ; TODO clean result
      ;(if (= 1 ins-res)
        (
         let [new-data (sd/query-eq-find-one "delegations" "id" id)]
         (logging/info "handle_update-delegations:" "\nnew-data\n" new-data)
         (sd/response_ok new-data)
         )
       ; (sd/response_failed "Could not update delegation." 406)
       ; )
      (sd/response_failed "Could not update delegation." 406))))

(defn handle_delete-delegation
  [req]
  (let [delegation (-> req :delegation)
        delegation-id (-> req :delegation :id)]
    (if (= 1 (first (jdbc/delete! (rdbms/get-ds) :delegations ["id = ?" delegation-id])))
      (sd/response_ok delegation)
      (logging/error "Failed delete delegation " delegation-id))))

(defn wwrap-find-delegation [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param "delegations" colname :delegation send404))))


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

; TODO Inst coercion
(def schema_export_delegations
  {:id s/Uuid
   :name s/Str
   :description s/Str
   :admin_comment (s/maybe s/Str)})

; TODO more checks
; TODO response coercion
; TODO docu
; TODO tests
(def ring-routes

  ["/delegations" 
   ["/"
    {:post {:summary (sd/sum_adm_todo "Create delegations.")
            ; TODO labels and descriptions
            :handler handle_create-delegations
                   ;:middleware [(wwrap-find-delegation :id "id" false)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_import_delegations}
            :responses {200 {:body schema_export_delegations}
                        406 {:body s/Any}}
            }
    ; delegation list / query
     :get {:summary  (sd/sum_adm "List delegations.")
           :handler handle_list-delegations
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full-data) s/Bool}}}}]
    ; edit delegation
   ["/:id"
    {:get {:summary (sd/sum_adm "Get delegations by id.")
           :handler handle_get-delegation
           :middleware [(wwrap-find-delegation :id "id" true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}}
     
     :put {:summary (sd/sum_adm "Update delegations with id.")
           ; TODO labels and descriptions
           :handler handle_update-delegations
           :middleware [(wwrap-find-delegation :id "id" true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}
                        :body schema_update_delegations}
           :responses {200 {:body s/Any} ;schema_export_delegations}
                       406 {:body s/Any}}}

     :delete {:summary (sd/sum_adm_todo "Delete delegation by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-delegation
              :middleware [(wwrap-find-delegation :id "id" true)]
              :parameters {:path {:id s/Str}}}}]]
   )