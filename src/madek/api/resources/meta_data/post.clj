(ns madek.api.resources.meta_data.post
  (:require
   [cheshire.core]
   [cheshire.core :as cheshire]
   [logbug.catcher :as catcher]
   [madek.api.resources.meta_data.common :refer :all]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [reitit.coercion.schema]
   [reitit.coercion.spec]
   [schema.core :as s]
   [taoensso.timbre :as timbre :refer [debug spy]]))

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
            person-id (-> req :parameters :body :person_id)
            role-id (-> req :parameters :body :role_id)
            tx (:tx req)
            user-id (-> req :authenticated-entity :id)]
        (if-let [result (create_md_and_people mr meta-key-id person-id role-id user-id tx)]
          (sd/response_ok result) ;)
          (if-let [retryresult (create_md_and_people mr meta-key-id person-id role-id user-id tx)]
            (sd/response_ok retryresult)
            (sd/response_failed "Could not create md people" 406)))))
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
  {:created_by_id (s/maybe s/Uuid)
   :media_entry_id (s/maybe s/Uuid)
   :collection_id (s/maybe s/Uuid)
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
   :role_id (s/maybe s/Uuid)
   :created_by_id s/Uuid
   :meta_data_updated_at s/Any
   :id s/Uuid
   :position s/Any})

(def ResponseSchema5
  {:meta_data MetaDataSchema5
   :md_people MdPeopleSchema5})

(def collection_id.meta_key_id.meta_data_people
  {:summary "Create meta-data people for media-entry"
   :handler handle_create-meta-data-people
   :middleware [;wrap-me-add-meta-data
                ; wrap-add-person
                ; wrap-add-role
                jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid
                       :meta_key_id s/Str}
                :body {:person_id s/Uuid
                       (s/optional-key :role_id) (s/maybe s/Uuid)
                       (s/optional-key :position) s/Int}}
   :responses {200 {:description "Returns the created meta-data people."
                    :body ResponseSchema5}}})

(def media_entry_id.meta_key_id.meta_data_people
  {:summary "Create meta-data people for media-entry"
   :handler handle_create-meta-data-people
   :middleware [;wrap-me-add-meta-data
                ; wrap-add-person
                ; wrap-add-role
                jqh/ring-wrap-add-media-resource
                jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid
                       :meta_key_id s/Str}
                :body {:person_id s/Uuid
                       (s/optional-key :role_id) (s/maybe s/Uuid)
                       (s/optional-key :position) s/Int}}
   :responses {200 {:description "Returns the created meta-data people."
                    :body ResponseSchema5}}})
