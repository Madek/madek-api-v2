(ns madek.api.resources.users.create
  (:require
   [clojure.java.io :as io]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [convert-map-if-exist]]
   [madek.api.utils.helper :refer [mslurp]]
   [madek.api.utils.validation :as v]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [error]]))

;#### create ##################################################################

(defn handle-create-user
  [{{data :body} :parameters tx :tx :as req}]
  (try
    (let [data (convert-map-if-exist data)
          query (-> (sql/insert-into :users)
                    (sql/values [data])
                    (sql/returning :*)
                    sql-format)
          result (jdbc/execute-one! tx query)]
      (if result
        (sd/response_ok result 201)
        (sd/response_failed)))
    (catch Exception e
      (error "handle-create-user failed" {:request req})
      (sd/parsed_response_exception e))))

(def schema
  {:person_id s/Uuid
   (s/optional-key :accepted_usage_terms_id) (s/maybe s/Uuid)
   (s/optional-key :email) v/email-validation
   (s/optional-key :first_name) s/Str
   (s/optional-key :institution) s/Str
   (s/optional-key :institutional_id) s/Str
   (s/optional-key :last_name) s/Str
   (s/optional-key :login) s/Str
   (s/optional-key :password_sign_in_enabled) s/Bool
   (s/optional-key :notes) (s/maybe s/Str)
   (s/optional-key :settings) v/vector-or-hashmap-validation})

(def schema_post
  {:institution s/Str
   :institutional_id (s/maybe s/Str)
   :email (s/maybe v/email-validation)
   :first_name (s/maybe s/Str)
   :person_id s/Uuid
   :active_until s/Any
   :settings s/Any
   :login s/Str
   :searchable s/Str
   :updated_at s/Any
   :accepted_usage_terms_id (s/maybe s/Uuid)
   :id s/Uuid
   :password_sign_in_enabled (s/maybe s/Bool)
   :notes (s/maybe s/Str)
   :last_name (s/maybe s/Str)
   :last_signed_in_at (s/maybe s/Any)
   :autocomplete s/Any
   :emails_locale (s/maybe s/Any)
   :created_at s/Any})

;; post /users
(def route
  {:accept "application/json"
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :description (mslurp (io/resource "md/admin-users-post.md"))
   :handler handle-create-user
   :middleware [wrap-authorize-admin!]
   :parameters {:body schema}
   :responses {201 {:description "Created."
                    :body schema_post}
               400 {:description "Bad Request"
                    :body s/Any}
               404 {:description "Not Found."
                    :schema s/Str
                    :examples {"application/json" {:message "People entry not found"}}}

               409 {:description "Conflict."
                    :schema s/Str
                    :examples {"application/json" {:message "Entry already exists"}}}}
   :summary (sd/sum_adm "Create user.")
   :swagger {:consumes "application/json"
             :produces "application/json"}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
