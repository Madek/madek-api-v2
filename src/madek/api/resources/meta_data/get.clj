(ns madek.api.resources.meta_data.get
  (:require [cheshire.core]
            [cheshire.core :as cheshire]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.db.core :refer [builder-fn-options-default]]
            [madek.api.db.dynamic_schema.common :refer [get-schema]]
            [madek.api.resources.meta-data.index :as meta-data.index]
            [madek.api.resources.meta-data.meta-datum :as meta-datum]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.helper :refer [convert-map-if-exist to-uuid]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [schema.core :as s]
            [taoensso.timbre :refer [error info]]))


;; ###meta-data-routes ##########################################################

(defn- col-key-for-mr-type [mr]
  (let [mr-type (-> mr :type)]
    (if (= mr-type "Collection")
      :collection_id
      :media_entry_id)))

(defn- assoc-media-resource-typed-id [mr ins-data]
  (assoc ins-data
         (col-key-for-mr-type mr)
         (-> mr :id)))



(defn fabric-meta-data
  [mr meta-key-id md-type user-id]
  (let [data {:meta_key_id meta-key-id
              :type md-type
              :created_by_id (to-uuid user-id)}]
    (assoc-media-resource-typed-id mr data)))

(defn db-get-meta-data
  ([mr mk-id md-type db]
   (let [mr-id (str (-> mr :id))
         mr-key (col-key-for-mr-type mr)
         db-query (-> (sql/select :*)
                      (sql/from :meta_data)
                      (sql/where [:and
                                  [:= :meta_key_id mk-id]
                                  [:= mr-key (to-uuid mr-id mr-key)]])
                      sql-format)
         db-result (jdbc/execute-one! db db-query builder-fn-options-default)
         db-type (:type db-result)]

     (if (or (= nil md-type) (= md-type db-type))
       db-result
       nil))))

(defn- db-create-meta-data
  ([db meta-data]
   (info "db-create-meta-data: " meta-data)
   (let [sql-query (-> (sql/insert-into :meta_data)
                       (sql/values [(convert-map-if-exist meta-data)])
                       (sql/returning :*)
                       sql-format)
         result (jdbc/execute-one! db sql-query builder-fn-options-default)]
     (if result
       result
       nil)))

  ([db mr meta-key-id md-type user-id]
   ;(info "db-create-meta-data: " "MK-ID: " meta-key-id "Type:" md-type "User: " user-id)
   (db-create-meta-data db (fabric-meta-data mr meta-key-id md-type user-id)))

  ([db mr meta-key-id md-type user-id meta-data]
   ;(info "db-create-meta-data: " "MK-ID: " meta-key-id "Type:" md-type "User: " user-id "MD: " meta-data)
   (let [md (merge (fabric-meta-data mr meta-key-id md-type user-id) meta-data)]
     ;(info "db-create-meta-data: "
     ;              "MK-ID: " meta-key-id
     ;              "Type:" md-type
     ;              "User: " user-id
     ;              "MD: " meta-data
     ;              "MD-new: " md)
     (db-create-meta-data db md))))






(defn db-get-meta-data-keywords
  [md-id tx]
  (sd/query-eq-find-all :meta_data_keywords :meta_datum_id md-id tx))
#_(let [query (-> (sd/build-query-base :meta_data_keywords :*)
                  (sql/merge-where [:= :meta_datum_id md-id])
                  (sql/merge-join :keywords [:= :keywords.id :meta_data_keywords.keyword_id])
                  (sql/order-by [:keywords.term :asc])
                  sql-format)]
    (info "db-get-meta-data-keywords:\n" query)
    (let [result (jdbc/query tx query)]
      (info "db-get-meta-data-keywords:\n" result)))

; TODO only some results
(defn handle_get-meta-data-keywords
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        tx (:tx req)]

    (if-let [md (db-get-meta-data mr meta-key-id MD_TYPE_KEYWORDS tx)]
      (let [md-id (-> md :id)
            mdr (db-get-meta-data-keywords md-id tx)
            mdr-ids (map (-> :keyword_id) mdr)
            keywords (map #(sd/query-eq-find-one :keywords :id % tx) mdr-ids)
            result {:meta_data md
                    MD_KEY_KW_IDS mdr-ids
                    MD_KEY_KW_DATA mdr
                    MD_KEY_KWS keywords}]
        (sd/response_ok result))
      (sd/response_failed "Not found" 404))))




(def MD_TYPE_PEOPLE "MetaDatum::People")
(def MD_KEY_PEOPLE :people)
(def MD_KEY_PEOPLE_DATA :md_people)
(def MD_KEY_PEOPLE_IDS :people_ids)

(def MD_TYPE_ROLES "MetaDatum::Roles")
(def MD_KEY_ROLES :roles)
(def MD_KEY_ROLES_DATA :md_roles)
(def MD_KEY_ROLES_IDS :roles_ids)


(def MD_TYPE_KEYWORDS "MetaDatum::Keywords")
(def MD_KEY_KWS :keywords)
(def MD_KEY_KW_DATA :md_keywords)
(def MD_KEY_KW_IDS :keywords_ids)


(defn db-get-meta-data-people
  [md-id tx]
  (sd/query-eq-find-all :meta_data_people :meta_datum_id md-id tx))

; TODO only some results
(defn handle_get-meta-data-people
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        tx (:tx req)]

    (if-let [md (db-get-meta-data mr meta-key-id MD_TYPE_PEOPLE tx)]
      (let [md-id (-> md :id)
            mdr (db-get-meta-data-people md-id tx)
            mdr-ids (map (-> :person_id) mdr)
            people (map #(sd/query-eq-find-one :people :id % tx) mdr-ids)
            result {:meta_data md
                    MD_KEY_PEOPLE_IDS mdr-ids
                    MD_KEY_PEOPLE_DATA mdr
                    MD_KEY_PEOPLE people}]
        (sd/response_ok result))
      (sd/response_failed "Not found" 404))))








(defn db-get-meta-data-roles [md-id tx]
  (sd/query-eq-find-all :meta_data_roles :meta_datum_id md-id tx))

(defn handle_get-meta-data-roles
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        tx (:tx req)]

    (if-let [md (db-get-meta-data mr meta-key-id MD_TYPE_ROLES tx)]
      (let [md-id (-> md :id)
            mdr (db-get-meta-data-roles md-id tx)
            mdr-rids (map (-> :role_id) mdr)
            mdr-pids (map (-> :person_id) mdr)
            roles (map #(sd/query-eq-find-one :roles :id % tx) mdr-rids)
            people (map #(sd/query-eq-find-one :people :id % tx) mdr-pids)
            result {:meta_data md

                    MD_KEY_ROLES_IDS mdr-rids
                    MD_KEY_PEOPLE_IDS mdr-pids
                    MD_KEY_ROLES_DATA mdr
                    MD_KEY_ROLES roles
                    MD_KEY_PEOPLE people}]
        (sd/response_ok result))
      (sd/response_failed "Not found" 404))))



(defn- add-meta-data-extra [result tx]
  (let [md-id (:id result)
        md-type (:type result)

        md-type-kw (case md-type
                     "MetaDatum::Keywords" MD_KEY_KW_DATA
                     "MetaDatum::People" MD_KEY_PEOPLE_DATA
                     "MetaDatum::Roles" MD_KEY_ROLES_DATA
                     "defaultmetadata")

        md-type-kw-data (case md-type
                          "MetaDatum::Keywords" MD_KEY_KWS
                          "MetaDatum::People" MD_KEY_PEOPLE
                          "MetaDatum::Roles" MD_KEY_ROLES
                          "defaultdata")
        ;(apply str md-type-kw "_data")

        mde (case md-type
              "MetaDatum::Keywords" (db-get-meta-data-keywords md-id tx)
              "MetaDatum::People" (db-get-meta-data-people md-id tx)
              "MetaDatum::Roles" (db-get-meta-data-roles md-id tx)
              "default")

        mde-data (case md-type
                   "MetaDatum::Keywords" (->>
                                           mde
                                           (map (-> :keyword_id))
                                           (map #(sd/query-eq-find-one :keywords :id % tx)))
                   "MetaDatum::People" (->>
                                         mde
                                         (map (-> :person_id))
                                         (map #(sd/query-eq-find-one :people :id % tx)))
                   "MetaDatum::Roles" (->>
                                        mde
                                        (map (-> :role_id))
                                        (map #(sd/query-eq-find-one :roles :id % tx)))
                   "default")
        mde-result {:meta-data result
                    (keyword md-type-kw) mde
                    (keyword md-type-kw-data) mde-data}]
    ;(info "handle_get-meta-key-meta-data"
    ;              "\nmedia id " md-id
    ;              "meta-data " mde-result)
    mde-result))

(defn handle_get-meta-key-meta-data
  [req]
  (let [mr (-> req :media-resource)
        tx (:tx req)
        meta-key-id (-> req :parameters :path :meta_key_id)]

    (if-let [result (db-get-meta-data mr meta-key-id nil tx)]
      (let [extra-result (add-meta-data-extra result tx)]
        ;(info "handle_get-meta-key-meta-data"
        ;              "\nmeta-key-id " meta-key-id
        ;              "meta-data " extra-result)
        (sd/response_ok extra-result))

      (sd/response_failed "No such meta data" 404))))

(defn handle_get-mr-meta-data-with-related [request]
  (let [tx (:tx request)
        media-resource (:media-resource request)
        meta-data (when media-resource (meta-data.index/get-meta-data request media-resource tx))]
    (when meta-data
      (->> meta-data
        (map #(add-meta-data-extra % tx))
        sd/response_ok))))

(defn wrap-add-keyword [handler]
  (fn [request] (sd/req-find-data
                  request handler
                  :keyword_id
                  :keywords :id
                  :keyword
                  true)))


(defn wrap-add-meta-key [handler]
  (fn [request] (sd/req-find-data
                  request handler
                  :meta_key_id
                  :meta-keys :id
                  :meta-key
                  true)))

; TODO meta-key makes error media_content:remark
(defn wrap-check-vocab [handler]
  (fn [req]
    (let [meta-key (req :meta-key)
          user-id (-> req :authenticated-entity :id str)
          tx (:tx req)
          user-vocab-query (meta-data.index/md-vocab-where-clause user-id tx)
          vocab-clause (-> (sql/select :*)
                           (sql/from :vocabularies)
                           (sql/where [:= :id (:vocabulary_id meta-key)])
                           (sql/where user-vocab-query)
                           (sql-format))
          result (jdbc/execute! tx vocab-clause)]

      ;(info "wrap-check-vocab"
      ;              "\nmeta-key-id" (:id meta-key)
      ;              "\nvocab-clause" vocab-clause
      ;              ;"\nresult" result
      ;              )

      (if (= 0 (count result))
        (sd/response_not_found "Invalid meta-key, or no vocabulary access.")
        (handler req)))))


;; Routes ####################################################################

;; ###meta-data-routes ##########################################################

(def meta_datum_id {:handler meta-datum/get-meta-datum
                    :middleware [sd/ring-wrap-add-meta-datum-with-media-resource
                                 sd/ring-wrap-authorization-view]
                    :summary "Get meta-data for id"
                    :description "Get meta-data for id. TODO: should return 404, if no such meta-data role exists."
                    :coercion reitit.coercion.schema/coercion
                    :parameters {:path {:meta_datum_id s/Uuid}}
                    :responses {200 {:body (get-schema :meta-data-schema.schema_export_meta-datum)}
                                401 {:body s/Any}
                                403 {:body s/Any}
                                500 {:body s/Any}}})


(def meta_datum_id.data-stream {:handler meta-datum/get-meta-datum-data-stream
                                ; TODO json meta-data: fix response conversion error
                                :middleware [sd/ring-wrap-add-meta-datum-with-media-resource
                                             sd/ring-wrap-authorization-view]
                                :summary "Get meta-data data-stream."
                                :description "Get meta-data data-stream."
                                :coercion reitit.coercion.schema/coercion
                                :parameters {:path {:meta_datum_id s/Uuid}}})


(def media-entry.media_entry_id.meta-data {:summary "Get meta-data for media-entry."
                                           :handler meta-data.index/get-index
                                           ; TODO 401s test fails
                                           :middleware [sd/ring-wrap-add-media-resource
                                                        sd/ring-wrap-authorization-view]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:media_entry_id s/Uuid}
                                                        :query {(s/optional-key :updated_after) s/Inst
                                                                (s/optional-key :meta_keys) s/Str}}
                                           :responses {200 {:body s/Any}}})



(def meta-data-role.meta_data_role_id {:summary " Get meta-data role for id "
                                       :handler meta-datum/handle_get-meta-datum-role
                                       :description " Get meta-datum-role for id. returns 404, if no such meta-data role exists. "
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:meta_data_role_id s/Str}}
                                       :responses {200 {:body (get-schema :meta-data-role-schema.schema_export_mdrole)}
                                                   404 {:body s/Any}}})


(def collection_id.meta-data {:summary "Get meta-data for collection."
                              :handler meta-data.index/get-index
                              :middleware [sd/ring-wrap-add-media-resource
                                           sd/ring-wrap-authorization-view]
                              ; TODO 401s test fails
                              :coercion reitit.coercion.schema/coercion
                              :parameters {:path {:collection_id s/Uuid}
                                           :query {(s/optional-key :updated_after) s/Inst
                                                   (s/optional-key :meta_keys) s/Str}}
                              :responses {200 {:body s/Any}}})




(def collection_id.meta-data-related {:summary "Get meta-data for collection."
                                      :handler handle_get-mr-meta-data-with-related
                                      :middleware [sd/ring-wrap-add-media-resource
                                                   sd/ring-wrap-authorization-view]
                                      ; TODO 401s test fails
                                      :coercion reitit.coercion.schema/coercion
                                      :parameters {:path {:collection_id s/Uuid}
                                                   :query {(s/optional-key :updated_after) s/Inst
                                                           (s/optional-key :meta_keys) s/Str}}
                                      :responses {200 {:body s/Any}}})

(def collection_id.meta-datum.meta_key_id {:summary "Get meta-data for collection and meta-key."
                                           :handler handle_get-meta-key-meta-data

                                           :middleware [wrap-add-meta-key
                                                        wrap-check-vocab
                                                        sd/ring-wrap-add-media-resource
                                                        sd/ring-wrap-authorization-view]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:collection_id s/Uuid
                                                               :meta_key_id s/Str}}
                                           :responses {200 {:body s/Any}}})


(def collection.meta_key_id.keyword {:summary "Get meta-data keywords for collection meta-key"
                          :handler handle_get-meta-data-keywords
                          :middleware [;wrap-me-add-meta-data
                                       sd/ring-wrap-add-media-resource
                                       sd/ring-wrap-authorization-view]
                          :coercion reitit.coercion.schema/coercion
                          :parameters {:path {:collection_id s/Uuid
                                              :meta_key_id s/Str}}
                          :responses {200 {:body s/Any}}})


(def meta_key_id.people2 {:summary "Get meta-data people for collection meta-key."
                         :handler handle_get-meta-data-people
                         :middleware [;wrap-me-add-meta-data
                                      sd/ring-wrap-add-media-resource
                                      sd/ring-wrap-authorization-edit-metadata]
                         :coercion reitit.coercion.schema/coercion
                         :parameters {:path {:collection_id s/Uuid
                                             :meta_key_id s/Str}}
                         :responses {200 {:body s/Any}}})


(def media_entry_id.meta-data-related {:summary "Get meta-data for media-entry."
                                       :handler handle_get-mr-meta-data-with-related
                                       :middleware [sd/ring-wrap-add-media-resource
                                                    sd/ring-wrap-authorization-view]
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:media_entry_id s/Uuid}
                                                    :query {(s/optional-key :updated_after) s/Inst
                                                            (s/optional-key :meta_keys) s/Str}}
                                       :responses {200 {:body s/Any}}})



(def media_entry_id.meta-datum.meta_key_id {:summary "Get meta-data for media-entry and meta-key."
                                            :handler handle_get-meta-key-meta-data
                                            :middleware [wrap-add-meta-key
                                                         ;wrap-check-vocab
                                                         sd/ring-wrap-add-media-resource
                                                         sd/ring-wrap-authorization-view]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid
                                                                :meta_key_id s/Str}}
                                            :responses {200 {:body s/Any}}})


(def media_entry.meta_key_id.keyword {:summary "Get meta-data keywords for media-entries meta-key"
                          :handler handle_get-meta-data-keywords
                          :middleware [;wrap-me-add-meta-data
                                       sd/ring-wrap-add-media-resource
                                       sd/ring-wrap-authorization-view]
                          :coercion reitit.coercion.schema/coercion
                          :parameters {:path {:media_entry_id s/Uuid
                                              :meta_key_id s/Str}}
                          :responses {200 {:body s/Any}}})

;; collection
(def meta_key_id.people {:summary "Get meta-data people for media-entries meta-key."
                         :handler handle_get-meta-data-people
                         :middleware [;wrap-me-add-meta-data
                                      sd/ring-wrap-add-media-resource
                                      sd/ring-wrap-authorization-view]
                         :coercion reitit.coercion.schema/coercion
                         :parameters {:path {:media_entry_id s/Uuid
                                             :meta_key_id s/Str}}
                         :responses {200 {:body s/Any}}})

(def meta_key_id.role {:summary "Get meta-data role for media-entry."
                       :handler handle_get-meta-data-roles
                       :middleware [sd/ring-wrap-add-media-resource
                                    sd/ring-wrap-authorization-view]
                       :coercion reitit.coercion.schema/coercion
                       :parameters {:path {:media_entry_id s/Uuid
                                           :meta_key_id s/Str}}
                       :responses {200 {:body s/Any}}})




;### Debug ####################################################################
;(debug/debug-ns *ns*)
