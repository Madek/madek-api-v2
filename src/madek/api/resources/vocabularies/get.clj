(ns madek.api.resources.vocabularies.get
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
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

;### DEFS ##################################################################

(def admin.vocabularies {:summary "Get list of vocabularies ids."
                         :description "Get list of vocabularies ids."
                         :handler get-index
                         :middleware [wrap-authorize-admin!]
                         :content-type "application/json"
                         :swagger (generate-swagger-pagination-params)
                         :coercion reitit.coercion.schema/coercion
                         :responses {200 {:body {:vocabularies [(get-schema :vocabularies.schema_export-vocabulary-admin)]}}}})

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
                            :responses {200 {:body (get-schema :vocabularies.schema_export-vocabulary-admin)}
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
                                  :responses {200 {:body (get-schema :vocabularies.schema_export-perms_all)}
                                              404 {:description "Not found."
                                                   :schema s/Str
                                                   :examples {"application/json" {:message "No such vocabulary."}}}}})

(def admin.vocabularies.users {:summary (sd/sum_adm_todo "List vocabulary user permissions")
                               :handler permissions/handle_list-vocab-user-perms
                               :middleware [wrap-authorize-admin!]
                               :content-type "application/json"
                               :accept "application/json"
                               :coercion reitit.coercion.schema/coercion

                               :swagger {:produces "application/json"
                                         :parameters [{:name "id"
                                                       :in "path"
                                                       :description "e.g.: columns"
                                                       :type "string"
                                                       :required true
                                                       :pattern "^[a-z0-9\\-\\_\\:]+$"}]}

                               :responses {200 {:body [(get-schema :vocabularies.vocabulary_user_permissions)]}
                                           404 {:body s/Any}}})

(def admin.vocabularies.users.user_id {:summary (sd/sum_adm_todo "Get vocabulary user permissions")
                                       :handler permissions/handle_get-vocab-user-perms
                                       :middleware [wrap-authorize-admin!]
                                       :content-type "application/json"
                                       :accept "application/json"
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:id s/Str
                                                           :user_id s/Uuid}}
                                       :responses {200 {:body (get-schema :vocabularies.vocabulary_user_permissions)}
                                                   404 {:description "Not found."
                                                        :schema s/Str
                                                        :examples {"application/json" {:message "No such vocabulary user permission."}}}}})
;(def admin.vocabularies.group.group_id {:summary (sd/sum_adm_todo "List vocabulary group permissions")
(def admin.vocabularies.groups {:summary (sd/sum_adm_todo "List vocabulary group permissions")
                                :handler permissions/handle_list-vocab-group-perms
                                :middleware [wrap-authorize-admin!]
                                :content-type "application/json"

                                :swagger {:produces "application/json"
                                          :parameters [{:name "id"
                                                        :in "path"
                                                        :description "e.g.: columns"
                                                        :type "string"
                                                        :required true
                                                        :pattern "^[a-z0-9\\-\\_\\:]+$"}]}

                                :accept "application/json"
                                :coercion reitit.coercion.schema/coercion
                                :responses {200 {:body [(get-schema :vocabularies.schema_export-group-perms)]}}})

(def admin.vocabularies.group.group_id {:summary (sd/sum_adm_todo "Get vocabulary group permissions")
                                        :handler permissions/handle_get-vocab-group-perms
                                        :middleware [wrap-authorize-admin!]
                                        :content-type "application/json"
                                        :accept "application/json"
                                        :coercion reitit.coercion.schema/coercion
                                        :parameters {:path {:id s/Str
                                                            :group_id s/Uuid}}
                                        :responses {200 {:body (get-schema :vocabularies.schema_export-group-perms)}
                                                    404 {:description "Not found."
                                                         :schema s/Str
                                                         :examples {"application/json" {:message "No such vocabulary group permission."}}}}})

(def user.vocabularies {:summary "Get list of vocabularies ids."
                        :description "Get list of vocabularies ids."
                        :handler get-index
                        :content-type "application/json"
                        :coercion reitit.coercion.schema/coercion
                        :swagger (generate-swagger-pagination-params)
                        :responses {200 {:body {:vocabularies [(get-schema :vocabularies.schema_export-vocabulary)]}}}})

(def user.vocabularies.id {:summary "Get vocabulary by id."
                           ;:description "Get a vocabulary by id. Returns 404, if no such vocabulary exists."
                           :swagger {:produces "application/json"}
                           :content-type "application/json"
                           :handler get-vocabulary
                           :coercion reitit.coercion.schema/coercion
                           :parameters {:path {:id s/Str}}
                           :responses {200 {:body (get-schema :vocabularies.schema_export-vocabulary)}
                                       404 {:description "Creation failed."
                                            :schema s/Str
                                            :examples {"application/json" {:message "Vocabulary could not be found!"}}}}})
;### Debug ####################################################################
;(debug/debug-ns *ns*)
