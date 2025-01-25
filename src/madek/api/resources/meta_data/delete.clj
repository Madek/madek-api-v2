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
                          (sql/returning :*)
                          sql-format)
            del-result (jdbc/execute! tx sql-query)

            p (println ">o> abc.del-result" del-result)]
        (sd/logwrite req (str "\nhandle_delete-meta-data-people:"
                              "\nmr-id: " (:id mr)
                              " meta-key: " meta-key-id
                              " person-id: " person-id
                              " result: " del-result))

        ;(if (= 1 (:next.jdbc/update-count del-result))
        (if (= 1 (count del-result))
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
        ;del-clause (dbh/sql-update-clause
        ;            "meta_datum_id" md-id
        ;            "role_id" role-id
        ;            "person_id" person-id)

        ;p (println ">o> abc.del-clause" del-clause)

        p (println ">o> abc1" md-id (type md-id))
        p (println ">o> abc2" role-id (type role-id))
        p (println ">o> abc3" person-id (type person-id))

        sql-query (-> (sql/delete-from :meta_data_roles)

                      ;(sql/where del-clause)

                      (dbh/sql-update-clause-new "meta_datum_id" md-id
                                                 "role_id" role-id
                                                 "person_id" person-id)

                      (sql/returning :*)
                      sql-format)

        p (println ">o> abc.sql-query" sql-query)
        del-result (jdbc/execute! tx sql-query)

        p (println ">o> abc.del-result" del-result)]

    (sd/logwrite req (str "handle_delete-meta-data-role:"
                          " mr-id: " (:id mr)
                          " meta-key: " meta-key-id
                          " role-id: " role-id
                          " person-id: " person-id
                          ;" clause: " del-clause
                          " result: " del-result))
    ;(if (< 1 (first del-result))
    (if (= 1 (count del-result))
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
                                                             ;:body s/Any

                                                             :body {:meta_data s/Any
                                                                    :md_keywords s/Any
                                                                    ;"meta_data": {
                                                                    ;              "created_by_id": "10fc1e68-a9cb-4863-b4f0-bf26cb70efdb",
                                                                    ;              "media_entry_id": null,
                                                                    ;              "collection_id": "2e9fa545-2d8b-418d-82bb-368b07841716",
                                                                    ;              "type": "MetaDatum::Keywords",
                                                                    ;              "meta_key_id": "madek_core:keywords",
                                                                    ;              "string": null,
                                                                    ;              "id": "f25f8929-24cd-4776-85ed-fa215b1bca05",
                                                                    ;              "meta_data_updated_at": "2025-01-16T18:24:56.045177Z",
                                                                    ;              "json": null,
                                                                    ;              "other_media_entry_id": null
                                                                    ;              },
                                                                    ;"md_keywords": [
                                                                    ;                {
                                                                    ;                 "id": "178b596e-d0a5-443e-879b-cca5057137b7",
                                                                    ;                 "created_by_id": "10fc1e68-a9cb-4863-b4f0-bf26cb70efdb",
                                                                    ;                 "meta_datum_id": "f25f8929-24cd-4776-85ed-fa215b1bca05",
                                                                    ;                 "keyword_id": "aa1c9ff4-005c-4fbd-b4e2-9b9ec7e8bc31",
                                                                    ;                 "created_at": "2024-12-05T12:14:58.830258Z",
                                                                    ;                 "updated_at": "2024-12-05T12:14:58.830258Z",
                                                                    ;                 "meta_data_updated_at": "2024-12-05T12:14:58.646707Z",
                                                                    ;                 "position": 0
                                                                    ;                 },
                                                                    ;                {
                                                                    ;                 "id": "f4b50f67-f0d1-42d9-9c99-1a7f79f7743f",
                                                                    ;                 "created_by_id": "10fc1e68-a9cb-4863-b4f0-bf26cb70efdb",
                                                                    ;                 "meta_datum_id": "f25f8929-24cd-4776-85ed-fa215b1bca05",
                                                                    ;                 "keyword_id": "b2a92aa5-2dac-4203-8e83-7787b473904b",
                                                                    ;                 "created_at": "2024-12-05T12:14:58.836569Z",
                                                                    ;                 "updated_at": "2024-12-05T12:14:58.836569Z",
                                                                    ;                 "meta_data_updated_at": "2024-12-05T12:14:58.646707Z",
                                                                    ;                 "position": 0
                                                                    ;                 }
                                                                    ;                ]
                                                                    }}}})
;(def meta_key_id.role.role_id.person_id {:summary "Delete meta-data role for media-entry."
;                                         :handler handle_delete-meta-data-role
;                                         :middleware [wrap-add-role
;                                                      wrap-add-person
;                                                      jqh/ring-wrap-add-media-resource
;                                                      jqh/ring-wrap-authorization-edit-metadata]
;                                         :coercion reitit.coercion.schema/coercion
;                                         :parameters {:path {:media_entry_id s/Uuid
;                                                             :meta_key_id s/Str
;                                                             :role_id s/Uuid
;                                                             :person_id s/Uuid}}
;                                         :responses {200 {:description "Returns the deleted meta-data."
;                                                          :body s/Any}}})

(require '[schema.core :as s])

;; Define the schema for `meta_data`
(def MetaDataSchema
  {:created_by_id s/Uuid
   :media_entry_id s/Uuid
   :collection_id (s/maybe s/Uuid)
   :type s/Str
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :id s/Uuid
   :meta_data_updated_at s/Any
   :json (s/maybe s/Str)
   :other_media_entry_id (s/maybe s/Uuid)})

;; Define the schema for each item in `md_people`
(def MdPeopleItemSchema
  {:meta_datum_id s/Uuid
   :person_id s/Uuid
   :created_by_id s/Uuid
   :meta_data_updated_at s/Any
   :id s/Uuid
   :position s/Int})

;; Define the top-level schema
(def ResponseSchema
  {:meta_data MetaDataSchema
   :md_people [MdPeopleItemSchema]}) ;; Note: `md_people` is a list of `MdPeopleItemSchema`

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

;:body s/Any
                                                                :body ResponseSchema

                                                                ;406
                                                                ;{
                                                                ; "message": {
                                                                ;             "message": "Failed to delete meta data people"
                                                                ;             }
                                                                ; }
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
                                                               ;:body s/Any

                                                               :body {:meta_data s/Any
                                                                      :md_people s/Any}

;{
                                                               ; "meta_data": {
                                                               ;               "created_by_id": "10fc1e68-a9cb-4863-b4f0-bf26cb70efdb",
                                                               ;               "media_entry_id": null,
                                                               ;               "collection_id": "03fe1dc5-4f38-44fe-9e29-a294fbc7aeee",
                                                               ;               "type": "MetaDatum::People",
                                                               ;               "meta_key_id": "madek_core:authors",
                                                               ;               "string": null,
                                                               ;               "id": "7f775726-d6b0-424c-9b06-fa0e39586609",
                                                               ;               "meta_data_updated_at": "2017-08-02T12:14:07.425871Z",
                                                               ;               "json": null,
                                                               ;               "other_media_entry_id": null
                                                               ;               },
                                                               ; "md_people": [
                                                               ;               {
                                                               ;                "meta_datum_id": "7f775726-d6b0-424c-9b06-fa0e39586609",
                                                               ;                "person_id": "b6934e58-e6de-42de-939c-ebe0756acb8a",
                                                               ;                "created_by_id": "10fc1e68-a9cb-4863-b4f0-bf26cb70efdb",
                                                               ;                "meta_data_updated_at": "2017-08-02T12:14:07.425871Z",
                                                               ;                "id": "231c6667-7847-46bb-a3f5-4082a549fd3e",
                                                               ;                "position": 0
                                                               ;                },
                                                               ;               {
                                                               ;                "meta_datum_id": "7f775726-d6b0-424c-9b06-fa0e39586609",
                                                               ;                "person_id": "d18ca872-bd76-43fe-9e2b-0b26e19b8be4",
                                                               ;                "created_by_id": "10fc1e68-a9cb-4863-b4f0-bf26cb70efdb",
                                                               ;                "meta_data_updated_at": "2017-08-02T12:14:07.425871Z",
                                                               ;                "id": "69e09740-637d-4fee-bd23-42ff83f28b26",
                                                               ;                "position": 0
                                                               ;                }
                                                               ;               ]
                                                               ; }
                                                               }}})
;(def meta_key_id.keyword.keyword_id {:summary "Delete meta-data keyword for collection."
;                                     :handler handle_delete-meta-data-keyword
;                                     :middleware [wrap-add-keyword
;                                                  jqh/ring-wrap-add-media-resource
;                                                  jqh/ring-wrap-authorization-edit-metadata]
;                                     :coercion reitit.coercion.schema/coercion
;                                     :parameters {:path {:collection_id s/Uuid
;                                                         :meta_key_id s/Str
;                                                         :keyword_id s/Uuid}}
;                                     :responses {200 {:description "Returns the deleted meta-data."
;                                                      :body s/Any}}})

(def MetaDataSchema2
  {:created_by_id s/Uuid
   :media_entry_id s/Uuid
   :collection_id (s/maybe s/Uuid)
   :type s/Str
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :id s/Uuid
   :meta_data_updated_at s/Any
   :json (s/maybe s/Str)
   :other_media_entry_id (s/maybe s/Uuid)})

;; Define the schema for each item in `md_roles`
(def MdRoleItemSchema2
  {:id s/Uuid
   :meta_datum_id s/Uuid
   :person_id s/Uuid
   :role_id (s/maybe s/Uuid)
   :position s/Any})

;; Define the top-level schema
(def ResponseSchema2
  {:meta_data MetaDataSchema2
   :md_roles [MdRoleItemSchema2]}) ;; Note: `md_roles` is a vector of `MdRoleItemSchema`

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

                                                          :body ResponseSchema2}}})

;(def meta_key_id.people.person_id {:summary "Delete meta-data people for media-entry"
;                                   :handler handle_delete-meta-data-people
;                                   :middleware [wrap-add-person
;                                                jqh/ring-wrap-add-media-resource
;                                                jqh/ring-wrap-authorization-edit-metadata]
;                                   :coercion reitit.coercion.schema/coercion
;                                   :parameters {:path {:media_entry_id s/Uuid
;                                                       :meta_key_id s/Str
;                                                       :person_id s/Uuid}}
;                                   :responses {200 {:description "Returns the deleted meta-data."
;                                                    :body s/Any}}})

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
                                                              :md_keywords s/Any}}}})

(def MetaDataSchema
  {:created_by_id s/Uuid
   :media_entry_id s/Uuid
   :collection_id (s/maybe s/Uuid)
   :type s/Str
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :id s/Uuid
   :meta_data_updated_at s/Any
   ;:json (s/maybe s/Str)
   :json (s/maybe s/Any)
   :other_media_entry_id (s/maybe s/Uuid)})

(def media_entry_id.meta-datum.meta_key_id {:summary "Delete meta-data for media-entry and meta-key"
                                            :handler handle-delete-meta-data
                                            :middleware [jqh/ring-wrap-add-media-resource
                                                         jqh/ring-wrap-authorization-view
                                                         wrap-me-add-meta-data]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid
                                                                :meta_key_id s/Str}}
                                            :responses {200 {:description "Returns the deleted meta-data."

                                                             ;:body s/Any
                                                             :body MetaDataSchema}}})

;(def meta_key_id.keyword.keyword_id {:summary "Delete meta-data keyword for collection."
;                                     :handler handle_delete-meta-data-keyword
;                                     :middleware [wrap-add-keyword
;                                                  jqh/ring-wrap-add-media-resource
;                                                  jqh/ring-wrap-authorization-edit-metadata]
;                                     :coercion reitit.coercion.schema/coercion
;                                     :parameters {:path {:collection_id s/Uuid
;                                                         :meta_key_id s/Str
;                                                         :keyword_id s/Uuid}}
;                                     :responses {200 {:description "Returns the deleted meta-data."
;                                                      :body s/Any}}})

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

                                                            :body {:created_by_id s/Uuid
                                                                   :media_entry_id (s/maybe s/Uuid)
                                                                   :collection_id s/Uuid
                                                                   :type s/Str
                                                                   :meta_key_id s/Str
                                                                   :string (s/maybe s/Str)
                                                                   :id s/Uuid
                                                                           ;:meta_data_updated_at (s/maybe s/Inst)
                                                                   :meta_data_updated_at (s/maybe s/Any)
                                                                           ;:meta_data_updated_at s/Inst
                                                                           ;:meta_data_updated_at s/Str

                                                                   :json (s/maybe s/Any)
                                                                   :other_media_entry_id (s/maybe s/Uuid)}}

                                                       406 {:description "Returns the cause of error."
                                                            :body {:message s/Str}}

                                                       500 {:description "Returns the cause of error."
                                                            :body {:message s/Str}}}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
