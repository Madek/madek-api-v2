(ns madek.api.resources.people.delete
  (:require
   [clj-uuid :as uuid]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

(defn delete-person
  "Delete a person by its id and returns true if delete was succesfull
  and false otherwise."
  [id tx]
  (-> (sql/delete-from :people)
      (sql/where [:= :people.id (uuid/as-uuid id)])
      (sql-format :inline false)
      (->> (jdbc/execute-one! tx))
      :next.jdbc/update-count
      (= 1)))

(defn handler
  [{{{id :id} :path} :parameters ds :tx :as req}]
  ; delete person should only false if no person was found; if the delete fails
  ; because of constraints an exception would have been raised
  (try
    (if (delete-person id ds)
      {:status 204}
      {:status 404 :body {:message "Person not found."}})
    (catch Exception ex (sd/parsed_response_exception ex))))

(def route
  {:summary (sd/sum_adm "Delete person by id")
   :description "Delete a person by id (the madek interal UUID). Returns 404, if no such person exists."
   :handler handler
   :middleware [wrap-authorize-admin!]
   :swagger {:produces "application/json"}
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :parameters {:path {:id s/Uuid}}
   :responses {204 {:description "No Content." :body nil}
               403 {:description "Forbidden."
                    :schema s/Str
                    :examples {"application/json" {:message "Violation of constraints."}}}
               404 {:description "Not found."
                    :schema s/Str
                    :examples {"application/json" {:message "Person not found."}}}}})


