(ns madek.api.resources.people.create
  (:require
   [clj-uuid :as uuid]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.debug :as debug]
   [madek.api.authorization :as authorization]
   [madek.api.db.core :refer [get-ds]]
   [madek.api.resources.people.common :refer [find-person-by-uid]]
   [madek.api.resources.people.get :as get-person]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.logging :as logging]
   [madek.api.utils.sql-next :refer [convert-sequential-values-to-sql-arrays]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [debug error info spy warn]]))

(defn handle-create-person
  [{{data :body} :parameters ds :tx :as req}]
  (try
    (let [{id :id} (-> (sql/insert-into :people)
                       (sql/values [data])
                       (sql-format)
                       ((partial jdbc/execute-one! ds) {:return-keys true}))]
      (sd/response_ok (spy (find-person-by-uid id ds)) 201))
    (catch Exception e
      (error "handle-create-person failed" {:request req})
      (sd/response_exception e))))

(def schema_import_person
  {:subtype (s/enum "Person" "PeopleGroup" "PeopleInstitutionalGroup")
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :external_uris) [s/Str]
   (s/optional-key :first_name) (s/maybe s/Str)
   (s/optional-key :institution) (s/maybe s/Str)
   (s/optional-key :institutional_id) (s/maybe s/Str)
   (s/optional-key :last_name) (s/maybe s/Str)
   (s/optional-key :pseudonym) (s/maybe s/Str)})

(def route
  {:accept "application/json"
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :description "Create a person.\n The \nThe [subtype] has to be one of [Person, ...]. \nAt least one of [first_name, last_name, description] must have a value."
   :handler handle-create-person
   :middleware [wrap-authorize-admin!]
   :parameters {:body schema_import_person}
   :responses {201 {:body get-person/schema}}
   :summary "Create a person"
   :swagger {:produces "application/json"
             :consumes "application/json"}})

;### Debug ####################################################################
(debug/debug-ns *ns*)
