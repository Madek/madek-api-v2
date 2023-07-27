(ns madek.api.resources.meta-data.index
  (:require
    [clojure.java.jdbc :as jdbc]
    ;[clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ;[madek.api.authorization :as authorization]
    [madek.api.constants :as constants]
    ;[madek.api.pagination :as pagination]
    [madek.api.resources.vocabularies.permissions :as permissions]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    [madek.api.utils.sql :as sql]
    [madek.api.resources.shared :as sd]
    ))

; TODO error if user-id is undefined (public)
(defn md-vocab-where-clause
  [user-id]
  (let [vocabulary-ids (permissions/accessible-vocabulary-ids user-id)]
    (if (empty? vocabulary-ids)
      [:= :vocabularies.enabled_for_public_view true]
      [:or
        [:= :vocabularies.enabled_for_public_view true]
        [:in :vocabularies.id vocabulary-ids]])))

(defn- base-query
  [user-id]
  ;(-> (sql/select :*)
  (-> (sql/select :meta_data.id
                  :meta_data.type
                  :meta_data.meta_key_id 
                  :meta_data.media_entry_id
                  :meta_data.collection_id
                  :meta_data.string
                  :meta_data.json
                  :meta_data.other_media_entry_id
                  :meta_data.meta_data_updated_at
                  )
      (sql/from :meta_data)
      (sql/merge-where [:in :meta_data.type
                        constants/SUPPORTED_META_DATA_TYPES])
      ; TODO use in other md access
      (sql/merge-join :meta_keys [:= :meta_data.meta_key_id :meta_keys.id])
      (sql/merge-join :vocabularies [:= :meta_keys.vocabulary_id :vocabularies.id])
      (sql/merge-where (md-vocab-where-clause user-id))

      (sql/order-by [:vocabularies.position :asc]
                    [:meta_keys.position :asc]
                    [:meta_data.id :asc])))

(defn- meta-data-query-for-media-entry [media-entry-id user-id]
  (-> (base-query user-id)
      (sql/merge-where [:= :meta_data.media_entry_id media-entry-id])))

(defn- meta-data-query-for-collection [collection-id user-id]
  (-> (base-query user-id)
      (sql/merge-where [:= :meta_data.collection_id collection-id])))


; TODO test with json
; TODO add query param meta-keys as json list of strings
(defn filter-meta-data-by-meta-key-ids [query request]
  (if-let [meta-keys (-> request :parameters :query :meta_keys sd/try-as-json)]
    (do
      (when-not (seq? meta-keys)
        String (throw (ex-info (str "The value of the meta-keys parameter"
                                    " must be a json encoded list of strings.")
                               {:status 422})))
      (sql/merge-where query [:in :meta_key_id meta-keys]))
    query))

(defn build-query [request base-query]
  (let [query (-> base-query
                  (sd/build-query-ts-after (-> request :parameters :query) :updated_after "meta_data.meta_data_updated_at")
                  (filter-meta-data-by-meta-key-ids request)
                  sql/format)]
    ;(logging/info "MD:build-query:\n " query)
    query))

(defn get-media-entry-meta-data [id user-id]
  (->> (meta-data-query-for-media-entry id user-id)
       (build-query nil)
       (jdbc/query (get-ds))))

(defn get-collection-meta-data [id user-id]
  (let [mdq (sql/format (meta-data-query-for-collection id user-id))
        result (jdbc/query (get-ds) mdq)] 
    ;(logging/info "get-collection-meta-data:"
    ;              "\n col id: " id
    ;              "\n user id: " user-id
    ;              "\n query: " mdq
    ;              "\n result: " result)
    result))

;(defn get-collection-meta-data [id user-id]
;  (->> (meta-data-query-for-collection id user-id)
;       (build-query nil)
;       (jdbc/query (get-ds))))

(defn get-meta-data [request media-resource]
  (let [user-id (-> request :authenticated-entity :id)]
    (when-let [id (:id media-resource)]
      (let [db-query (build-query request
                                  (case (:type media-resource)
                                    "MediaEntry" (meta-data-query-for-media-entry id user-id)
                                    "Collection" (meta-data-query-for-collection id user-id)))]
        ;(logging/info "get-meta-data" "\n db-query \n" db-query)
        (jdbc/query (get-ds) db-query)))))

(defn get-index [request]
  ;(logging/info "get-index" "\nmedia-resource\n" (:media-resource request))
  (when-let [media-resource (:media-resource request)]
    (when-let [meta-data (get-meta-data request media-resource)]

      {:body
       (conj
        {:meta-data meta-data}
        (case (:type media-resource)
          "MediaEntry" {:media_entry_id (:id media-resource)}
          "Collection" {:collection_id (:id media-resource)}))})))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
