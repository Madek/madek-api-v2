(ns madek.api.resources.delegations-groups
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared :as sd]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [error info]]))

(def res-req-name :delegation_group)
(def res-table-name "delegations_groups")
(def res-col-name :delegation_id)

; TODO query
(defn handle_list-delegations_groups
  [req]
  (let [delegation_id (-> req :parameters :query :delegation_id)
        group_id (-> req :parameters :query :group_id)
        col-sel (if (true? (-> req :parameters :query :full-data))
                  (sql/select :*)
                  (sql/select :group_id))
        base-query (-> col-sel (sql/from :delegations_groups))
        query (cond-> base-query
                delegation_id (sql/where [:= :delegation_id delegation_id])
                group_id (sql/where [:= :group_id group_id]))
        db-result (jdbc/execute! (:tx req) (sql-format query))]
    (info "handle_list-delegations_group" "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_list-delegations_groups-by-group
  [req]
  (let [;full-data (true? (-> req :parameters :query :full-data))
        group-id (-> req :authenticated-entity :id)
        db-result (sd/query-eq-find-all :delegations_groups :group_id group-id (:tx req))
        id-set (map :delegation_id db-result)]
    (info "handle_list-delegations_group" "\nresult\n" db-result "\nid-set\n" id-set)
    (sd/response_ok {:delegation_ids id-set})
    ;(if full-data (sd/response_ok db-result) (sd/response_ok {:delegation_ids id-set})) 
    ))

(defn handle_get-delegations_group
  [req]
  (let [favorite_collection (-> req res-req-name)]
    ;(info "handle_get-favorite_collection" favorite_collection)
    ; TODO hide some fields
    (sd/response_ok favorite_collection)))

(defn handle_create-delegations_group
  [req]
  (let [group (or (-> req :group) (-> req :authenticated-entity))
        delegation (-> req :delegation)
        data {:group_id (:id group) :delegation_id (:id delegation)}
        sql-query (-> (sql/insert-into :delegations_groups) (sql/values [data]) sql-format)]
    (if-let [delegations_group (-> req res-req-name)]
      ; already has delegations_group
      (sd/response_ok delegations_group)
      ; create delegations_group entry
      (if-let [ins_res (first (jdbc/execute! (:tx req) sql-query))]
        ; TODO clean result
        (sd/response_ok ins_res)
        (sd/response_failed "Could not create delegations_group." 406)))))

(defn handle_delete-delegations_group
  [req]
  (let [delegations_group (-> req res-req-name)
        group-id (:group_id delegations_group)
        delegation-id (res-col-name delegations_group)
        sql-query (-> (sql/delete-from :delegations_groups)
                      (sql/where [:= :group_id group-id] [:= :delegation_id delegation-id])
                      (sql/returning :*)
                      sql-format)
        result (jdbc/execute-one! (:tx req) sql-query)]
    (if result
      (sd/response_ok delegations_group)
      (error "Failed delete delegations_group "
             "group-id: " group-id "delegation-id: " delegation-id))))

(defn wwrap-find-delegations_group [send404]
  (fn [handler]
    (fn [request]
      (sd/req-find-data2
       request handler
       :group_id :delegation_id
       :delegations_groups
       :group_id :delegation_id
       res-req-name
       send404))))

; rubbish find by users groups
(defn wwrap-find-delegations_group-by-auth [send404]
  (fn [handler]
    (fn [request]
      (let [group-id (-> request :authenticated-entity :id str)
            del-id (-> request :parameters :path :delegation_id str)]
        (info "uid\n" group-id "del-id\n" del-id)
        (sd/req-find-data-search2
         request handler
         group-id del-id
         :delegations_groups
         :group_id :delegation_id
         res-req-name
         send404)))))

(defn wwrap-find-group [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :groups :id
                                    :group true))))

(defn wwrap-find-delegation [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :delegations :id
                                    :delegation true))))

(def schema_delegations_groups_export
  {:group_id s/Uuid
   :delegation_id s/Uuid
   :updated_at s/Any
   :created_at s/Any})

; TODO response coercion
; TODO docu
; TODO tests
; group self edit favorites 
(def query-routes
  ["/delegation/groups"
   {:swagger {:tags ["delegation/groups"]}}
   {:get
    {:summary (sd/sum_adm "Query delegation groups.")
     :handler handle_list-delegations_groups-by-group
     :swagger {:produces "application/json"}
     :coercion reitit.coercion.schema/coercion
     :parameters {:query {(s/optional-key :delegation_id) s/Uuid
                          (s/optional-key :group_id) s/Uuid}}
     :responses {200 {:body {:delegation_ids [s/Uuid]}}}}}])

(def admin-routes
  [["/delegation/groups"
    {:swagger {:tags ["admin/delegation/groups"] :security [{"auth" []}]}}
    ["/"
     {:get
      {:summary (sd/sum_adm "Query delegations_groups.")
       :handler handle_list-delegations_groups
       :coercion reitit.coercion.schema/coercion
       :parameters {:query {(s/optional-key :group_id) s/Uuid
                            (s/optional-key :delegation_id) s/Uuid
                            (s/optional-key :full-data) s/Bool}}}}]

    ["/delegation/groups/:delegation_id/:group_id"
     {:post
      {:summary (sd/sum_adm "Create delegations_group for group and delegation.")
       :handler handle_create-delegations_group
       :middleware [(wwrap-find-group :group_id)
                    (wwrap-find-delegation :delegation_id)
                    (wwrap-find-delegations_group false)]
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:group_id s/Uuid
                           :delegation_id s/Uuid}}}

      :get
      {:summary (sd/sum_adm "Get delegations_group for group and delegation.")
       :handler handle_get-delegations_group
       :middleware [(wwrap-find-delegations_group true)]
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:group_id s/Uuid
                           :delegation_id s/Uuid}}}

      :delete
      {:summary (sd/sum_adm "Delete delegations_group for group and delegation.")
       :coercion reitit.coercion.schema/coercion
       :handler handle_delete-delegations_group
       :middleware [(wwrap-find-delegations_group true)]
       :parameters {:path {:group_id s/Uuid
                           :delegation_id s/Uuid}}}}]]])
