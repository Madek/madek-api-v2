(ns madek.api.resources.meta_data.delete
  (:require [cheshire.core]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.db.core :refer [builder-fn-options-default]]


            [madek.api.resources.meta_data.common :refer :all]

            ;[madek.api.resources.meta_data.common :refer [db-get-meta-data-keywords wrap-add-role
            ;                                              wrap-col-add-meta-data wrap-add-person db-get-meta-data-roles wrap-me-add-meta-data]]


            [madek.api.resources.shared :as sd]
            [madek.api.utils.helper :refer [convert-map-if-exist to-uuid]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [schema.core :as s]
            [taoensso.timbre :refer [info]]))

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
;(defn- sql-cls-upd-meta-data [stmt mr mk-id]
;  (let [colomn (col-key-for-mr-type mr)
;        md-sql (-> stmt
;                   (sql/where [:and
;                               [:= :meta_key_id mk-id]
;                               [:= colomn (to-uuid (-> mr :id) colomn)]]))]
;    md-sql))
;
;(defn fabric-meta-data
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

;(def MD_TYPE_KEYWORDS "MetaDatum::Keywords")
;(def MD_KEY_KWS :keywords)
;(def MD_KEY_KW_DATA :md_keywords)
;(def MD_KEY_KW_IDS :keywords_ids)
;(def MD_TYPE_PEOPLE "MetaDatum::People")
;(def MD_KEY_PEOPLE :people)
;(def MD_KEY_PEOPLE_DATA :md_people)
;(def MD_KEY_PEOPLE_IDS :people_ids)
;(def MD_TYPE_ROLES "MetaDatum::Roles")
;(def MD_KEY_ROLES :roles)
;(def MD_KEY_ROLES_DATA :md_roles)
;(def MD_KEY_ROLES_IDS :roles_ids)


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



;(defn db-get-meta-data-people
;  [md-id tx]
;  (sd/query-eq-find-all :meta_data_people :meta_datum_id md-id tx))


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
        del-clause (sd/sql-update-clause
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
;
;(defn wrap-col-add-meta-data [handler]
;  (fn [request] (sd/req-find-data2
;                  request handler
;                  :collection_id
;                  :meta_key_id
;                  :meta_data
;                  :collection_id
;                  :meta_key_id
;                  :meta-data
;                  false)))




; ### Handler ##################################################################



(def delete.meta_key_id.keyword.keyword_id {:summary "Delete meta-data keyword for collection."
                                            :handler handle_delete-meta-data-keyword
                                            :middleware [wrap-add-keyword
                                                         sd/ring-wrap-add-media-resource
                                                         sd/ring-wrap-authorization-edit-metadata]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:collection_id s/Uuid
                                                                :meta_key_id s/Str
                                                                :keyword_id s/Uuid}}
                                            :responses {200 {:body s/Any}}})

(def meta_key_id.role.role_id.person_id {:summary "Delete meta-data role for media-entry."
                                         :handler handle_delete-meta-data-role
                                         :middleware [wrap-add-role
                                                      wrap-add-person
                                                      sd/ring-wrap-add-media-resource
                                                      sd/ring-wrap-authorization-edit-metadata]
                                         :coercion reitit.coercion.schema/coercion
                                         :parameters {:path {:media_entry_id s/Uuid
                                                             :meta_key_id s/Str
                                                             :role_id s/Uuid
                                                             :person_id s/Uuid}}
                                         :responses {200 {:body s/Any}}})


(def media_entry.meta_key_id.people.person_id {:summary "Delete meta-data people for media-entry"
                                               :handler handle_delete-meta-data-people
                                               :middleware [wrap-add-person
                                                            sd/ring-wrap-add-media-resource
                                                            sd/ring-wrap-authorization-edit-metadata]
                                               :coercion reitit.coercion.schema/coercion
                                               :parameters {:path {:media_entry_id s/Uuid
                                                                   :meta_key_id s/Str
                                                                   :person_id s/Uuid}}
                                               :responses {200 {:body s/Any}}})





(def media_entry_id.meta-datum.meta_key_id {:summary "Delete meta-data for media-entry and meta-key"
                                            :handler handle-delete-meta-data
                                            :middleware [sd/ring-wrap-add-media-resource
                                                         sd/ring-wrap-authorization-view
                                                         wrap-me-add-meta-data]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid
                                                                :meta_key_id s/Str}}
                                            :responses {200 {:body s/Any}}})



(def collection.meta_key_id.people.person_id {:summary "Delete meta-data people for collection."
                                              :handler handle_delete-meta-data-people
                                              :middleware [wrap-add-person
                                                           sd/ring-wrap-add-media-resource
                                                           sd/ring-wrap-authorization-edit-metadata]
                                              :coercion reitit.coercion.schema/coercion
                                              :parameters {:path {:collection_id s/Uuid
                                                                  :meta_key_id s/Str
                                                                  :person_id s/Uuid}}
                                              :responses {200 {:body s/Any}}})


(def meta_key_id.keyword.keyword_id {:summary "Delete meta-data keyword for collection."
                                     :handler handle_delete-meta-data-keyword
                                     :middleware [wrap-add-keyword
                                                  sd/ring-wrap-add-media-resource
                                                  sd/ring-wrap-authorization-edit-metadata]
                                     :coercion reitit.coercion.schema/coercion
                                     :parameters {:path {:collection_id s/Uuid
                                                         :meta_key_id s/Str
                                                         :keyword_id s/Uuid}}
                                     :responses {200 {:body s/Any}}})


(def collection_id.meta-datum.meta_key_id {:summary "Delete meta-data for collection and meta-key"
                                           :handler handle-delete-meta-data
                                           :middleware [sd/ring-wrap-add-media-resource
                                                        sd/ring-wrap-authorization-view
                                                        wrap-col-add-meta-data]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:collection_id s/Uuid
                                                               :meta_key_id s/Str}}
                                           :responses {200 {:body s/Any}}})

(def meta_key_id.role.role_id.person_id {:summary "Delete meta-data role for media-entry."
                                         :handler handle_delete-meta-data-role
                                         :middleware [wrap-add-role
                                                      wrap-add-person
                                                      sd/ring-wrap-add-media-resource
                                                      sd/ring-wrap-authorization-edit-metadata]
                                         :coercion reitit.coercion.schema/coercion
                                         :parameters {:path {:media_entry_id s/Uuid
                                                             :meta_key_id s/Str
                                                             :role_id s/Uuid
                                                             :person_id s/Uuid}}
                                         :responses {200 {:body s/Any}}})


(def meta_key_id.people.person_id {:summary "Delete meta-data people for media-entry"
                                   :handler handle_delete-meta-data-people
                                   :middleware [wrap-add-person
                                                sd/ring-wrap-add-media-resource
                                                sd/ring-wrap-authorization-edit-metadata]
                                   :coercion reitit.coercion.schema/coercion
                                   :parameters {:path {:media_entry_id s/Uuid
                                                       :meta_key_id s/Str
                                                       :person_id s/Uuid}}
                                   :responses {200 {:body s/Any}}})


(def meta_key_id.keyword.keyword_id2 {:summary "Delete meta-data keyword for media-entry."
                                      :handler handle_delete-meta-data-keyword
                                      :middleware [wrap-add-keyword
                                                   sd/ring-wrap-add-media-resource
                                                   sd/ring-wrap-authorization-edit-metadata]
                                      :coercion reitit.coercion.schema/coercion
                                      :parameters {:path {:media_entry_id s/Uuid
                                                          :meta_key_id s/Str
                                                          :keyword_id s/Uuid}}
                                      :responses {200 {:body s/Any}}})


(def media_entry_id.meta-datum.meta_key_id {:summary "Delete meta-data for media-entry and meta-key"
                                            :handler handle-delete-meta-data
                                            :middleware [sd/ring-wrap-add-media-resource
                                                         sd/ring-wrap-authorization-view
                                                         wrap-me-add-meta-data]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid
                                                                :meta_key_id s/Str}}
                                            :responses {200 {:body s/Any}}})


(def meta_key_id.keyword.keyword_id {:summary "Delete meta-data keyword for collection."
                                     :handler handle_delete-meta-data-keyword
                                     :middleware [wrap-add-keyword
                                                  sd/ring-wrap-add-media-resource
                                                  sd/ring-wrap-authorization-edit-metadata]
                                     :coercion reitit.coercion.schema/coercion
                                     :parameters {:path {:collection_id s/Uuid
                                                         :meta_key_id s/Str
                                                         :keyword_id s/Uuid}}
                                     :responses {200 {:body s/Any}}})


(def collection_id.meta-datum.meta_key_id {:summary "Delete meta-data for collection and meta-key"
                                           :handler handle-delete-meta-data
                                           :middleware [sd/ring-wrap-add-media-resource
                                                        sd/ring-wrap-authorization-view
                                                        wrap-col-add-meta-data]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:collection_id s/Uuid
                                                               :meta_key_id s/Str}}
                                           :responses {200 {:body s/Any}}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
