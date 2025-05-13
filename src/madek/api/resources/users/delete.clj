(ns madek.api.resources.users.delete
  (:require
   [clj-uuid :as uuid]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.debug :as debug]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.users.common :refer [wrap-find-user]]
   [madek.api.resources.users.get :as get-user]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

(defn delete-user
  "Delete a user by its id and returns true if delete was succesfull
  and false otherwise."
  [id tx]
  (-> (sql/delete-from :users)
      (sql/where [:= :users.id (uuid/as-uuid id)])
      (sql-format :inline false)
      (->> (jdbc/execute-one! tx))
      :next.jdbc/update-count
      (= 1)))

(defn handler
  [{{id :id :as user} :user tx :tx :as req}]
  (try
    (if (delete-user id tx)
      (sd/response_ok user)
      (sd/response_failed "Could not delete user." 406))
    (catch Exception ex (sd/parsed_response_exception ex))))

(def route
  {:summary (sd/sum_adm "Delete user by id")
   :description "Delete a user by id. Returns 404, if no such user exists."
   :handler handler
   :middleware [wrap-authorize-admin!
                (wrap-find-user :id)]
   :swagger {:produces "application/json"}
   :coercion reitit.coercion.schema/coercion
   :content-type "application/json"
   :parameters {:path {:id s/Str}}
   :responses {200 {:description "Deleted."
                    :body get-user/schema}
               422 (sd/create-error-message-response "Unprocessable Content" "References still exist")
               404 (sd/create-error-message-response "Not Found." "No such user.")}})

(debug/debug-ns *ns*)
