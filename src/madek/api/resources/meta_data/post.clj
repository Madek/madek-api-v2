(ns madek.api.resources.meta_data.post
  (:require [cheshire.core]
            [cheshire.core :as cheshire]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.resources.meta_data.common :refer :all]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.json_query_param_helper :as jqh]
            [madek.api.utils.helper :refer [to-uuid strip-prefixes-generic]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [schema.core :as s]
            [taoensso.timbre :refer [error]]))

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
          (sd/response_failed "Failed to add meta data text" 406))))
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
          (sd/response_failed "Failed to add meta data text-date" 406))))
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
          (sd/response_failed "Failed to add meta data json" 406))))
    (catch Exception ex (sd/response_exception ex))))

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
          (sd/response_ok result) ;)
          (if-let [retryresult (create_md_and_people mr meta-key-id person-id user-id tx)]
            ;((sd/logwrite req (str "handle_create-meta-data-people:"
            ;                       "mr-id: " (:id mr)
            ;                       "meta-key: " meta-key-id
            ;                       "person-id:" person-id
            ;                       "result: " retryresult))
            (sd/response_ok retryresult) ;)
            (sd/response_failed "Could not create md people" 406)))))
    (catch Exception ex (sd/response_exception ex))))

(defn db-create-meta-data-roles
  [db md-id role-id person-id position]
  (let [data {:meta_datum_id (to-uuid md-id)
              :person_id person-id
              :role_id role-id}
        data (if (nil? position)
               data
               (assoc data :position position))

        sql-query (-> (sql/insert-into :meta_data_roles)
                      (sql/values [data])
                      (sql/returning :*) ;; FIXME: is correct but key-prefix is still in use
                      sql-format)
        result (-> (jdbc/execute! db sql-query)
                   strip-prefixes-generic)]
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
            person-id (-> req :authenticated-entity :person_id)
            position (-> req :parameters :path :position)
            tx (:tx req)]

        (if-let [result (create_md_and_role mr meta-key-id role-id person-id position user-id tx)]
          (handle_create-roles-success req (:id mr) role-id person-id result)

          (if-let [retryresult (create_md_and_role mr meta-key-id role-id person-id position user-id tx)]
            (handle_create-roles-success req (:id mr) role-id person-id retryresult)
            (sd/response_failed "Could not create md role." 406)))))
    (catch Exception ex (sd/response_exception ex))))

;; ######## handler ################################################

(def meta-datum.meta_key_id.text
  {:summary "Create meta-data text for media-entry"
   :handler handle_create-meta-data-text
   :middleware [jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str}
                :body {:string s/Str}}
   :responses {200 {:description "Returns the created meta-data text."
                    :body {:created_by_id s/Uuid
                           :media_entry_id (s/maybe s/Uuid)
                           :collection_id (s/maybe s/Uuid)
                           :type s/Str
                           :meta_key_id s/Str
                           :string (s/maybe s/Str)
                           :id s/Uuid
                           :meta_data_updated_at (s/maybe s/Any)
                           :json (s/maybe s/Any)
                           :other_media_entry_id (s/maybe s/Any)}}}})
(def meta-datum.meta_key_id.text-date
  {:summary "Create meta-data text-date for media-entry"
   :handler handle_create-meta-data-text-date
   :middleware [jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str}
                :body {:string s/Str}}
   :responses {200 {:description "Returns the created meta-data text-date."
                    :body {:created_by_id s/Uuid
                           :media_entry_id (s/maybe s/Uuid)
                           :collection_id (s/maybe s/Uuid)
                           :type s/Str
                           :meta_key_id s/Str
                           :string (s/maybe s/Str)
                           :id s/Uuid
                           :meta_data_updated_at (s/maybe s/Any)
                           :json (s/maybe s/Any)
                           :other_media_entry_id (s/maybe s/Any)}}}})

(s/defschema MetaDataJSON
  {:created_by_id s/Uuid
   :media_entry_id (s/maybe s/Uuid)
   :collection_id s/Uuid
   :type (s/enum "MetaDatum::JSON")
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :id s/Uuid
   :meta_data_updated_at s/Any
   :json (s/maybe s/Any)
   :other_media_entry_id (s/maybe s/Uuid)})

(def meta-datum.meta_key_id.json
  {:summary "Create meta-data json for media-entry"
   :handler handle_create-meta-data-json
   :middleware [jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str}
                :body {:json s/Any}}
   :responses {200 {:description "Returns the created meta-data json."
                    :body {:created_by_id s/Uuid
                           :media_entry_id (s/maybe s/Uuid)
                           :collection_id (s/maybe s/Uuid)
                           :type s/Str
                           :meta_key_id s/Str
                           :string (s/maybe s/Str)
                           :id s/Uuid
                           :meta_data_updated_at (s/maybe s/Any)
                           :json s/Any
                           :other_media_entry_id (s/maybe s/Any)}}}})
(def meta-datum.meta_key_id.keyword.keyword_id
  {:summary "Create meta-data keyword for media-entry."
   :handler handle_create-meta-data-keyword
   :middleware [;wrap-me-add-meta-data
                wrap-add-keyword
                jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str ;; is this meta_datum_id
                       :keyword_id s/Uuid}}
   :responses {200 {:description "Returns the created meta-data keyword."
                    :body {:meta_data s/Any
                           :md_keywords s/Any}}}})

(def MetaDataSchema4
  {:created_by_id s/Uuid
   :media_entry_id (s/maybe s/Uuid)
   :collection_id (s/maybe s/Uuid)
   :type s/Str
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :id s/Uuid
   :meta_data_updated_at s/Any
   :json (s/maybe s/Any)
   :other_media_entry_id (s/maybe s/Uuid)})

(def MdPeopleSchema4
  {:meta_datum_id s/Uuid
   :person_id s/Uuid
   :created_by_id s/Uuid
   :meta_data_updated_at s/Any
   :id s/Uuid
   :position s/Int})

(def ResponseSchema4
  {:meta_data MetaDataSchema4
   :md_people MdPeopleSchema4})

(def media_entry_id.meta-datum.meta_key_id.people.person_id
  {:summary "Create meta-data people for a media-entries meta-key."
   :handler handle_create-meta-data-people
   :middleware [wrap-add-person
                jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata
                wrap-me-add-meta-data]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str
                       :person_id s/Uuid}}
   :responses {200 {:description "Returns the created meta-data people."
                    :body ResponseSchema4}}})

(def media_entry_id.meta-datum.meta_key_id.role.role_id.person_id.position
  {:summary "Create meta-data role for media-entry."
   :handler handle_create-meta-data-role
   :middleware [wrap-add-role
                wrap-add-person
                jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str
                       :role_id s/Uuid
                       :person_id s/Uuid
                       :position s/Int}}
   :responses {200 {:description "Returns the created meta-data role."
                    :body {:meta_data s/Any
                           :md_roles s/Any}}}})
(def collection_id.meta-datum:meta_key_id.text
  {:summary "Create meta-data text for collection."
   :handler handle_create-meta-data-text
   :middleware [jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :accept "application/json"
   :content-type "application/json"
   :swagger {:produces "application/json" :consumes "application/json"}
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str}
                :body {:string s/Str}}
   :responses {200 {:description "Returns the created meta-data text."
                    :body {:created_by_id s/Uuid
                           :media_entry_id (s/maybe s/Uuid)
                           :collection_id s/Uuid
                           :type s/Str
                           :meta_key_id s/Str
                           :string (s/maybe s/Str)
                           :id s/Uuid
                           :meta_data_updated_at (s/maybe s/Any)
                           :json (s/maybe s/Any)
                           :other_media_entry_id (s/maybe s/Uuid)}}
               500 {:description "Returns the cause of error."
                    :body {:message s/Str}}}})

(def collection_id.meta-datum:meta_key_id.text-date
  {:summary "Create meta-data json for collection."
   :handler handle_create-meta-data-text-date
   :middleware [jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str}
                :body {:string s/Str}}
   :responses {200 {:description "Returns the created meta-data text-date."
                    :body {:created_by_id s/Uuid
                           :media_entry_id (s/maybe s/Uuid)
                           :collection_id s/Uuid
                           :type s/Str
                           :meta_key_id s/Str
                           :string (s/maybe s/Str)
                           :id s/Uuid
                           :meta_data_updated_at (s/maybe s/Any)
                           :json (s/maybe s/Any)
                           :other_media_entry_id (s/maybe s/Uuid)}}}})

(def collection_id.meta_key_id.json
  {:summary "Create meta-data json for collection."
   :handler handle_create-meta-data-json
   :middleware [jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str}
                :body {:json s/Any}}
   :responses {200 {:description "Returns the created meta-data json."
                    :body MetaDataJSON}}})

(def MetaDataSchema
  {:created_by_id s/Uuid
   :media_entry_id (s/maybe s/Uuid)
   :collection_id s/Uuid
   :type (s/enum "MetaDatum::Keywords")
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :id s/Uuid
   :meta_data_updated_at s/Any
   :json (s/maybe s/Any)
   :other_media_entry_id (s/maybe s/Uuid)})

(def MdKeywordsSchema
  {:id s/Uuid
   :created_by_id s/Uuid
   :meta_datum_id s/Uuid
   :keyword_id s/Uuid
   :created_at s/Any
   :updated_at s/Any
   :meta_data_updated_at s/Any
   :position s/Int})

(def BodySchema
  {:meta_data MetaDataSchema
   :md_keywords [MdKeywordsSchema]})

(def collection_id.meta_key_id.keyword.keyword_id
  {:summary "Create meta-data keyword for collection."
   :handler handle_create-meta-data-keyword
   :middleware [;wrap-me-add-meta-data
                wrap-add-keyword
                jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str
                       :keyword_id s/Uuid}}
   :responses {200 {:description "Returns the created meta-data keyword."
                    :body BodySchema}}})

(def MetaDataSchema5
  {:created_by_id s/Uuid
   :media_entry_id (s/maybe s/Uuid)
   :collection_id s/Uuid
   :type s/Str
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :id s/Uuid
   :meta_data_updated_at s/Any
   :json (s/maybe s/Any)
   :other_media_entry_id (s/maybe s/Uuid)})

;; FIXME: remove key-prefix
(def MdPeopleSchema5
  {:meta_datum_id s/Uuid
   :person_id s/Uuid
   :created_by_id s/Uuid
   :meta_data_updated_at s/Any
   :id s/Uuid
   :position s/Any})

(def ResponseSchema5
  {:meta_data MetaDataSchema5
   :md_people MdPeopleSchema5})

(def collection_id.meta_key_id.people.person_id
  {:summary "Create meta-data people for media-entry"
   :handler handle_create-meta-data-people
   :middleware [;wrap-me-add-meta-data
                wrap-add-person
                jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str
                       :person_id s/Uuid}}
   :responses {200 {:description "Returns the created meta-data people."
                    :body ResponseSchema5}}})

(def MetaDataSchema3
  {:created_by_id s/Uuid
   :media_entry_id (s/maybe s/Uuid)
   :collection_id s/Uuid
   :type s/Str
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :id s/Uuid
   :meta_data_updated_at s/Any
   :json (s/maybe s/Any)
   :other_media_entry_id (s/maybe s/Uuid)})

(def MdRoleItemSchema3
  {:id s/Uuid
   :meta_datum_id s/Uuid
   :person_id s/Uuid
   :role_id s/Uuid
   :position s/Int})

(def ResponseSchema3
  {:meta_data MetaDataSchema3
   :md_roles [MdRoleItemSchema3]})

(def collection_id.meta_key_id.role.role_id
  {:summary "Create meta-data role for collection"
   :handler handle_create-meta-data-role
   :middleware [wrap-add-role
                jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str
                       :role_id s/Uuid}}
   :responses {200 {:description "Returns the created meta-data role."
                    :body ResponseSchema3}}})
