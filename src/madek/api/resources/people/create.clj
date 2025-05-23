(ns madek.api.resources.people.create
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.people.common :refer [find-person-by-uid]]
   [madek.api.resources.people.get :as get-person]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [convert-map-if-exist]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [error]]))

(defn handle-create-person
  [{{data :body} :parameters
    {auth-entity-id :id} :authenticated-entity
    tx :tx :as req}]
  (try
    (let [{id :id} (-> (sql/insert-into :people)
                       (sql/values [(-> data
                                        convert-map-if-exist
                                        (assoc :creator_id auth-entity-id))])
                       sql-format
                       ((partial jdbc/execute-one! tx) {:return-keys true}))]
      (sd/response_ok (find-person-by-uid id tx) 201))
    (catch Exception e
      (error "handle-create-person failed" {:request req})
      (sd/parsed_response_exception e))))

(def schema
  {;; TODO: fixme, create customized schema to validate enums
   ;:subtype (s/enum "Person" "PeopleGroup" "PeopleInstitutionalGroup")
   :subtype (s/maybe s/Str)

   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :external_uris) [s/Str]
   (s/optional-key :first_name) (s/maybe s/Str)
   (s/optional-key :institution) (s/maybe s/Str)
   (s/optional-key :institutional_id) (s/maybe s/Str)
   (s/optional-key :last_name) (s/maybe s/Str)
   (s/optional-key :admin_comment) (s/maybe s/Str)
   (s/optional-key :pseudonym) (s/maybe s/Str)
   (s/optional-key :identification_info) (s/maybe s/Str)
   (s/optional-key :institutional_directory_infos) [s/Str]
   (s/optional-key :institutional_directory_inactive_since) (s/maybe s/Any)})

(def route
  {:accept "application/json"
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :description "Create a person.\nThe [subtype] has to be one of [\"Person\" \"PeopleGroup\" \"PeopleInstitutionalGroup\"].
   \nAt least one of [first_name, last_name, description] must have a value.
     \n\nDefault: \"subtype\": \"Person\",\n  \"institutional_id\": null, "
   :handler handle-create-person
   :middleware [wrap-authorize-admin!]
   :parameters {:body schema}
   :responses {201 {:description "Created."
                    :body get-person/schema}
               409 (sd/create-error-message-response "Conflict." "Violation of constraint")}
   :summary "Create a person"
   :swagger {:produces "application/json"
             :consumes "application/json"}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
