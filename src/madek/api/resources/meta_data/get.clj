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

(def media-entry.media_entry_id.meta-data {:summary "Get meta-data for media-entry."
                                           :handler meta-data.index/get-index
                                           :description "Example for meta_keys: ['s9r2epz5t2:i532t703bbkv628idkar']"
                                           ; TODO 401s test fails
                                           :middleware [jqh/ring-wrap-add-media-resource
                                                        jqh/ring-wrap-authorization-view]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:media_entry_id s/Uuid}
                                                        :query {(s/optional-key :updated_after) s/Inst
                                                                (s/optional-key :meta_keys) s/Str}}
                                           :responses {200 {:description "Returns the meta-data for the media-entry."
                                                            :body s/Any}}})

(def meta-data-role.meta_data_role_id {:summary " Get meta-data role for id "
                                       :handler meta-datum/handle_get-meta-datum-role
                                       :description " Get meta-datum-role for id. returns 404, if no such meta-data role exists. "
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:meta_data_role_id s/Str}}
                                       :responses {200 {:description "Returns the meta-data role."
                                                        :body schema_export_mdrole}
                                                   404 {:description "Not found."
                                                        :body s/Any}}})

(def collection_id.meta-data {:summary "Get meta-data for collection."
                              :handler meta-data.index/get-index
                              :middleware [jqh/ring-wrap-add-media-resource
                                           jqh/ring-wrap-authorization-view]
                              ; TODO 401s test fails
                              :coercion reitit.coercion.schema/coercion
                              :parameters {:path {:collection_id s/Uuid}
                                           :query {(s/optional-key :updated_after) s/Inst
                                                   (s/optional-key :meta_keys) s/Str}}
                              :responses {200 {:description "Returns the meta-data for the collection."
                                               ;:body s/Any
                                               :body

                                               {:collection_id s/Uuid
                                                :meta-data [{:media_entry_id (s/maybe s/Uuid)
                                                             :collection_id s/Uuid
                                                             :type (s/enum "MetaDatum::Text"
                                                                     "MetaDatum::People"
                                                                     "MetaDatum::TextDate"
                                                                     "MetaDatum::Keywords")
                                                             :meta_key_id s/Str
                                                             :string (s/maybe s/Str)
                                                             :id s/Uuid
                                                             ;:meta_data_updated_at s/Inst
                                                             :meta_data_updated_at s/Str
                                                             :json (s/maybe s/Any)
                                                             :other_media_entry_id (s/maybe s/Uuid)}]}


                                               }}})


;(s/defschema MetaDatum
;  {:media_entry_id (s/maybe s/Uuid)
;   :collection_id s/Uuid
;   :type (s/enum "MetaDatum::Text"
;           "MetaDatum::People"
;           "MetaDatum::TextDate"
;           "MetaDatum::Keywords")
;   :meta_key_id s/Str
;   :string (s/maybe s/Str)
;   :id s/Uuid
;   :meta_data_updated_at s/Inst
;   :json (s/maybe s/Any)
;   :other_media_entry_id (s/maybe s/Uuid)})
;
;(s/defschema MdPeople
;  {:meta_datum_id s/Uuid
;   :person_id s/Uuid
;   :created_by_id s/Uuid
;   :meta_data_updated_at s/Inst
;   :id s/Uuid
;   :position s/Int})
;
;(s/defschema Person
;  {:institution s/Str
;   :institutional_id (s/maybe s/Str)
;   :description (s/maybe s/Str)
;   :first_name s/Str
;   :external_uris [s/Str]
;   :identification_info (s/maybe s/Str)
;   :searchable s/Str
;   :updated_at s/Inst
;   :id s/Uuid
;   :last_name (s/maybe s/Str)
;   :admin_comment (s/maybe s/Str)
;   :pseudonym (s/maybe s/Str)
;   :created_at s/Inst
;   :subtype (s/enum "Person" "PeopleGroup")})
;
;(s/defschema MdKeyword
;  {:id s/Uuid
;   :created_by_id s/Uuid
;   :meta_datum_id s/Uuid
;   :keyword_id s/Uuid
;   :created_at s/Inst
;   :updated_at s/Inst
;   :meta_data_updated_at s/Inst
;   :position s/Int})
;
;(s/defschema Keyword
;  {:description (s/maybe s/Str)
;   :external_uris [s/Str]
;   :meta_key_id s/Str
;   :creator_id s/Uuid
;   :term s/Str
;   :updated_at s/Inst
;   :rdf_class s/Str
;   :id s/Uuid
;   :position s/Int
;   :created_at s/Inst})
;
;(s/defschema Entry
;  {:meta-data MetaDatum
;   :defaultmetadata s/Str
;   :defaultdata s/Str
;   (s/optional-key :md_people) [MdPeople]
;   (s/optional-key :people) [Person]
;   (s/optional-key :md_keywords) [MdKeyword]
;   (s/optional-key :keywords) [Keyword]})
;
;(s/defschema Entries [Entry])



;; TODO
(s/def Entry
  {:meta-data s/Any
   (s/optional-key :defaultmetadata) s/Str
   (s/optional-key :defaultdata) s/Str
   (s/optional-key :md_people) s/Any
   (s/optional-key :people) s/Any
   (s/optional-key :md_keywords) s/Any
   (s/optional-key :keywords) s/Any})

(def collection_id.meta-data-related {:summary "Get meta-data for collection."
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
                                                       ;:body s/Any


                                                       :body [Entry]


                                                       }}})

(def collection_id.meta-datum.meta_key_id {:summary "Get meta-data for collection and meta-key."
                                           :handler handle_get-meta-key-meta-data

                                           :middleware [wrap-add-meta-key
                                                        wrap-check-vocab
                                                        jqh/ring-wrap-add-media-resource
                                                        jqh/ring-wrap-authorization-view]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:collection_id s/Uuid
                                                               :meta_key_id s/Str}}
                                           :responses {200 {:description "Returns the meta-data for the collection and meta-key."
                                                            :body s/Any}}})

(def collection.meta_key_id.keyword {:summary "Get meta-data keywords for collection meta-key"
                                     :handler handle_get-meta-data-keywords
                                     :middleware [;wrap-me-add-meta-data
                                                  jqh/ring-wrap-add-media-resource
                                                  jqh/ring-wrap-authorization-view]
                                     :coercion reitit.coercion.schema/coercion
                                     :parameters {:path {:collection_id s/Uuid
                                                         :meta_key_id s/Str}}
                                     :responses {200 {:description "Returns the meta-data keywords for the collection."
                                                      :body s/Any}}})

(def meta_key_id.people2 {:summary "Get meta-data people for collection meta-key."
                          :handler handle_get-meta-data-people
                          :middleware [jqh/ring-wrap-add-media-resource
                                       jqh/ring-wrap-authorization-view]
                          :coercion reitit.coercion.schema/coercion
                          :parameters {:path {:collection_id s/Uuid
                                              :meta_key_id s/Str}}
                          :responses {200 {:description "Returns the meta-data people for the collection."
                                           :body s/Any}}})

(def media_entry_id.meta-data-related {:summary "Get meta-data for media-entry."
                                       :handler handle_get-mr-meta-data-with-related
                                       :middleware [jqh/ring-wrap-add-media-resource
                                                    jqh/ring-wrap-authorization-view]
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:media_entry_id s/Uuid}
                                                    :query {(s/optional-key :updated_after) s/Inst
                                                            (s/optional-key :meta_keys) s/Str}}
                                       :responses {200 {:description "Returns the meta-data for the media-entry."
                                                        :body s/Any}}})

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
                                                             :body s/Any}}})

(def media_entry.meta_key_id.keyword {:summary "Get meta-data keywords for media-entries meta-key"
                                      :handler handle_get-meta-data-keywords
                                      :middleware [;wrap-me-add-meta-data
                                                   jqh/ring-wrap-add-media-resource
                                                   jqh/ring-wrap-authorization-view]
                                      :coercion reitit.coercion.schema/coercion
                                      :parameters {:path {:media_entry_id s/Uuid
                                                          :meta_key_id s/Str}}
                                      :responses {200 {:description "Returns the meta-data keywords for the media-entry."
                                                       :body s/Any}}})

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
                                          :body s/Any}}})

(def meta_key_id.role {:summary "Get meta-data role for media-entry."
                       :handler handle_get-meta-data-roles
                       :middleware [jqh/ring-wrap-add-media-resource
                                    jqh/ring-wrap-authorization-view]
                       :coercion reitit.coercion.schema/coercion
                       :parameters {:path {:media_entry_id s/Uuid
                                           :meta_key_id s/Str}}
                       :responses {200 {:description "Returns the meta-data role for the media-entry."
                                        :body s/Any}}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
