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
            [taoensso.timbre :refer [info]]))

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

(defn- db-delete-meta-data-keyword
  [db md-id kw-id]
  (let [sql-query (-> (sql/delete-from :meta_data_keywords)
                      (sql/where [:= :meta_datum_id md-id] [:= :keyword_id kw-id])
                      sql-format)
        result (jdbc/execute-one! db sql-query)]
    (info "db-delete-meta-data-keyword"
          "\nmd-id\n" md-id
          "\nkw-id\n" kw-id
          "\nresult\n" result)
    result))

(defn handle_delete-meta-data-keyword
  [req]
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
          (sd/response_ok {:meta_data md
                           MD_KEY_KW_DATA mdr})
          (sd/response_failed "Could not delete md keyword." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-meta-data-people
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            person-id (-> req :parameters :path :person_id)
            tx (:tx req)
            md (db-get-meta-data mr meta-key-id MD_TYPE_PEOPLE tx)
            md-id (-> md :id)
            sql-query (-> (sql/delete-from :meta_data_people)
                          (sql/where [:and
                                      [:= :meta_datum_id md-id]
                                      [:= :person_id person-id]])
                          sql-format)
            del-result (jdbc/execute-one! tx sql-query)]
        (sd/logwrite req (str "\nhandle_delete-meta-data-people:"
                              "\nmr-id: " (:id mr)
                              " meta-key: " meta-key-id
                              " person-id: " person-id
                              " result: " del-result))

        (if (= 1 (:next.jdbc/update-count del-result))
          (sd/response_ok {:meta_data md
                           MD_KEY_PEOPLE_DATA (db-get-meta-data-people md-id tx)})
          (sd/response_failed {:message "Failed to delete meta data people"} 406))))
    (catch Exception ex (sd/response_exception ex))))

; TODO del meta-data if md-roles is empty ? sql-trigger ?
(defn handle_delete-meta-data-role
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        role-id (-> req :parameters :path :role_id)
        person-id (-> req :parameters :path :person_id)
        tx (:tx req)
        md (db-get-meta-data mr meta-key-id MD_TYPE_ROLES tx)
        md-id (-> md :id)
        ;mdr (db-get-meta-data-roles md-id)
        del-clause (dbh/sql-update-clause
                    "meta_datum_id" md-id
                    "role_id" role-id
                    "person_id" person-id)
        sql-query (-> (sql/delete-from :meta_data_roles)
                      (sql/where del-clause)
                      sql-format)
        del-result (jdbc/execute! tx sql-query)]

    (sd/logwrite req (str "handle_delete-meta-data-role:"
                          " mr-id: " (:id mr)
                          " meta-key: " meta-key-id
                          " role-id: " role-id
                          " person-id: " person-id
                          " clause: " del-clause
                          " result: " del-result))
    (if (< 1 (first del-result))
      (sd/response_ok {:meta_data md
                       MD_KEY_ROLES_DATA (db-get-meta-data-roles md-id tx)})
      (sd/response_failed "Could not delete meta-data role." 406))))

; ### Handler ##################################################################

(def delete.meta_key_id.keyword.keyword_id {:summary "Delete meta-data keyword for collection."
                                            :handler handle_delete-meta-data-keyword
                                            :middleware [wrap-add-keyword
                                                         jqh/ring-wrap-add-media-resource
                                                         jqh/ring-wrap-authorization-edit-metadata]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:collection_id s/Uuid
                                                                :meta_key_id s/Str
                                                                :keyword_id s/Uuid}}
                                            :responses {200 {:description "Returns the deleted meta-data."
                                                             :body s/Any}}})

(def meta_key_id.role.role_id.person_id {:summary "Delete meta-data role for media-entry."
                                         :handler handle_delete-meta-data-role
                                         :middleware [wrap-add-role
                                                      wrap-add-person
                                                      jqh/ring-wrap-add-media-resource
                                                      jqh/ring-wrap-authorization-edit-metadata]
                                         :coercion reitit.coercion.schema/coercion
                                         :parameters {:path {:media_entry_id s/Uuid
                                                             :meta_key_id s/Str
                                                             :role_id s/Uuid
                                                             :person_id s/Uuid}}
                                         :responses {200 {:description "Returns the deleted meta-data."
                                                          :body s/Any}}})

(def media_entry.meta_key_id.people.person_id {:summary "Delete meta-data people for media-entry"
                                               :handler handle_delete-meta-data-people
                                               :middleware [wrap-add-person
                                                            jqh/ring-wrap-add-media-resource
                                                            jqh/ring-wrap-authorization-edit-metadata]
                                               :coercion reitit.coercion.schema/coercion
                                               :parameters {:path {:media_entry_id s/Uuid
                                                                   :meta_key_id s/Str
                                                                   :person_id s/Uuid}}
                                               :responses {200 {:description "Returns the deleted meta-data."
                                                                :body s/Any}}})

(def media_entry_id.meta-datum.meta_key_id {:summary "Delete meta-data for media-entry and meta-key"
                                            :handler handle-delete-meta-data
                                            :middleware [jqh/ring-wrap-add-media-resource
                                                         jqh/ring-wrap-authorization-view
                                                         wrap-me-add-meta-data]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid
                                                                :meta_key_id s/Str}}
                                            :responses {200 {:description "Returns the deleted meta-data."
                                                             :body s/Any}}})

(def collection.meta_key_id.people.person_id {:summary "Delete meta-data people for collection."
                                              :handler handle_delete-meta-data-people
                                              :middleware [wrap-add-person
                                                           jqh/ring-wrap-add-media-resource
                                                           jqh/ring-wrap-authorization-edit-metadata]
                                              :coercion reitit.coercion.schema/coercion
                                              :parameters {:path {:collection_id s/Uuid
                                                                  :meta_key_id s/Str
                                                                  :person_id s/Uuid}}
                                              :responses {200 {:description "Returns the deleted meta-data."
                                                               :body s/Any}}})

(def meta_key_id.keyword.keyword_id {:summary "Delete meta-data keyword for collection."
                                     :handler handle_delete-meta-data-keyword
                                     :middleware [wrap-add-keyword
                                                  jqh/ring-wrap-add-media-resource
                                                  jqh/ring-wrap-authorization-edit-metadata]
                                     :coercion reitit.coercion.schema/coercion
                                     :parameters {:path {:collection_id s/Uuid
                                                         :meta_key_id s/Str
                                                         :keyword_id s/Uuid}}
                                     :responses {200 {:description "Returns the deleted meta-data."
                                                      :body s/Any}}})


(def meta_key_id.role.role_id.person_id {:summary "Delete meta-data role for media-entry."
                                         :handler handle_delete-meta-data-role
                                         :middleware [wrap-add-role
                                                      wrap-add-person
                                                      jqh/ring-wrap-add-media-resource
                                                      jqh/ring-wrap-authorization-edit-metadata]
                                         :coercion reitit.coercion.schema/coercion
                                         :parameters {:path {:media_entry_id s/Uuid
                                                             :meta_key_id s/Str
                                                             :role_id s/Uuid
                                                             :person_id s/Uuid}}
                                         :responses {200 {:description "Returns the deleted meta-data."
                                                          :body s/Any}}})

(def meta_key_id.people.person_id {:summary "Delete meta-data people for media-entry"
                                   :handler handle_delete-meta-data-people
                                   :middleware [wrap-add-person
                                                jqh/ring-wrap-add-media-resource
                                                jqh/ring-wrap-authorization-edit-metadata]
                                   :coercion reitit.coercion.schema/coercion
                                   :parameters {:path {:media_entry_id s/Uuid
                                                       :meta_key_id s/Str
                                                       :person_id s/Uuid}}
                                   :responses {200 {:description "Returns the deleted meta-data."
                                                    :body s/Any}}})

(def meta_key_id.keyword.keyword_id2 {:summary "Delete meta-data keyword for media-entry."
                                      :handler handle_delete-meta-data-keyword
                                      :middleware [wrap-add-keyword
                                                   jqh/ring-wrap-add-media-resource
                                                   jqh/ring-wrap-authorization-edit-metadata]
                                      :coercion reitit.coercion.schema/coercion
                                      :parameters {:path {:media_entry_id s/Uuid
                                                          :meta_key_id s/Str
                                                          :keyword_id s/Uuid}}
                                      :responses {200 {:description "Returns the deleted meta-data."
                                                       ;:body s/Any


                                                       ;{
                                                       ; "meta_data": {
                                                       ;               "created_by_id": "98954f14-0f95-4de6-b7d5-0113643cb2b3",
                                                       ;               "media_entry_id": "a0040f48-020e-47bd-ae10-df7b28c8ff9c",
                                                       ;               "collection_id": null,
                                                       ;               "type": "MetaDatum::Keywords",
                                                       ;               "meta_key_id": "zhdk_bereich:project_type",
                                                       ;               "string": null,
                                                       ;               "id": "8d626684-76eb-4953-ba67-44ace3d87292",
                                                       ;               "meta_data_updated_at": "2025-01-17T17:25:08.918321Z",
                                                       ;               "json": null,
                                                       ;               "other_media_entry_id": null
                                                       ;               },
                                                       ; "md_keywords": [
                                                       ;                 {
                                                       ;                  "id": "a97cc120-6899-4e61-ade6-ce3df67dd80b",
                                                       ;                  "created_by_id": "98954f14-0f95-4de6-b7d5-0113643cb2b3",
                                                       ;                  "meta_datum_id": "8d626684-76eb-4953-ba67-44ace3d87292",
                                                       ;                  "keyword_id": "1ecab382-d875-45c2-aeef-4420b702ec69",
                                                       ;                  "created_at": "2019-06-28T13:24:59.889018Z",
                                                       ;                  "updated_at": "2019-06-28T13:24:59.889018Z",
                                                       ;                  "meta_data_updated_at": "2019-06-28T13:24:59.889018Z",
                                                       ;                  "position": 0
                                                       ;                  }
                                                       ;                 ]
                                                       ; }

                                                       :body {:meta_data s/Any
                                                              :md_keywords s/Any}


                                                       }}})

(def media_entry_id.meta-datum.meta_key_id {:summary "Delete meta-data for media-entry and meta-key"
                                            :handler handle-delete-meta-data
                                            :middleware [jqh/ring-wrap-add-media-resource
                                                         jqh/ring-wrap-authorization-view
                                                         wrap-me-add-meta-data]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid
                                                                :meta_key_id s/Str}}
                                            :responses {200 {:description "Returns the deleted meta-data."
                                                             :body s/Any}}})

(def meta_key_id.keyword.keyword_id {:summary "Delete meta-data keyword for collection."
                                     :handler handle_delete-meta-data-keyword
                                     :middleware [wrap-add-keyword
                                                  jqh/ring-wrap-add-media-resource
                                                  jqh/ring-wrap-authorization-edit-metadata]
                                     :coercion reitit.coercion.schema/coercion
                                     :parameters {:path {:collection_id s/Uuid
                                                         :meta_key_id s/Str
                                                         :keyword_id s/Uuid}}
                                     :responses {200 {:description "Returns the deleted meta-data."
                                                      :body s/Any}}})

(def collection_id.meta-datum.meta_key_id {:summary "Delete meta-data for collection and meta-key A3"
                                           :description "- 124e558f-9c89-4256-8c59-6731b4cb0a49
   - media_content:test"
                                           :handler handle-delete-meta-data
                                           :middleware [jqh/ring-wrap-add-media-resource
                                                        jqh/ring-wrap-authorization-view
                                                        wrap-col-add-meta-data]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:collection_id s/Uuid
                                                               :meta_key_id s/Str}}
                                           :responses {200 {:description "Returns the deleted meta-data."
                                                            ;:body s/Any

                                                            :body {
                                                                           :created_by_id s/Uuid
                                                                           :media_entry_id (s/maybe s/Uuid)
                                                                           :collection_id s/Uuid
                                                                           :type s/Str
                                                                           :meta_key_id s/Str
                                                                           :string s/Str
                                                                           :id s/Uuid
                                                                           ;:meta_data_updated_at (s/maybe s/Inst)
                                                                           :meta_data_updated_at (s/maybe s/Any)
                                                                           ;:meta_data_updated_at s/Inst
                                                                           ;:meta_data_updated_at s/Str

                                                                           :json (s/maybe s/Any)
                                                                           :other_media_entry_id (s/maybe s/Uuid)

                                                                           }


                                                            }

                                                       406 {:description "Returns the cause of error."
                                                            :body {:message s/Str}}

                                                       500 {:description "Returns the cause of error."
                                                            :body {:message s/Str}}

                                                       }})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
