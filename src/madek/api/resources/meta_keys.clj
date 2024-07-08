(ns madek.api.resources.meta-keys
  (:require
   [clojure.java.io :as io]
   [clojure.spec.alpha :as sa]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.meta-keys.index :as mkindex]
   [madek.api.resources.meta-keys.meta-key :as mk]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.coercion.spec-alpha-definition-any :as sp-any]
   [madek.api.utils.coercion.spec-alpha-definition-nil :as sp-nil]
   [madek.api.utils.coercion.spec-alpha-definition-str :as sp-str]
   [madek.api.utils.helper :refer [cast-to-hstore convert-map-if-exist cast-to-hstore convert-map-if-exist
                                   replace-java-hashmaps mslurp replace-java-hashmaps v]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.spec]
   [schema.core :as s]
   [spec-tools.core :as st]
   [taoensso.timbre :refer [info]]))

(defn adm-export-meta-key [meta-key]
  (-> meta-key
      (assoc :hints (sd/transform_ml (:hints meta-key))
             :labels (sd/transform_ml (:labels meta-key))
             :descriptions (sd/transform_ml (:descriptions meta-key))
             :documentation_urls (sd/transform_ml (:documentation_urls meta-key)))))

(defn adm-export-meta-key-list [meta-key]
  (-> meta-key
      (assoc :hints (sd/transform_ml (:hints meta-key))
             :labels (sd/transform_ml (:labels meta-key))
             :descriptions (sd/transform_ml (:descriptions meta-key))
             :documentation_urls (sd/transform_ml (:documentation_urls meta-key))

             :labels_2 (sd/transform_ml (:labels_2 meta-key))
             :descriptions_2 (sd/transform_ml (:descriptions_2 meta-key)))))

(defn user-export-meta-key [meta-key]
  (-> meta-key
      (dissoc :admin_comment :admin_comment_2)
      (assoc :hints (sd/transform_ml (:hints meta-key))
             :labels (sd/transform_ml (:labels meta-key))
             :descriptions (sd/transform_ml (:descriptions meta-key))
             :documentation_urls (sd/transform_ml (:documentation_urls meta-key)))))

(defn user-export-meta-key-list [meta-key]
  (-> meta-key
      (dissoc :admin_comment :admin_comment_2)
      (assoc :hints (sd/transform_ml (:hints meta-key))
             :labels (sd/transform_ml (:labels meta-key))
             :descriptions (sd/transform_ml (:descriptions meta-key))
             :documentation_urls (sd/transform_ml (:documentation_urls meta-key))
             :labels_2 (sd/transform_ml (:labels_2 meta-key))
             :descriptions_2 (sd/transform_ml (:descriptions_2 meta-key)))))

(defn handle_adm-query-meta-keys [req]
  (let [db-result (mkindex/db-query-meta-keys req)
        result (map adm-export-meta-key-list db-result)]
    (sd/response_ok {:meta-keys result}))) ;; TODO: add headers.x-total-count?

(defn handle_usr-query-meta-keys [req]
  (let [db-result (mkindex/db-query-meta-keys req)
        result (map user-export-meta-key-list db-result)]
    (sd/response_ok {:meta-keys result})))

(defn handle_adm-get-meta-key [req]
  (let [mk (-> req :meta_key)
        tx (:tx req)
        result (mk/include-io-mappings
                (adm-export-meta-key mk) (:id mk) tx)]
    (sd/response_ok result)))

(defn handle_usr-get-meta-key [req]
  (let [mk (-> req :meta_key)
        tx (:tx req)
        result (mk/include-io-mappings
                (user-export-meta-key mk) (:id mk) tx)]
    (sd/response_ok result)))

(defn handle_create_meta-key [req]
  (let [data (-> req :parameters :body)
        tx (:tx req)
        sql-query (-> (sql/insert-into :meta_keys)
                      (sql/values [(convert-map-if-exist (cast-to-hstore data))])
                      (sql/returning :*)
                      sql-format)
        db-result (jdbc/execute-one! tx sql-query)
        db-result (replace-java-hashmaps db-result)]
    (sd/response_ok db-result)))

(defn handle_update_meta-key [req]
  (let [data (-> req :parameters :body)
        id (-> req :path-params :id)
        dwid (assoc data :id id)
        tx (:tx req)
        dwid (convert-map-if-exist (cast-to-hstore dwid))
        sql-query (-> (sql/update :meta_keys)
                      (sql/set dwid)
                      (sql/returning :*)
                      (sql/where [:= :id id])
                      sql-format)
        db-result (jdbc/execute-one! tx sql-query)
        db-result (replace-java-hashmaps db-result)]

    (info "handle_update_meta-key:"
          "\nid: " id
          "\ndwid\n" dwid)

    (if db-result
      (sd/response_ok db-result)
      (sd/response_failed "Could not update meta_key." 406))))

(defn handle_delete_meta-key [req]
  (let [id (-> req :path-params :id)
        tx (:tx req)
        sql-query (-> (sql/delete-from :meta_keys)
                      (sql/where [:= :id id])
                      (sql/returning :*)
                      sql-format)
        db-result (jdbc/execute-one! tx sql-query)]

    (if db-result
      (sd/response_ok db-result)
      (sd/response_failed "Could not delete meta-key." 406))))

(def schema_create-meta-key
  {:id s/Str
   :is_extensible_list s/Bool
   :meta_datum_object_type (s/enum "MetaDatum::Text"
                                   "MetaDatum::TextDate"
                                   "MetaDatum::JSON"
                                   "MetaDatum::Keywords"
                                   "MetaDatum::People"
                                   "MetaDatum::Roles")
   (s/optional-key :keywords_alphabetical_order) s/Bool
   (s/optional-key :position) s/Int
   :is_enabled_for_media_entries s/Bool
   :is_enabled_for_collections s/Bool
   :vocabulary_id s/Str

   (s/optional-key :allowed_people_subtypes) [(s/enum "People" "PeopleGroup")] ; TODO check more people subtypes?!?
   (s/optional-key :text_type) s/Str
   (s/optional-key :allowed_rdf_class) (s/maybe s/Str)

   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
   (s/optional-key :hints) (s/maybe sd/schema_ml_list)
   (s/optional-key :documentation_urls) (s/maybe sd/schema_ml_list)

   (s/optional-key :admin_comment) (s/maybe s/Str)})

(sa/def ::meta-query-def (sa/keys :opt-un [::sp/is_extensible_list ::sp/keywords_alphabetical_order ::sp/position
                                           ::sp/is_enabled_for_media_entries ::sp/is_enabled_for_collections ::sp/vocabulary_id
                                           ::sp/allowed_people_subtypes ::sp-str/text_type ::sp-nil/allowed_rdf_class
                                           ::sp-nil/labels ::sp-nil/descriptions ::sp-nil/hints ::sp-nil/documentation_urls
                                           ::sp-nil/admin_comment]))

(sa/def ::schema_update-meta-key (sa/keys :req-un [::sp-str/id]))

(sa/def ::schema_export-meta-key-usr
  (sa/keys
   :req-un [::sp-str/id ::sp/vocabulary_id
            ::sp-any/labels ::sp-any/descriptions ::sp-any/hints ::sp-any/documentation_urls]
   :opt-un [::sp/is_extensible_list ::sp/meta_datum_object_type ::sp/keywords_alphabetical_order ::sp/position
            ::sp/is_enabled_for_media_entries ::sp/is_enabled_for_collections
            ::sp/allowed_people_subtypes ::sp/text_type ::sp-nil/allowed_rdf_class
            ::sp/io_mappings
            ::sp/is_enabled_for_public_use ::sp/is_enabled_for_public_view ::sp/position_2
            ::sp/labels_2 ::sp/descriptions_2 ::sp/id_2]))

(sa/def ::schema_export-meta-key-adm
  (sa/keys
   :req-un [::sp-str/id ::sp/vocabulary_id
            ::sp/admin_comment
            ::sp-any/labels ::sp-any/descriptions ::sp-any/hints ::sp-any/documentation_urls]
   :opt-un [::sp/is_extensible_list ::sp/meta_datum_object_type ::sp/keywords_alphabetical_order ::sp/position
            ::sp/is_enabled_for_media_entries ::sp/is_enabled_for_collections
            ::sp/allowed_people_subtypes ::sp/text_type ::sp-nil/allowed_rdf_class
            ::sp/io_mappings
            ::sp/is_enabled_for_public_use ::sp/is_enabled_for_public_view ::sp/position_2
            ::sp/labels_2 ::sp/descriptions_2 ::sp/id_2 ::sp/admin_comment_2]))

(sa/def ::meta-query-def (sa/keys :opt-un [::sp/id ::sp/vocabulary_id ::sp/meta_datum_object_type
                                           ::sp/is_enabled_for_collections ::sp/is_enabled_for_media_entries ::sp/scope ::sp/page ::sp/size]))

(sa/def ::meta-keys-id-query-def (sa/keys :req-un [::sp-str/id]))

(sa/def :meta-response-adm-def/meta-keys (st/spec {:spec (sa/coll-of ::schema_export-meta-key-adm)
                                                   :description "A list of meta-keys"}))

(sa/def ::meta-keys-id-response-adm-def (sa/keys :req-un [:meta-response-adm-def/meta-keys]))

(sa/def :meta-response-usr-def/meta-keys (st/spec {:spec (sa/coll-of ::schema_export-meta-key-usr)
                                                   :description "A list of meta-keys"}))

(sa/def ::meta-keys-id-response-usr-def (sa/keys :req-un [:meta-response-usr-def/meta-keys]))

(defn wwrap-find-meta_key [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data-new request handler
                                        param
                                        :meta_keys colname
                                        :meta_key send404))))

(def admin-routes
  ["/"
   {:openapi {:tags ["admin/meta-keys"] :security [{"auth" []}]}}
   ["meta-keys"
    {:get {:summary (sd/sum_adm "Get all meta-key ids")
           :description "Get list of meta-key ids. Paging is used as you get a limit of 100 entries."
           :handler handle_adm-query-meta-keys
           :middleware [wrap-authorize-admin!]
           ; FIXME: returns vocabulary.id instead of meta-keys.id ??
           :parameters {:query ::meta-query-def}
           :content-type "application/json"
           :coercion reitit.coercion.spec/coercion
           :responses {200 {:description "Meta-Keys-Object that contians list of meta-key-entries OR empty list"
                            :schema ::meta-keys-id-response-adm-def}}}

     :post {:summary (sd/sum_adm "Create meta-key.")
            :handler handle_create_meta-key
            :middleware [wrap-authorize-admin!]
            :description (mslurp (io/resource "md/meta-key-post.md"))
            :parameters {:body schema_create-meta-key}
            :content-type "application/json"
            :coercion reitit.coercion.schema/coercion
            :responses {200 {:description "Returns the created meta-key."
                             :schema schema_create-meta-key}
                        404 {:description "Duplicate key error"
                             :schema s/Str
                             :examples {"application/json" {:msg "ERROR: duplicate key value violates unique constraint \\\"meta_keys_pkey\\\"\\n  Detail: Key (id)=(copyright:test_me_now31) already exists."}}}
                        500 {:description "Internal Server Error"
                             :schema s/Str
                             :examples {"application/json" {:msg "ERROR: new row for relation \"meta_keys\" violates check constraint \"meta_key_id_chars\"\n  Detail: Failing row contains (copyright-test_me_now10, t, MetaDatum::TextDate, t, 0, t, t, copyright, string, {People}, line, Keyword, \"de\"=>\"string\", \"en\"=>\"string\", \"de\"=>\"string\", \"en\"=>\"string\", \"de\"=>\"string\", \"en\"=>\"string\", \"de\"=>\"string\", \"en\"=>\"string\")."}}}
                        406 {:description "Creation failed"
                             :schema s/Any}}}}]

   ["meta-keys/:id"
    {:get {:summary (sd/sum_adm "Get meta-key by id")
           :description "Get meta-key by id. Returns 404, if no such meta-key exists."
           :content-type "application/json"
           :accept "application/json"
           :middleware [wrap-authorize-admin!
                        (jqh/wrap-check-valid-meta-key-new :id)
                        (wwrap-find-meta_key :id :id true)]
           :handler handle_adm-get-meta-key
           :coercion reitit.coercion.spec/coercion
           :parameters {:path ::meta-keys-id-query-def}
           :responses {200 {:description "Returns the meta-key."
                            :schema ::schema_export-meta-key-adm}
                       404 {:description "No entry found for the given id"
                            :schema map?}
                       422 {:description "Wrong format"
                            :body map?
                            :examples {"application/json" {:message "Wrong meta_key_id format! See documentation. (fdas)"}}}}}

     :put {:summary (sd/sum_adm "Update meta-key.")
           :handler handle_update_meta-key
           :content-type "application/json"
           :accept "application/json"
           :description (mslurp (io/resource "md/meta-key-put.md"))
           :middleware [wrap-authorize-admin!
                        (jqh/wrap-check-valid-meta-key-new :id)
                        (wwrap-find-meta_key :id :id true)]
           :coercion reitit.coercion.spec/coercion
           :parameters {:path ::meta-keys-id-query-def
                        :body ::schema_update-meta-key}
           :responses {200 {:description "Returns the updated meta-key."
                            :body ::schema_export-meta-key-adm}
                       406 {:description "Update failed"
                            :schema string?
                            :examples {"application/json" {:message "Could not update meta_key."}}}}}

     :delete {:summary (sd/sum_adm "Delete meta-key.")
              :handler handle_delete_meta-key
              :middleware [(jqh/wrap-check-valid-meta-key-new :id)
                           (wwrap-find-meta_key :id :id true)]
              :coercion reitit.coercion.spec/coercion
              :parameters {:path ::meta-keys-id-query-def}
              :responses {200 {:description "Returns the deleted meta-key."
                               :body ::schema_export-meta-key-adm}
                          406 {:description "Entry not found"
                               :schema string?
                               :examples {"application/json" {:message "No such entity in :meta_keys as :id with copyright:test_me_now22"}}}
                          422 {:description "Wrong format"
                               :body any?}}}}]])

; TODO tests
(def query-routes
  ["/"
   {:openapi {:tags ["meta-keys"]}}
   ["meta-keys"
    {:get {:summary (sd/sum_usr_pub "Get all meta-key ids")
           :description "Get list of meta-key ids. Paging is used as you get a limit of 100 entries."
           :handler handle_usr-query-meta-keys
           :parameters {:query sp/schema_pagination_opt}
           :content-type "application/json"
           :coercion reitit.coercion.spec/coercion
           :responses {200 {:description "Meta-Keys-Object that contians list of meta-key-entries OR empty list"
                            :body ::meta-keys-id-response-usr-def}}}}]

   ["meta-keys/:id"
    {:get {:summary (sd/sum_usr_pub (v "Get meta-key by id"))
           :description "Get meta-key by id. Returns 404, if no such meta-key exists."
           :content-type "application/json"
           :accept "application/json"
           :handler handle_usr-get-meta-key
           :middleware [(jqh/wrap-check-valid-meta-key-new :id)
                        (wwrap-find-meta_key :id :id true)]
           :coercion reitit.coercion.spec/coercion
           :parameters {:path ::meta-keys-id-query-def}
           :responses {200 {:description "Returns the meta-key."
                            :body ::schema_export-meta-key-usr}
                       404 {:description "No entry found for the given id"
                            :body map?
                            :examples {"application/json" {:message "No such entity in :meta_keys as :id with not-existing:key"}}}
                       422 {:description "Wrong format"
                            :body map?
                            :examples {"application/json" {:message "Wrong meta_key_id format! See documentation. (fdas)"}}}}}}]])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
