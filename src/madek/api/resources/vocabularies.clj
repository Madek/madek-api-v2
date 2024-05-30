(ns madek.api.resources.vocabularies
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]

   [madek.api.resources.vocabularies.get :as get]
   [madek.api.resources.vocabularies.delete :as delete]
   [madek.api.resources.vocabularies.post :as post]

   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.shared :as sd]
   [madek.api.resources.shared :refer [generate-swagger-pagination-params]]
   [madek.api.resources.vocabularies.index :refer [get-index]]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [madek.api.resources.vocabularies.vocabulary :refer [get-vocabulary]]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [cast-to-hstore convert-map-if-exist mslurp]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [info]]))

; TODO logwrite

(defn handle_create-vocab [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            sql-query (-> (sql/insert-into :vocabularies)
                          (sql/values [(convert-map-if-exist (cast-to-hstore data))])
                          (sql/returning :*)
                          sql-format)
            ins-res (jdbc/execute-one! (:tx req) sql-query)
            ins-res (sd/transform_ml_map ins-res)]

        (if ins-res
          (sd/response_ok ins-res)
          (sd/response_failed "Could not create vocabulary." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-vocab [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :path-params :id)
            dwid (assoc data :id id)
            tx (:tx req)
            dwid (convert-map-if-exist (cast-to-hstore dwid))
            old-data (sd/query-eq-find-one :vocabularies :id id tx)]

        (if old-data
          (let [is_admin_endpoint (str/includes? (-> req :uri) "/admin/")
                cols (if is_admin_endpoint [:*] [:id :position :labels :descriptions :admin_comment])
                sql-query (-> (sql/update :vocabularies)
                              (sql/set dwid)
                              (sql/where [:= :id id]))
                sql-query (apply sql/returning sql-query cols)
                sql-query (-> sql-query
                              sql-format)
                upd-res (jdbc/execute-one! tx sql-query)
                upd-res (sd/transform_ml_map upd-res)]

            (if upd-res
              (do
                (info "handle_update-vocab" "\nid: " id "\nnew-data:\n" upd-res)
                (sd/response_ok upd-res))
              (sd/response_failed "Could not update vocabulary." 406)))
          (sd/response_not_found "No such vocabulary."))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-vocab [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)
            tx (:tx req)]
        (if-let [old-data (sd/query-eq-find-one :vocabularies :id id tx)]
          (let [sql-query (-> (sql/delete-from :vocabularies)
                              (sql/where [:= :id id])
                              (sql/returning :*)
                              sql-format)
                db-result (jdbc/execute-one! tx sql-query)]

            (if db-result
              (sd/response_ok (sd/transform_ml_map old-data))
              (sd/response_failed "Could not delete vocabulary." 406)))
          (sd/response_not_found "No such vocabulary."))))
    (catch Exception ex (sd/parsed_response_exception ex))))

;(def schema_export-vocabulary
;  {:id s/Str
;   :position s/Int
;   :labels (s/maybe sd/schema_ml_list)
;   :descriptions (s/maybe sd/schema_ml_list)
;   (s/optional-key :admin_comment) (s/maybe s/Str)})
;
;(def schema_export-vocabulary-admin
;  {:id s/Str
;   :enabled_for_public_view s/Bool
;   :enabled_for_public_use s/Bool
;   :position s/Int
;   :labels (s/maybe sd/schema_ml_list)
;   :descriptions (s/maybe sd/schema_ml_list)
;   (s/optional-key :admin_comment) (s/maybe s/Str)})
;
;(def schema_import-vocabulary
;  {:id s/Str
;   :enabled_for_public_view s/Bool
;   :enabled_for_public_use s/Bool
;   :position s/Int
;   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
;   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
;   (s/optional-key :admin_comment) (s/maybe s/Str)})
;
;(def schema_update-vocabulary
;  {;(s/optional-key :enabled_for_public_view) s/Bool
;   ;(s/optional-key :enabled_for_public_use) s/Bool
;   (s/optional-key :position) s/Int
;   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
;   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
;   (s/optional-key :admin_comment) (s/maybe s/Str)})
;
;(def schema_perms-update
;  {(s/optional-key :enabled_for_public_view) s/Bool
;   (s/optional-key :enabled_for_public_use) s/Bool})
;
;(def schema_perms-update-user-or-group
;  {(s/optional-key :use) s/Bool
;   (s/optional-key :view) s/Bool})
;
;(def schema_export-user-perms
;  {:id s/Uuid
;   :user_id s/Uuid
;   :vocabulary_id s/Str
;   :use s/Bool
;   :view s/Bool})
;
;(def schema_export-group-perms
;  {:id s/Uuid
;   :group_id s/Uuid
;   :vocabulary_id s/Str
;   :use s/Bool
;   :view s/Bool})
;
;;(def schema_export-perms_all
;;  {:vocabulary {:id s/Str
;;                :enabled_for_public_view s/Bool
;;                :enabled_for_public_use s/Bool}
;;
;;   :users [schema_export-user-perms]
;;   :groups [schema_export-group-perms]})

; TODO vocab permission
(def admin-routes
  ["/vocabularies"
   {:swagger {:tags ["admin/vocabularies"] :security [{"auth" []}]}}
   ["/"
    {:get get/admin.vocabularies

     :post post/admin.vocabularies}]

   ["/:id"
    {:get get/admin.vocabularies.id

     :put {:summary (sd/sum_adm_todo "Update vocabulary.")
           :handler handle_update-vocab
           :middleware [wrap-authorize-admin!]
           :content-type "application/json"
           :accept "application/json"
           :coercion reitit.coercion.schema/coercion

           :description (mslurp "./md/vocabularies-put.md")

           :swagger {:produces "application/json"
                     :consumes "application/json"
                     :parameters [{:name "id"
                                   :in "path"
                                   :type "string"
                                   :required true}]}

           :parameters {:body (get-schema :vocabularies.schema_update-vocabulary)}
           :responses {200 {:body (get-schema :vocabularies.schema_export-vocabulary-admin)}
                       400 {:body s/Any}
                       404 {:description "Not found."
                            :schema s/Str
                            :examples {"application/json" {:message "No such vocabulary."}}}}}

     :delete delete/admin.vocabularies.id}]

   ["/:id/perms"
    ["/"
     {:get get/admin.vocabularies.id.perms


      :put
      {:summary (sd/sum_adm "Update vocabulary resource permissions")
       :handler handle_update-vocab
       :middleware [wrap-authorize-admin!]
       :content-type "application/json"
       :accept "application/json"
       :coercion reitit.coercion.schema/coercion

       ;; FIXME: input-validation is missing
       :parameters {:path {:id s/Str}
                    :body (get-schema :vocabularies.schema_perms-update)}

       :responses {200 {:body (get-schema :vocabularies.schema_export-vocabulary)}
                   404 {:description "Not found."
                        :schema s/Str
                        :examples {"application/json" {:message "No such vocabulary."}}}
                   406 {:description "Not Acceptable."
                        :schema s/Str
                        :examples {"application/json" {:message "Could not update vocabulary."}}}}}}]

    ["/users"
     {:get get/admin.vocabularies.users
      }]

    ["/user/:user_id"
     {:get get/admin.vocabularies.users.user_id

      :post post/admin.vocabularies.users.user_id


      :put
      {:summary (sd/sum_adm "Update vocabulary user permissions")
       :handler permissions/handle_update-vocab-user-perms
       :middleware [wrap-authorize-admin!]
       :content-type "application/json"

       ;; TODO: remove this
       :description (str "TODO: REMOVE THIS | user_id: columns , id: d48e4387-b80d-45de-9077-5d88c331fa6a")

       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :user_id s/Uuid}
                    :body (get-schema :vocabularies.schema_perms-update-user-or-group)}
       :responses {200 {:body (get-schema :vocabularies.vocabulary_user_permissions)}
                   406 {:description "Not Acceptable."
                        :schema s/Str
                        :examples {"application/json" {:message "Could not update vocabulary user permission"}}}}}

      :delete delete/admin.vocabularies.user.user_id
      }]

    ["/groups"
     {:get get/admin.vocabularies.groups
      }]

    ["/group/:group_id"
     {:get get/admin.vocabularies.group.group_id


      :post post/admin.vocabularies.group.group_id


      :put
      {:summary (sd/sum_adm_todo "Update vocabulary group permissions")
       :handler permissions/handle_update-vocab-group-perms
       :middleware [wrap-authorize-admin!]
       :content-type "application/json"

       ;; TODO: remove this
       :description (str "TODO: REMOVE THIS | id: columns , id: ecb0de43-ccd2-463a-85a6-826c6ff99cdf")

       :accept "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:id s/Str
                           :group_id s/Uuid}
                    :body (get-schema :vocabularies.schema_perms-update-user-or-group)}
       :responses {200 {:body (get-schema :vocabularies.schema_export-group-perms)}
                   404 {:description "Not Found."
                        :schema s/Str
                        :examples {"application/json" {:message "No such vocabulary group permission"}}}
                   406 {:description "Not Acceptable."
                        :schema s/Str
                        :examples {"application/json" {:message "Could not update vocabulary group permission"}}}}}

      :delete delete/admin.vocabularies.group.group_id
      }]]])

(def user-routes
  ["/vocabularies"
   {:swagger {:tags ["vocabulary"]}}
   ["/" {:get get/user.vocabularies}]

   ["/:id" {:get get/user.vocabularies.id}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
