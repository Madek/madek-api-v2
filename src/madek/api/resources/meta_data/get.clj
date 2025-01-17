(ns madek.api.resources.meta_data.get
  (:require [cheshire.core]
            [clojure.java.io :as io]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.resources.meta-data.index :as meta-data.index]
            [madek.api.resources.meta-data.meta-datum :as meta-datum]
            [madek.api.resources.meta_data.common :refer :all]
            [madek.api.resources.shared.core :as fl]
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
   ;(s/optional-key :media_entry_id) s/Uuid
   :media_entry_id s/Uuid
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
                    :description "Get meta-data for id. TODO: should return 404, if no such meta-data role exists.\ne4f7c451-e6ad-4356-8715-5e5aed60d25a"
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
                                :summary "Get meta-data data-stream.\ne4f7c451-e6ad-4356-8715-5e5aed60d25a"
                                :description "Get meta-data data-stream.\n- ToCheck: really correct"
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

(def media-entry.media_entry_id.meta-data {:summary (fl/?token? "Get meta-data for media-entry.L7")
                                           :handler meta-data.index/get-index
                                           :description "- b24aaccf-ab37-491e-aebe-61e4f7762804"
                                           ; TODO 401s test fails
                                           :middleware [jqh/ring-wrap-add-media-resource
                                                        jqh/ring-wrap-authorization-view]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:media_entry_id s/Uuid}
                                                        :query {(s/optional-key :updated_after) s/Inst
                                                                (s/optional-key :meta_keys) s/Str}}
                                           :responses {200 {:description "Returns the meta-data for the media-entry."
                                                            ;:body s/Any
                                                            :body {:meta-data s/Any
                                                                   :media_entry_id s/Uuid
                                                                   }

                                                            ;{
                                                            ; "meta-data": [
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::Text",
                                                            ;                "meta_key_id": "madek_core:title",
                                                            ;                "string": "Roberta Martins 18/19 LAP von links",
                                                            ;                "id": "0ff4c1f8-b20e-4cf9-ac4e-ef86d667092c",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:03:42.079458Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                },
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::Text",
                                                            ;                "meta_key_id": "madek_core:subtitle",
                                                            ;                "string": "Prüfung",
                                                            ;                "id": "456f537d-3648-4b5e-b0a3-7bab0155f40f",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                },
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::TextDate",
                                                            ;                "meta_key_id": "madek_core:portrayed_object_date",
                                                            ;                "string": "21.05.2019",
                                                            ;                "id": "cb5a8be6-9441-4538-8bb7-86b4180f09c6",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                },
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::Text",
                                                            ;                "meta_key_id": "madek_core:copyright_notice",
                                                            ;                "string": "Zürcher Hochschule der Künste, Tanzakademie Zürich",
                                                            ;                "id": "fa63b678-e0e8-41c2-89ec-081f2e9d088d",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:03:42.079458Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                },
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::Keywords",
                                                            ;                "meta_key_id": "zhdk_bereich:project_type",
                                                            ;                "string": null,
                                                            ;                "id": "33dd5fb7-f9f9-4219-9ea1-2eb285c82747",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                },
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::Text",
                                                            ;                "meta_key_id": "zhdk_bereich:project_leader",
                                                            ;                "string": "Roberta Martins",
                                                            ;                "id": "60fa41fc-afb8-460a-a813-5af8d799997d",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                },
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::Keywords",
                                                            ;                "meta_key_id": "media_content:type",
                                                            ;                "string": null,
                                                            ;                "id": "ae3b0662-e4cc-4369-ae7d-514e05b08a7b",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                },
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::Text",
                                                            ;                "meta_key_id": "media_content:portrayed_object_location",
                                                            ;                "string": "Toni Areal Studio 1",
                                                            ;                "id": "33cd1fcf-7359-4066-a855-daf3021adaac",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                },
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::Text",
                                                            ;                "meta_key_id": "media_object:other_creative_participants",
                                                            ;                "string": "Pianist: Tiffany Butt",
                                                            ;                "id": "ac1dab92-943e-4031-97d4-aad4b506e2aa",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                },
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::Keywords",
                                                            ;                "meta_key_id": "copyright:license",
                                                            ;                "string": null,
                                                            ;                "id": "1d343d9e-0341-4cf2-8ce8-fe67fc4161d4",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:02:46.311323Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                },
                                                            ;               {
                                                            ;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
                                                            ;                "collection_id": null,
                                                            ;                "type": "MetaDatum::Text",
                                                            ;                "meta_key_id": "copyright:copyright_usage",
                                                            ;                "string": "Das Werk darf nur mit Einwilligung des Autors/Rechteinhabers weiter verwendet werden.",
                                                            ;                "id": "6ecfad90-5130-4aba-91eb-c0c9ba25a9ea",
                                                            ;                "meta_data_updated_at": "2019-05-21T11:02:46.552815Z",
                                                            ;                "json": null,
                                                            ;                "other_media_entry_id": null
                                                            ;                }
                                                            ;               ],
                                                            ; "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804"
                                                            ; }

                                                            }}})

(def meta-data-role.meta_data_role_id {:summary " Get meta-data role for id "
                                       :handler meta-datum/handle_get-meta-datum-role
                                       :description " Get meta-datum-role for id. returns 404, if no such meta-data role exists. "
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:meta_data_role_id s/Str}}
                                       :responses {200 {:description "Returns the meta-data role."
                                                        :body schema_export_mdrole}
                                                   404 {:description "Not found."
                                                        :body s/Any}}})

(def collection_id.meta-data {:summary (fl/?token? "Get meta-data for collection.")
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

(def collection_id.meta-data-related {:summary (fl/?token? "Get meta-data for collection.")
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




;(s/defschema KeywordEntry
;  {:meta-data s/Any
;   :md_keywords s/Any
;   :keywords s/Any})

(s/defschema KeywordEntry
  {
   ;:meta_data s/Any
   ;:meta-data s/Any

   ;; FIXME: support meta-data only
   (s/optional-key :meta_data) s/Any
   (s/optional-key :meta-data) s/Any

   ;(s/optional-key :meta-data) s/Any
   (s/optional-key :keywords) s/Any
   (s/optional-key :keywords_ids) s/Any
   (s/optional-key  :md_keywords) s/Any
   (s/optional-key  :defaultmetadata) s/Any
   (s/optional-key  :defaultdata) s/Any
   (s/optional-key  :people) s/Any
   (s/optional-key  :md_people) s/Any
   ;:keywords s/Any
   })

; 25a5d974-1855-458b-b6ba-cc3272a4865b
; media_content:portrayed_object_materials

(def collection_id.meta-datum.meta_key_id {:summary (fl/?no-auth? "Get meta-data for collection and meta-key.")

                                           :description "
81f47499-d7a9-4e28-9e58-c6e2db2334ea
madek_core:keywords
madek_core:subtitle
"

                                           :handler handle_get-meta-key-meta-data

                                           :middleware [wrap-add-meta-key
                                                        wrap-check-vocab
                                                        jqh/ring-wrap-add-media-resource
                                                        jqh/ring-wrap-authorization-view]
                                           :coercion reitit.coercion.schema/coercion
                                           :parameters {:path {:collection_id s/Uuid
                                                               :meta_key_id s/Str}}
                                           :responses {200 {:description "Returns the meta-data for the collection and meta-key."
                                                            ;:body s/Any

                                                            :body KeywordEntry

                                                            }}})


;(s/defschema MetaData
;  {:created_by_id s/Uuid
;   :media_entry_id (s/maybe s/Uuid)
;   :collection_id s/Uuid
;   :type (s/enum "MetaDatum::Keywords")
;   :meta_key_id s/Str
;   :string (s/maybe s/Str)
;   :id s/Uuid
;   :meta_data_updated_at s/Inst
;   :json (s/maybe s/Any)
;   :other_media_entry_id (s/maybe s/Uuid)})
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
;(s/defschema Value
;  {:meta_data MetaData
;   :keywords_ids [s/Uuid]
;   :md_keywords [MdKeyword]
;   :keywords [Keyword]})




; TODO
; 25a5d974-1855-458b-b6ba-cc3272a4865b
; media_content:portrayed_object_materials

(def collection.meta_key_id.keyword {:summary (fl/?token? "Get meta-data keywords for collection meta-key X1")
                                     :handler handle_get-meta-data-keywords
                                     :middleware [;wrap-me-add-meta-data
                                                  jqh/ring-wrap-add-media-resource
                                                  jqh/ring-wrap-authorization-view]
                                     :coercion reitit.coercion.schema/coercion
                                     :parameters {:path {:collection_id s/Uuid
                                                         :meta_key_id s/Str}}
                                     :responses {200 {:description "Returns the meta-data keywords for the collection."
                                                      ;:body s/Any

                                                      :body KeywordEntry

                                                      }}})

;(s/defschema MetaData
;  {:created_by_id s/Uuid
;   :media_entry_id (s/maybe s/Uuid)
;   :collection_id s/Uuid
;   :type (s/enum "MetaDatum::People")
;   :meta_key_id s/Str
;   :string (s/maybe s/Str)
;   :id s/Uuid
;   :meta_data_updated_at s/Inst
;   :json (s/maybe s/Any)
;   :other_media_entry_id (s/maybe s/Uuid)})
;
;(s/defschema MdPerson
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
;   :first_name (s/maybe s/Str)
;   :external_uris [s/Str]
;   :identification_info (s/maybe s/Str)
;   :searchable s/Str
;   :updated_at s/Inst
;   :id s/Uuid
;   :last_name (s/maybe s/Str)
;   :admin_comment (s/maybe s/Str)
;   :pseudonym (s/maybe s/Str)
;   :created_at s/Inst
;   :subtype (s/enum "PeopleInstitutionalGroup" "PeopleIndividual")})
;
;(s/defschema PeopleEntry
;  {:meta_data MetaData
;   :people_ids [s/Uuid]
;   :md_people [MdPerson]
;   :people [Person]})





(s/defschema PeopleEntry
  {:meta_data s/Any
   :people_ids s/Any
   :md_people s/Any
   :people s/Any})



(def meta_key_id.people2 {
                          :summary "Get meta-data people for collection meta-key. S2"
                          :description "- 2e9fa545-2d8b-418d-82bb-368b07841716\n
- mkid zhdk_bereich:institutional_affiliation
"
                          :handler handle_get-meta-data-people
                          :middleware [jqh/ring-wrap-add-media-resource
                                       jqh/ring-wrap-authorization-view]
                          :coercion reitit.coercion.schema/coercion
                          :parameters {:path {:collection_id s/Uuid
                                              :meta_key_id s/Str}}
                          :responses {200 {:description "Returns the meta-data people for the collection."
                                           ;:body s/Any

                                           :body PeopleEntry

                                           }}})




(def media_entry_id.meta-data-related {:summary (fl/?token? "Get meta-data for media-entry. L8")
                                       :handler handle_get-mr-meta-data-with-related
                                       :middleware [jqh/ring-wrap-add-media-resource
                                                    jqh/ring-wrap-authorization-view]
                                       :coercion reitit.coercion.schema/coercion
                                       :parameters {:path {:media_entry_id s/Uuid}
                                                    :query {(s/optional-key :updated_after) s/Inst
                                                            (s/optional-key :meta_keys) s/Str}}
                                       :responses {200 {:description "Returns the meta-data for the media-entry."

                                                        ;:body s/Any
                                                        :body [{
                                                                :meta-data s/Any
                                                                (s/optional-key :defaultmetadata) s/Str
                                                                (s/optional-key :defaultdata) s/Str
                                                                (s/optional-key :md_keywords) s/Any
                                                                (s/optional-key :keywords) s/Any
                                                                }]


                                                        }}})

;[
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::Text",
;                "meta_key_id": "madek_core:title",
;                "string": "Roberta Martins 18/19 LAP von links",
;                "id": "0ff4c1f8-b20e-4cf9-ac4e-ef86d667092c",
;                "meta_data_updated_at": "2019-05-21T11:03:42.079458Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "defaultmetadata": "default",
;  "defaultdata": "default"
;  },
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::Text",
;                "meta_key_id": "madek_core:subtitle",
;                "string": "Prüfung",
;                "id": "456f537d-3648-4b5e-b0a3-7bab0155f40f",
;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "defaultmetadata": "default",
;  "defaultdata": "default"
;  },
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::TextDate",
;                "meta_key_id": "madek_core:portrayed_object_date",
;                "string": "21.05.2019",
;                "id": "cb5a8be6-9441-4538-8bb7-86b4180f09c6",
;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "defaultmetadata": "default",
;  "defaultdata": "default"
;  },
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::Text",
;                "meta_key_id": "madek_core:copyright_notice",
;                "string": "Zürcher Hochschule der Künste, Tanzakademie Zürich",
;                "id": "fa63b678-e0e8-41c2-89ec-081f2e9d088d",
;                "meta_data_updated_at": "2019-05-21T11:03:42.079458Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "defaultmetadata": "default",
;  "defaultdata": "default"
;  },
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::Keywords",
;                "meta_key_id": "zhdk_bereich:project_type",
;                "string": null,
;                "id": "33dd5fb7-f9f9-4219-9ea1-2eb285c82747",
;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "md_keywords": [
;                  {
;                   "id": "16fb0bfe-8743-4dd8-837e-9b276b257f3c",
;                   "created_by_id": "1c51e525-251e-417a-b62f-e15772cb53bb",
;                   "meta_datum_id": "33dd5fb7-f9f9-4219-9ea1-2eb285c82747",
;                   "keyword_id": "1ecab382-d875-45c2-aeef-4420b702ec69",
;                   "created_at": "2019-05-21T11:07:13.436174Z",
;                   "updated_at": "2019-05-21T11:07:13.436174Z",
;                   "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
;                   "position": 0
;                   }
;                  ],
;  "keywords": [
;               {
;                "description": null,
;                "external_uris": [],
;                "meta_key_id": "zhdk_bereich:project_type",
;                "creator_id": "38192700-cb5f-44b5-ad19-900301dfdfcb",
;                "term": "Abschlussarbeit",
;                "updated_at": "2017-02-22T22:27:04.889694Z",
;                "rdf_class": "Keyword",
;                "id": "1ecab382-d875-45c2-aeef-4420b702ec69",
;                "position": 0,
;                "created_at": "2016-08-17T17:56:44.721694Z"
;                }
;               ]
;  },
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::Text",
;                "meta_key_id": "zhdk_bereich:project_leader",
;                "string": "Roberta Martins",
;                "id": "60fa41fc-afb8-460a-a813-5af8d799997d",
;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "defaultmetadata": "default",
;  "defaultdata": "default"
;  },
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::Keywords",
;                "meta_key_id": "media_content:type",
;                "string": null,
;                "id": "ae3b0662-e4cc-4369-ae7d-514e05b08a7b",
;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "md_keywords": [
;                  {
;                   "id": "ad3dd06d-da4e-4330-9dc4-09bec9feeac2",
;                   "created_by_id": "1c51e525-251e-417a-b62f-e15772cb53bb",
;                   "meta_datum_id": "ae3b0662-e4cc-4369-ae7d-514e05b08a7b",
;                   "keyword_id": "0441b27e-3e4b-4a81-a3e2-b3bc2a25781a",
;                   "created_at": "2019-05-21T11:07:13.436174Z",
;                   "updated_at": "2019-05-21T11:07:13.436174Z",
;                   "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
;                   "position": 0
;                   }
;                  ],
;  "keywords": [
;               {
;                "description": null,
;                "external_uris": [],
;                "meta_key_id": "media_content:type",
;                "creator_id": "bd0643bd-e7f5-4e0f-a717-5f7e16cf2332",
;                "term": "Tanz",
;                "updated_at": "2017-02-04T09:00:41.741044Z",
;                "rdf_class": "Keyword",
;                "id": "0441b27e-3e4b-4a81-a3e2-b3bc2a25781a",
;                "position": 8,
;                "created_at": "2016-08-17T17:56:44.721694Z"
;                }
;               ]
;  },
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::Text",
;                "meta_key_id": "media_content:portrayed_object_location",
;                "string": "Toni Areal Studio 1",
;                "id": "33cd1fcf-7359-4066-a855-daf3021adaac",
;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "defaultmetadata": "default",
;  "defaultdata": "default"
;  },
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::Text",
;                "meta_key_id": "media_object:other_creative_participants",
;                "string": "Pianist: Tiffany Butt",
;                "id": "ac1dab92-943e-4031-97d4-aad4b506e2aa",
;                "meta_data_updated_at": "2019-05-21T11:07:13.436174Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "defaultmetadata": "default",
;  "defaultdata": "default"
;  },
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::Keywords",
;                "meta_key_id": "copyright:license",
;                "string": null,
;                "id": "1d343d9e-0341-4cf2-8ce8-fe67fc4161d4",
;                "meta_data_updated_at": "2019-05-21T11:02:46.311323Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "md_keywords": [
;                  {
;                   "id": "e73d0554-d29a-4c56-9fcc-8237b8ebafcf",
;                   "created_by_id": "1c51e525-251e-417a-b62f-e15772cb53bb",
;                   "meta_datum_id": "1d343d9e-0341-4cf2-8ce8-fe67fc4161d4",
;                   "keyword_id": "bc1934f6-b580-4c84-b680-c73b82c93caf",
;                   "created_at": "2019-05-21T11:02:46.311323Z",
;                   "updated_at": "2019-05-21T11:02:46.311323Z",
;                   "meta_data_updated_at": "2019-05-21T11:02:46.311323Z",
;                   "position": 0
;                   }
;                  ],
;  "keywords": [
;               {
;                "description": "Das Werk darf nur mit Einwilligung des Autors/Rechteinhabers weiter verwendet werden.",
;                "external_uris": [
;                                  "http://www.ige.ch"
;                                  ],
;                "meta_key_id": "copyright:license",
;                "creator_id": "d48e4387-b80d-45de-9077-5d88c331fa6a",
;                "term": "Alle Rechte vorbehalten",
;                "updated_at": "2019-05-21T03:32:34.662329Z",
;                "rdf_class": "License",
;                "id": "bc1934f6-b580-4c84-b680-c73b82c93caf",
;                "position": null,
;                "created_at": "2017-05-11T03:35:17.981574Z"
;                }
;               ]
;  },
; {
;  "meta-data": {
;                "media_entry_id": "b24aaccf-ab37-491e-aebe-61e4f7762804",
;                "collection_id": null,
;                "type": "MetaDatum::Text",
;                "meta_key_id": "copyright:copyright_usage",
;                "string": "Das Werk darf nur mit Einwilligung des Autors/Rechteinhabers weiter verwendet werden.",
;                "id": "6ecfad90-5130-4aba-91eb-c0c9ba25a9ea",
;                "meta_data_updated_at": "2019-05-21T11:02:46.552815Z",
;                "json": null,
;                "other_media_entry_id": null
;                },
;  "defaultmetadata": "default",
;  "defaultdata": "default"
;  }
; ]


;2befd736-48bc-403e-ac69-9a94011e9470
;madek_core:title

(def media_entry_id.meta-datum.meta_key_id {
                                            :summary "Get meta-data for media-entry and meta-key. X6"
                                            :description "- 2befd736-48bc-403e-ac69-9a94011e9470\n - madek_core:title"
                                            :handler handle_get-meta-key-meta-data
                                            :middleware [wrap-add-meta-key
                                                         ;wrap-check-vocab
                                                         jqh/ring-wrap-add-media-resource
                                                         jqh/ring-wrap-authorization-view]
                                            :coercion reitit.coercion.schema/coercion
                                            :parameters {:path {:media_entry_id s/Uuid
                                                                :meta_key_id s/Str}}
                                            :responses {200 {:description "Returns the meta-data for the media-entry and meta-key."

                                                             ;:body s/Any

                                                             :body [{
                                                                     :meta-data s/Any
                                                                     ;(s/optional-key :defaultmetadata) s/Str
                                                                     ;(s/optional-key :defaultdata) s/Str

                                                                     :defaultmetadata s/Str
                                                                     :defaultdata s/Str

                                                                     ;(s/optional-key :md_keywords) s/Any
                                                                     ;(s/optional-key :keywords) s/Any
                                                                     }]

                                                             }}})

(def media_entry.meta_key_id.keyword {
                                      :summary "Get meta-data keywords for media-entries meta-key"
                                      :description "- a0040f48-020e-47bd-ae10-df7b28c8ff9c\n- zhdk_bereich:project_type"
                                      :handler handle_get-meta-data-keywords
                                      :middleware [;wrap-me-add-meta-data
                                                   jqh/ring-wrap-add-media-resource
                                                   jqh/ring-wrap-authorization-view]
                                      :coercion reitit.coercion.schema/coercion
                                      :parameters {:path {:media_entry_id s/Uuid
                                                          :meta_key_id s/Str}}
                                      :responses {200 {:description "Returns the meta-data keywords for the media-entry."
                                                       ;:body s/Any
                                                       :body {:meta-data s/Any
                                                              :keywords_ids s/Any
                                                              :md_keywords s/Any
                                                              :keywords s/Any}

                                                       ;{
                                                       ; "meta_data": {
                                                       ;               "created_by_id": "98954f14-0f95-4de6-b7d5-0113643cb2b3",
                                                       ;               "media_entry_id": "a0040f48-020e-47bd-ae10-df7b28c8ff9c",
                                                       ;               "collection_id": null,
                                                       ;               "type": "MetaDatum::Keywords",
                                                       ;               "meta_key_id": "zhdk_bereich:project_type",
                                                       ;               "string": null,
                                                       ;               "id": "8d626684-76eb-4953-ba67-44ace3d87292",
                                                       ;               "meta_data_updated_at": "2019-06-28T13:24:59.889018Z",
                                                       ;               "json": null,
                                                       ;               "other_media_entry_id": null
                                                       ;               },
                                                       ; "keywords_ids": [
                                                       ;                  "1ecab382-d875-45c2-aeef-4420b702ec69"
                                                       ;                  ],
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
                                                       ;                 ],
                                                       ; "keywords": [
                                                       ;              {
                                                       ;               "description": null,
                                                       ;               "external_uris": [],
                                                       ;               "meta_key_id": "zhdk_bereich:project_type",
                                                       ;               "creator_id": "38192700-cb5f-44b5-ad19-900301dfdfcb",
                                                       ;               "term": "Abschlussarbeit",
                                                       ;               "updated_at": "2017-02-22T22:27:04.889694Z",
                                                       ;               "rdf_class": "Keyword",
                                                       ;               "id": "1ecab382-d875-45c2-aeef-4420b702ec69",
                                                       ;               "position": 0,
                                                       ;               "created_at": "2016-08-17T17:56:44.721694Z"
                                                       ;               }
                                                       ;              ]
                                                       ; }


                                                       }}})

;; collection
(def meta_key_id.people {
                         :summary "Get meta-data people for media-entries meta-key. K1"
                         :description "- a0040f48-020e-47bd-ae10-df7b28c8ff9c\n- zhdk_bereich:institutional_affiliation"
                         :handler handle_get-meta-data-people
                         :middleware [;wrap-me-add-meta-data
                                      jqh/ring-wrap-add-media-resource
                                      jqh/ring-wrap-authorization-view]
                         :coercion reitit.coercion.schema/coercion
                         :parameters {:path {:media_entry_id s/Uuid
                                             :meta_key_id s/Str}}
                         :responses {200 {:description "Returns the meta-data people for the media-entry."

                                          ;:body s/Any


                                          :body {

                                                 :meta_data s/Any
                                                 :people_ids s/Any
                                                 :md_people s/Any
                                                 :people s/Any
                                                 }

                                          ;{
                                          ; "meta_data": {
                                          ;               "created_by_id": "98954f14-0f95-4de6-b7d5-0113643cb2b3",
                                          ;               "media_entry_id": "a0040f48-020e-47bd-ae10-df7b28c8ff9c",
                                          ;               "collection_id": null,
                                          ;               "type": "MetaDatum::People",
                                          ;               "meta_key_id": "zhdk_bereich:institutional_affiliation",
                                          ;               "string": null,
                                          ;               "id": "e1bb915a-0afc-4097-b87a-fddfb2b2a5f1",
                                          ;               "meta_data_updated_at": "2019-06-28T13:24:59.889018Z",
                                          ;               "json": null,
                                          ;               "other_media_entry_id": null
                                          ;               },
                                          ; "people_ids": [
                                          ;                "072c68a6-4452-43a2-9a2e-d126d545c255"
                                          ;                ],
                                          ; "md_people": [
                                          ;               {
                                          ;                "meta_datum_id": "e1bb915a-0afc-4097-b87a-fddfb2b2a5f1",
                                          ;                "person_id": "072c68a6-4452-43a2-9a2e-d126d545c255",
                                          ;                "created_by_id": "98954f14-0f95-4de6-b7d5-0113643cb2b3",
                                          ;                "meta_data_updated_at": "2019-06-28T13:24:59.889018Z",
                                          ;                "id": "08a6075b-59db-4c99-8c62-9edc7a95c681",
                                          ;                "position": 0
                                          ;                }
                                          ;               ],
                                          ; "people": [
                                          ;            {
                                          ;             "institution": "local",
                                          ;             "institutional_id": "14669.alle",
                                          ;             "description": null,
                                          ;             "first_name": null,
                                          ;             "external_uris": [],
                                          ;             "identification_info": null,
                                          ;             "searchable": " Bachelor Design - Cast / Audiovisual Media DDE_FDE_BDE_VCA.alle",
                                          ;             "updated_at": "2017-12-01T10:38:08.510909Z",
                                          ;             "id": "072c68a6-4452-43a2-9a2e-d126d545c255",
                                          ;             "last_name": "Bachelor Design - Cast / Audiovisual Media",
                                          ;             "admin_comment": null,
                                          ;             "pseudonym": "DDE_FDE_BDE_VCA.alle",
                                          ;             "created_at": "2016-10-18T17:48:31.308228Z",
                                          ;             "subtype": "PeopleInstitutionalGroup"
                                          ;             }
                                          ;            ]
                                          ; }

                                          }}})

(def meta_key_id.role {
                       :summary "Get meta-data role for media-entry. K7"
                       :description "- 3f961ce6-1a10-477a-b3b3-ba933fe70e28\n- media_object:creative_participants_roles"
                       :handler handle_get-meta-data-roles
                       :middleware [jqh/ring-wrap-add-media-resource
                                    jqh/ring-wrap-authorization-view]
                       :coercion reitit.coercion.schema/coercion
                       :parameters {:path {:media_entry_id s/Uuid
                                           :meta_key_id s/Str}}
                       :responses {200 {:description "Returns the meta-data role for the media-entry."

                                        ;:body s/Any


                                        ;{
                                        ; "meta_data": {
                                        ;               "created_by_id": "3d5e7743-14c2-46a1-8c23-19d0ccbbda15",
                                        ;               "media_entry_id": "3f961ce6-1a10-477a-b3b3-ba933fe70e28",
                                        ;               "collection_id": null,
                                        ;               "type": "MetaDatum::Roles",
                                        ;               "meta_key_id": "media_object:creative_participants_roles",
                                        ;               "string": null,
                                        ;               "id": "0c75558e-28fa-4bd7-81bb-ff5725b14442",
                                        ;               "meta_data_updated_at": "2020-03-15T21:30:13.617071Z",
                                        ;               "json": null,
                                        ;               "other_media_entry_id": null
                                        ;               },
                                        ; "roles_ids": [
                                        ;               null
                                        ;               ],
                                        ; "people_ids": [
                                        ;                "26308df5-54cc-4f6c-bbeb-3084b82af1ea"
                                        ;                ],
                                        ; "md_roles": [
                                        ;              {
                                        ;               "id": "f472180a-c01b-4998-abc2-f18ed8a60eef",
                                        ;               "meta_datum_id": "0c75558e-28fa-4bd7-81bb-ff5725b14442",
                                        ;               "person_id": "26308df5-54cc-4f6c-bbeb-3084b82af1ea",
                                        ;               "role_id": null,
                                        ;               "position": 0
                                        ;               }
                                        ;              ],
                                        ; "roles": [
                                        ;           null
                                        ;           ],
                                        ; "people": [
                                        ;            {
                                        ;             "institution": "zhdk.ch",
                                        ;             "institutional_id": "115038",
                                        ;             "description": null,
                                        ;             "first_name": "Michael",
                                        ;             "external_uris": [],
                                        ;             "identification_info": null,
                                        ;             "searchable": "Michael Koch ",
                                        ;             "updated_at": "2024-12-10T10:46:28.883941Z",
                                        ;             "id": "26308df5-54cc-4f6c-bbeb-3084b82af1ea",
                                        ;             "last_name": "Koch",
                                        ;             "admin_comment": null,
                                        ;             "pseudonym": null,
                                        ;             "created_at": "2011-08-31T11:36:38Z",
                                        ;             "subtype": "Person"
                                        ;             }
                                        ;            ]
                                        ; }

                                        :body {
                                               :meta_data s/Any
                                               :roles_ids s/Any
                                               :people_ids s/Any
                                               :md_roles s/Any
                                               :roles s/Any
                                               :people s/Any
                                               }


                                        }}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
