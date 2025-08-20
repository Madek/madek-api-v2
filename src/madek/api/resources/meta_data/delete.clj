(ns madek.api.resources.meta_data.delete
  (:require [cheshire.core]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.resources.meta_data.common :refer :all]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.resources.shared.json_query_param_helper :as jqh]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [schema.core :as s]
            [taoensso.timbre :refer [debug info]]))

(defn- handle-delete-meta-data [req]
  (let [mr (-> req :media-resource)
        meta-data (-> req :meta-data)
        meta-key-id (:meta_key_id meta-data)
        sql-query (-> (sql/delete-from :meta_data)
                      (sql-cls-upd-meta-data mr meta-key-id)
                      sql-format)
        del-result (jdbc/execute-one! (:tx req) sql-query)]
    (if (= 1 (::jdbc/update-count del-result))
      (sd/response_ok meta-data)
      (sd/response_failed "Could not delete meta_data." 406))))

(defn- db-delete-meta-data-keyword [db md-id kw-id]
  (let [sql-query (-> (sql/delete-from :meta_data_keywords)
                      (sql/where [:= :meta_datum_id md-id] [:= :keyword_id kw-id])
                      sql-format)
        result (jdbc/execute-one! db sql-query)]
    (info "db-delete-meta-data-keyword" "\nmd-id\n" md-id "\nkw-id\n" kw-id "\nresult\n" result)
    result))

(defn handle-delete-meta-data-keyword [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            kw-id (-> req :parameters :path :keyword_id)
            tx (:tx req)
            md (db-get-meta-data mr meta-key-id MD_TYPE_KEYWORDS tx)
            md-id (-> md :id)
            delete-result (db-delete-meta-data-keyword tx md-id kw-id)
            mdr (db-get-meta-data-keywords md-id tx)]

        (sd/logwrite req (str "handle_delete-meta-data-keyword:"
                              "mr-id: " (:id mr)
                              "md-id: " md-id
                              "meta-key: " meta-key-id
                              "keyword-id: " kw-id
                              "result: " delete-result))

        (if (= 1 (:next.jdbc/update-count delete-result))
          (sd/response_ok {:meta_data md MD_KEY_KW_DATA mdr})
          (sd/response_failed "Could not delete md keyword." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle-delete-meta-data-people [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            meta-datum-person-id (-> req :parameters :path :meta_datum_person_id)
            tx (:tx req)
            md (db-get-meta-data mr meta-key-id MD_TYPE_PEOPLE tx)
            sql-query (-> (sql/delete-from :meta_data_people)
                          (sql/where [:= :id meta-datum-person-id])
                          (sql/returning :*)
                          sql-format)
            del-result (jdbc/execute! tx sql-query)]
        (if (= 1 (count del-result))
          (sd/response_ok {:meta_data md MD_KEY_PEOPLE_DATA (db-get-meta-data-people (:id md) tx)})
          (sd/response_failed "Failed to delete meta data people" 406))))
    (catch Exception ex (sd/response_exception ex))))

(def delete.meta_key_id.keyword.keyword_id {:summary "Delete meta-data keyword for collection."
                                            :handler handle-delete-meta-data-keyword
                                            :middleware [wrap-add-keyword jqh/ring-wrap-add-media-resource jqh/ring-wrap-authorization-edit-metadata]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:collection_id s/Uuid :meta_key_id s/Str :keyword_id s/Uuid}}
                                            :responses {200 {:description "Returns the deleted meta-data."
                                                             :body {:meta_data {:created_by_id s/Uuid
                                                                                :media_entry_id (s/maybe s/Uuid)
                                                                                :collection_id s/Uuid
                                                                                :type (s/enum "MetaDatum::Keywords")
                                                                                :meta_key_id s/Str
                                                                                :string (s/maybe s/Str)
                                                                                :id s/Uuid
                                                                                :meta_data_updated_at s/Any
                                                                                :json (s/maybe s/Any)
                                                                                :other_media_entry_id (s/maybe s/Uuid)}
                                                                    :md_keywords [{:id s/Uuid
                                                                                   :created_by_id s/Uuid
                                                                                   :meta_datum_id s/Uuid
                                                                                   :keyword_id s/Uuid
                                                                                   :created_at s/Any
                                                                                   :updated_at s/Any
                                                                                   :meta_data_updated_at s/Any
                                                                                   :position s/Int}]}}}})

(def MetaDataSchema
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

(def media_entry_id.meta-datum.meta_key_id {:summary "Delete meta-data for media-entry and meta-key"
                                            :handler handle-delete-meta-data
                                            :middleware [jqh/ring-wrap-add-media-resource jqh/ring-wrap-authorization-view wrap-me-add-meta-data]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid :meta_key_id s/Str}}
                                            :responses {200 {:description "Returns the deleted meta-data." :body s/Any}}})

(def collection_id.meta_key_id.meta_data_people.meta_datum_person_id
  {:summary "Delete meta-data people for collection."
   :handler handle-delete-meta-data-people
   :middleware [#_wrap-add-person jqh/ring-wrap-add-media-resource jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:collection_id s/Uuid :meta_key_id s/Str :meta_datum_person_id s/Uuid}}
   :responses {200 {:description "Returns the deleted meta-data."
                    :body {:meta_data {:created_by_id s/Uuid
                                       :media_entry_id (s/maybe s/Uuid)
                                       :collection_id s/Uuid
                                       :type s/Str
                                       :meta_key_id s/Str
                                       :string (s/maybe s/Str)
                                       :id s/Uuid
                                       :meta_data_updated_at s/Any
                                       :json (s/maybe s/Any)
                                       :other_media_entry_id (s/maybe s/Uuid)}
                           :md_people [{:meta_datum_id s/Uuid
                                        :person_id s/Uuid
                                        :role_id (s/maybe s/Uuid)
                                        :created_by_id s/Uuid
                                        :meta_data_updated_at s/Any
                                        :id s/Uuid
                                        :position s/Int}]}}}})

(def media_entry_id.meta_key_id.meta_data_people.meta_datum_person_id
  {:summary "Delete meta-data people for collection."
   :handler handle-delete-meta-data-people
   :middleware [#_wrap-add-person jqh/ring-wrap-add-media-resource jqh/ring-wrap-authorization-edit-metadata]
   :coercion reitit.coercion.schema/coercion
   :parameters {:path {:media_entry_id s/Uuid :meta_key_id s/Str :meta_datum_person_id s/Uuid}}
   :responses {200 {:description "Returns the deleted meta-data."
                    :body {:meta_data {:created_by_id s/Uuid
                                       :media_entry_id s/Uuid
                                       :collection_id (s/maybe s/Uuid)
                                       :type s/Str
                                       :meta_key_id s/Str
                                       :string (s/maybe s/Str)
                                       :id s/Uuid
                                       :meta_data_updated_at s/Any
                                       :json (s/maybe s/Any)
                                       :other_media_entry_id (s/maybe s/Uuid)}
                           :md_people [{:meta_datum_id s/Uuid
                                        :person_id s/Uuid
                                        :role_id (s/maybe s/Uuid)
                                        :created_by_id s/Uuid
                                        :meta_data_updated_at s/Any
                                        :id s/Uuid
                                        :position s/Int}]}}}})

(def MetaDataSchema2
  {:created_by_id s/Uuid
   :media_entry_id s/Uuid
   :collection_id (s/maybe s/Uuid)
   :type s/Str
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :id s/Uuid
   :meta_data_updated_at s/Any
   :json (s/maybe s/Any)
   :other_media_entry_id (s/maybe s/Uuid)})

(def meta_key_id.keyword.keyword_id2 {:summary "Delete meta-data keyword for media-entry."
                                      :handler handle-delete-meta-data-keyword
                                      :middleware [wrap-add-keyword jqh/ring-wrap-add-media-resource jqh/ring-wrap-authorization-edit-metadata]
                                      :coercion reitit.coercion.schema/coercion
                                      :parameters {:path {:media_entry_id s/Uuid :meta_key_id s/Str :keyword_id s/Uuid}}
                                      :responses {200 {:description "Returns the deleted meta-data."
                                                       :body {:meta_data {:created_by_id s/Uuid
                                                                          :media_entry_id s/Uuid
                                                                          :collection_id (s/maybe s/Uuid)
                                                                          :type s/Str
                                                                          :meta_key_id s/Str
                                                                          :string (s/maybe s/Str)
                                                                          :id s/Uuid
                                                                          :meta_data_updated_at s/Any
                                                                          :json (s/maybe s/Any)
                                                                          :other_media_entry_id (s/maybe s/Uuid)}
                                                              :md_keywords [{:id s/Uuid
                                                                             :created_by_id s/Uuid
                                                                             :meta_datum_id s/Uuid
                                                                             :keyword_id s/Uuid
                                                                             :created_at s/Any
                                                                             :updated_at s/Any
                                                                             :meta_data_updated_at s/Any
                                                                             :position s/Int}]}}}})

(def media_entry_id.meta-datum.meta_key_id {:summary "Delete meta-data for media-entry and meta-key"
                                            :handler handle-delete-meta-data
                                            :middleware [jqh/ring-wrap-add-media-resource jqh/ring-wrap-authorization-view wrap-me-add-meta-data]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid :meta_key_id s/Str}}
                                            :responses {200 {:description "Returns the deleted meta-data." :body MetaDataSchema}}})

(def collection_id.meta-datum.meta_key_id {:summary "Delete meta-data for collection and meta-key"
                                           :handler handle-delete-meta-data
                                           :middleware [jqh/ring-wrap-add-media-resource jqh/ring-wrap-authorization-view wrap-col-add-meta-data]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:collection_id s/Uuid :meta_key_id s/Str}}
                                           :responses {200 {:description "Returns the deleted meta-data."
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
                                                       406 {:description "Returns the cause of error." :body {:message s/Str}}
                                                       500 {:description "Returns the cause of error." :body {:message s/Str}}}})
