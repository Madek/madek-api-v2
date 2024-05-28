(ns madek.api.resources.meta_data.post
  (:require [cheshire.core]
            [cheshire.core :as cheshire]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]

            [madek.api.resources.meta_data.common :refer :all]


            [logbug.catcher :as catcher]
            [madek.api.db.core :refer [builder-fn-options-default]]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.helper :refer [convert-map-if-exist to-uuid]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [schema.core :as s]
            [taoensso.timbre :refer [error info]]))

;(defn- col-key-for-mr-type [mr]
;  (let [mr-type (-> mr :type)]
;    (if (= mr-type "Collection")
;      :collection_id
;      :media_entry_id)))
;
;(defn- assoc-media-resource-typed-id [mr ins-data]
;  (assoc ins-data
;         (col-key-for-mr-type mr)
;         (-> mr :id)))
;
;
;(defn- fabric-meta-data
;  [mr meta-key-id md-type user-id]
;  (let [data {:meta_key_id meta-key-id
;              :type md-type
;              :created_by_id (to-uuid user-id)}]
;    (assoc-media-resource-typed-id mr data)))
;
;(defn db-get-meta-data
;  ([mr mk-id md-type db]
;   (let [mr-id (str (-> mr :id))
;         mr-key (col-key-for-mr-type mr)
;         db-query (-> (sql/select :*)
;                      (sql/from :meta_data)
;                      (sql/where [:and
;                                  [:= :meta_key_id mk-id]
;                                  [:= mr-key (to-uuid mr-id mr-key)]])
;                      sql-format)
;         db-result (jdbc/execute-one! db db-query builder-fn-options-default)
;         db-type (:type db-result)]
;
;     (if (or (= nil md-type) (= md-type db-type))
;       db-result
;       nil))))
;
;(defn- db-create-meta-data
;  ([db meta-data]
;   (info "db-create-meta-data: " meta-data)
;   (let [sql-query (-> (sql/insert-into :meta_data)
;                       (sql/values [(convert-map-if-exist meta-data)])
;                       (sql/returning :*)
;                       sql-format)
;         result (jdbc/execute-one! db sql-query builder-fn-options-default)]
;     (if result
;       result
;       nil)))
;
;  ([db mr meta-key-id md-type user-id]
;   ;(info "db-create-meta-data: " "MK-ID: " meta-key-id "Type:" md-type "User: " user-id)
;   (db-create-meta-data db (fabric-meta-data mr meta-key-id md-type user-id)))
;
;  ([db mr meta-key-id md-type user-id meta-data]
;   ;(info "db-create-meta-data: " "MK-ID: " meta-key-id "Type:" md-type "User: " user-id "MD: " meta-data)
;   (let [md (merge (fabric-meta-data mr meta-key-id md-type user-id) meta-data)]
;     ;(info "db-create-meta-data: "
;     ;              "MK-ID: " meta-key-id
;     ;              "Type:" md-type
;     ;              "User: " user-id
;     ;              "MD: " meta-data
;     ;              "MD-new: " md)
;     (db-create-meta-data db md))))



; TODO tests, response coercion
(defn handle_create-meta-data-text
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            user-id (-> req :authenticated-entity :id str)
            meta-key-id (-> req :parameters :path :meta_key_id)
            text-data (-> req :parameters :body :string)
            md-type "MetaDatum::Text"
            tx (:tx req)
            mdnew {:string text-data}
            ins-result (db-create-meta-data tx mr meta-key-id md-type user-id mdnew)]

        (sd/logwrite req (str "handle_create-meta-data-text"
                              " mr-id: " (:id mr)
                                         " meta-key-id: " meta-key-id
                                         " ins-result: " ins-result))

        (if (= md-type (:type ins-result))
          (sd/response_ok ins-result)
          (sd/response_failed {:message "Failed to add meta data text"} 406))))
    (catch Exception ex (sd/response_exception ex))))



; TODO tests, response coercion
(defn handle_create-meta-data-text-date
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            user-id (-> req :authenticated-entity :id str)
            meta-key-id (-> req :parameters :path :meta_key_id)
            text-data (-> req :parameters :body :string)
            md-type "MetaDatum::TextDate"
            mdnew {:string text-data}
            ins-result (db-create-meta-data (:tx req) mr meta-key-id md-type user-id mdnew)]

        (sd/logwrite req (str "handle_create-meta-data-text-date:"
                              " mr-id: " (:id mr)
                                         " meta-key-id: " meta-key-id
                                         " ins-result: " ins-result))

        (if (= md-type (:type ins-result))
          (sd/response_ok ins-result)
          (sd/response_failed {:message "Failed to add meta data text-date"} 406))))
    (catch Exception ex (sd/response_exception ex))))



; TODO tests, response coercion
(defn handle_create-meta-data-json
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            user-id (-> req :authenticated-entity :id str)
            meta-key-id (-> req :parameters :path :meta_key_id)
            json-data (-> req :parameters :body :json)
            json-parsed (cheshire/parse-string json-data)
            md-type "MetaDatum::JSON"
            ;mdnew {:json json-parsed}
            mdnew {:json (with-meta json-parsed {:pgtype "jsonb"})}
            ins-result (db-create-meta-data (:tx req) mr meta-key-id md-type user-id mdnew)]

        (sd/logwrite req (str "handle_create-meta-data-json:"
                              " mr-id: " (:id mr)
                                         " meta-key-id: " meta-key-id
                                         " ins-result: " ins-result))

        (if (= md-type (:type ins-result))
          (sd/response_ok ins-result)
          (sd/response_failed {:message "Failed to add meta data json"} 406))))
    (catch Exception ex (sd/response_exception ex))))



;(defn- db-create-meta-data-keyword
;  [db md-id kw-id user-id]
;  (let [data {:meta_datum_id md-id
;              :keyword_id kw-id
;              :created_by_id user-id}
;        sql-query (-> (sql/insert-into :meta_data_keywords)
;                      (sql/values [data])
;                      (sql/returning :*)
;                      sql-format)
;        result (jdbc/execute! db sql-query builder-fn-options-default)]
;    (info "db-create-meta-data-keyword"
;      "\nkw-data\n" data
;      "\nresult\n" result)
;    result))



;(def MD_TYPE_KEYWORDS "MetaDatum::Keywords")
;(def MD_KEY_KWS :keywords)
;(def MD_KEY_KW_DATA :md_keywords)
;(def MD_KEY_KW_IDS :keywords_ids)

;(defn create_md_and_keyword
;  [mr meta-key-id kw-id user-id tx]
;
;  (try
;    (catcher/with-logging {}
;      (jdbc/with-transaction [tx tx]
;        (let [meta-data (db-get-meta-data mr meta-key-id nil tx)])
;        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
;          ; already has meta-data
;          (if-let [result (db-create-meta-data-keyword tx (:id meta-data) kw-id user-id)]
;            {:meta_data meta-data
;             MD_KEY_KW_DATA result}
;            nil)
;
;          ; create meta-data and md-kw
;          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_KEYWORDS user-id)]
;            (if-let [ip-result (db-create-meta-data-keyword tx (-> mdins-result :id) kw-id user-id)]
;              {:meta_data mdins-result
;               MD_KEY_KW_DATA ip-result}
;              nil)
;            nil))))
;    (catch Exception _
;      (error "Could not create md keyword" _)
;      nil)))

; TODO tests, response coercion
(defn handle_create-meta-data-keyword
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            kw-id (-> req :parameters :path :keyword_id)
            tx (:tx req)
            user-id (-> req :authenticated-entity :id)]

        (if-let [result (create_md_and_keyword mr meta-key-id kw-id user-id tx)]
          ;((sd/logwrite req  (str "handle_create-meta-data-keyword:" "mr-id: " (:id mr) "kw-id: " kw-id "result: " result))
          (sd/response_ok result)
          ;)
          (if-let [retryresult (create_md_and_keyword mr meta-key-id kw-id user-id tx)]
            ((sd/logwrite req (str "handle_create-meta-data-keyword:" "mr-id: " (:id mr) "kw-id: " kw-id "result: " retryresult))
             (sd/response_ok retryresult))
            (sd/response_failed "Could not create md keyword" 406)))))
    (catch Exception ex (sd/response_exception ex))))




;(defn- db-create-meta-data-people
;  [db md-id person-id user-id]
;  (let [data {:meta_datum_id (to-uuid md-id)
;              :person_id person-id
;              :created_by_id user-id}
;        sql-query (-> (sql/insert-into :meta_data_people)
;                      (sql/values [data])
;                      (sql/returning :*)
;                      sql-format)
;        result (jdbc/execute-one! db sql-query)]
;
;    ;(info "db-create-meta-data-people" "\npeople-data\n" data "\nresult\n" result)
;    result))

;(def MD_TYPE_PEOPLE "MetaDatum::People")
;(def MD_KEY_PEOPLE :people)
;(def MD_KEY_PEOPLE_DATA :md_people)
;(def MD_KEY_PEOPLE_IDS :people_ids)

;(defn create_md_and_people
;  [mr meta-key-id person-id user-id tx]
;  (try
;    (catcher/with-logging {}
;      (jdbc/with-transaction [tx tx]
;        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
;          ; already has meta-data
;          (do
;            (if-let [result (db-create-meta-data-people tx (:id meta-data) person-id user-id)]
;              (do
;                {:meta_data meta-data
;                 MD_KEY_PEOPLE_DATA result})
;              nil))
;
;          ; create meta-data and md-people
;          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_PEOPLE user-id)]
;            (do
;              (if-let [ip-result (db-create-meta-data-people tx (-> mdins-result :id str) person-id user-id)]
;                (do
;                  {:meta_data mdins-result
;                   MD_KEY_PEOPLE_DATA ip-result})
;                nil))
;            nil))))
;    (catch Exception _
;      (error "Could not create md people" _)
;      nil)))

; TODO tests, response coercion
(defn handle_create-meta-data-people
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            person-id (-> req :parameters :path :person_id)
            tx (:tx req)
            user-id (-> req :authenticated-entity :id)]

        (if-let [result (create_md_and_people mr meta-key-id person-id user-id tx)]
          ;((sd/logwrite req (str "handle_create-meta-data-people:"
          ;                       "mr-id: " (:id mr)
          ;                       "meta-key: " meta-key-id
          ;                       "person-id:" person-id
          ;                       "result: " result))
          (sd/response_ok result)                           ;)
          (if-let [retryresult (create_md_and_people mr meta-key-id person-id user-id tx)]
            ;((sd/logwrite req (str "handle_create-meta-data-people:"
            ;                       "mr-id: " (:id mr)
            ;                       "meta-key: " meta-key-id
            ;                       "person-id:" person-id
            ;                       "result: " retryresult))
            (sd/response_ok retryresult)                    ;)
            (sd/response_failed "Could not create md people" 406)))))
    (catch Exception ex (sd/response_exception ex))))


;(def MD_TYPE_ROLES "MetaDatum::Roles")
;(def MD_KEY_ROLES :roles)
;(def MD_KEY_ROLES_DATA :md_roles)
;(def MD_KEY_ROLES_IDS :roles_ids)

(defn db-create-meta-data-roles
  [db md-id role-id person-id position]
  (let [data {:meta_datum_id md-id
              :person_id person-id
              :role_id role-id
              :position position}
        sql-query (-> (sql/insert-into :meta_data_roles)
                      (sql/values [data])
                      sql-format)
        result (jdbc/execute! db sql-query)]
    result))

(defn- create_md_and_role
  [mr meta-key-id role-id person-id position user-id tx]
  (try
    (catcher/with-logging {}
      (jdbc/with-transaction [tx tx]
        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
          ;already has meta-data
          (if-let [result (db-create-meta-data-roles tx (:id meta-data) role-id person-id position)]
            {:meta_data meta-data
             MD_KEY_ROLES_DATA result}
            nil)

          ;create meta-data and role
          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_ROLES user-id)]
            (if-let [ip-result (db-create-meta-data-roles
                                 tx
                                 (-> mdins-result :id str)
                                 role-id person-id position)]
              {:meta_data mdins-result
               MD_KEY_ROLES_DATA ip-result}
              nil)
            nil))))
    (catch Exception ex
      (error "Could not create md role" ex)
      nil)))

(defn- handle_create-roles-success [req mr-id role-id person-id result]
  (sd/logwrite req (str "handle_create-meta-data-role:"
                        " mr-id: " mr-id
                                   " role-id: " role-id
                                   " person-id: " person-id
                                   " result: " result))
  (sd/response_ok result))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-role
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            user-id (-> req :authenticated-entity :id)
            meta-key-id (-> req :parameters :path :meta_key_id)
            role-id (-> req :parameters :path :role_id)
            person-id (-> req :parameters :path :person_id)
            position (-> req :parameters :path :position)
            tx (:tx req)]

        (if-let [result (create_md_and_role mr meta-key-id role-id person-id position user-id tx)]
          (handle_create-roles-success req (:id mr) role-id person-id result)

          (if-let [retryresult (create_md_and_role mr meta-key-id role-id person-id position user-id tx)]
            (handle_create-roles-success req (:id mr) role-id person-id retryresult)
            (sd/response_failed "Could not create md role." 406)))))
    (catch Exception ex (sd/response_exception ex))))


;(defn wrap-add-keyword [handler]
;  (fn [request] (sd/req-find-data
;                  request handler
;                  :keyword_id
;                  :keywords :id
;                  :keyword
;                  true)))
;
;(defn wrap-add-person [handler]
;  (fn [request] (sd/req-find-data
;                  request handler
;                  :person_id
;                  :people :id
;                  :person
;                  true)))
;
;(defn wrap-add-role [handler]
;  (fn [request] (sd/req-find-data
;                  request handler
;                  :role_id
;                  :roles :id
;                  :role
;                  true)))
;
;(defn wrap-me-add-meta-data [handler]
;  (fn [request] (sd/req-find-data2
;                  request handler
;                  :media_entry_id
;                  :meta_key_id
;                  :meta_data
;                  :media_entry_id
;                  :meta_key_id
;                  :meta-data
;                  false)))





;; ######## handler ################################################

(def meta-datum.meta_key_id.text
  {:summary "Create meta-data text for media-entry"
   :handler handle_create-meta-data-text
   :middleware [sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str}
                :body {:string s/Str}}
   :responses {200 {:body s/Any}}})


(def meta-datum.meta_key_id.text-date
  {:summary "Create meta-data text-date for media-entry"
   :handler handle_create-meta-data-text-date
   :middleware [sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str}
                :body {:string s/Str}}
   :responses {200 {:body s/Any}}})

(def meta-datum.meta_key_id.json
  {:summary "Create meta-data json for media-entry"
   :handler handle_create-meta-data-json
   :middleware [sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str}
                :body {:json s/Any}}
   :responses {200 {:body s/Any}}})


(def meta-datum.meta_key_id.keyword.keyword_id
  {:summary "Create meta-data keyword for media-entry."
   :handler handle_create-meta-data-keyword
   :middleware [;wrap-me-add-meta-data
                wrap-add-keyword
                sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str                   ;; is this meta_datum_id
                       :keyword_id s/Uuid}}
   :responses {200 {:body s/Any}}})

(def media_entry_id.meta-datum.meta_key_id.people.person_id
  {:summary "Create meta-data people for a media-entries meta-key."
   :handler handle_create-meta-data-people
   :middleware [wrap-add-person
                sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata
                wrap-me-add-meta-data]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str
                       :person_id s/Uuid}}
   :responses {200 {:body s/Any}}})


(def media_entry_id.meta-datum.meta_key_id.role.role_id.person_id.position
  {:summary "Create meta-data role for media-entry."
   :handler handle_create-meta-data-role
   :middleware [wrap-add-role
                wrap-add-person
                sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str
                       :role_id s/Uuid
                       :person_id s/Uuid
                       :position s/Int}}
   :responses {200 {:body s/Any}}})


(def collection_id.meta-datum:meta_key_id.text
  {:summary "Create meta-data text for collection."
   :handler handle_create-meta-data-text
   :middleware [sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :accept "application/json"
   :content-type "application/json"
   :swagger {:produces "application/json" :consumes "application/json"}
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str}
                :body {:string s/Str}}
   :responses {200 {:body s/Any}}})


(def collection_id.meta-datum:meta_key_id.text-date
  {:summary "Create meta-data json for collection."
   :handler handle_create-meta-data-text-date
   :middleware [sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str}
                :body {:string s/Str}}
   :responses {200 {:body s/Any}}})


(def collection_id.meta_key_id.json
  {:summary "Create meta-data json for collection."
   :handler handle_create-meta-data-json
   :middleware [sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str}
                :body {:json s/Any}}
   :responses {200 {:body s/Any}}})

(def collection_id.meta_key_id.keyword.keyword_id
  {:summary "Create meta-data keyword for collection."
   :handler handle_create-meta-data-keyword
   :middleware [;wrap-me-add-meta-data
                wrap-add-keyword
                sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str
                       :keyword_id s/Uuid}}
   :responses {200 {:body s/Any}}})


(def collection_id.meta_key_id.people.person_id
  {:summary "Create meta-data people for media-entry"
   :handler handle_create-meta-data-people
   :middleware [;wrap-me-add-meta-data
                wrap-add-person
                sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str
                       :person_id s/Uuid}}
   :responses {200 {:body s/Any}}})


(def collection_id.meta_key_id.role.role_id
  {:summary "Create meta-data role for media-entry"
   :handler handle_create-meta-data-role
   :middleware [wrap-add-role
                sd/ring-wrap-add-media-resource
                sd/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str
                       :role_id s/Uuid}}
   :responses {200 {:body s/Any}}})

