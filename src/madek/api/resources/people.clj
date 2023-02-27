(ns madek.api.resources.people
  (:require
    [clj-uuid]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.constants :refer [presence]]
    [madek.api.pagination :as pagination]
    [madek.api.resources.media-entries.index :refer [get-index]]
    [madek.api.resources.media-entries.media-entry :refer [get-media-entry]]
    [madek.api.resources.shared :as shared]
    [madek.api.utils.auth :refer [wrap-authorize-admin!]]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    [ring.util.codec :refer [url-decode]]
    [schema.core :as s]
    [clojure.tools.logging :as log]
    ))


(defn id-where-clause
  [id]
  (if
    (re-matches
      #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
      id)
    (sql/where [:or [:= :id id] [:= :institutional_id id]])
    (sql/where [:= :institutional_id id])))

(defn jdbc-id-where-clause
  [id]
  (->
    id
    id-where-clause
    sql/format
    (update-in [0] #(clojure.string/replace % "WHERE" ""))))


;### create person
;#############################################################

(defn create-person
  [request]
  (let [params
          (as-> (:body request) params
            (or params {})
            (assoc params :id (or (:id params) (clj-uuid/v4))))]
    {:body
       (dissoc
         (->>
           (jdbc/insert! (rdbms/get-ds) :people params)
           first)
         :previous_id
         :searchable),
     :status 201}))


;### get person
;################################################################

(defn find-person-sql
  [id]
  (->
    (id-where-clause id)
    (sql/select :*)
    (sql/from :people)
    sql/format))

(defn get-person
  [id-or-institutinal-person-id]
  (if-let [person
             (->>
               id-or-institutinal-person-id
               find-person-sql
               (jdbc/query (rdbms/get-ds))
               first)]
    {:body
       (->
         person
         (assoc ; support old (singular) version of field
           :external_uri (first (person :external_uris)))
         (dissoc :previous_id :searchable))}
    {:status 404, :body "No such person found"}))


;### delete person
;##############################################################

(defn delete-person
  [id]
  (if
    (=
      1
      (first (jdbc/delete! (rdbms/get-ds) :people (jdbc-id-where-clause id))))
    {:status 204}
    {:status 404}))


;### patch person
;##############################################################

(defn patch-person
  [{body :body, {id :id} :params}]
  (if
    (=
      1
      (first
        (jdbc/update! (rdbms/get-ds) :people body (jdbc-id-where-clause id))))
    {:body
       (->>
         id
         find-person-sql
         (jdbc/query (rdbms/get-ds))
         first)}
    {:status 404}))

;### index ####################################################################

(defn build-index-query
  [{query-params :query-params}]
  (->
    (sql/select :id)
    (sql/from :people)
    (sql/order-by [:id :asc])
    (pagination/add-offset-for-honeysql query-params)
    sql/format))

(defn index
  [request]
  {:body {:people (jdbc/query (rdbms/get-ds) (build-index-query request))}})


;### routes ###################################################################

(def admin-protected-routes
  (->
    (cpj/routes
      (cpj/POST "/people/" [] create-person)
      (cpj/DELETE "/people/:id" [id] (delete-person id))
      (cpj/PATCH "/people/:id" [] patch-person))
    wrap-authorize-admin!))

(def routes
  (->
    (cpj/routes
      (cpj/GET "/people/" [] index)
      (cpj/GET "/people/:id" [id] (get-person id))
      (cpj/ANY "*" _ admin-protected-routes))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)

(def schema_export_person
  {:id s/Uuid
   :first_name (s/maybe s/Str)
   :last_name (s/maybe s/Str)
   :description (s/maybe s/Str)
   :subtype s/Str
   :institutional_id (s/maybe s/Str) ;(s/maybe s/Uuid)
   :pseudonym (s/maybe s/Str)
   
   ; TODO when to use old vs new style?
   :external_uris [s/Any]
   :external_uri s/Str
   
   :created_at s/Any
   :updated_at s/Any
   }) ; TODO use s/Inst TODO json middleware

(def schema_import_person
  {:subtype s/Str
   (s/optional-key :id) s/Uuid

   (s/optional-key :first_name) (s/maybe s/Str)
   (s/optional-key :last_name) s/Str
   (s/optional-key :pseudonym) s/Str
   (s/optional-key :searchable) s/Str

   (s/optional-key :description) s/Str

   (s/optional-key :institutional_id) s/Str ;s/Uuid

   ; TODO when to use old vs new style?
   ;(s/optional-key :external_uri) s/Str
   (s/optional-key :external_uris) [s/Str]
   }) ; TODO use s/Inst TODO json middleware

(def schema_import_person_result
  {:subtype s/Str
   :id s/Uuid
   (s/optional-key :institutional_id) (s/maybe s/Str) ;s/Uuid
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :first_name) (s/maybe s/Str)
   (s/optional-key :last_name) (s/maybe s/Str)
   (s/optional-key :pseudonym) (s/maybe s/Str)
   (s/optional-key :external_uris) [s/Str]
   (s/optional-key :updated_at) s/Any ;s/Inst
   (s/optional-key :created_at) s/Any ;s/Inst

   })



(def schema_update_person
  {
   ;:subtype s/Str
   (s/optional-key :id) s/Uuid
   ;:id s/Uuid

   (s/optional-key :first_name) (s/maybe s/Str)
   (s/optional-key :last_name) s/Str
   (s/optional-key :pseudonym) s/Str
   (s/optional-key :searchable) s/Str

   (s/optional-key :description) s/Str

   (s/optional-key :institutional_id) s/Str ;s/Uuid

   ; TODO when to use old vs new style?
   ;(s/optional-key :external_uri) s/Str
   (s/optional-key :external_uris) [s/Str]}) ; TODO use s/Inst TODO json middleware



(def schema_query_person
  {(s/optional-key :subtype) s/Str
   (s/optional-key :id) s/Uuid

   (s/optional-key :first_name) s/Str
   (s/optional-key :last_name) s/Str
   (s/optional-key :pseudonym) s/Str

   (s/optional-key :description) s/Str

   (s/optional-key :institutional_id) s/Uuid

   ;(s/optional-key :external_uri) s/Str
}) ; TODO use s/Inst TODO json middleware


(defn handle_create-person
  "TODO check subtype, catch errors"
  [request]
  (let [params (get-in request [:parameters :body])
        data_wid (assoc params :id (or (:id params) (clj-uuid/v4)))
        resultdb (->> (jdbc/insert! (rdbms/get-ds) :people data_wid) first)
        result (dissoc resultdb :previous_id :searchable)]
        ;result (:id resultdb)]
    (log/info (apply str ["handler_create-person: \ndata:" data_wid "\nresult-db: " resultdb "\nresult: " result]))
    ;{:status 201 :body {:id result}}
    {:status 201 :body result}))

(defn handle_get-person
  [req]
  (let [id (shared/get-path-params req :id)]
    (get-person (str id))))

(defn handle_delete-person [req]
  (let [id (shared/get-path-params req :id)]
    (delete-person (str id))))

(defn handle_patch-person
  ;[req]
  ;(patch-person req)
  [req]
  (let [body (get-in req [:parameters :body])
        pid (shared/get-path-params req :id)
        id (or (:id body) pid)]
    (log/info "handle_patch" "\nbody:\n" body "\nid:\n" id)
    (patch-person {:params {:id (str id)} :body body})))
