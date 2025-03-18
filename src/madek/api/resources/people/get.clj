(ns madek.api.resources.people.get
  (:require
   [clojure.spec.alpha :as sa]
   [honey.sql :refer [format] :rename {format sql-format}]
   [madek.api.resources.people.common :refer [person-query]]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.coercion.spec-alpha-definition-nil :as sp-nil]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [spec-tools.core :as st]
   [taoensso.timbre :refer [debug]]))

(sa/def :usr/people
  (sa/keys :req-un [::sp/id
                    ::sp/created_at
                    ::sp-nil/description
                    ::sp/external_uris
                    ::sp-nil/first_name
                    ::sp/institution
                    ::sp-nil/institutional_id
                    ::sp-nil/last_name
                    ::sp-nil/admin_comment
                    ::sp-nil/pseudonym
                    ::sp/subtype
                    ::sp/updated_at
                    ::sp-nil/identification_info
                    ::sp/institutional_directory_infos]))

(sa/def :usr-people-list/people (st/spec {:spec (sa/coll-of :usr/people)
                                          :description "A list of persons"}))

(sa/def ::response-people-body (sa/keys :opt-un [:usr-people-list/people ::sp/data ::sp/pagination]))

(def schema
  {:created_at s/Any
   :description (s/maybe s/Str)
   :external_uris [s/Str]
   :first_name (s/maybe s/Str)
   :id s/Uuid
   :institution s/Str
   :institutional_id (s/maybe s/Str)
   :last_name (s/maybe s/Str)
   :admin_comment (s/maybe s/Str)
   :pseudonym (s/maybe s/Str)
   :subtype (s/enum "Person" "PeopleGroup" "PeopleInstitutionalGroup")
   :updated_at s/Any
   :identification_info (s/maybe s/Str)
   :institutional_directory_infos [s/Str]})

(defn handler
  [{{{id :id} :path} :parameters
    tx :tx :as req}]
  (debug req)
  (debug id)
  (if-let [person (-> (person-query id)
                      sql-format
                      (->> (jdbc/execute-one! tx)))]
    (sd/response_ok person)
    (sd/response_failed "No such person found" 404)))

(def route
  {:summary (sd/?no-auth? (sd/sum_adm "Get person by uid"))
   :description "Get a person by uid (either uuid or pair of json encoded [institution, institutional_id]).
   Returns 404, if no such people exists."
   :handler handler
   :middleware []
   :swagger {:produces "application/json"}
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :parameters {:path {:id s/Str}}
   :responses {200 {:description "Person found."
                    :body schema}
               404 (sd/create-error-message-response "Not Found." "No such person found.")}})
