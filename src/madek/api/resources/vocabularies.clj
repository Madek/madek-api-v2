(ns madek.api.resources.vocabularies
  (:require
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [madek.api.resources.shared :as sd]
    [madek.api.resources.vocabularies.index :refer [get-index]]
    [madek.api.resources.vocabularies.vocabulary :refer [get-vocabulary]]
    [reitit.coercion.schema]
    [schema.core :as s]
    ))

;(def routes
;  (cpj/routes
;    (cpj/GET "/vocabularies/" _ get-index)
;    (cpj/GET "/vocabularies/:id" _ get-vocabulary)
;    (cpj/ANY "*" _ sd/dead-end-handler)
;    ))



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

(def schema_update-vocabulary
  {(s/optional-key :enabled_for_public_view) s/Bool
   (s/optional-key :enabled_for_public_use) s/Bool
   (s/optional-key :position) s/Int
   (s/optional-key :labels) {:de s/Str :en s/Str s/Str s/Str}
   (s/optional-key :descriptions) {:de s/Str :en s/Str s/Str s/Str}})

(def schema_perms-update
  {(s/optional-key :enabled_for_public_view) s/Bool
   (s/optional-key :enabled_for_public_use) s/Bool
   })

(def schema_perms-update-user-or-group
  {(s/optional-key :use) s/Bool
   (s/optional-key :view) s/Bool})


(def admin-routes
  ["/vocabularies"
   ["/"
    {:get {:summary "Get list of vocabularies ids."
           :description "Get list of vocabularies ids."
           :handler get-index
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :page) s/Int}}
           :responses {200 {:body {:vocabularies [s/Any]}}};{:id s/Str}]}}}
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

   ["/:id"
    {:get {:summary "Get vocabulary by id."
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
           :parameters {:path {:id s/Str} :body schema_update-vocabulary}
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
   
   ["/:id/perms"
    ["/"
     {:get
      {:summary (sd/sum_adm_todo "List vocabulary permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str}}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}}]

    ["/resource"
     {:get
      {:summary (sd/sum_adm_todo "Get vocabulary resource permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str}}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}

      :put
      {:summary (sd/sum_adm_todo "Update vocabulary resource permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str}
                    :body schema_perms-update}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}}]

    ["/user"
     {:get
      {:summary (sd/sum_adm_todo "List vocabulary user permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str}}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}}]

    ["/user/:user_id"
     {:get
      {:summary (sd/sum_adm_todo "Get vocabulary user permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :user_id s/Uuid}}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}
      :post
      {:summary (sd/sum_adm_todo "Create vocabulary user permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :user_id s/Uuid}
                    :body schema_perms-update-user-or-group}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}

      :put
      {:summary (sd/sum_adm_todo "Update vocabulary user permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :user_id s/Uuid}
                    :body schema_perms-update-user-or-group}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}

      :delete
      {:summary (sd/sum_adm_todo "Delete vocabulary user permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :user_id s/Uuid}}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}}]

    ["/group"
     {:get
      {:summary (sd/sum_adm_todo "List vocabulary group permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :group_id s/Uuid}}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}}]

    ["/group/:group_id"
     {:get
      {:summary (sd/sum_adm_todo "Get vocabulary group permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :group_id s/Uuid}}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}

      :post
      {:summary (sd/sum_adm_todo "Create vocabulary group permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :group_id s/Uuid}
                    :body schema_perms-update-user-or-group}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}

      :put
      {:summary (sd/sum_adm_todo "Update vocabulary group permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :group_id s/Uuid}
                    :body schema_perms-update-user-or-group}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}

      :delete
      {:summary (sd/sum_adm_todo "Delete vocabulary group permissions")
       :handler (constantly sd/no_impl)
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :group_id s/Uuid}}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}}}}]]])


; TODO user routes
; TODO post, patch, delete
; TODO tests
(def user-routes
  ["/vocabularies"
   ["/" {:get {:summary "Get list of vocabularies ids."
               :description "Get list of vocabularies ids."
               :handler get-index
               :content-type "application/json"
               :coercion reitit.coercion.schema/coercion
               :parameters {:query {(s/optional-key :page) s/Int}}
               :responses {200 {:body {:vocabularies [{:id s/Str}]}}}
               :swagger {:produces "application/json"}}}]

   ["/:id" {:get {:summary "Get vocabulary by id."
                  :description "Get a vocabulary by id. Returns 404, if no such vocabulary exists."
                  :swagger {:produces "application/json"}
                  :content-type "application/json"
                  :handler get-vocabulary
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Str}}
                  :responses {200 {:body schema_export-vocabulary}
                              404 {:body s/Any}}}}]
   
   
  ]
  )

;### Debug ####################################################################
;(debug/debug-ns *ns*)
