(ns madek.api.resources.groups
  (:require [clj-uuid]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.pagination :as pagination]
            [madek.api.resources.groups.shared :as groups]
            [madek.api.db.core :refer [get-ds]]
            [madek.api.resources.groups.users :as group-users]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.auth :refer [wrap-authorize-admin!]]
            [madek.api.utils.helper :refer [convert-groupid f mslurp t]]
            [madek.api.utils.sql-next :refer [convert-sequential-values-to-sql-arrays]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [schema.core :as s]
            [taoensso.timbre :refer [error info]]))

;### create group #############################################################

;; FIXME: not in use?
;(defn create-group [request]
;  (let [params (as-> (:body request) params
;                 (or params {})
;                 (assoc params :id (or (:id params) (clj-uuid/v4))))]
;    {:body (dissoc
;            (->> (jdbc/execute-one! (:tx req) (-> (sql/insert-into :groups)
;                                                 (sql/values [params])
;                                                 (sql/returning :*)
;                                                 sql-format)))
;            :previous_id :searchable)
;     :status 201}))

;### get group ################################################################

(defn get-group [id-or-institutional-group-id tx]
  (if-let [group (groups/find-group id-or-institutional-group-id tx)]
    {:body (dissoc group :previous_id :searchable)}
    {:status 404 :body "No such group found"}))             ; TODO: toAsk 204 No Content

;### delete group ##############################################################

(defn delete-group [id tx]
  (let [sec (groups/jdbc-update-group-id-where-clause id)
        query (-> (sql/delete-from :groups)
                  (sql/where (:where sec))
                  sql-format)
        res (jdbc/execute-one! tx query)
        update-count (get res :next.jdbc/update-count)]

    (if (= 1 update-count)
      {:status 204 :content-type "application/json"}        ;TODO / FIXME: response should support octet-stream as well?
      {:status 404})))

;### patch group ##############################################################
(defn db_update-group [group-id body tx]
  (let [where-clause (:where (groups/jdbc-update-group-id-where-clause group-id))
        query (-> (sql/update :groups)
                  (sql/set (-> body convert-sequential-values-to-sql-arrays))
                  (sql/where where-clause)
                  (sql/returning :*)
                  sql-format)
        result (jdbc/execute-one! tx query)]
    result))

(defn patch-group [{body :body {group-id :group-id} :params} tx]
  (try
    (if-let [result (db_update-group group-id body tx)]
      {:body result}
      {:status 404})

    (catch Exception e
      (error "handle-patch-group failed, group-id=" group-id)
      (sd/parsed_response_exception e))))

;### index ####################################################################
; TODO test query and paging
(defn build-index-query [req]
  (let [query-params (-> req :parameters :query)]
    (-> (if (true? (:full_data query-params))
          (sql/select :*)
          (sql/select :id))
        (sql/from :groups)
        (sql/order-by [:id :asc])
        (sd/build-query-param query-params :id)
        (sd/build-query-param query-params :institutional_id)
        (sd/build-query-param query-params :type)
        (sd/build-query-param query-params :created_by_user_id)
        (sd/build-query-param-like query-params :name)
        (sd/build-query-param-like query-params :institutional_name)
        (sd/build-query-param-like query-params :institution)
        (sd/build-query-param-like query-params :searchable)
        (pagination/add-offset-for-honeysql query-params)
        sql-format)))

(defn index [req]
  (let [result (jdbc/execute! (:tx req) (build-index-query req))]
    (sd/response_ok {:groups result})))

;### routes ###################################################################

(def schema_import-group
  {;(s/optional-key :id) s/Str
   :name s/Str
   ;:type (s/enum "Group" "AuthenticationGroup" "InstitutionalGroup")
   (s/optional-key :type) (s/enum "Group" "AuthenticationGroup" "InstitutionalGroup")
   (s/optional-key :institutional_id) (s/maybe s/Str)
   (s/optional-key :institutional_name) (s/maybe s/Str)
   (s/optional-key :institution) (s/maybe s/Str)
   (s/optional-key :created_by_user_id) (s/maybe s/Uuid)})

(def schema_update-group
  {(s/optional-key :name) s/Str
   (s/optional-key :type) (s/enum "Group" "AuthenticationGroup" "InstitutionalGroup")
   (s/optional-key :institutional_id) (s/maybe s/Str)
   (s/optional-key :institutional_name) (s/maybe s/Str)
   (s/optional-key :institution) (s/maybe s/Str)
   (s/optional-key :created_by_user_id) (s/maybe s/Uuid)})

(def schema_export-group
  {:id s/Uuid
   (s/optional-key :name) s/Str
   (s/optional-key :type) s/Str                             ; TODO enum
   (s/optional-key :created_by_user_id) (s/maybe s/Uuid)
   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any
   (s/optional-key :institutional_id) (s/maybe s/Str)
   (s/optional-key :institutional_name) (s/maybe s/Str)
   (s/optional-key :institution) (s/maybe s/Str)
   (s/optional-key :searchable) s/Str})

(defn handle_create-group
  [req]
  (try
    (let [params (get-in req [:parameters :body])
          data_wid (assoc params :id (or (:id params) (clj-uuid/v4)))
          data_wtype (assoc data_wid :type (or (:type data_wid) "Group"))
          resultdb (->> (jdbc/execute-one! (:tx req) (-> (sql/insert-into :groups)
                                                         (sql/values [data_wtype])
                                                         (sql/returning :*)
                                                         sql-format)))
          result (dissoc resultdb :previous_id :searchable)]
      (info (apply str ["handler_create-group: \ndata:" data_wtype "\nresult-db: " resultdb "\nresult: " result]))
      {:status 201 :body result})
    (catch Exception e
      (error "handle-create-group failed" {:request req})
      (sd/parsed_response_exception e))))

(defn handle_get-group [req]
  (let [group-id (-> req :parameters :path :id)
        tx (:tx req)
        id (-> (convert-groupid group-id) :group-id)]
    (info "handle_get-group" "\nid\n" id)
    (get-group id tx)))

(defn handle_delete-group [req]
  (let [id (-> req :parameters :path :id)]
    (delete-group id (:tx req))))

(defn handle_update-group [req]
  (let [id (-> req :parameters :path :id)
        tx (:tx req)
        body (-> req :parameters :body)]
    ;(info "handle_update-group" "\nid\n" id "\nbody\n" body)
    (patch-group {:params {:group-id id} :body body} tx)))







(defn fetch-table-metadata [table-name]
  (jdbc/execute! (get-ds)
    ["SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name= ?"
     table-name]
    {:result-set-fn :hash-map}))


(require '[schema.core :as schema])

(def type-mapping {"varchar" schema/Str
                   "int4" schema/Int
                   "boolean" schema/Bool
                   "uuid" schema/Uuid
                   "text" schema/Str
                   "character varying" schema/Str
                   "timestamp with time zone" schema/Any
                   })

;(defn postgres-to-schema [metadata]
;  (into {}
;    (map (fn [{:keys [column_name data_type is_nullable]}]
;           (do
;             (println ">o> postgres-to-schema=" column_name data_type is_nullable)
;             {column_name (if (= is_nullable "YES")
;                              (do
;                                (println ">o> 1data_type=" data_type (type-mapping data_type)
;                                (schema/maybe (type-mapping data_type))))
;                              (do
;                                (println ">o> 2data_type=" data_type (type-mapping data_type))
;                                (type-mapping data_type))
;                              )})
;      metadata))))

;(defn postgres-to-schema [metadata]
;
;  (into {}
;    (map (fn [{:keys [required_attr column_name data_type is_nullable]}]
;           (cond
;             (not (nil? column_name)) (do
;                                        (println ">o> column_name=" column_name "|" data_type "|" is_nullable)
;
;                                        {
;                                         (s/optional-key (keyword column_name)) (if (= is_nullable "YES")
;
;                                                                                  ;(schema/maybe (type-mapping data_type))
;                                                                                  ;(type-mapping data_type)
;
;                                                                                  (do
;                                                                                    ;(println ">o> 1data_type, column_name=" column_name "|" data_type "|" (type-mapping data_type))
;                                                                                    ;(println ">o> column_name=" column_name (class column_name))
;                                                                                    (println ">o> column_name=" data_type (class data_type))
;                                                                                    ;(schema/maybe (type-mapping data_type))))
;                                                                                    ;((s/optional-key (keyword column_name)) (schema/maybe (type-mapping data_type)))  )
;                                                                                    (schema/maybe (type-mapping data_type)))
;
;                                                                                  (do
;                                                                                    ;(println ">o> 2data_type, column_name=" column_name "|" data_type "|" (type-mapping data_type))
;                                                                                    ;(println ">o> column_name=" column_name (class column_name))
;                                                                                    (println ">o> column_name=" data_type (class data_type))
;                                                                                    ;((s/optional-key (keyword column_name)) (type-mapping data_type) )          )
;                                                                                    (type-mapping data_type))
;
;                                                                                  )
;
;                                         }
;                                        )
;             (not (nil? required_attr)) (do
;                                          (println ">o> required_attr=" required_attr "|" data_type)
;                                          {
;                                           (s/required-key (keyword required_attr)) (type-mapping data_type)
;                                           }
;
;                                          )
;
;             :else (do
;                     (println ">o> ERROR: wrong configuration? column_name=" column_name data_type)
;                     {}
;                     )
;
;             )
;           metadata))))


(defn ensure-required-attr [entries]
  (map (fn [entry]
         (if (contains? entry :required)
           entry
           (assoc entry :required false)))
    entries))

(defn ensure-required-attr [entries]
  ;(map (fn [entry]
  ;       (if (and (map? entry) (not (contains? entry :required)))
  ;         (assoc entry :required false)
  ;         entry))
  ;  entries)
  ;
     (let [
              entries   (map (fn [entry]
                               (if (and (map? entry) (not (contains? entry :required)))
                                 (assoc entry :required false)
                                 entry))
                          entries)

           entries   (map (fn [entry]
                               (if (and (map? entry) (not (contains? entry :is_nullable)))
                                 (assoc entry :is_nullable "NO")
                                 entry))
                          entries)
              ]entries)

  )

(defn postgres-to-schema [metadata]
  (into {}
    (map (fn [{:keys [column_name data_type is_nullable required]}]
           (println ">o> =>" column_name data_type is_nullable required)
           (if (true? required)
             {(s/required-key (keyword column_name)) (type-mapping data_type)}
             {(s/optional-key (keyword column_name)) (if (= is_nullable "YES")
                                                       (schema/maybe (type-mapping data_type))
                                                       (type-mapping data_type))}
             )
           )
      metadata)))

  (comment

    (let [
          res (fetch-table-metadata "groups")
          p (println ">o> 1res=" res)


          ;(s/optional-key :full_data) s/Bool
          ;(s/optional-key :page) s/Int
          ;(s/optional-key :count) s/Int}

          ;res2 [{:required_attr "full_data", :data_type "boolean", :is_nullable "NO"}
          ;      {:required_attr "page", :data_type "int4", :is_nullable "NO"}
          ;      {:required_attr "count", :data_type "int4", :is_nullable "NO"}]


          ;res2 [{:required_attr "full_data", :data_type "boolean"}
          ;      {:required_attr "page", :data_type "int4"}
          ;      {:required_attr "count", :data_type "int4"}]

          res2 [{:column_name "full_data", :data_type "boolean" :required true}
                {:column_name "page", :data_type "int4" :required true}
                {:column_name "count", :data_type "int4" :required true}]
          res (concat res res2)
          ;p (println ">o> 2res=" res)


          res (ensure-required-attr res)
          p (println ">o> 2ares=" res)

          res (postgres-to-schema res)
          p (println ">o> 3res=" res)

          ]
res
      )
    )


  (def schema_raw_pagination [{:required_attr "full_data", :data_type "boolean"}
                              {:required_attr "page", :data_type "int4"}
                              {:required_attr "count", :data_type "int4"}])
  ;; dynamic schema
  (def schema_query-groups (merge (fetch-table-metadata "groups") schema_raw_pagination))


  ;;; static schema
  ;(def schema_query-groups
  ;  {(s/optional-key :id) s/Uuid
  ;   (s/optional-key :name) s/Str
  ;   (s/optional-key :type) s/Str
  ;   (s/optional-key :created_at) s/Any
  ;   (s/optional-key :updated_at) s/Any
  ;   (s/optional-key :institutional_id) s/Str
  ;   (s/optional-key :institutional_name) s/Str
  ;   (s/optional-key :institution) s/Str
  ;   (s/optional-key :created_by_user_id) s/Uuid
  ;   (s/optional-key :searchable) s/Str
  ;
  ;   (s/optional-key :full_data) s/Bool
  ;   (s/optional-key :page) s/Int
  ;   (s/optional-key :count) s/Int}
  ;  )

  (def user-routes
    [["/groups"
      {:swagger {:tags ["groups"]}}
      ["/" {:get {:summary "Get all group ids"
                  :description "Get list of group ids. Paging is used as you get a limit of 100 entries."
                  :handler index
                  :middleware [wrap-authorize-admin!]
                  :swagger {:produces "application/json"}
                  :content-type "application/json"
                  :parameters {:query schema_query-groups}
                  ;:accept "application/json"
                  :coercion reitit.coercion.schema/coercion
                  :responses {200 {:body {:groups [schema_export-group]}}}}}]
      ["/:id" {:get {:summary "Get group by id"
                     :description "Get group by id. Returns 404, if no such group exists."
                     :swagger {:produces "application/json"}
                     :content-type "application/json"
                     :handler handle_get-group
                     :middleware [wrap-authorize-admin!]
                     :coercion reitit.coercion.schema/coercion
                     :parameters {:path {:id s/Uuid}}
                     :responses {200 {:body schema_export-group}
                                 404 {:body s/Any}}}}]]])

  (def ring-routes
    ["/groups"
     {:swagger {:tags ["admin/groups"] :security [{"auth" []}]}}
     ["/" {:get {:summary (f "Get all group ids" " / TODO: no-input-validation")
                 :description "Get list of group ids. Paging is used as you get a limit of 100 entries."
                 :handler index
                 :middleware [wrap-authorize-admin!]
                 :swagger {:produces "application/json"}
                 :parameters {:query schema_query-groups}
                 :content-type "application/json"
                 :coercion reitit.coercion.schema/coercion
                 :responses {200 {:body {:groups [schema_export-group]}}}}

           :post {:summary (f "Create a group" "groups::person_id-not-exists")
                  :description "Create a group."
                  :handler handle_create-group
                  :middleware [wrap-authorize-admin!]
                  :swagger {:produces "application/json" :consumes "application/json"}
                  :content-type "application/json"
                  :accept "application/json"
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:body schema_import-group}
                  :responses {201 {:body schema_export-group}
                              404 {:description "Not Found."
                                   :schema s/Str
                                   :examples {"application/json" {:message "User entry not found"}}}

                              409 {:description "Conflict."
                                   :schema s/Str
                                   :examples {"application/json" {:message "Entry already exists"}}}
                              500 {:body s/Any}}}}]

     ["/:id" {:get {:summary "Get group by id OR institutional-id"
                    :description "CAUTION: Get group by id OR institutional-id. Returns 404, if no such group exists."
                    :swagger {:produces "application/json"}
                    :content-type "application/json"
                    :accept "application/json"
                    :handler handle_get-group
                    :middleware [wrap-authorize-admin!]
                    :coercion reitit.coercion.schema/coercion

                    ;:parameters {:path {:id s/Uuid}}
                    :parameters {:path {:id s/Any}}
                    ;; can be uuid (group-id) or string (institutional-id)
                    ;; http://localhost:3104/api/admin/groups/%3Fthis%23id%2Fneeds%2Fto%2Fbe%2Furl%26encoded>,

                    :responses {200 {:body schema_export-group}
                                404 {:description "Not Found."
                                     :schema s/Str
                                     :examples {"application/json" {:message "No such group found"}}}}}
              :delete {:summary "Deletes a group by id"
                       :description "Delete a group by id"
                       :handler handle_delete-group
                       :middleware [wrap-authorize-admin!]
                       :coercion reitit.coercion.schema/coercion
                       :parameters {:path {:id s/Uuid}}
                       :responses {403 {:body s/Any}
                                   ;; TODO: response of type octet-stream not yet supported?
                                   204 {:description "No Content. The resource was deleted successfully."
                                        :schema nil
                                        :examples {"application/json" nil}}}}
              :put {:summary "Get group by id"
                    :swagger {:produces "application/json"}
                    :content-type "application/json"
                    :accept "application/json"
                    :handler handle_update-group
                    ;:description "Get group by id. Returns 404, if no such group exists."
                    :description (mslurp "./md/admin-groups-put.md")
                    :middleware [wrap-authorize-admin!]
                    :coercion reitit.coercion.schema/coercion
                    :parameters {:path {:id s/Uuid}
                                 :body schema_update-group}
                    :responses {200 {:body s/Any}           ;groups/schema_export-group}
                                404 {:body s/Any}}}}]       ; TODO error handling

     ; groups-users/ring-routes
     ["/:group-id/users/" {:get {:summary "Get group users by id"
                                 :description "Get group users by id. (zero-based paging)"
                                 :swagger {:produces "application/json"}
                                 :content-type "application/json"

                                 :handler group-users/handle_get-group-users
                                 :middleware [wrap-authorize-admin!]
                                 :coercion reitit.coercion.schema/coercion
                                 :parameters {:path {:group-id s/Uuid}
                                              :query {(s/optional-key :page) s/Int
                                                      (s/optional-key :count) s/Int}}
                                 :responses {200 {:description "OK - Returns a list of group users OR an empty list."
                                                  :schema {:body {:users [group-users/schema_export-group-user-simple]}}}}}

                           ; TODO works with tests, but not with the swagger ui
                           ; TODO: broken test / duplicate key issue
                           :put {:summary "Update group users by group-id and list of users."
                                 :description "Update group users by group-id and list of users."
                                 :swagger {:consumes "application/json" :produces "application/json"}
                                 :content-type "application/json"
                                 :accept "application/json"
                                 :handler group-users/handle_update-group-users
                                 :coercion reitit.coercion.schema/coercion
                                 :parameters {:path {:group-id s/Uuid}
                                              :body group-users/schema_update-group-user-list}

                                 :responses {200 {:body s/Any} ;groups/schema_export-group}
                                             404 {:body s/Str}}}}]

     ["/:group-id/users/:user-id" {:get {:summary "Get group user by group-id and user-id"
                                         :description "gid= uuid/institutional_id\n
                                       user_id= uuid|email\n
                                       Get group user by group-id and user-id."
                                         :swagger {:produces "application/json"}
                                         :content-type "application/json"
                                         :handler group-users/handle_get-group-user
                                         :middleware [wrap-authorize-admin!]
                                         :coercion reitit.coercion.schema/coercion

                                         ;:parameters {:path {:group-id s/Uuid :user-id s/Uuid}}
                                         :parameters {:path {:group-id s/Str :user-id s/Str}}

                                         :responses {200 {:body group-users/schema_export-group-user-simple}
                                                     404 {:description "Creation failed."
                                                          :schema s/Str
                                                          :examples {"application/json" {:message "No such group or user."}}}}}

                                   ; TODO error handling
                                   :put {:summary "Get group user by group-id and user-id"
                                         :description "Get group user by group-id and user-id."
                                         :swagger {:produces "application/json"}
                                         :content-type "application/json"
                                         :handler group-users/handle_add-group-user
                                         :middleware [wrap-authorize-admin!]
                                         :coercion reitit.coercion.schema/coercion

                                         ;; TODO: FIX: group-id and user-id are not uuids
                                         :parameters {:path {:group-id s/Str :user-id s/Str}}
                                         ;:parameters {:path {:group-id s/Uuid :user-id s/Uuid}}

                                         :responses {200 {:body {:users [group-users/schema_export-group-user-simple]}}
                                                     404 {:description "Creation failed."
                                                          :schema s/Str
                                                          :examples {"application/json" {:message "No such group or user."}}}}} ; TODO error handling
                                   :delete {:summary "Deletes a group-user by group-id and user-id"
                                            :description "Delete a group-user by group-id and user-id."
                                            ;:swagger {:produces "application/json"}
                                            ;:content-type "application/json"
                                            :handler group-users/handle_delete-group-user
                                            :middleware [wrap-authorize-admin!]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:group-id s/Uuid :user-id s/Uuid}}
                                            :responses {200 {:body {:users [group-users/schema_export-group-user-simple]}}
                                                        404 {:description "Not Found."
                                                             :schema s/Str
                                                             :examples {"application/json" {:message "No such group or user."}}}
                                                        406 {:body s/Str}}}}] ; TODO error handling
     ])
  ;### Debug ####################################################################
  ;(debug/debug-ns *ns*)
