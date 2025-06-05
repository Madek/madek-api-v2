(ns madek.api.resources.roles
  (:require
   [clojure.spec.alpha :as sa]
   [madek.api.resources.roles.role :as role]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]
   [schema.core :as s]
   [spec-tools.core :as st]))

(def schema_create-role
  {:meta_key_id s/Str
   :labels sd/schema_ml_list})

(def schema_update-role
  {:labels sd/schema_ml_list})

(sa/def :roles-resp-def/role (sa/keys :req-un [::sp/id ::sp/meta_key_id ::sp/labels]
                                      :opt-un [::sp/creator_id ::sp/created_at ::sp/updated_at]))

(sa/def :roles-resp-def/roles (st/spec {:spec (sa/coll-of :roles-resp-def/role)
                                        :description "A list of roles"}))
(sa/def ::response-roles-body (sa/keys :opt-un [:roles-resp-def/roles ::sp/data ::sp/pagination]))

(def schema_export-role
  {:id s/Uuid
   :meta_key_id s/Str
   :labels sd/schema_ml_list
   (s/optional-key :creator_id) s/Uuid
   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any})

; TODO roles by meta_key_id ?
; TODO tests
(def user-routes
  ["/"
   {:openapi {:tags ["roles"]}}
   ["roles/" {:get {:summary "Get list of roles."
                    :description "Get list of roles."
                    :handler role/get-index
                    :coercion spec/coercion
                    :parameters {:query sp/schema_pagination_opt}
                    :responses {200 {:description "Returns the roles."
                                     :body ::response-roles-body}}}}]

   ["roles/:id"
    {:get {:summary "Get role by id"
           :description "Get a role by id. Returns 404, if no such role exists."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler role/handle_get-role-usr
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:description "Returns the role."
                            :body schema_export-role}
                       404 {:description "Not found."
                            :body s/Any}}}}]])

; switch to meta_key as address?
; TODO tests
(def admin-routes
  ["/"
   {:openapi {:tags ["admin/roles"] :security ADMIN_AUTH_METHODS}}
   ["roles/"
    {:get {:summary (sd/sum_adm "Get list of roles.")
           :description "Get list of roles."
           :handler role/get-index
           :middleware [wrap-authorize-admin!]
           :coercion spec/coercion
           :parameters {:query sp/schema_pagination_opt}
           :responses {200 {:description "Returns the roles."
                            :body ::response-roles-body}}}

     :post {:summary (sd/sum_adm "Create role.")
            :handler role/handle_create-role
            :swagger {:produces "application/json"
                      :consumes "application/json"}
            :content-type "application/json"
            :accept "application/json"
            :middleware [wrap-authorize-admin!]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_create-role}
            :responses {200 {:description "Returns the created role."
                             :body schema_export-role}
                        403 (sd/create-error-message-response "Forbidden." "Violation of constraint.")
                        404 {:description "Not found."
                             :body s/Any}
                        406 (sd/create-error-message-response "Not Acceptable." "Could not create role.")}}}]

   ["roles/:id"
    {:get {:summary (sd/sum_adm "Get role by id")
           :description "Get a role by id. Returns 404, if no such role exists."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :middleware [wrap-authorize-admin!]
           :handler role/handle_get-role-admin
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:description "Returns the role."
                            :body schema_export-role}
                       404 {:description "Not found."
                            :body s/Any}}}

     :put {:summary (sd/sum_adm "Update role.")
           :handler role/handle_update-role
           :swagger {:produces "application/json"
                     :consumes "application/json"}
           :content-type "application/json"
           :middleware [wrap-authorize-admin!]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body schema_update-role}
           :responses {200 {:description "Returns the updated role."
                            :body schema_export-role}
                       404 {:description "Not found."
                            :body s/Any}
                       406 (sd/create-error-message-response "Not Acceptable." "Could not update role.")}}

     :delete {:summary (sd/sum_adm "Delete role.")
              :handler role/handle_delete-role
              :swagger {:produces "application/json"}
              :content-type "application/json"
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:description "Returns the deleted role."
                               :body schema_export-role}
                          404 (sd/create-error-message-response "Not Found." "No such role.")
                          406 (sd/create-error-message-response "Not Acceptable." "Could not delete role.")}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
