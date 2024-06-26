(ns madek.api.resources.users.get
  (:require
   [clojure.data.json :as json]
   [clojure.spec.alpha :as sa]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.users.common :refer [wrap-find-user]]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.coercion.spec-alpha-definition-nil :as sp-nil]
   [madek.api.utils.validation :as v]
   [reitit.coercion.schema]
   [schema.core :as s]
   [spec-tools.core :as st]))

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

(sa/def ::users-resp-def (sa/keys :req-un [::sp/id ::sp-nil/accepted_usage_terms_id ::sp/created_at ::sp-nil/first_name
                                           ::sp/institution ::sp-nil/institutional_id ::sp/is_admin ::sp-nil/last_name
                                           ::sp-nil/last_signed_in_at ::sp-nil/login ::sp-nil/notes ::sp/person_id ::sp/updated_at]
                                  :opt-un [::sp/email ::sp/settings]))

(sa/def :users-list/users (st/spec {:spec (sa/coll-of ::users-resp-def)
                                    :description "A list of persons"}))

(sa/def ::users-body-resp-def (sa/keys :req-un [:users-list/users]))

(def schema
  {:accepted_usage_terms_id (s/maybe s/Uuid)
   :created_at s/Any

   ;:email (s/with-fn-validation valid-email? s/Str)

   ;(s/optional-key :email) email-validation ;; ?? TODO: invalid email?
   (s/optional-key :email) s/Str

   :first_name (s/maybe s/Str)
   :id s/Uuid
   :institution (s/maybe s/Str)
   :institutional_id (s/maybe s/Str)
   :is_admin s/Bool
   :last_name (s/maybe s/Str)
   :last_signed_in_at (s/maybe s/Any)
   :login (s/maybe s/Str)
   :notes (s/maybe s/Str)
   :person_id s/Uuid

   ;:settings (s/with-fn-validation valid-json? s/Str) ;; Validate settings as JSON ;; broken
   (s/optional-key :settings) v/vector-or-hashmap-validation

   :updated_at s/Any})

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
   :responses {200 {:body schema}
               404 {:description "Not Found."
                    :schema s/Str
                    :examples {"application/json" {:message "No such user."}}}}})
