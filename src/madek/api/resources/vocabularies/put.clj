(ns madek.api.resources.vocabularies.put
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.resources.vocabularies.common :as c]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [cast-to-hstore convert-map-if-exist mslurp]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [info]]))

(defn handle_update-vocab [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :path-params :id)
            dwid (assoc data :id id)
            tx (:tx req)
            dwid (convert-map-if-exist (cast-to-hstore dwid))
            old-data (dbh/query-eq-find-one :vocabularies :id id tx)]

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

;### DEFS ##################################################################

(def admin.vocabularies.id {:summary (sd/sum_adm_todo "Update vocabulary.")
                            :handler handle_update-vocab
                            :middleware [wrap-authorize-admin!]
                            :content-type "application/json"
                            :accept "application/json"
                            :coercion reitit.coercion.schema/coercion
                            :description (mslurp (io/resource "md/vocabularies-put.md"))
                            :parameters {:path {:id s/Str}
                                         :body c/schema_update-vocabulary}
                            :responses {200 {:description "Success." :body c/schema_export-vocabulary-admin}
                                        402 {:description "Bad request." :body {:body s/Any}}
                                        404 (sd/create-error-message-response "Not Found." "No such vocabulary.")}})

(def admin.vocabularies.id.perms {:summary (sd/sum_adm "Update vocabulary resource permissions")
                                  :handler handle_update-vocab
                                  :middleware [wrap-authorize-admin!]
                                  :content-type "application/json"
                                  :accept "application/json"
                                  :coercion reitit.coercion.schema/coercion
                                  :parameters {:path {:id s/Str}
                                               :body c/schema_perms-update}
                                  :responses {200 {:description "Success." :body c/schema_export-vocabulary}
                                              404 (sd/create-error-message-response "Not Found." "No such vocabulary.")
                                              406 (sd/create-error-message-response "Not Acceptable." "Could not update vocabulary.")}})

(def admin.vocabularies.users.user_id {:summary (sd/sum_adm "Update vocabulary user permissions")
                                       :handler permissions/handle_update-vocab-user-perms
                                       :middleware [wrap-authorize-admin!]
                                       :content-type "application/json"

                                       ;; TODO: remove this
                                       :description (str "TODO: REMOVE THIS | user_id: columns , id: d48e4387-b80d-45de-9077-5d88c331fa6a")

                                       :accept "application/json"
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:id s/Str
                                                           :user_id s/Uuid}
                                                    :body c/schema_perms-update-user-or-group}
                                       :responses {200 {:description "Returns the updated vocabulary user permission."
                                                        :body c/schema_export-user-perms}
                                                   406 (sd/create-error-message-response "Not Acceptable."
                                                                                         "Could not update vocabulary user permission")}})

(def admin.vocabularies.group.group_id {:summary (sd/sum_adm_todo "Update vocabulary group permissions")
                                        :handler permissions/handle_update-vocab-group-perms
                                        :middleware [wrap-authorize-admin!]
                                        :content-type "application/json"

                                        ;; TODO: remove this
                                        :description (str "TODO: REMOVE THIS | id: columns , id: ecb0de43-ccd2-463a-85a6-826c6ff99cdf")

                                        :accept "application/json"
                                        :coercion reitit.coercion.schema/coercion
                                        :parameters {:path {:id s/Str
                                                            :group_id s/Uuid}
                                                     :body c/schema_perms-update-user-or-group}
                                        :responses {200 {:description "Returns the updated vocabulary group permission."
                                                         :body c/schema_export-group-perms}
                                                    404 (sd/create-error-message-response "Not Found."
                                                                                          "No such vocabulary group permission")
                                                    406 (sd/create-error-message-response "Not Acceptable."
                                                                                          "Could not update vocabulary group permission")}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
