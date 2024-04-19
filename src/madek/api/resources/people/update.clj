(ns madek.api.resources.people.update
  (:require
   [clj-uuid :refer [as-uuid]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.people.common :refer [find-person-by-uid]]
   [madek.api.resources.people.create :as create]
   [madek.api.resources.people.get :as get-person]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.sql-next :refer [convert-sequential-values-to-sql-arrays]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

(defn update-person
  "Updates and returns true if that happened and false otherwise"
  [person-id data ds]
  (-> (sql/update :people)
      (sql/set (-> data convert-sequential-values-to-sql-arrays))
      (sql/where [:= :people.id (as-uuid person-id)])
      (sql-format :inline false)
      (->> (jdbc/execute-one! ds))
      :next.jdbc/update-count
      (= 1)))

(defn update-person-handler
  [{{data :body} :parameters
    {person-id :id} :path-params
    ds :tx :as req}]
  (if-let [person (find-person-by-uid person-id ds)]
    (if (update-person person-id data ds)
      {:status 200 :body (find-person-by-uid person-id ds)}
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
   :responses {200 {:body get-person/schema}
               404 {:description "Not found."
                    :schema s/Str
                    :examples {"application/json" {:message "Person not found."}}}
               409 {:description "Conflict."
                    :schema s/Str
                    :examples {"application/json" {:message "Update of person failed"}}}}})

