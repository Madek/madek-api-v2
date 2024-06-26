(ns madek.api.resources.groups
  (:require [clj-uuid]
            [clojure.java.io :as io]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.pagination :as pagination]
            [madek.api.resources.groups.shared :as groups]
            [madek.api.resources.groups.users :as group-users]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.utils.auth :refer [wrap-authorize-admin!]]
            [madek.api.utils.helper :refer [convert-groupid f mslurp]]
            [madek.api.utils.pagination :refer [optional-pagination-params pagination-validation-handler swagger-ui-pagination]]
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
    {:status 404 :body "No such group found"})) ; TODO: toAsk 204 No Content

;### delete group ##############################################################

(defn delete-group [id tx]
  (let [sec (groups/jdbc-update-group-id-where-clause id)
        query (-> (sql/delete-from :groups)
                  (sql/where (:where sec))
                  sql-format)
        res (jdbc/execute-one! tx query)
        update-count (get res :next.jdbc/update-count)]

    (if (= 1 update-count)
      {:status 204 :content-type "application/json"} ;TODO / FIXME: response should support octet-stream as well?
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
        (dbh/build-query-param query-params :id)
        (dbh/build-query-param query-params :institutional_id)
        (dbh/build-query-param query-params :type)
        (dbh/build-query-param query-params :created_by_user_id)
        (dbh/build-query-param-like query-params :name)
        (dbh/build-query-param-like query-params :institutional_name)
        (dbh/build-query-param-like query-params :institution)
        (dbh/build-query-param-like query-params :searchable)
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
   (s/optional-key :type) s/Str ; TODO enum
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

(def schema_query-groups
  {(s/optional-key :id) s/Uuid
   (s/optional-key :name) s/Str
   (s/optional-key :type) s/Str
   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any
   (s/optional-key :institutional_id) s/Str
   (s/optional-key :institutional_name) s/Str
   (s/optional-key :institution) s/Str
   (s/optional-key :created_by_user_id) s/Uuid
   (s/optional-key :searchable) s/Str
   (s/optional-key :full_data) s/Bool})

(def user-routes
  [["/"
    {:swagger {:tags ["groups"]}}
    ["groups" {:get {:summary "Get all group ids"
                     :description "Get list of group ids. Paging is used as you get a limit of 100 entries."
                     :handler index
                     :swagger (swagger-ui-pagination)
                     :middleware [(pagination-validation-handler (merge optional-pagination-params schema_query-groups))]
                     :parameters {:query schema_query-groups}
                     :coercion reitit.coercion.schema/coercion
                     :responses {200 {:body {:groups [schema_export-group]}}}}}]

    ["groups/:id" {:get {:summary "Get group by id"
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
  ["/"
   {:swagger {:tags ["admin/groups"] :security [{"auth" []}]}}
   ["groups" {:get {:summary (f "Get all group ids" " / TODO: no-input-validation")
                    :description "Get list of group ids. Paging is used as you get a limit of 100 entries."
                    :handler index
                    :middleware [wrap-authorize-admin!
                                 (pagination-validation-handler (merge optional-pagination-params schema_query-groups))]
                    :swagger (swagger-ui-pagination)
                    :parameters {:query schema_query-groups}
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

   ["groups/:id" {:get {:summary "Get group by id OR institutional-id"
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
                        :description (mslurp (io/resource "md/admin-groups-put.md"))
                        :middleware [wrap-authorize-admin!]
                        :coercion reitit.coercion.schema/coercion
                        :parameters {:path {:id s/Uuid}
                                     :body schema_update-group}
                        :responses {200 {:body s/Any} ;groups/schema_export-group}
                                    404 {:body s/Any}}}}] ; TODO error handling

   ; groups-users/ring-routes
   ["groups/:group-id/users" {:get {:summary "Get group users by id"
                                    :description "Get group users by id. (zero-based paging)"
                                    :content-type "application/json"
                                    :handler group-users/handle_get-group-users
                                    :middleware [wrap-authorize-admin!
                                                 (pagination-validation-handler)]
                                    :coercion reitit.coercion.schema/coercion
                                    :swagger (swagger-ui-pagination)
                                    :parameters {:path {:group-id s/Uuid}}
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

   ["groups/:group-id/users/:user-id" {:get {:summary "Get group user by group-id and user-id"
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
