(ns madek.api.resources.users.update
  (:require
   [clojure.java.io :as io]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.debug :as debug]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.users.common :refer [find-user-by-uid wrap-find-user]]
   [madek.api.resources.users.get :as get-user]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [mslurp convert-map-if-exist]]
   [madek.api.utils.sql-next :refer [convert-sequential-values-to-sql-arrays]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [error info spy]]))

(defn update-user
  "Updates and returns true if that happened and false otherwise"
  [user-id data tx]
  (-> (sql/update :users)
      (sql/set (-> data convert-map-if-exist))
      (sql/where [:= :users.id [:uuid user-id]])
      (sql-format :inline true)
      (->> (jdbc/execute-one! tx))
      :next.jdbc/update-count
      (= 1)))

(defn update-user-handler
  [{{data :body} :parameters
    {user-id :id} :path-params
    {auth-entity-id :id} :authenticated-entity
    tx :tx :as req}]
  (if (update-user user-id
                   (assoc data :updator_id auth-entity-id)
                   tx)
    (sd/response_ok (find-user-by-uid user-id tx) 200)
    (sd/response_not_found "No such user.")))

(def schema
  {(s/optional-key :accepted_usage_terms_id) (s/maybe s/Uuid) ; TODO
   (s/optional-key :autocomplete) s/Str
   (s/optional-key :email) (s/maybe s/Str)
   (s/optional-key :first_name) s/Str
   (s/optional-key :institution) s/Str
   (s/optional-key :last_name) s/Str
   (s/optional-key :active_until) s/Any
   (s/optional-key :login) s/Str
   (s/optional-key :password_sign_in_enabled) s/Bool
   (s/optional-key :notes) (s/maybe s/Str) ; TODO
   (s/optional-key :searchable) s/Str})

(def route
  {:summary (sd/sum_adm "Update user with id")
   :swagger {:consumes "application/json"
             :produces "application/json"}
   :coercion reitit.coercion.schema/coercion

   ;:description "Patch a user with id. Returns 404, if no such user exists."
   :description (mslurp (io/resource "md/users-patch.md"))

   :content-type "application/json"
   :accept "application/json"
   :parameters {:path {:id s/Str}
                :body schema}
   :handler update-user-handler
   :middleware [wrap-authorize-admin!
                (wrap-find-user :id)]
   :responses {200 {:description "User updated."
                    :body get-user/schema}
               404 (sd/create-error-message-response "Not found." "No such user.")}})

(debug/debug-ns *ns*)
