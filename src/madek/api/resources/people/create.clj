(ns madek.api.resources.people.create
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.people.common]
   [madek.api.resources.people.common :refer [find-person-by-uid]]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [convert-map-if-exist]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [error]]))

(defn handle-create-person
  [{{data :body} :parameters tx :tx :as req}]
  (try
    (let [{id :id} (-> (sql/insert-into :people)
                       (sql/values [(convert-map-if-exist data)])
                       sql-format
                       ((partial jdbc/execute-one! tx) {:return-keys true}))]
      (sd/response_ok (find-person-by-uid id tx) 201))
    (catch Exception e
      (error "handle-create-person failed" {:request req})
      (sd/parsed_response_exception e))))

;(def schema
;  {;; TODO: fixme, create customized schema to validate enums
;   ;:subtype (s/enum "Person" "PeopleGroup" "PeopleInstitutionalGroup")
;   :subtype (s/maybe s/Str)
;
;   (s/optional-key :description) (s/maybe s/Str)
;   (s/optional-key :external_uris) [s/Str]
;   (s/optional-key :first_name) (s/maybe s/Str)
;   (s/optional-key :institution) (s/maybe s/Str)
;   (s/optional-key :institutional_id) (s/maybe s/Str)
;   (s/optional-key :last_name) (s/maybe s/Str)
;   (s/optional-key :admin_comment) (s/maybe s/Str)
;   (s/optional-key :pseudonym) (s/maybe s/Str)})

(def route
  {:accept "application/json"
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :description "Create a person.\nThe [subtype] has to be one of [\"Person\" \"PeopleGroup\" \"PeopleInstitutionalGroup\"]. \nAt least one of [first_name, last_name, description] must have a value."
   :handler handle-create-person
   :middleware [wrap-authorize-admin!]
   :parameters {:body (get-schema :people.schema)}
   :responses {201 {:body (get-schema :people.get.schema)}
               409 {:description "Conflict."
                    :schema s/Str
                    :examples {"application/json" {:message "Violation of constraint"}}}}
   :summary "Create a person"
   :swagger {:produces "application/json"
             :consumes "application/json"}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
