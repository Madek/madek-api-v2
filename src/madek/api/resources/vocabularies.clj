(ns madek.api.resources.vocabularies
  (:require
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [madek.api.resources.shared :as sd]
    [madek.api.resources.vocabularies.index :refer [get-index]]
    [madek.api.resources.vocabularies.vocabulary :refer [get-vocabulary]]
    [reitit.coercion.schema]
    [schema.core :as s]
    
    [clojure.java.jdbc :as jdbc]
    [madek.api.utils.rdbms :as rdbms]
    [logbug.catcher :as catcher]))

; TODO logwrite


(defn handle_create-vocab [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            ins-res (jdbc/insert! (rdbms/get-ds) :vocabularies data)]
        (if-let [result (first ins-res)]
          (sd/response_ok result)
          (sd/response_failed "Could not create vocabulary." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-vocab [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :parameters :path :id)
            dwid (assoc data :id id)]
        (logging/info "handle_update-vocab"
                      "\nid\n" id "\ndwid\n" dwid)
        (if-let [upd-res (jdbc/update! (rdbms/get-ds) :vocabularies dwid ["id = ?" id])]
          (let [new-data (sd/query-eq-find-one :vocabularies :id id)]
            (logging/info "handle_update-vocab"
                          "\nid: " id "\nnew-data:\n" new-data)
            (sd/response_ok new-data))
          (sd/response_failed "Could not update vocabulary." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-vocab [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)
            old-data (sd/query-eq-find-one :vocabularies :id id)
            db-result (jdbc/delete! (rdbms/get-ds) :vocabularies ["id = ?" id])]
        (if (= 1 (first db-result))
          (sd/response_ok old-data)
          (sd/response_failed "Could not delete vocabulary." 406))))
    (catch Exception ex (sd/response_exception ex))))

(def schema_export-vocabulary
  {:id s/Str
   :enabled_for_public_view s/Bool
   :enabled_for_public_use s/Bool
   :position s/Int
   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)

   (s/optional-key :admin_comment) (s/maybe s/Str)
   
   })

(def schema_import-vocabulary
  {
   :id s/Str
   :enabled_for_public_view s/Bool
   :enabled_for_public_use s/Bool
   :position s/Int
   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)
   (s/optional-key :admin_comment) (s/maybe s/Str)
   })

(def schema_update-vocabulary
  {
   (s/optional-key :enabled_for_public_view) s/Bool
   (s/optional-key :enabled_for_public_use) s/Bool
   (s/optional-key :position) s/Int
   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
   (s/optional-key :admin_comment) (s/maybe s/Str)
   })

(def schema_perms-update
  {(s/optional-key :enabled_for_public_view) s/Bool
   (s/optional-key :enabled_for_public_use) s/Bool
   })

(def schema_perms-update-user-or-group
  {(s/optional-key :use) s/Bool
   (s/optional-key :view) s/Bool})

; TODO vocab permission
(def admin-routes
  ["/vocabularies"
   ["/"
    {:get {:summary "Get list of vocabularies ids."
           :description "Get list of vocabularies ids."
           :handler get-index
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :page) s/Int
                                (s/optional-key :count) s/Int
                                }}
           :responses {200 {:body {:vocabularies [schema_export-vocabulary]}}} ; TODO response coercion
           :swagger {:produces "application/json"}}

     :post {:summary (sd/sum_adm_todo "Create vocabulary.")
            :handler handle_create-vocab
            :content-type "application/json"
            :accept "application/json"
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_import-vocabulary}
            :responses {200 {:body s/Any} ; TODO response coercion
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
           :handler handle_update-vocab
           :content-type "application/json"
           :accept "application/json"
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str} 
                        :body schema_update-vocabulary}
           :responses {200 {:body s/Any}
                       404 {:body s/Any}
                       500 {:body s/Any}}
           :swagger {:consumes "application/json" :produces "application/json"}}

     :delete {:summary (sd/sum_adm_todo "Delete vocabulary.")
              :handler handle_delete-vocab
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

    ;["/resource"
    ; {:get
    ;  {:summary (sd/sum_adm_todo "Get vocabulary resource permissions")
    ;   :handler (constantly sd/no_impl)
    ;   :content-type "application/json"
    ;   :accept "application/json"
    ;   :coercion reitit.coercion.schema/coercion
    ;   :parameters {:path {:id s/Str}}
    ;   :responses {200 {:body s/Any}
    ;               404 {:body s/Any}}}

    ;  :put
    ;  {:summary (sd/sum_adm_todo "Update vocabulary resource permissions")
    ;   :handler (constantly sd/no_impl)
    ;   :content-type "application/json"
    ;   :accept "application/json"
    ;   :coercion reitit.coercion.schema/coercion
    ;   :parameters {:path {:id s/Str}
    ;                :body schema_perms-update}
    ;   :responses {200 {:body s/Any}
    ;               404 {:body s/Any}}}}]

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
               :responses {200 {:body {:vocabularies [ schema_export-vocabulary ]}}}
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
