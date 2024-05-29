(ns madek.api.resources.people
  (:require [clj-uuid]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.pagination :as pagination]
            [madek.api.resources.shared :as sd]
            [reitit.coercion.schema]))

; TODO clean code
;(defn transform_export [person]
;  (-> person
;      (assoc
;       ; support old (singular) version of field
;       :external_uri (first (person :external_uris)))
;      (dissoc :previous_id :searchable)))

;(defn id-where-clause
;  [id]
;  (if
;   (re-matches
;    #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
;    id)
;    (sql/where [:or [:= :id id] [:= :institutional_id id]])
;    (sql/where [:= :institutional_id id])))

;(defn jdbc-id-where-clause
;  [id]
;  (->
;   id
;   id-where-clause
;   sql-format
;   (update-in [0] #(clojure.string/replace % "WHERE" ""))))

;### create person
;#############################################################

;### get person
;################################################################

;; TODO: not in use?
;(defn find-person-sql
;  [id]
;  (->
;   (id-where-clause id)
;   (sql/select :*)
;   (sql/from :people)
;   (sql/returning :*)
;   sql-format))

;(defn db-person-get [id tx]
;  (jdbc/execute-one! tx (find-person-sql id)))

;### delete person
;##############################################################

;### patch person
;##############################################################

;### index ####################################################################
;(defn- sql-base-query [full-data]
;  (let [sel (if (true? full-data)
;              (sql/select :*)
;              (sql/select :id :subtype :first_name :last_name :searchable))]
;    (-> sel (sql/from :people))))

;(defn build-index-query
;  [query-params]
;  (let [full-data (-> query-params :full_data)]
;    (->
;     (sql-base-query full-data)
;     (sd/build-query-param-like query-params :searchable)
;     (sd/build-query-param-like query-params :description)
;     (sd/build-query-param-like query-params :institutional_id)
;     (sd/build-query-param-like query-params :pseudonym)
;     (sd/build-query-param-like query-params :first_name)
;     (sd/build-query-param-like query-params :last_name)
;     (sd/build-query-param query-params :subtype)
;     (pagination/add-offset-for-honeysql query-params)
;     sql-format)))

;; TODO: not in use?
;(defn handle_query-people
;  [req]
;  (let [query-params (-> req :parameters :query)
;        sql-query (build-index-query query-params)
;        db-result (jdbc/execute! (:tx req) sql-query)
;        result (map transform_export db-result)]
;    ;(info "handle_query-people: \n" sql-query)
;    (sd/response_ok {:people result})))

;### routes ###################################################################

;### Debug ####################################################################
;(debug/debug-ns *ns*)

;(def schema_export_person
;  {:id s/Uuid
;   :first_name (s/maybe s/Str)
;   :last_name (s/maybe s/Str)
;   :description (s/maybe s/Str)
;   :subtype (s/enum "Person" "PeopleGroup" "PeopleInstitutionalGroup")
;   :institutional_id (s/maybe s/Str)
;   :pseudonym (s/maybe s/Str)
;
;   ; TODO when to use old vs new style?
;   :external_uris [s/Str]
;   :external_uri (s/maybe s/Str)
;
;   :created_at s/Any
;   :updated_at s/Any})

;(def schema_import_person
;  {:subtype (s/enum "Person" "PeopleGroup" "PeopleInstitutionalGroup")
;   (s/optional-key :description) s/Str
;   (s/optional-key :external_uris) [s/Str]
;   (s/optional-key :first_name) (s/maybe s/Str)
;   (s/optional-key :id) s/Uuid
;   (s/optional-key :institutional_id) s/Str
;   (s/optional-key :last_name) s/Str
;   (s/optional-key :pseudonym) s/Str
;   (s/optional-key :searchable) s/Str})
;
;(def schema_import_person_result
;  {:subtype (s/enum "Person" "PeopleGroup" "PeopleInstitutionalGroup")
;   (s/optional-key :created_at) s/Any
;   (s/optional-key :description) (s/maybe s/Str)
;   (s/optional-key :external_uri) (s/maybe s/Str)
;   (s/optional-key :external_uris) [s/Str]
;   (s/optional-key :first_name) (s/maybe s/Str)
;   (s/optional-key :id) s/Uuid
;   (s/optional-key :institutional_id) (s/maybe s/Str)
;   (s/optional-key :last_name) (s/maybe s/Str)
;   (s/optional-key :pseudonym) (s/maybe s/Str)
;   (s/optional-key :updated_at) s/Any})
;
;(def schema_export_people
;  {:id s/Uuid
;   :subtype (s/enum "Person" "PeopleGroup" "PeopleInstitutionalGroup")
;   (s/optional-key :created_at) s/Any
;   (s/optional-key :description) (s/maybe s/Str)
;   (s/optional-key :external_uri) (s/maybe s/Str)
;   (s/optional-key :external_uris) [s/Str]
;   (s/optional-key :first_name) (s/maybe s/Str)
;   (s/optional-key :institutional_id) (s/maybe s/Str)
;   (s/optional-key :last_name) (s/maybe s/Str)
;   (s/optional-key :pseudonym) (s/maybe s/Str)
;   (s/optional-key :searchable) s/Str
;   (s/optional-key :updated_at) s/Any})
;
;(def schema_update_person
;  {(s/optional-key :id) s/Uuid
;   (s/optional-key :description) s/Str
;   (s/optional-key :external_uris) [s/Str]
;   (s/optional-key :first_name) (s/maybe s/Str)
;   (s/optional-key :institutional_id) s/Str
;   (s/optional-key :last_name) s/Str
;   (s/optional-key :pseudonym) s/Str
;   (s/optional-key :searchable) s/Str})
;
;(def schema_query_people
;  {(s/optional-key :count) s/Int
;   (s/optional-key :description) s/Str
;   (s/optional-key :first_name) s/Str
;   (s/optional-key :full_data) s/Bool
;   (s/optional-key :id) s/Uuid
;   (s/optional-key :institutional_id) s/Str
;   (s/optional-key :last_name) s/Str
;   (s/optional-key :page) s/Int
;   (s/optional-key :pseudonym) s/Str
;   (s/optional-key :searchable) s/Str
;   (s/optional-key :subtype) (s/enum "Person"
;                                     "PeopleGroup"
;                                     "PeopleInstitutionalGroup")})

;; TODO: not in use?
;"TODO check subtype, catch errors"
;(defn handle_create-person
;  [request]
;  (try
;    (catcher/with-logging {}
;      (let [data (-> request :parameters :body)
;            data_wid (assoc data
;                            :id (or (:id data) (clj-uuid/v4))
;                            :subtype (-> data :subtype str))
;            sql-query (-> (sql/insert-into :people)
;                          (sql/values [data_wid])
;                          sql-format)
;            db-result (jdbc/execute-one! (:tx request) sql-query)]
;
;        (if-let [result (::jdbc/update-count db-result)]
;          (sd/response_ok (transform_export result) 201)
;          (sd/response_failed "Could not create person." 406))))
;    (catch Exception ex (sd/response_exception ex))))
;
;(defn handle_get-person
;  [req]
;  (let [id-or-institutinal-person-id (-> req :parameters :path :id str)]
;    (if-let [person (db-person-get id-or-institutinal-person-id (:tx req))]
;      (sd/response_ok (transform_export person))
;      (sd/response_failed "No such person found" 404))))
;
;(defn handle_delete-person [req]
;  (try
;    (catcher/with-logging {}
;      (let [id (-> req :parameters :path :id)
;            tx (:tx req)]
;        (if-let [old-data (db-person-get id tx)]
;          (let [sql-query (-> (sql/delete-from :people)
;                              (sql/where (jdbc-id-where-clause id))
;                              sql-format)
;                del-result (jdbc/execute! tx sql-query)]
;
;            (if (= 1 (::jdbc/update-count del-result))
;              (sd/response_ok (transform_export old-data) 200)
;              (sd/response_failed "Could not delete person." 406)))
;          (sd/response_failed "No such person data." 404))))
;    (catch Exception ex (sd/response_exception ex))))

;(defn handle_patch-person
;  [req]
;  (try
;    (catcher/with-logging {}
;      (let [body (get-in req [:parameters :body])
;            tx (:tx req)
;            id (-> req :parameters :path :id)
;            sql-query (-> (sql/update :people)
;                          (sql/set body)
;                          (sql/where (jdbc-id-where-clause id))
;                          sql-format)
;            upd-result (jdbc/execute! tx sql-query)]
;
;        (if (= 1 (first upd-result))
;          (sd/response_ok (transform_export (db-person-get id tx)))
;          (sd/response_failed "Could not update person" 406))))
;    (catch Exception ex (sd/response_exception ex))))

;;; TODO: not in use?
;(def admin-routes
;  ["/people"
;   ["/" {:get
;         {:summary "Get all people ids???"
;          :description "Query list of people only for ids or full-data. Optional Paging."
;          :handler handle_query-people
;          :middleware [wrap-authorize-admin!]
;          :swagger {:produces "application/json"}
;          :parameters {:query schema_query_people}
;          :content-type "application/json"
;                ;:accept "application/json"
;          :coercion reitit.coercion.schema/coercion
;          :responses {200 {:body {:people [schema_export_people]}}}}
;
;         :post
;         {:summary "Create a person"
;          :description "Create a person.\n The \nThe [subtype] has to be one of [Person, ...]. \nAt least one of [first_name, last_name, description] must have a value."
;          :handler handle_create-person
;          :middleware [wrap-authorize-admin!]
;          :swagger {:produces "application/json" :consumes "application/json"}
;          :content-type "application/json"
;          :accept "application/json"
;          :coercion reitit.coercion.schema/coercion
;          :parameters {:body schema_import_person}
;          :responses {201 {:body schema_import_person_result}
;                      406 {:body s/Any}}}}]
;
;   ["/:id"
;    {:get
;     {:summary "Get person by id"
;      :description "Get person by id. Returns 404, if no such person exists. TODO query params."
;      :swagger {:produces "application/json"}
;      :content-type "application/json"
;      :accept "application/json"
;      :handler handle_get-person
;      :middleware [wrap-authorize-admin!]
;      :coercion reitit.coercion.schema/coercion
;      :parameters {:path {:id s/Str}}
;      :responses {200 {:body schema_export_person}
;                  404 {:body s/Str}}}
;
;     :put
;     {:summary "Updates person entity fields"
;      :description "Updates the person entity fields"
;      :swagger {:consumes "application/json" :produces "application/json"}
;      :content-type "application/json"
;      :accept "application/json"
;      :handler handle_patch-person
;      :middleware [wrap-authorize-admin!]
;      :coercion reitit.coercion.schema/coercion
;      :parameters {:path {:id s/Str}
;                   :body schema_update_person}
;      :responses {200 {:body schema_export_person}
;                  404 {:body s/Str}}}
;
;     :delete
;     {:summary "Deletes a person by id"
;      :description "Delete a person by id"
;      :swagger {:produces "application/json"}
;      :content-type "application/json"
;      :handler handle_delete-person
;      :middleware [wrap-authorize-admin!]
;      :coercion reitit.coercion.schema/coercion
;      :parameters {:path {:id s/Str}}
;      :responses {200 {:body schema_export_person}
;                  404 {:body s/Any}}}}]])
;
;; TODO user can create a person
;; are public routes
;;; TODO: not in use?
;
;(def user-routes
;  ["/people"
;   {:swagger {:tags ["people"]}}
;   ["/" {:get {:summary (sd/sum_pub "Get all people ids")
;               :description "Query list of people only for ids or full-data. Optional Paging."
;               :handler handle_query-people
;
;               :swagger {:produces "application/json"}
;               :parameters {:query schema_query_people}
;               :content-type "application/json"
;               :coercion reitit.coercion.schema/coercion
;               :responses {200 {:body {:people [schema_export_people]}}}}}]
;
;   ["/:id" {:get {:summary (sd/sum_pub "Get person by id OR string")
;                  :description "Get person by id. Returns 404, if no such person exists. TODO query params."
;                  :swagger {:produces "application/json"}
;                  :content-type "application/json"
;                  :accept "application/json"
;                  :handler handle_get-person
;                  :coercion reitit.coercion.schema/coercion
;
;                  ;:parameters {:path {:id s/Str}}
;                  :parameters {:path {:id s/Any}}
;
;                  :responses {200 {:body schema_export_person}
;                              404 {:body s/Str}}}}]])
;(debug/debug-ns *ns*)