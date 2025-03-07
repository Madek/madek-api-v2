(ns madek.api.resources.people.update
  (:require
   [clj-uuid :refer [as-uuid]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.people.common :refer [find-person-by-uid]]
   [madek.api.resources.people.create :as create]
   [madek.api.resources.people.get :as get-person]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.sql-next :refer [convert-sequential-values-to-sql-arrays]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

(defn update-person
  "Updates and returns true if that happened and false otherwise"
  [person-id data tx]
  (-> (sql/update :people)
      (sql/set (-> data convert-sequential-values-to-sql-arrays))
      (sql/where [:= :people.id (as-uuid person-id)])
      (sql-format :inline false)
      (->> (jdbc/execute-one! tx))
      :next.jdbc/update-count
      (= 1)))

(defn update-person-handler
  [{{data :body} :parameters
    {person-id :id} :path-params
    {auth-entity-id :id} :authenticated-entity
    tx :tx :as req}]
  (if-let [person (find-person-by-uid person-id tx)]
    (if (update-person person-id
                       (assoc data :updator_id auth-entity-id)
                       tx)
      {:status 200 :body (find-person-by-uid person-id tx)}
      (throw (ex-info "Update of person failed" {:status 409})))
    {:status 404 :body {:message "Person not found."}}))

(def route
  {:summary (sd/sum_adm "Update person with id")
   :description "Patch a person with id. Returns 404, if no such person exists."
   :swagger {:consumes "application/json"
             :produces "application/json"}
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :accept "application/json"
   :parameters {:path {:id s/Uuid}
                :body (-> create/schema
                          (dissoc :subtype)
                          (assoc (s/optional-key :subtype) (:subtype create/schema)))}
   :handler update-person-handler
   :middleware [wrap-authorize-admin!]
   :responses {200 {:description "Updated."
                    :body get-person/schema}
               404 (sd/create-error-message-response "Not Found." "Person not found.")
               409 (sd/create-error-message-response "Conflict." "Update of person failed")}})
