(ns madek.api.resources.vocabularies.get
  (:require
   [clojure.spec.alpha :as sa]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.vocabularies.common :as c]
   [madek.api.resources.vocabularies.index :refer [get-index]]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [madek.api.resources.vocabularies.vocabulary :refer [get-vocabulary]]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [reitit.coercion.schema]
   [schema.core :as s]
   [spec-tools.core :as st]))

(sa/def ::descriptions map?)

(sa/def :adm/schema_export-vocabulary
  (sa/keys :req-un [::sp/id ::sp/position ::sp/labels ::descriptions]
           :opt-un [::sp/admin_comment]))

(sa/def :adm/vocabularies-response (st/spec {:spec (sa/coll-of :adm/schema_export-vocabulary)
                                             :description "A list of vocabularies"}))

(sa/def :adm/vocabularies-response-combined
  (st/spec
   {:spec (sa/or :flat :adm/vocabularies-response
                 :paginated :adm/context-keys-response-paginated)
    :description "Supports both flat and paginated full_texts formats"}))

;### DEFS ##################################################################

(def admin.vocabularies {:summary "Get list of vocabularies ids."
                         :description "Get list of vocabularies ids."
                         :handler get-index
                         :middleware [wrap-authorize-admin!]
                         :content-type "application/json"
                         :coercion reitit.coercion.spec/coercion
                         :parameters {:query sp/schema_pagination_opt}
                         :responses {200 {:description "Returns the list of vocabularies."
                                          :body :adm/vocabularies-response-combined}}})

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
                                        404 (sd/create-error-message-response "Creation failed."
                                                                              "Vocabulary could not be found!")}})

(def admin.vocabularies.id.perms {:summary (sd/sum_adm "List vocabulary permissions")
                                  :handler permissions/handle_list-vocab-perms
                                  :middleware [wrap-authorize-admin!]
                                  :content-type "application/json"
                                  :accept "application/json"
                                  :coercion reitit.coercion.schema/coercion
                                  :parameters {:path {:id s/Str}}
                                  :responses {200 {:description "Returns the list of vocabulary permissions."
                                                   :body c/schema_export-perms_all}
                                              404 (sd/create-error-message-response "Not found." "No such vocabulary.")}})

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
                                                   404 (sd/create-error-message-response "Not found."
                                                                                         "No such vocabulary user permission.")}})

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
                                                    404 (sd/create-error-message-response "Not found."
                                                                                          "No such vocabulary group permission.")}})

(def user.vocabularies {:summary "Get list of vocabularies ids."
                        :description "Get list of vocabularies ids."
                        :handler get-index
                        :content-type "application/json"
                        :coercion reitit.coercion.spec/coercion
                        :parameters {:query sp/schema_pagination_opt}
                        :responses {200 {:description "Returns the list of vocabularies."
                                         :body :adm/vocabularies-response-combined}}})
(def user.vocabularies.id {:summary "Get vocabulary by id."
                           :swagger {:produces "application/json"}
                           :content-type "application/json"
                           :handler get-vocabulary
                           :coercion reitit.coercion.schema/coercion
                           :parameters {:path {:id s/Str}}
                           :responses {200 {:description "Returns the vocabulary."
                                            :body c/schema_export-vocabulary}
                                       404 (sd/create-error-message-response "Creation failed."
                                                                             "Vocabulary could not be found!")}})
;### Debug ####################################################################
;(debug/debug-ns *ns*)
