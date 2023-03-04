(ns madek.api.resources.media-entries.index
  (:refer-clojure :exclude [str keyword])
  (:require [madek.api.utils.core :refer [str keyword]]
            [cheshire.core :as json])
  (:require
    [clojure.core.match :refer [match]]
    [clojure.java.jdbc :as jdbc]
    [clojure.set :refer [rename-keys]]
    [clojure.string :as str]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [madek.api.pagination :as pagination]
    [madek.api.resources.media-entries.advanced-filter :as advanced-filter]
    [madek.api.resources.media-entries.advanced-filter.permissions :as permissions :refer [filter-by-query-params]]
    [madek.api.resources.shared :as shared]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    ))

;### collection_id ############################################################

(defn- filter-by-collection-id [sqlmap {:keys [collection_id] :as query-params}]
  (if-not collection_id
    sqlmap
    (-> sqlmap
        (sql/merge-join [:collection_media_entry_arcs :arcs]
                        [:= :arcs.media_entry_id :media_entries.id])
        (sql/merge-where [:= :arcs.collection_id collection_id])
        (sql/merge-select
          [:arcs.created_at :arc_created_at]
          [:arcs.order :arc_order]
          [:arcs.position :arc_position]
          [:arcs.created_at :arc_created_at]
          [:arcs.updated_at :arc_updated_at]
          [:arcs.id :arc_id]))))


;### query ####################################################################

(def ^:private base-query
  (-> (sql/select [:media_entries.id :media_entry_id]
                  [:media_entries.created_at :media_entry_created_at])
      (sql/from :media_entries)))

(defn- order-by-media-entry-attribute [query [attribute order]]
  (let [order-by-arg (match [(keyword attribute) (keyword order)]
                            [:created_at :desc] [:media-entries.created_at :desc :nulls-last]
                            [:created_at _] [:media-entries.created_at]
                            [:edit_session_updated_at _] [:media_entries.edit_session_updated_at])]
    (sql/merge-order-by query order-by-arg)))

(defn- order-by-arc-attribute [query [attribute order]]
  (let [order-by-arg (match [(keyword attribute) (keyword order)]
                            [:order :desc] [:arcs.order :desc :nulls-last]
                            [:order _] [:arcs.order]
                            [:position :asc] [:arcs.position :asc]
                            [:position :desc] [:arcs.position :desc :nulls-last]
                            [:created_at :desc] [:arcs.created_at :desc :nulls-last]
                            [:created_at _] [:arcs.created_at])]
    (sql/merge-order-by query order-by-arg)))


(defn- order-by-meta-datum-text [query [meta-key-id order]]
  (let [from-name (-> meta-key-id
                      (clojure.string/replace #"\W+" "_")
                      clojure.string/lower-case
                      (#(str "meta-data-" %)))]
    (-> query
        (sql/merge-left-join [:meta_data from-name]
                             [:= (keyword (str from-name".meta_key_id")) meta-key-id])
        (sql/merge-order-by [(-> from-name (str ".string") keyword)
                             (case (keyword order)
                               :asc :asc
                               :desc :desc
                               :asc)
                             :nulls-last])
        (sql/merge-where [:= (keyword (str from-name".media_entry_id")) :media_entries.id]))))

(defn- order-reducer [query [scope & more]]
  (case scope
    "media_entry" (order-by-media-entry-attribute query more)
    "arc" (order-by-arc-attribute query more)
    "MetaDatum::Text" (order-by-meta-datum-text query more)))

(defn- order-by-title [query order]
  (let [direction (-> (str/split order #"_") (last))]
    (reduce order-reducer [query ["MetaDatum::Text" "madek_core:title" direction]])))

(defn- find-collection-default-sorting [collection-id]
  (let [query {:select [:sorting]
               :from [:collections]
               :where [:= :collections.id collection-id]}]
    (:sorting (first (jdbc/query (rdbms/get-ds) (-> query sql/format))))))

(defn- handle-missing-collection-id [collection-id code-to-run]
  (if (or (not collection-id) (nil? collection-id))
    (throw (ex-info "collection_id param must be given" {:status 422}))
    code-to-run))

(defn- order-by-string [query order collection-id]
  (case order
    "asc" (sql/order-by query [:media_entries.created-at (keyword order)])
    "desc" (sql/order-by query [:media_entries.created-at (keyword order)])
    "title_asc" (order-by-title query order)
    "title_desc" (order-by-title query order)
    "last_change" (order-by-media-entry-attribute query [:edit_session_updated_at :asc])
    "manual_asc" (handle-missing-collection-id collection-id (order-by-arc-attribute query [:position :asc]))
    "manual_desc" (handle-missing-collection-id collection-id (order-by-arc-attribute query [:position :desc]))
  )
)

(def ^:private available-sortings '("desc" "asc" "title_asc" "title_desc"
                                    "last_change" "manual_asc" "manual_desc"))

(defn- default-order [query]
  (sql/order-by query [:media_entries.created-at :asc]))

(defn- order-by-collection-sorting [query collection-id]
  (handle-missing-collection-id collection-id
    (if-let [sorting (find-collection-default-sorting collection-id)]
      (let [prepared-sorting (->> (str/split (str/replace sorting "created_at " "") #" ") (str/join "_") str/lower-case)]
        (order-by-string query prepared-sorting collection-id))
      (sql/order-by query [:media_entries.created-at :asc]))))

(def ^:private not-allowed-order-param-message
  (str "only the following values are allowed as order parameter: "
       (str/join ", " available-sortings) " and stored_in_collection"))

(defn- set-order [query query-params]
  (-> (let [order (-> query-params :order)
            collection-id (-> query-params :collection_id)]
        (logging/info "set-order" "\norder\n" order )
        (cond
          (nil? order) (default-order query)
          (string? order) (cond
                            (some #(= order %) available-sortings) (order-by-string query order collection-id)
                            (= order "stored_in_collection") (order-by-collection-sorting query collection-id)
                            :else (throw (ex-info not-allowed-order-param-message
                                                  {:status 422})))
          (seq? order)(reduce order-reducer query order)
          :else (default-order query))
      )
      (sql/merge-order-by :media_entries.id)))

; test {"meta_data":[{"key":"any","match":"nitai"}]}
; test2 {"meta_data":[{"key":"test:string","match":"par tial"},{"key":"filter:7cq5ila0xxqlrc7wod2g","value":"3768574c-d4d8-4fac-ad73-0e2dbd4cc443"},{"key":"test:licenses"},{"not_key":"filter:1vq1h2t25y92yq8ojg11"},{"key":"test:people","value":"9f70e42c-8d01-4b2b-8f10-2719921797fc"},{"key":"any","type":"MetaDatum::Keywords","value":"694b858d-e8eb-4e51-8bee-f55fd8a0491b"}],"media_files":[{"key":"content_type","value":"image/jpeg"},{"key":"uploader_id","value":"7bf54b03-42e5-4dc8-8a36-309ca9b1563f"},{"key":"extension","value":"jpg"}],"permissions":[{"key":"responsible_user","value":"935e9257-b7a7-4783-bb11-553907ca67f6"},{"key":"public","value":"true"},{"key":"entrusted_to_user","value":"73fbb710-eedc-481d-a411-692705decd09"},{"key":"entrusted_to_group","value":"e8b962f6-df73-4b6f-b2b6-3f71230cd0aa"}]}

(defn- build-query [request]
  (let [;query-params (or (:query-params request) (-> request :parameters :query))
        query-params (:query-params request)
        authenticated-entity (:authenticated-entity request)]
    (logging/info "build-query" "\nquery-params\n" query-params)
    (I> identity-with-logging
        base-query
        (set-order query-params)
        (filter-by-collection-id query-params)
        (permissions/filter-by-query-params query-params
                                            authenticated-entity)
        (advanced-filter/filter-by (:filter_by query-params))
        (pagination/add-offset-for-honeysql query-params)
        sql/format)))

(defn- query-index-resources [request]
  (jdbc/query (rdbms/get-ds) (build-query request)))


;### index ####################################################################

(defn get-index [{{collection-id :collection_id} :query-params :as request}]
  (catcher/with-logging {}
    (try
      (let [data (query-index-resources request)]
        {:body
         (merge
           {:media-entries (->> data
                                (map #(select-keys % [:media_entry_id :media_entry_created_at]))
                                (map #(rename-keys % {:media_entry_id :id
                                                      :media_entry_created_at :created_at})))}
           (when collection-id
             {:arcs (->> data
                         (map #(select-keys % [:arc_id :media_entry_id :arc_order :arc_position :arc_created_at :arc_updated_at]))
                         (map #(rename-keys % {:arc_id :id
                                               :arc_order :order
                                               :arc_position :position
                                               :arc_created_at :created_at
                                               :arc_updated_at :updated_at})))}))})
      (catch Exception e (merge (ex-data e) {:body {:message (.getMessage e)}}))
    )
  )
)

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'filter-by-permissions)
;(debug/wrap-with-log-debug #'set-order)
