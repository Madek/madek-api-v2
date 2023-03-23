(ns madek.api.resources.vocabularies
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.pagination :as pagination]
    [madek.api.resources.shared :as sd]
    [madek.api.resources.vocabularies.index :refer [get-index]]
    [madek.api.resources.vocabularies.vocabulary :refer [get-vocabulary]]
    [madek.api.utils.rdbms :as rdbms]
    [reitit.coercion.schema]
    [schema.core :as s]
    ))

(def routes
  (cpj/routes
    (cpj/GET "/vocabularies/" _ get-index)
    (cpj/GET "/vocabularies/:id" _ get-vocabulary)
    (cpj/ANY "*" _ sd/dead-end-handler)
    ))



(def schema_export-vocabulary
  {:id s/Str
   :enabled_for_public_view s/Bool
   :enabled_for_public_use s/Bool
   :position s/Int
   :labels {:de s/Str :en s/Str s/Str s/Str}
   :descriptions {:de s/Str :en s/Str s/Str s/Str}
   :description s/Str
   :label s/Str})

(def schema_import-vocabulary
  {:id s/Str
   :enabled_for_public_view s/Bool
   :enabled_for_public_use s/Bool
   :position s/Int
   :labels {:de s/Str :en s/Str s/Str s/Str}
   :descriptions {:de s/Str :en s/Str s/Str s/Str}})

; TODO user routes
; TODO post, patch, delete
; TODO tests
(def ring-routes
  ["/vocabularies"
   ["/" {:get {:summary "Get list of vocabularies ids."
               :description "Get list of vocabularies ids."
               :handler get-index
               :content-type "application/json"
               :coercion reitit.coercion.schema/coercion
               :parameters {:query {(s/optional-key :page) s/Int}}
               :responses {200 {:body {:vocabularies [{:id s/Str}]}}}
               :swagger {:produces "application/json"}}

         :post {:summary (sd/sum_adm_todo "Create vocabulary.")
                :handler (constantly sd/no_impl)
                :content-type "application/json"
                :accept "application/json"
                :coercion reitit.coercion.schema/coercion
                :parameters {:body schema_import-vocabulary}
                :responses {200 {:body schema_export-vocabulary}
                            406 {:body s/Any}}
                :swagger {:consumes "application/json" :produces "application/json"}}}]

   ["/:id" {:get {:summary "Get vocabulary by id."
                  :description "Get a vocabulary by id. Returns 404, if no such vocabulary exists."
                  :swagger {:produces "application/json"}
                  :content-type "application/json"
                  :handler get-vocabulary
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Str}}
                  :responses {200 {:body schema_export-vocabulary}
                              404 {:body s/Any}}}

            :put {:summary (sd/sum_adm_todo "Update vocabulary.")
                  :handler (constantly sd/no_impl)
                  :content-type "application/json"
                  :accept "application/json"
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Str} :body schema_import-vocabulary}
                  :responses {200 {:body schema_export-vocabulary}
                              404 {:body s/Any}
                              500 {:body s/Any}}
                  :swagger {:consumes "application/json" :produces "application/json"}}

            :delete {:summary (sd/sum_adm_todo "Delete vocabulary.")
                     :handler (constantly sd/no_impl)
                     :content-type "application/json"
                     :accept "application/json"
                     :coercion reitit.coercion.schema/coercion
                     :parameters {:path {:id s/Str}}
                     :responses {200 {:body s/Any}
                                 404 {:body s/Any}}
                     :swagger {:produces "application/json"}}}]
  ]
  )

;### Debug ####################################################################
;(debug/debug-ns *ns*)
