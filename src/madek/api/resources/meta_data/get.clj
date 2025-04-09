(ns madek.api.resources.meta_data.get
  (:require [cheshire.core]
            [clojure.java.io :as io]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.resources.meta-data.index :as meta-data.index]
            [madek.api.resources.meta-data.meta-datum :as meta-datum]
            [madek.api.resources.meta_data.common :refer :all]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.resources.shared.json_query_param_helper :as jqh]
            [madek.api.utils.helper :refer [mslurp]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [schema.core :as s]))

;; ###meta-data-routes ##########################################################

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
            keywords (map #(dbh/query-eq-find-one :keywords :id % tx) mdr-ids)
            result {:meta_data md
                    MD_KEY_KW_IDS mdr-ids
                    MD_KEY_KW_DATA mdr
                    MD_KEY_KWS keywords}]
        (sd/response_ok result))
      (sd/response_failed "Not found" 404))))

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
            people (map #(dbh/query-eq-find-one :people :id % tx) mdr-ids)
            result {:meta_data md
                    MD_KEY_PEOPLE_IDS mdr-ids
                    MD_KEY_PEOPLE_DATA mdr
                    MD_KEY_PEOPLE people}]
        (sd/response_ok result))
      (sd/response_failed "Not found" 404))))

;;  #### Class-Functions ######################################################

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
            roles (map #(dbh/query-eq-find-one :roles :id % tx) mdr-rids)
            people (map #(dbh/query-eq-find-one :people :id % tx) mdr-pids)
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
                                          (map #(dbh/query-eq-find-one :keywords :id % tx)))
                   "MetaDatum::People" (->>
                                        mde
                                        (map (-> :person_id))
                                        (map #(dbh/query-eq-find-one :people :id % tx)))
                   "MetaDatum::Roles" (->>
                                       mde
                                       (map (-> :role_id))
                                       (map #(dbh/query-eq-find-one :roles :id % tx)))
                   "default")
        mde-result {:meta_data result
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

(def schema_export_meta-datum
  {:id s/Uuid
   :meta_key_id s/Str
   :type s/Str
   :value (s/->Either [[{:id s/Uuid}] s/Str])
   (s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :collection_id) s/Uuid})

(def schema_export_mdrole
  {:id s/Uuid
   :meta_datum_id s/Uuid
   :person_id s/Uuid
   :role_id (s/maybe s/Uuid)
   :position s/Int})

;; ###meta-data-routes ##########################################################

(def meta_datum_id {:handler meta-datum/get-meta-datum
                    :middleware [jqh/ring-wrap-add-meta-datum-with-media-resource
                                 jqh/ring-wrap-authorization-view]
                    :summary "Get meta-data for id"
                    :description "Get meta-data for id. TODO: should return 404, if no such meta-data role exists."
                    :coercion reitit.coercion.schema/coercion
                    :parameters {:path {:meta_datum_id s/Uuid}}
                    :responses {200 {:description "Returns the meta-data."
                                     :body schema_export_meta-datum}
                                401 {:description "Unauthorized."
                                     :body s/Any}
                                403 {:description "Forbidden."
                                     :body s/Any}
                                500 {:description "Internal server error."
                                     :body s/Any}}})

(def meta_datum_id.data-stream {:handler meta-datum/get-meta-datum-data-stream
                                ; TODO json meta-data: fix response conversion error
                                :middleware [jqh/ring-wrap-add-meta-datum-with-media-resource
                                             jqh/ring-wrap-authorization-view]
                                :summary "Get meta-data data-stream."
                                :description "Get meta-data data-stream."
                                :coercion reitit.coercion.schema/coercion
                                :responses {200 {:description "Returns the meta-data data-stream."
                                                 :body s/Any}
                                            401 {:description "Unauthorized."
                                                 :body s/Any}
                                            403 {:description "Forbidden."
                                                 :body s/Any}
                                            500 {:description "Internal server error."
                                                 :body s/Any}}
                                :parameters {:path {:meta_datum_id s/Uuid}}})

(def media-entry.media_entry_id.meta-data {:summary (sd/?token? "Get meta-data for media-entry.")
                                           :handler meta-data.index/get-index
                                           ; TODO 401s test fails
                                           :middleware [jqh/ring-wrap-add-media-resource
                                                        jqh/ring-wrap-authorization-view]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:media_entry_id s/Uuid}
                                                        :query {(s/optional-key :updated_after) s/Inst
                                                                (s/optional-key :meta_keys) s/Str}}
                                           :responses {200 {:description "Returns the meta-data for the media-entry."
                                                            :body {:meta_data [{:media_entry_id s/Uuid
                                                                                :collection_id (s/maybe s/Uuid)
                                                                                :type (s/maybe (s/enum "MetaDatum::Text" "MetaDatum::People" "MetaDatum::Roles" "MetaDatum::TextDate" "MetaDatum::Keywords"))
                                                                                :meta_key_id s/Str
                                                                                :string (s/maybe s/Str)
                                                                                :id s/Uuid
                                                                                :meta_data_updated_at s/Any
                                                                                :json (s/maybe s/Any)
                                                                                :other_media_entry_id (s/maybe s/Uuid)}]
                                                                   :media_entry_id s/Uuid}}}})
(def meta-data-role.meta_data_role_id {:summary " Get meta-data role for id "
                                       :handler meta-datum/handle_get-meta-datum-role
                                       :description " Get meta-datum-role for id. returns 404, if no such meta-data role exists. "
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:meta_data_role_id s/Str}}
                                       :responses {200 {:description "Returns the meta-data role."
                                                        :body schema_export_mdrole}
                                                   404 {:description "Not found."
                                                        :body s/Any}}})

(def collection_id.meta-data {:summary (sd/?token? "Get meta-data for collection.")
                              :handler meta-data.index/get-index
                              :middleware [jqh/ring-wrap-add-media-resource
                                           jqh/ring-wrap-authorization-view]
                              ; TODO 401s test fails
                              :coercion reitit.coercion.schema/coercion
                              :parameters {:path {:collection_id s/Uuid}
                                           :query {(s/optional-key :updated_after) s/Inst
                                                   (s/optional-key :meta_keys) s/Str}}
                              :responses {200 {:description "Returns the meta-data for the collection."
                                               :body {:collection_id s/Uuid
                                                      :meta_data [{:media_entry_id (s/maybe s/Uuid)
                                                                   :collection_id s/Uuid
                                                                   :type (s/enum "MetaDatum::Text"
                                                                                 "MetaDatum::People"
                                                                                 "MetaDatum::TextDate"
                                                                                 "MetaDatum::Keywords")
                                                                   :meta_key_id s/Str
                                                                   :string (s/maybe s/Str)
                                                                   :id s/Uuid
                                                                   :meta_data_updated_at s/Any
                                                                   :json (s/maybe s/Any)
                                                                   :other_media_entry_id (s/maybe s/Uuid)}]}}}})

(s/def KeywordEntry2
  {:meta_data s/Any
   (s/optional-key :defaultmetadata) s/Str
   (s/optional-key :defaultdata) s/Str
   (s/optional-key :md_people) s/Any
   (s/optional-key :people) s/Any
   (s/optional-key :md_keywords) s/Any
   (s/optional-key :keywords) s/Any})

(def collection_id.meta-data-related {:summary (sd/?token? "Get meta-data for collection.")
                                      :description (mslurp (io/resource "md/meta-data-related.md"))
                                      :handler handle_get-mr-meta-data-with-related
                                      :middleware [jqh/ring-wrap-add-media-resource
                                                   jqh/ring-wrap-authorization-view]
                                      ; TODO 401s test fails
                                      :coercion reitit.coercion.schema/coercion
                                      :parameters {:path {:collection_id s/Uuid}
                                                   :query {(s/optional-key :updated_after) s/Inst
                                                           (s/optional-key :meta_keys) s/Str}}
                                      :responses {200 {:description "Returns the meta-data for the collection."
                                                       :body [KeywordEntry2]}}})

(s/defschema KeywordEntry
  {;; FIXME: support meta-data only
   (s/optional-key :meta_data) s/Any
   (s/optional-key :keywords) s/Any
   (s/optional-key :keywords_ids) s/Any
   (s/optional-key :md_keywords) s/Any
   (s/optional-key :defaultmetadata) s/Any
   (s/optional-key :defaultdata) s/Any
   (s/optional-key :people) s/Any
   (s/optional-key :md_people) s/Any})

(def collection_id.meta-datum.meta_key_id {:summary (sd/?no-auth? "Get meta-data for collection and meta-key.")
                                           :handler handle_get-meta-key-meta-data
                                           :middleware [wrap-add-meta-key
                                                        wrap-check-vocab
                                                        jqh/ring-wrap-add-media-resource
                                                        jqh/ring-wrap-authorization-view]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:collection_id s/Uuid
                                                               :meta_key_id s/Str}}
                                           :responses {200 {:description "Returns the meta-data for the collection and meta-key."
                                                            :body KeywordEntry}}})

(def collection.meta_key_id.keyword {:summary (sd/?token? "Get meta-data keywords for collection meta-key")
                                     :handler handle_get-meta-data-keywords
                                     :middleware [;wrap-me-add-meta-data
                                                  jqh/ring-wrap-add-media-resource
                                                  jqh/ring-wrap-authorization-view]
                                     :coercion reitit.coercion.schema/coercion
                                     :parameters {:path {:collection_id s/Uuid
                                                         :meta_key_id s/Str}}
                                     :responses {200 {:description "Returns the meta-data keywords for the collection."
                                                      :body KeywordEntry}}})

(s/defschema PeopleEntry
  {:meta_data s/Any
   :people_ids s/Any
   :md_people s/Any
   :people s/Any})

(def meta_key_id.people2 {:summary "Get meta-data people for collection meta-key."
                          :handler handle_get-meta-data-people
                          :middleware [jqh/ring-wrap-add-media-resource
                                       jqh/ring-wrap-authorization-view]
                          :coercion reitit.coercion.schema/coercion
                          :parameters {:path {:collection_id s/Uuid
                                              :meta_key_id s/Str}}
                          :responses {200 {:description "Returns the meta-data people for the collection."
                                           :body PeopleEntry}}})

(def MetaDataSchema2
  {:media_entry_id s/Uuid
   :type s/Str
   :meta_key_id s/Str
   :id s/Uuid
   :meta_data_updated_at s/Any
   (s/optional-key :collection_id) (s/maybe s/Uuid)
   (s/optional-key :string) (s/maybe s/Str)
   (s/optional-key :json) (s/maybe s/Any)
   (s/optional-key :other_media_entry_id) (s/maybe s/Uuid)})

(def MDKeywordsSchema2
  {:id s/Uuid
   :created_by_id s/Uuid
   :meta_datum_id s/Uuid
   :keyword_id s/Uuid
   :created_at s/Any
   :updated_at s/Any
   :meta_data_updated_at s/Any
   :position s/Int})

(def KeywordsSchema2
  {:id s/Uuid
   :term s/Str
   :rdf_class s/Str
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :external_uris) [s/Str]
   (s/optional-key :meta_key_id) (s/maybe s/Str)
   (s/optional-key :creator_id) (s/maybe s/Uuid)
   (s/optional-key :updated_at) (s/maybe s/Inst)
   (s/optional-key :created_at) (s/maybe s/Inst)
   (s/optional-key :position) (s/maybe s/Int)})

(def BodySchema2
  [{:meta_data MetaDataSchema2
    (s/optional-key :defaultmetadata) s/Str
    (s/optional-key :defaultdata) s/Str
    (s/optional-key :md_keywords) [MDKeywordsSchema2]
    (s/optional-key :keywords) [KeywordsSchema2]}])

(def media_entry_id.meta-data-related {:summary (sd/?token? "Get meta-data for media-entry.")
                                       :handler handle_get-mr-meta-data-with-related
                                       :middleware [jqh/ring-wrap-add-media-resource
                                                    jqh/ring-wrap-authorization-view]
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:media_entry_id s/Uuid}
                                                    :query {(s/optional-key :updated_after) s/Inst
                                                            (s/optional-key :meta_keys) s/Str}}
                                       :responses {200 {:description "Returns the meta-data for the media-entry."
                                                        :body BodySchema2}}})

(def media_entry_id.meta-datum.meta_key_id {:summary "Get meta-data for media-entry and meta-key."
                                            :handler handle_get-meta-key-meta-data
                                            :middleware [wrap-add-meta-key
                                                         ;wrap-check-vocab
                                                         jqh/ring-wrap-add-media-resource
                                                         jqh/ring-wrap-authorization-view]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid
                                                                :meta_key_id s/Str}}
                                            :responses {200 {:description "Returns the meta-data for the media-entry and meta-key."
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
                                                                    (s/optional-key :defaultmetadata) s/Str
                                                                    (s/optional-key :defaultdata) s/Str
                                                                    (s/optional-key :md_keywords) s/Any
                                                                    (s/optional-key :keywords) s/Any
                                                                    (s/optional-key :md_people) s/Any
                                                                    (s/optional-key :people) s/Any
                                                                    (s/optional-key :md_roles) s/Any
                                                                    (s/optional-key :roles) s/Any}}}})

(def media_entry.meta_key_id.keyword {:summary "Get meta-data keywords for media-entries meta-key"
                                      :handler handle_get-meta-data-keywords
                                      :middleware [;wrap-me-add-meta-data
                                                   jqh/ring-wrap-add-media-resource
                                                   jqh/ring-wrap-authorization-view]
                                      :coercion reitit.coercion.schema/coercion
                                      :parameters {:path {:media_entry_id s/Uuid
                                                          :meta_key_id s/Str}}
                                      :responses {200 {:description "Returns the meta-data keywords for the media-entry."
                                                       :body {:meta_data s/Any
                                                              :keywords_ids s/Any
                                                              :md_keywords s/Any
                                                              :keywords s/Any}}}})

(def body-schema
  {:meta_data
   {:created_by_id s/Uuid
    :media_entry_id s/Uuid
    :collection_id (s/maybe s/Str)
    :type s/Str
    :meta_key_id s/Str
    :string (s/maybe s/Str)
    :id s/Uuid
    :meta_data_updated_at s/Any
    :json (s/maybe s/Any)
    :other_media_entry_id (s/maybe s/Str)}

   :people_ids
   [s/Uuid]

   :md_people
   [{:meta_datum_id s/Uuid
     :person_id s/Uuid
     :created_by_id s/Uuid
     :meta_data_updated_at s/Any
     :id s/Uuid
     :position s/Int}]

   :people
   [{:institution s/Str
     :institutional_id (s/maybe s/Str)
     :description (s/maybe s/Str)
     :first_name (s/maybe s/Str)
     :external_uris [s/Any]
     :identification_info (s/maybe s/Any)
     :institutional_directory_infos [s/Str]
     :searchable s/Str
     :updated_at s/Any
     :id s/Uuid
     :last_name s/Str
     :admin_comment (s/maybe s/Str)
     :pseudonym s/Str
     :created_at s/Any
     :subtype s/Str}]})

;; collection
(def meta_key_id.people {:summary "Get meta-data people for media-entries meta-key."
                         :handler handle_get-meta-data-people
                         :middleware [;wrap-me-add-meta-data
                                      jqh/ring-wrap-add-media-resource
                                      jqh/ring-wrap-authorization-view]
                         :coercion reitit.coercion.schema/coercion
                         :parameters {:path {:media_entry_id s/Uuid
                                             :meta_key_id s/Str}}
                         :responses {200 {:description "Returns the meta-data people for the media-entry."
                                          :body body-schema}}})

(def MetaDataSchema
  {:created_by_id s/Uuid
   :media_entry_id s/Uuid
   :collection_id (s/maybe s/Uuid)
   :type (s/enum "MetaDatum::Roles")
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :id s/Uuid
   :meta_data_updated_at s/Any
   :json (s/maybe s/Any)
   :other_media_entry_id (s/maybe s/Uuid)})

(def MdRolesSchema
  {:id s/Uuid
   :meta_datum_id s/Uuid
   :person_id s/Uuid
   :role_id (s/maybe s/Uuid)
   :position s/Int})

(def PeopleSchema
  {:institution s/Str
   :institutional_id (s/maybe s/Str)
   :description (s/maybe s/Str)
   :first_name s/Str
   :external_uris [s/Str]
   :identification_info (s/maybe s/Str)
   :institutional_directory_infos [s/Str]
   :searchable s/Str
   :updated_at s/Any
   :id s/Uuid
   :last_name s/Str
   :admin_comment (s/maybe s/Str)
   :pseudonym (s/maybe s/Str)
   :created_at s/Any
   :subtype (s/enum "Person")})

(def BodySchema
  {:meta_data MetaDataSchema
   :roles_ids [s/Any]
   :people_ids [s/Uuid]
   :md_roles [MdRolesSchema]
   :roles [s/Any]
   :people [PeopleSchema]})

(def meta_key_id.role {:summary "Get meta-data role for media-entry."
                       :handler handle_get-meta-data-roles
                       :middleware [jqh/ring-wrap-add-media-resource
                                    jqh/ring-wrap-authorization-view]
                       :coercion reitit.coercion.schema/coercion
                       :parameters {:path {:media_entry_id s/Uuid
                                           :meta_key_id s/Str}}
                       :responses {200 {:description "Returns the meta-data role for the media-entry."
                                        :body BodySchema}}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
