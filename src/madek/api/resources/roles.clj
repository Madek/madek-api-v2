(ns madek.api.resources.roles
  (:require
   [madek.api.resources.roles.role :as role]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.pagination :refer [pagination-validation-handler swagger-ui-pagination]]
   [reitit.coercion.schema]
   [schema.core :as s]))

(def schema_create-role
  {;:id s/Uuid
   :meta_key_id s/Str
   :labels sd/schema_ml_list
   ;:creator_id s/Uuid
   ;:created_at s/Any
   ;:updated_at s/Any
   })

(def schema_update-role
  {;:id s/Uuid
   ;:meta_key_id s/Str
   :labels sd/schema_ml_list
   ;:creator_id s/Uuid
   ;:created_at s/Any
   ;:updated_at s/Any
   })
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
   {:swagger {:tags ["roles"]}}
   ["roles" {:get {:summary "Get list of roles."
                   :description "Get list of roles."
                   :handler role/get-index
                   :middleware [(pagination-validation-handler)]
                   :swagger (swagger-ui-pagination)
                   :parameters {:query {}}
                   :coercion reitit.coercion.schema/coercion}
             :responses {200 {:body {:roles [schema_export-role]}}}}]

   ["roles/:id"
    {:get {:summary "Get role by id"
           :description "Get a role by id. Returns 404, if no such role exists."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler role/handle_get-role-usr
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body schema_export-role}
                       404 {:body s/Any}}}}]])

; switch to meta_key as address?
; TODO tests
(def admin-routes
  ["/"
   {:swagger {:tags ["admin/roles"]}}
   ["roles"
    {:get {:summary (sd/sum_adm "Get list of roles.")
           :description "Get list of roles."
           :handler role/get-index
           :middleware [wrap-authorize-admin!
                        (pagination-validation-handler)]
           :swagger (swagger-ui-pagination)
           :parameters {:query {}}
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:body {:roles [schema_export-role]}}}}

     :post {:summary (sd/sum_adm "Create role.")
            :handler role/handle_create-role
            :swagger {:produces "application/json"
                      :consumes "application/json"}
            :content-type "application/json"
            :accept "application/json"
            :middleware [wrap-authorize-admin!]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_create-role}
            :responses {200 {:body schema_export-role}
                        404 {:body s/Any}
                        403 {:description "Forbidden."
                             :schema s/Str
                             :examples {"application/json" {:message "Violation of constraint."}}}
                        406 {:description "Not Acceptable."
                             :schema s/Str
                             :examples {"application/json" {:message "Could not create role."}}}}}}]

   ["roles/:id"
    {:get {:summary (sd/sum_adm "Get role by id")
           :description "Get a role by id. Returns 404, if no such role exists."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :middleware [wrap-authorize-admin!]
           :handler role/handle_get-role-admin
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body schema_export-role}
                       404 {:body s/Any}}}

     :put {:summary (sd/sum_adm "Update role.")
           :handler role/handle_update-role
           :swagger {:produces "application/json"
                     :consumes "application/json"}
           :content-type "application/json"
           :middleware [wrap-authorize-admin!]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body schema_update-role}
           :responses {200 {:body schema_export-role}
                       404 {:body s/Any}
                       406 {:description "Not Acceptable."
                            :schema s/Str
                            :examples {"application/json" {:message "Could not update role."}}}}}

     :delete {:summary (sd/sum_adm "Delete role.")
              :handler role/handle_delete-role
              :swagger {:produces "application/json"}
              :content-type "application/json"
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:body schema_export-role}
                          404 {:description "Not found."
                               :schema s/Str
                               :examples {"application/json" {:message "No such role."}}}
                          406 {:description "Not Acceptable."
                               :schema s/Str
                               :examples {"application/json" {:message "Could not delete role."}}}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
