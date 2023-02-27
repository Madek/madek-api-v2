(ns madek.api.resources.groups
  (:require
    [clj-uuid]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.constants :refer [presence]]
    [madek.api.pagination :as pagination]
    [madek.api.resources.groups.shared :as groups]
    [madek.api.resources.groups.users :as users]
    [madek.api.resources.media-entries.index :refer [get-index]]
    [madek.api.resources.media-entries.media-entry :refer [get-media-entry]]
    [madek.api.resources.shared :as shared]
    [madek.api.utils.auth :refer [wrap-authorize-admin!]]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    [ring.util.codec :refer [url-decode]]
    
    [schema.core :as s]
    ))



;### create group #############################################################

(defn create-group [request]
  (let [params (as-> (:body request) params
                 (or params {})
                 (assoc params :id (or (:id params) (clj-uuid/v4))))]
    {:body (dissoc
             (->> (jdbc/insert!
                    (rdbms/get-ds) :groups params)
                  first)
             :previous_id :searchable)
     :status 201}))


;### get group ################################################################

(defn get-group [id-or-institutinal-group-id]
  (if-let [group (groups/find-group id-or-institutinal-group-id)]
    {:body (dissoc group :previous_id :searchable)}
    {:status 404 :body "No such group found"}))


;### delete group ##############################################################

(defn delete-group [id]
  (if (= 1 (first (jdbc/delete! (rdbms/get-ds)
                                :groups (groups/jdbc-update-group-id-where-clause id))))
    {:status 204}
    {:status 404}))


;### patch group ##############################################################

(defn patch-group [{body :body {group-id :group-id} :params}]
  (if (= 1 (first (jdbc/update! (rdbms/get-ds)
                                :groups
                                body (groups/jdbc-update-group-id-where-clause group-id))))
    {:body (groups/find-group group-id)}
    {:status 404}))

;### index ####################################################################

(defn build-index-query [{query-params :query-params}]
  (-> (sql/select :id)
      (sql/from :groups)
      (sql/order-by [:id :asc])
      (pagination/add-offset-for-honeysql query-params)
      sql/format))

(defn index [request]
  {:body
   {:groups (jdbc/query (rdbms/get-ds) (build-index-query request))}})


;### routes ###################################################################

(def routes
  (-> (cpj/routes
        (cpj/GET "/groups/" [] index)
        (cpj/POST "/groups/" [] create-group)
        (cpj/ANY "/groups/:group-id/users/*" [] users/routes)
        (cpj/GET "/groups/:group-id" [group-id] (get-group group-id))
        (cpj/DELETE "/groups/:group-id" [group-id] (delete-group group-id))
        (cpj/PATCH "/groups/:group-id" [] patch-group))
      wrap-authorize-admin!))

(def schema_import-group
  {(s/optional-key :id) s/Str
   :name s/Str
   ;:type (s/enum "Group" "AuthenticationGroup" "InstitutionalGroup")
   (s/optional-key :type) (s/enum "Group" "AuthenticationGroup" "InstitutionalGroup")
   (s/optional-key :institutional_id) (s/maybe s/Str)
   (s/optional-key :institutional_name) (s/maybe s/Str)
   (s/optional-key :person_id) (s/maybe s/Uuid)})

(def schema_export-group
  {:id s/Uuid
   :name s/Str
   :type s/Str ; TODO enum
   :institutional_id (s/maybe s/Str)
   :institutional_name (s/maybe s/Str)
   :person_id (s/maybe s/Uuid)})

(defn handle_create-group
  "TODO  catch errors"
  [request]
  (let [params (get-in request [:parameters :body])
        data_wid (assoc params :id (or (:id params) (clj-uuid/v4)))
        data_wtype (assoc data_wid :type (or (:type data_wid) "Group"))
        resultdb (->> (jdbc/insert! (rdbms/get-ds) :groups data_wtype) first)
        result (dissoc resultdb :previous_id :searchable)]
    (logging/info (apply str ["handler_create-group: \ndata:" data_wtype "\nresult-db: " resultdb "\nresult: " result]))
    ;{:status 201 :body {:id result}}
    {:status 201 :body result}))

(defn handle_get-group [req]
  (let [id (-> req :parameters :path :id)]
    (logging/info "handle_get-group" "\nid\n" id)
    (get-group id)))

(defn handle_delete-group [req]
  (let [id (-> req :parameters :path :id)]
    (delete-group id)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
