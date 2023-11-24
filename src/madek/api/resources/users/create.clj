(ns madek.api.resources.users.create
  (:require
   [clj-uuid :as uuid]
   [honey.sql  :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.debug :as debug]
   [madek.api.authorization :as authorization]
   [madek.api.db.core :refer [get-ds]]
   [madek.api.resources.shared :as sd]
   [madek.api.resources.users.common :refer [find-user-by-uid]]
   [madek.api.resources.users.get :as get-user]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.logging :as logging]
   [madek.api.utils.sql-next :refer [convert-sequential-values-to-sql-arrays]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [debug error info spy warn]]))

;#### create ##################################################################

(defn handle-create-user
  [{{data :body} :parameters ds :tx :as req}]
  (try
    (if-let [{id :id} (-> (sql/insert-into :users)
                          (sql/values [data])
                          (sql-format :inline true)
                          spy
                          ((partial jdbc/execute-one! ds) {:return-keys true}))]
      (sd/response_ok (spy (find-user-by-uid id ds)) 201)
      (sd/response_failed "Could not create user." 406))
    (catch Exception e
      (error "handle-create-user failed" {:request req})
      (sd/response_exception e))))

(def schema
  {:person_id s/Uuid
   (s/optional-key :accepted_usage_terms_id) (s/maybe s/Uuid)
   (s/optional-key :autocomplete) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :id) s/Uuid
   (s/optional-key :institution) s/Str
   (s/optional-key :institutional_id) s/Str
   (s/optional-key :login) s/Str
   (s/optional-key :notes) (s/maybe s/Str)
   (s/optional-key :searchable) s/Str
   (s/optional-key :settings) s/Any})

(def route
  {:summary (sd/sum_adm "Create user.")
   :description "Create user."
   :swagger {:consumes "application/json"
             :produces "application/json"}
   :content-type "application/json"
   :accept "application/json"
   :handler handle-create-user
   :middleware [wrap-authorize-admin!]
   :coercion reitit.coercion.schema/coercion
   :parameters {:body schema}
   :responses {201 {:body get-user/schema}
               406 {:body s/Any}}})

;### Debug ####################################################################
(debug/debug-ns *ns*)
