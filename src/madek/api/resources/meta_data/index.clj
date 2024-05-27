(ns madek.api.resources.meta-data.index
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.constants :as constants]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [next.jdbc :as jdbc]))

; TODO error if user-id is undefined (public)
(defn md-vocab-where-clause
  [user-id tx]
  (let [vocabulary-ids (permissions/accessible-vocabulary-ids user-id tx)]
    (if (empty? vocabulary-ids)
      [:= :vocabularies.enabled_for_public_view true]
      [:or
       [:= :vocabularies.enabled_for_public_view true]
       [:in :vocabularies.id vocabulary-ids]])))

(defn- base-query
  [user-id tx]
  ;(-> (sql/select :*)
  (-> (sql/select :meta_data.id
                  :meta_data.type
                  :meta_data.meta_key_id
                  :meta_data.media_entry_id
                  :meta_data.collection_id
                  :meta_data.string
                  :meta_data.json
                  :meta_data.other_media_entry_id
                  :meta_data.meta_data_updated_at)
      (sql/from :meta_data)
      (sql/where [:in :meta_data.type
                  constants/SUPPORTED_META_DATA_TYPES])
      ; TODO use in other md access
      (sql/join :meta_keys [:= :meta_data.meta_key_id :meta_keys.id])
      (sql/join :vocabularies [:= :meta_keys.vocabulary_id :vocabularies.id])
      (sql/where (md-vocab-where-clause user-id tx))

      (sql/order-by [:vocabularies.position :asc]
                    [:meta_keys.position :asc]
                    [:meta_data.id :asc])))

(defn- meta-data-query-for-media-entry [media-entry-id user-id tx]
  (-> (base-query user-id tx)
      (sql/where [:= :meta_data.media_entry_id media-entry-id])))

(defn- meta-data-query-for-collection [collection-id user-id tx]
  (-> (base-query user-id tx)
      (sql/where [:= :meta_data.collection_id collection-id])))

; TODO test with json
; TODO add query param meta-keys as json list of strings
(defn filter-meta-data-by-meta-key-ids [query request]
  (if-let [meta-keys (-> request :parameters :query :meta_keys jqh/try-as-json)]
    (do
      (when-not (seq? meta-keys)
        String (throw (ex-info (str "The value of the meta-keys parameter"
                                    " must be a json encoded list of strings.")
                               {:status 422})))
      (sql/where query [:in :meta_key_id meta-keys]))
    query))

(defn build-query [request base-query]
  (let [query (-> base-query
                  (dbh/build-query-ts-after (-> request :parameters :query) :updated_after "meta_data.meta_data_updated_at")
                  (filter-meta-data-by-meta-key-ids request)
                  sql-format)]
    ;(info "MD:build-query:\n " query)
    query))

(defn get-media-entry-meta-data [id user-id tx]
  (->> (meta-data-query-for-media-entry id user-id tx)
       (build-query nil)
       (jdbc/execute! tx)))

(defn get-collection-meta-data [id user-id tx]
  (let [mdq (sql-format (meta-data-query-for-collection id user-id tx))
        result (jdbc/execute! tx mdq)]
    ;(info "get-collection-meta-data:"
    ;              "\n col id: " id
    ;              "\n user id: " user-id
    ;              "\n query: " mdq
    ;              "\n result: " result)
    result))

;(defn get-collection-meta-data [id user-id tx]
;  (->> (meta-data-query-for-collection id user-id)
;       (build-query nil)
;       (jdbc/query tx)))

(defn get-meta-data [request media-resource tx]
  (let [user-id (-> request :authenticated-entity :id)]
    (when-let [id (:id media-resource)]
      (let [db-query (build-query request
                                  (case (:type media-resource)
                                    "MediaEntry" (meta-data-query-for-media-entry id user-id tx)
                                    "Collection" (meta-data-query-for-collection id user-id tx)))]
        ;(info "get-meta-data" "\n db-query \n" db-query)
        (jdbc/execute! tx db-query)))))

(defn get-index [request]
  ;(info "get-index" "\nmedia-resource\n" (:media-resource request))
  (when-let [media-resource (:media-resource request)]
    (when-let [meta-data (get-meta-data request media-resource (:tx request))]
      (let [data (conj
                  {:meta-data meta-data}
                  (case (:type media-resource)
                    "MediaEntry" {:media_entry_id (:id media-resource)}
                    "Collection" {:collection_id (:id media-resource)}))]
        (sd/response_ok data)))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
