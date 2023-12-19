(ns madek.api.resources.people.get
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.debug :as debug]
   [madek.api.authorization :as authorization]
   [madek.api.db.core :refer [get-ds]]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.logging :as logging]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [debug error info spy warn]]))

(def schema
  {:id s/Uuid
   :first_name (s/maybe s/Str)
   :last_name (s/maybe s/Str)
   :pseudonym (s/maybe s/Str)
   :created_at s/Any
   :updated_at s/Any
   :institutional_id (s/maybe s/Str)
   :subtype (s/enum "Person" "PeopleGroup" "PeopleInstitutionalGroup")
   :description (s/maybe s/Str)
   :external_uris [s/Str]
   :institution (s/maybe s/Str)})

(defn handler
  [{:as req}]
  {})

(def route
  {:summary (sd/sum_adm "Get person by id")
   :description "Get a person by id. Returns 404, if no such people exists."
   :handler handler
   :middleware []
   :swagger {:produces "application/json"}
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :parameters {:path {:id s/Str}}
   :responses {200 {:body schema}
               404 {:body s/Any}}})
