(ns madek.api.resources.people.delete
  (:require
   [clj-uuid :as uuid]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.debug :as debug]
   [madek.api.authorization :as authorization]
   [madek.api.db.core :refer [get-ds]]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.logging :as logging]
   [madek.api.utils.sql-next :refer [convert-sequential-values-to-sql-arrays]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [debug error info spy warn]]))

(defn delete-person
  "Delete a person by its id and returns true if delete was succesfull
  and false otherwise."
  [id tx]
  (-> (sql/delete-from :people)
      (sql/where [:= :people.id (uuid/as-uuid id)])
      (sql-format :inline true)
      (->> (jdbc/execute-one! tx))
      :next.jdbc/update-count
      (= 1)))

(defn handler
  [{{{id :id} :path} :parameters ds :tx :as req}]
  ; delete person should only false if no person was found; if the delete fails
  ; becase of constraints an exception would have been raised
  (if (delete-person id ds)
    {:status 204}
    {:status 404 :body {:message "Person not found."}}))

(def route
  {:summary (sd/sum_adm "Delete person by id")
   :description "Delete a person by id (the madek interal UUID). Returns 404, if no such person exists."
   :handler handler
   :middleware [wrap-authorize-admin!]
   :swagger {:produces "application/json"}
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :parameters {:path {:id s/Str}}
   :responses {204 {:body nil}
               404 {:body s/Any}}})


