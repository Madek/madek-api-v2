(ns madek.api.resources.users.get
  (:require
   [clojure.data.json :as json]
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.shared :as sd]
   [madek.api.resources.users.common :refer [wrap-find-user]]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [reitit.coercion.schema]
   [schema.core :as s]))

(s/defn valid-email?
  [email]
  (re-matches #"^[\w\.-]+@([\w-]+\.)+[\w-]{2,4}$" email))

;; Define a custom validation function for JSON
(s/defn valid-json?
  [json-str]
  (try
    (json/read-str json-str)
    (catch Exception e
      false)))

;(def schema
;  {:accepted_usage_terms_id (s/maybe s/Uuid)
;   :created_at s/Any
;
;   ;:email (s/with-fn-validation valid-email? s/Str)
;
;   ;(s/optional-key :email) email-validation ;; ?? TODO: invalid email?
;   (s/optional-key :email) s/Str
;
;   :first_name (s/maybe s/Str)
;   :id s/Uuid
;   :institution (s/maybe s/Str)
;   :institutional_id (s/maybe s/Str)
;   :is_admin s/Bool
;   :last_name (s/maybe s/Str)
;   :last_signed_in_at (s/maybe s/Any)
;   :login (s/maybe s/Str)
;   :notes (s/maybe s/Str)
;   :person_id s/Uuid
;
;   ;:settings (s/with-fn-validation valid-json? s/Str) ;; Validate settings as JSON ;; broken
;   (s/optional-key :settings) vector-or-hashmap-validation
;
;   :updated_at s/Any})

;:get.users-schema-payload = {:institution (maybe Str), :institutional_id (maybe Str), :first_name (maybe Str),
;:person_id java.util.UUID, :login (maybe Str), :updated_at Any, #schema.core.OptionalKey{:k :settings} Any,
;:id java.util.UUID, :notes (maybe Str), :last_name (maybe Str), :last_signed_in_at (maybe Any), :created_at Any}

(defn handler
  [{user :user :as req}]
  (sd/response_ok user))

(def route
  {:summary (sd/sum_adm "Get user by id. / body-schema-example")
   :description "Get a user by id. Returns 404, if no such users exists."
   :handler handler
   :middleware [wrap-authorize-admin!
                (wrap-find-user :id)]
   :swagger {:produces "application/json"}
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :parameters {:path {:id s/Str}}
   :responses {200 {:body (get-schema :get.users-schema-payload)}
               404 {:description "Not Found."
                    :schema s/Str
                    :examples {"application/json" {:message "No such user."}}}}})
