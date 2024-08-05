(ns madek.api.resources.vocabularies.get
  (:require
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [madek.api.resources.vocabularies.common :as c]
   [madek.api.resources.vocabularies.index :refer [get-index]]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [madek.api.resources.vocabularies.vocabulary :refer [get-vocabulary]]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [reitit.coercion.schema]
   [schema.core :as s]))

;### DEFS ##################################################################

(def admin.vocabularies {:summary "Get list of vocabularies ids. DES!!"
                         :description "Get list of vocabularies ids."
                         :handler get-index
                         :middleware [wrap-authorize-admin!]
                         :content-type "application/json"
                         :swagger (jqh/generate-swagger-pagination-params)
                         :coercion reitit.coercion.schema/coercion
                         :responses {200 {:description "Returns the list of vocabularies."
                                          :body {:vocabularies [c/schema_export-vocabulary-admin]}}}})

(def admin.vocabularies.id {:summary (sd/sum_adm "Get vocabulary by id.")
                            :handler get-vocabulary
                            :middleware [wrap-authorize-admin!]
                            :swagger {:produces "application/json"}
                            :content-type "application/json"

                            ;:description "Get a vocabulary by id. Returns 404, if no such vocabulary exists."
                            ;; TODO: remove this
                            :description (str "TODO: REMOVE THIS | id: media_content")

                            :coercion reitit.coercion.schema/coercion
                            :parameters {:path {:id s/Str}}
                            :responses {200 {:description "Returns the vocabulary."
                                             :body c/schema_export-vocabulary-admin}
                                        404 {:description "Creation failed."
                                             :schema s/Str
                                             :examples {"application/json" {:message "Vocabulary could not be found!"}}}}})

(def admin.vocabularies.id.perms {:summary (sd/sum_adm "List vocabulary permissions")
                                  :handler permissions/handle_list-vocab-perms
                                  :middleware [wrap-authorize-admin!]
                                  :content-type "application/json"
                                  :accept "application/json"
                                  :coercion reitit.coercion.schema/coercion
                                  :parameters {:path {:id s/Str}}
                                  :responses {200 {:description "Returns the list of vocabulary permissions."
                                                   :body c/schema_export-perms_all}
                                              404 {:description "Not found."
                                                   :schema s/Str
                                                   :examples {"application/json" {:message "No such vocabulary."}}}}})

(def admin.vocabularies.users {:summary (sd/sum_adm_todo "List vocabulary user permissions")
                               :handler permissions/handle_list-vocab-user-perms
                               :middleware [wrap-authorize-admin!]
                               :content-type "application/json"
                               :accept "application/json"
                               :coercion reitit.coercion.schema/coercion
                               :parameters {:path {:id s/Str}}
                               :responses {200 {:description "Returns the list of vocabulary user permissions."
                                                :body [c/schema_export-user-perms]}
                                           404 {:description "Not found."
                                                :body s/Any}}})

(def admin.vocabularies.users.user_id {:summary (sd/sum_adm_todo "Get vocabulary user permissions")
                                       :handler permissions/handle_get-vocab-user-perms
                                       :middleware [wrap-authorize-admin!]
                                       :content-type "application/json"
                                       :accept "application/json"
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:id s/Str
                                                           :user_id s/Uuid}}
                                       :responses {200 {:description "Returns the vocabulary user permission."
                                                        :body c/schema_export-user-perms}
                                                   404 {:description "Not found."
                                                        :schema s/Str
                                                        :examples {"application/json" {:message "No such vocabulary user permission."}}}}})

(def admin.vocabularies.groups {:summary (sd/sum_adm_todo "List vocabulary group permissions")
                                :handler permissions/handle_list-vocab-group-perms
                                :middleware [wrap-authorize-admin!]
                                :content-type "application/json"
                                :parameters {:path {:id s/Str}}
                                :accept "application/json"
                                :coercion reitit.coercion.schema/coercion
                                :responses {200 {:description "Returns the list of vocabulary group permissions."
                                                 :body [c/schema_export-group-perms]}}})

(def admin.vocabularies.group.group_id {:summary (sd/sum_adm_todo "Get vocabulary group permissions")
                                        :handler permissions/handle_get-vocab-group-perms
                                        :middleware [wrap-authorize-admin!]
                                        :content-type "application/json"
                                        :accept "application/json"
                                        :coercion reitit.coercion.schema/coercion
                                        :parameters {:path {:id s/Str
                                                            :group_id s/Uuid}}
                                        :responses {200 {:description "Returns the vocabulary group permission."
                                                         :body c/schema_export-group-perms}
                                                    404 {:description "Not found."
                                                         :schema s/Str
                                                         :examples {"application/json" {:message "No such vocabulary group permission."}}}}})

(def user.vocabularies {:summary "Get list of vocabularies ids."
                        :description "Get list of vocabularies ids."
                        :handler get-index
                        :content-type "application/json"
                        :coercion reitit.coercion.schema/coercion
                        :swagger (jqh/generate-swagger-pagination-params)
                        :responses {200 {:description "Returns the list of vocabularies."
                                         :body {:vocabularies [c/schema_export-vocabulary]}}}})

(def user.vocabularies.id {:summary "Get vocabulary by id."
                           :swagger {:produces "application/json"}
                           :content-type "application/json"
                           :handler get-vocabulary
                           :coercion reitit.coercion.schema/coercion
                           :parameters {:path {:id s/Str}}
                           :responses {200 {:description "Returns the vocabulary."
                                            :body c/schema_export-vocabulary}
                                       404 {:description "Creation failed."
                                            :schema s/Str
                                            :examples {"application/json" {:message "Vocabulary could not be found!"}}}}})
;### Debug ####################################################################
;(debug/debug-ns *ns*)
