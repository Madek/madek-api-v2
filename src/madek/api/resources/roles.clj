(ns madek.api.resources.roles
  (:require
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.roles.role :as role]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.validation :refer [positive-number-0-to-100-validation positive-number-1-to-1000-validation]]
   [reitit.coercion.schema]
   [schema.core :as s]))

;(def schema_create-role
;  {;:id s/Uuid
;   :meta_key_id s/Str
;   :labels sd/schema_ml_list
;   ;:creator_id s/Uuid
;   ;:created_at s/Any
;   ;:updated_at s/Any
;   })
;
;(def schema_update-role
;  {;:id s/Uuid
;   ;:meta_key_id s/Str
;   :labels sd/schema_ml_list
;   ;:creator_id s/Uuid
;   ;:created_at s/Any
;   ;:updated_at s/Any
;   })
;(def schema_export-role
;  {:id s/Uuid
;   :meta_key_id s/Str
;   :labels sd/schema_ml_list
;   (s/optional-key :creator_id) s/Uuid
;   (s/optional-key :created_at) s/Any
;   (s/optional-key :updated_at) s/Any})

; TODO roles by meta_key_id ?
; TODO tests
(def user-routes
  ["/roles"
   {:swagger {:tags ["roles"]}}
   ["/" {:get {:summary "Get list of roles."
               :description "Get list of roles."
               :handler role/get-index
               :swagger {:produces "application/json"}

               ;; TODO: use swagger-definition with preset values & infos
               :parameters {:query {(s/optional-key :page) s/Int
                                    (s/optional-key :count) s/Int}}
               :content-type "application/json"
               :coercion reitit.coercion.schema/coercion}
         :responses {200 {:body {:roles [(get-schema :roles.schema_export-role)]}}}}]

   ["/:id"
    {:get {:summary "Get role by id"
           :description "Get a role by id. Returns 404, if no such role exists."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler role/handle_get-role-usr
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body (get-schema :roles.schema_export-role)}
                       404 {:body s/Any}}}}]])

; switch to meta_key as address?
; TODO tests
(def admin-routes
  ["/roles"
   {:swagger {:tags ["admin/roles"] :security [{"auth" []}]}}
   ["/" {:get {:summary (sd/sum_adm "Get list of roles.")
               :description "Get list of roles."
               :handler role/get-index
               :swagger {:produces "application/json"}

               ;; TODO: use swagger-definition with preset values & infos
               ;; Main problem: no validation within swagger-ui, no additional infos
               :parameters {:query {(s/required-key :page) positive-number-0-to-100-validation
                                    (s/required-key :count) positive-number-1-to-1000-validation}}

               :content-type "application/json"
               :coercion reitit.coercion.schema/coercion
               :responses {200 {:body {:roles [(get-schema :roles.schema_export-role)]}}}}

         :post {:summary (sd/sum_adm "Create role.")
                :handler role/handle_create-role
                :swagger {:produces "application/json"
                          :consumes "application/json"}
                :content-type "application/json"
                :accept "application/json"
                :coercion reitit.coercion.schema/coercion
                :parameters {:body (get-schema :roles.schema_create-role)}
                :responses {200 {:body (get-schema :roles.schema_export-role)}
                            404 {:body s/Any}
                            403 {:description "Forbidden."
                                 :schema s/Str
                                 :examples {"application/json" {:message "Violation of constraint."}}}
                            406 {:description "Not Acceptable."
                                 :schema s/Str
                                 :examples {"application/json" {:message "Could not create role."}}}}}}]

   ["/:id"
    {:get {:summary (sd/sum_adm "Get role by id")
           :description "Get a role by id. Returns 404, if no such role exists."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler role/handle_get-role-admin
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body (get-schema :roles.schema_export-role)}
                       404 {:body s/Any}}}

     :put {:summary (sd/sum_adm "Update role.")
           :handler role/handle_update-role
           :swagger {:produces "application/json"
                     :consumes "application/json"}
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body (get-schema :roles.schema_update-role)}
           :responses {200 {:body (get-schema :roles.schema_export-role)}
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
              :responses {200 {:body (get-schema :roles.schema_export-role)}
                          404 {:description "Not found."
                               :schema s/Str
                               :examples {"application/json" {:message "No such role."}}}
                          406 {:description "Not Acceptable."
                               :schema s/Str
                               :examples {"application/json" {:message "Could not delete role."}}}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
