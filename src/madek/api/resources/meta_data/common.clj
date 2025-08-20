(ns madek.api.resources.meta_data.common
  (:require [cheshire.core]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.db.core :refer [builder-fn-options-default]]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.utils.helper :refer [convert-map-if-exist strip-prefixes-generic to-uuid]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [taoensso.timbre :refer [debug error info]]))

(def MD_TYPE_PEOPLE "MetaDatum::People")
(def MD_KEY_PEOPLE :people)
(def MD_KEY_PEOPLE_DATA :md_people)
(def MD_KEY_PEOPLE_IDS :people_ids)
(def MD_TYPE_KEYWORDS "MetaDatum::Keywords")
(def MD_KEY_KWS :keywords)
(def MD_KEY_KW_DATA :md_keywords)
(def MD_KEY_KW_IDS :keywords_ids)

(defn col-key-for-mr-type [mr]
  (let [mr-type (-> mr :type)]
    (if (= mr-type "Collection")
      :collection_id
      :media_entry_id)))

(defn assoc-media-resource-typed-id [mr ins-data]
  (assoc ins-data
         (col-key-for-mr-type mr)
         (-> mr :id)))

(defn sql-cls-upd-meta-data [stmt mr mk-id]
  (let [colomn (col-key-for-mr-type mr)
        md-sql (-> stmt
                   (sql/where [:and
                               [:= :meta_key_id mk-id]
                               [:= colomn (to-uuid (-> mr :id) colomn)]]))]
    md-sql))

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

(defn db-create-meta-data
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

(defn db-create-meta-data-keyword
  [db md-id kw-id user-id]
  (let [data {:meta_datum_id md-id
              :keyword_id kw-id
              :created_by_id user-id}
        sql-query (-> (sql/insert-into :meta_data_keywords)
                      (sql/values [data])
                      (sql/returning :*)
                      sql-format)
        result (jdbc/execute! db sql-query builder-fn-options-default)]
    (info "db-create-meta-data-keyword"
          "\nkw-data\n" data
          "\nresult\n" result)
    result))

(defn create_md_and_keyword
  [mr meta-key-id kw-id user-id tx]

  (try
    (catcher/with-logging {}
      (jdbc/with-transaction [tx tx]
        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
          ; already has meta-data
          (if-let [result (db-create-meta-data-keyword tx (:id meta-data) kw-id user-id)]
            {:meta_data meta-data
             MD_KEY_KW_DATA result}
            nil)

          ; create meta-data and md-kw
          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_KEYWORDS user-id)]
            (if-let [ip-result (db-create-meta-data-keyword tx (-> mdins-result :id) kw-id user-id)]
              {:meta_data mdins-result
               MD_KEY_KW_DATA ip-result}
              nil)
            nil))))
    (catch Exception _
      (error "Could not create md keyword" _)
      nil)))

(defn db-create-meta-data-people
  ([db md-id person-id user-id]
   (db-create-meta-data-people db md-id person-id nil user-id))
  ([db md-id person-id role-id user-id]
   (let [data {:meta_datum_id (to-uuid md-id)
               :person_id person-id
               :role_id role-id
               :created_by_id user-id}
         sql-query (-> (sql/insert-into :meta_data_people)
                       (sql/values [data])
                       (sql/returning :*)
                       sql-format)
         result (-> (jdbc/execute-one! db sql-query)
                    strip-prefixes-generic)]

     ;(info "db-create-meta-data-people" "\npeople-data\n" data "\nresult\n" result)
     result)))

(defn create_md_and_people
  [mr meta-key-id person-id role-id user-id tx]
  (try
    (catcher/with-logging {}
      (jdbc/with-transaction [tx tx]
        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
          ; already has meta-data
          (do (debug "create_md_and_people: already has meta-data")
              (if-let [result (db-create-meta-data-people tx (:id meta-data) person-id role-id user-id)]
                {:meta_data meta-data
                 MD_KEY_PEOPLE_DATA result}
                nil))

          ; create meta-data and md-people
          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_PEOPLE user-id)]
            (do (debug "create_md_and_people: creating new meta-data")
                (if-let [ip-result (db-create-meta-data-people tx (-> mdins-result :id str) person-id role-id user-id)]
                  {:meta_data mdins-result
                   MD_KEY_PEOPLE_DATA ip-result}
                  nil))
            nil))))
    (catch Exception _
      (error "Could not create md people" _)
      nil)))

(defn db-get-meta-data-keywords
  [md-id tx]
  (dbh/query-eq-find-all :meta_data_keywords :meta_datum_id md-id tx))
#_(let [query (-> (sd/build-query-base :meta_data_keywords :*)
                  (sql/merge-where [:= :meta_datum_id md-id])
                  (sql/merge-join :keywords [:= :keywords.id :meta_data_keywords.keyword_id])
                  (sql/order-by [:keywords.term :asc])
                  sql-format)]
    (info "db-get-meta-data-keywords:\n" query)
    (let [result (jdbc/query tx query)]
      (info "db-get-meta-data-keywords:\n" result)))

(defn db-get-meta-data-people
  [md-id tx]
  (dbh/query-eq-find-all :meta_data_people :meta_datum_id md-id tx))

(defn wrap-add-keyword [handler]
  (fn [request] (sd/req-find-data
                 request handler
                 :keyword_id
                 :keywords :id
                 :keyword
                 true)))

(defn wrap-add-person [handler]
  (fn [request] (sd/req-find-data
                 request handler
                 :person_id
                 :people :id
                 :person
                 true)))

(defn wrap-add-role [handler]
  (fn [request] (sd/req-find-data
                 request handler
                 :role_id
                 :roles :id
                 :role
                 true)))

(defn wrap-me-add-meta-data [handler]
  (fn [request] (sd/req-find-data2
                 request handler
                 :media_entry_id
                 :meta_key_id
                 :meta_data
                 :media_entry_id
                 :meta_key_id
                 :meta-data
                 false)))

(defn wrap-col-add-meta-data [handler]
  (fn [request] (sd/req-find-data2
                 request handler
                 :collection_id
                 :meta_key_id
                 :meta_data
                 :collection_id
                 :meta_key_id
                 :meta-data
                 false)))

(defn wrap-add-meta-key [handler]
  (fn [request] (sd/req-find-data
                 request handler
                 :meta_key_id
                 :meta-keys :id
                 :meta-key
                 true)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
