(ns madek.api.resources.meta_data.put
  (:require [cheshire.core]
            [cheshire.core :as cheshire]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.db.core :refer [builder-fn-options-default]]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.helper :refer [convert-map-if-exist to-uuid]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [schema.core :as s]
            [taoensso.timbre :refer [error info]]))

(defn- col-key-for-mr-type [mr]
  (let [mr-type (-> mr :type)]
    (if (= mr-type "Collection")
      :collection_id
      :media_entry_id)))

(defn- assoc-media-resource-typed-id [mr ins-data]
  (assoc ins-data
         (col-key-for-mr-type mr)
         (-> mr :id)))



(defn- sql-cls-upd-meta-data-typed-id [stmt mr mk-id md-type]
  (let [colomn (col-key-for-mr-type mr)

        md-sql (-> stmt
                   (sql/where [:and
                               [:= :meta_key_id mk-id]
                               [:= :type md-type]
                               [:= colomn (to-uuid (-> mr :id) colomn)]]))]
    md-sql))

(defn- fabric-meta-data
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



(defn- handle_update-meta-data-text-base
  [req md-type upd-data]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            sql-query (-> (sql/update :meta_data)
                          (sql/set (convert-map-if-exist upd-data))
                          (sql-cls-upd-meta-data-typed-id mr meta-key-id md-type)
                          sql-format)
            tx (:tx req)
            upd-result (jdbc/execute-one! tx sql-query)
            result-data (db-get-meta-data mr meta-key-id md-type tx)]

        (sd/logwrite req (str "handle_update-meta-data-text-base:"
                              " mr-id: " (:id mr)
                                         " mr-type: " (:type mr)
                                         " md-type: " md-type
                                         " meta-key-id: " meta-key-id
                                         " upd-result: " upd-result))

        (if (= 1 (::jdbc/update-count upd-result))
          (sd/response_ok result-data)
          (sd/response_failed {:message "Failed to update meta data text base"} 406))))
    (catch Exception ex (sd/response_exception ex))))


(defn handle_update-meta-data-text
  [req]
  (let [text-data (-> req :parameters :body :string)
        upd-data {:string text-data}
        md-type "MetaDatum::Text"]

    (info "handle_update-meta-data-text" "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))


(defn handle_update-meta-data-text-date
  [req]
  (let [text-data (-> req :parameters :body :string)
        upd-data {:string text-data}
        ; TODO multi line ? or other params
        md-type "MetaDatum::TextDate"]
    (info "handle_update-meta-data-text-date" "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))



(defn handle_update-meta-data-json
  [req]
  (let [text-data (-> req :parameters :body :json)
        json-parsed (cheshire/parse-string text-data)
        ;upd-data {:json json-parsed}
        upd-data {:json (with-meta json-parsed {:pgtype "jsonb"})}
        md-type "MetaDatum::JSON"]
    (info "handle_update-meta-data-json"
      "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))

;(defn- db-create-meta-data-keyword
;  [db md-id kw-id user-id]
;  (let [data {:meta_datum_id md-id
;              :keyword_id kw-id
;              :created_by_id user-id}
;        sql-query (-> (sql/insert-into :meta_data_keywords)
;                      (sql/values [data])
;                      (sql/returning :*)
;                      sql-format)
;        result (jdbc/execute! db sql-query builder-fn-options-default)]
;    (info "db-create-meta-data-keyword"
;      "\nkw-data\n" data
;      "\nresult\n" result)
;    result))



(def MD_TYPE_KEYWORDS "MetaDatum::Keywords")
(def MD_KEY_KWS :keywords)
(def MD_KEY_KW_DATA :md_keywords)
(def MD_KEY_KW_IDS :keywords_ids)

;(defn create_md_and_keyword
;  [mr meta-key-id kw-id user-id tx]
;
;  (try
;    (catcher/with-logging {}
;      (jdbc/with-transaction [tx tx]
;        (let [meta-data (db-get-meta-data mr meta-key-id nil tx)])
;        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
;          ; already has meta-data
;          (if-let [result (db-create-meta-data-keyword tx (:id meta-data) kw-id user-id)]
;            {:meta_data meta-data
;             MD_KEY_KW_DATA result}
;            nil)
;
;          ; create meta-data and md-kw
;          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_KEYWORDS user-id)]
;            (if-let [ip-result (db-create-meta-data-keyword tx (-> mdins-result :id) kw-id user-id)]
;              {:meta_data mdins-result
;               MD_KEY_KW_DATA ip-result}
;              nil)
;            nil))))
;    (catch Exception _
;      (error "Could not create md keyword" _)
;      nil)))
;
;
;(defn- db-create-meta-data-people
;  [db md-id person-id user-id]
;  (let [data {:meta_datum_id (to-uuid md-id)
;              :person_id person-id
;              :created_by_id user-id}
;        sql-query (-> (sql/insert-into :meta_data_people)
;                      (sql/values [data])
;                      (sql/returning :*)
;                      sql-format)
;        result (jdbc/execute-one! db sql-query)]
;
;    ;(info "db-create-meta-data-people" "\npeople-data\n" data "\nresult\n" result)
;    result))

(def MD_TYPE_PEOPLE "MetaDatum::People")
(def MD_KEY_PEOPLE :people)
(def MD_KEY_PEOPLE_DATA :md_people)
(def MD_KEY_PEOPLE_IDS :people_ids)

;(defn create_md_and_people
;  [mr meta-key-id person-id user-id tx]
;  (try
;    (catcher/with-logging {}
;      (jdbc/with-transaction [tx tx]
;        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
;          ; already has meta-data
;          (do
;            (if-let [result (db-create-meta-data-people tx (:id meta-data) person-id user-id)]
;              (do
;                {:meta_data meta-data
;                 MD_KEY_PEOPLE_DATA result})
;              nil))
;
;          ; create meta-data and md-people
;          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_PEOPLE user-id)]
;            (do
;              (if-let [ip-result (db-create-meta-data-people tx (-> mdins-result :id str) person-id user-id)]
;                (do
;                  {:meta_data mdins-result
;                   MD_KEY_PEOPLE_DATA ip-result})
;                nil))
;            nil))))
;    (catch Exception _
;      (error "Could not create md people" _)
;      nil)))



;(defn wrap-add-keyword [handler]
;  (fn [request] (sd/req-find-data
;                  request handler
;                  :keyword_id
;                  :keywords :id
;                  :keyword
;                  true)))
;
;
;
;(defn wrap-add-meta-key [handler]
;  (fn [request] (sd/req-find-data
;                  request handler
;                  :meta_key_id
;                  :meta-keys :id
;                  :meta-key
;                  true)))


(def media_entry.meta_key_id.json {:summary "Update meta-data json for media-entry"
                                   :handler handle_update-meta-data-json
                                   :middleware [sd/ring-wrap-add-media-resource
                                                sd/ring-wrap-authorization-edit-metadata]
                                   :coercion reitit.coercion.schema/coercion
                                   :parameters {:path {:media_entry_id s/Uuid
                                                       :meta_key_id s/Str}
                                                :body {:json s/Any}}
                                   :responses {200 {:body s/Any}}})


(def meta_key_id.text-date {:summary "Update meta-data text-date for media-entry"
                            :handler handle_update-meta-data-text-date
                            :middleware [sd/ring-wrap-add-media-resource
                                         sd/ring-wrap-authorization-edit-metadata]
                            :coercion reitit.coercion.schema/coercion
                            :parameters {:path {:media_entry_id s/Uuid
                                                :meta_key_id s/Str}
                                         :body {:string s/Str}}
                            :responses {200 {:body s/Any}}})


(def media_entry.meta_key_id.text {:summary "Update meta-data text for media-entry"
                                   :handler handle_update-meta-data-text
                                   :middleware [sd/ring-wrap-add-media-resource
                                                sd/ring-wrap-authorization-edit-metadata]
                                   :coercion reitit.coercion.schema/coercion
                                   :parameters {:path {:media_entry_id s/Uuid
                                                       :meta_key_id s/Str}
                                                :body {:string s/Str}}
                                   :responses {200 {:body s/Any}}})


(def collection.meta_key_id.json {:summary "Update meta-data json for collection."
                                  :handler handle_update-meta-data-json
                                  :middleware [sd/ring-wrap-add-media-resource
                                               sd/ring-wrap-authorization-edit-metadata]
                                  :coercion reitit.coercion.schema/coercion
                                  :parameters {:path {:collection_id s/Uuid
                                                      :meta_key_id s/Str}
                                               :body {:json s/Any}}
                                  :responses {200 {:body s/Any}}})

(def text.meta_key_id.text-date {:summary "Update meta-data text-date for collection."
                                 :handler handle_update-meta-data-text-date
                                 :middleware [sd/ring-wrap-add-media-resource
                                              sd/ring-wrap-authorization-edit-metadata]
                                 :coercion reitit.coercion.schema/coercion
                                 :parameters {:path {:collection_id s/Uuid
                                                     :meta_key_id s/Str}
                                              :body {:string s/Str}}
                                 :responses {200 {:body s/Any}}})


(def meta_key_id.text {:summary "Update meta-data text for collection."
                       :handler handle_update-meta-data-text
                       :middleware [sd/ring-wrap-add-media-resource
                                    sd/ring-wrap-authorization-edit-metadata]
                       :accept "application/json"
                       :content-type "application/json"
                       :swagger {:produces "application/json" :consumes "application/json"}
                       :coercion reitit.coercion.schema/coercion
                       :parameters {:path {:collection_id s/Uuid
                                           :meta_key_id s/Str}
                                    :body {:string s/Str}}
                       :responses {200 {:body s/Any}}})