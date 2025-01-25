(ns madek.api.resources.vocabularies.post
  (:require
   [clojure.java.io :as io]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.vocabularies.common :as c]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [cast-to-hstore convert-map-if-exist mslurp]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

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

;### DEFS ##################################################################

(def admin.vocabularies {:summary (sd/sum_adm "Create vocabulary.")
                         :handler handle_create-vocab
                         :middleware [wrap-authorize-admin!]

                         :description (mslurp (io/resource "md/vocabularies-post.md"))

                         :content-type "application/json"
                         :accept "application/json"
                         :coercion reitit.coercion.schema/coercion
                         :parameters {:body c/schema_import-vocabulary}
                         :responses {200 {:description "Returns the created vocabulary."
                                          :body c/schema_export-vocabulary-admin}
                                     406 (sd/create-error-message-response "Creation failed." "Could not create vocabulary.")
                                     ;; FIXME: change to 409
                                     500 (sd/create-error-message-response "Duplicate key" "ERROR: duplicate key value violates unique constraint 'vocabularies_pkey' Detail: Key (id)=(toni_dokumentation2) already exists.")}
                         :swagger {:consumes "application/json" :produces "application/json"}})

(def admin.vocabularies.users.user_id {:summary (sd/sum_adm "Create vocabulary user permissions")
                                       :handler permissions/handle_create-vocab-user-perms

                                       ;; TODO: remove this
                                       :description (str "TODO: REMOVE THIS | user_id: columns , id: d48e4387-b80d-45de-9077-5d88c331fa6a")

                                       :middleware [wrap-authorize-admin!]
                                       :content-type "application/json"
                                       :accept "application/json"
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:id s/Str
                                                           :user_id s/Uuid}
                                                    :body c/schema_perms-update-user-or-group}

                                       :responses {200 {:description "Returns the created vocabulary user permission."
                                                        :body c/schema_export-user-perms}
                                                   404 (sd/create-error-message-response "Not Found." "{Vocabulary|User} entry not found")
                                                   409 (sd/create-error-message-response "Conflict." "Entry already exists")}})

(def admin.vocabularies.group.group_id {:summary (sd/sum_adm_todo "Create vocabulary group permissions")
                                        :handler permissions/handle_create-vocab-group-perms
                                        :middleware [wrap-authorize-admin!]
                                        :content-type "application/json"
                                        :accept "application/json"
                                        :coercion reitit.coercion.schema/coercion
                                        :parameters {:path {:id s/Str
                                                            :group_id s/Uuid}
                                                     :body c/schema_perms-update-user-or-group}
                                        :responses {200 {:description "Returns the created vocabulary group permission."
                                                         :body c/schema_export-group-perms}
                                                    404 (sd/create-error-message-response "Not Found." "Vocabulary entry not found")
                                                    406 (sd/create-error-message-response "Not Acceptable." "Could not delete vocabulary group permission")
                                                    409 (sd/create-error-message-response "Conflict." "Entry already exists")}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
