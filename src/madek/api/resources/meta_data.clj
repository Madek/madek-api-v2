(ns madek.api.resources.meta-data
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

(defn- col-key-for-mr-type [mr]
  (let [mr-type (-> mr :type)]
    (if (= mr-type "Collection")
      :collection_id
      :media_entry_id)))

(defn- assoc-media-resource-typed-id [mr ins-data]
  (assoc ins-data
         (col-key-for-mr-type mr)
         (-> mr :id)))

(defn- sql-cls-upd-meta-data [stmt mr mk-id]
  (let [colomn (col-key-for-mr-type mr)
        md-sql (-> stmt
                   (sql/where [:and
                               [:= :meta_key_id mk-id]
                               [:= colomn (to-uuid (-> mr :id) colomn)]]))]
    md-sql))

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

(defn- handle-delete-meta-data [req]
  (let [mr (-> req :media-resource)
        meta-data (-> req :meta-data)
        meta-key-id (:meta_key_id meta-data)
        sql-query (-> (sql/delete-from :meta_data)
                      (sql-cls-upd-meta-data mr meta-key-id)
                      sql-format)
        del-result (jdbc/execute-one! (:tx req) sql-query)]
    (if (= 1 (::jdbc/update-count del-result))

      (sd/response_ok meta-data)
      (sd/response_failed "Could not delete meta_data." 406))))

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

; TODO tests, response coercion
(defn handle_create-meta-data-text
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            user-id (-> req :authenticated-entity :id str)
            meta-key-id (-> req :parameters :path :meta_key_id)
            text-data (-> req :parameters :body :string)
            md-type "MetaDatum::Text"
            tx (:tx req)
            mdnew {:string text-data}
            ins-result (db-create-meta-data tx mr meta-key-id md-type user-id mdnew)]

        (sd/logwrite req (str "handle_create-meta-data-text"
                              " mr-id: " (:id mr)
                              " meta-key-id: " meta-key-id
                              " ins-result: " ins-result))

        (if (= md-type (:type ins-result))
          (sd/response_ok ins-result)
          (sd/response_failed {:message "Failed to add meta data text"} 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-meta-data-text
  [req]
  (let [text-data (-> req :parameters :body :string)
        upd-data {:string text-data}
        md-type "MetaDatum::Text"]

    (info "handle_update-meta-data-text" "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))

; TODO tests, response coercion
(defn handle_create-meta-data-text-date
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            user-id (-> req :authenticated-entity :id str)
            meta-key-id (-> req :parameters :path :meta_key_id)
            text-data (-> req :parameters :body :string)
            md-type "MetaDatum::TextDate"
            mdnew {:string text-data}
            ins-result (db-create-meta-data (:tx req) mr meta-key-id md-type user-id mdnew)]

        (sd/logwrite req (str "handle_create-meta-data-text-date:"
                              " mr-id: " (:id mr)
                              " meta-key-id: " meta-key-id
                              " ins-result: " ins-result))

        (if (= md-type (:type ins-result))
          (sd/response_ok ins-result)
          (sd/response_failed {:message "Failed to add meta data text-date"} 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-meta-data-text-date
  [req]
  (let [text-data (-> req :parameters :body :string)
        upd-data {:string text-data}
        ; TODO multi line ? or other params
        md-type "MetaDatum::TextDate"]
    (info "handle_update-meta-data-text-date" "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))

; TODO tests, response coercion
(defn handle_create-meta-data-json
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            user-id (-> req :authenticated-entity :id str)
            meta-key-id (-> req :parameters :path :meta_key_id)
            json-data (-> req :parameters :body :json)
            json-parsed (cheshire/parse-string json-data)
            md-type "MetaDatum::JSON"
            ;mdnew {:json json-parsed}
            mdnew {:json (with-meta json-parsed {:pgtype "jsonb"})}
            ins-result (db-create-meta-data (:tx req) mr meta-key-id md-type user-id mdnew)]

        (sd/logwrite req (str "handle_create-meta-data-json:"
                              " mr-id: " (:id mr)
                              " meta-key-id: " meta-key-id
                              " ins-result: " ins-result))

        (if (= md-type (:type ins-result))
          (sd/response_ok ins-result)
          (sd/response_failed {:message "Failed to add meta data json"} 406))))
    (catch Exception ex (sd/response_exception ex))))

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

(defn- db-create-meta-data-keyword
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

(defn- db-delete-meta-data-keyword
  [db md-id kw-id]
  (let [sql-query (-> (sql/delete-from :meta_data_keywords)
                      (sql/where [:= :meta_datum_id md-id] [:= :keyword_id kw-id])
                      sql-format)
        result (jdbc/execute-one! db sql-query)]
    (info "db-delete-meta-data-keyword"
          "\nmd-id\n" md-id
          "\nkw-id\n" kw-id
          "\nresult\n" result)
    result))

(def MD_TYPE_KEYWORDS "MetaDatum::Keywords")
(def MD_KEY_KWS :keywords)
(def MD_KEY_KW_DATA :md_keywords)
(def MD_KEY_KW_IDS :keywords_ids)

(defn create_md_and_keyword
  [mr meta-key-id kw-id user-id tx]

  (try
    (catcher/with-logging {}
      (jdbc/with-transaction [tx tx]
        (let [meta-data (db-get-meta-data mr meta-key-id nil tx)])
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

; TODO tests, response coercion
(defn handle_create-meta-data-keyword
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            kw-id (-> req :parameters :path :keyword_id)
            tx (:tx req)
            user-id (-> req :authenticated-entity :id)]

        (if-let [result (create_md_and_keyword mr meta-key-id kw-id user-id tx)]
          ;((sd/logwrite req  (str "handle_create-meta-data-keyword:" "mr-id: " (:id mr) "kw-id: " kw-id "result: " result))
          (sd/response_ok result)
          ;)
          (if-let [retryresult (create_md_and_keyword mr meta-key-id kw-id user-id tx)]
            ((sd/logwrite req (str "handle_create-meta-data-keyword:" "mr-id: " (:id mr) "kw-id: " kw-id "result: " retryresult))
             (sd/response_ok retryresult))
            (sd/response_failed "Could not create md keyword" 406)))))
    (catch Exception ex (sd/response_exception ex))))

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

(defn handle_delete-meta-data-keyword
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            kw-id (-> req :parameters :path :keyword_id)
            tx (:tx req)
            md (db-get-meta-data mr meta-key-id MD_TYPE_KEYWORDS tx)
            md-id (-> md :id)
            delete-result (db-delete-meta-data-keyword tx md-id kw-id)
            mdr (db-get-meta-data-keywords md-id tx)]

        (sd/logwrite req (str "handle_delete-meta-data-keyword:"
                              "mr-id: " (:id mr)
                              "md-id: " md-id
                              "meta-key: " meta-key-id
                              "keyword-id: " kw-id
                              "result: " delete-result))

        (if (= 1 (:next.jdbc/update-count delete-result))
          (sd/response_ok {:meta_data md
                           MD_KEY_KW_DATA mdr})
          (sd/response_failed "Could not delete md keyword." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn- db-create-meta-data-people
  [db md-id person-id user-id]
  (let [data {:meta_datum_id (to-uuid md-id)
              :person_id person-id
              :created_by_id user-id}
        sql-query (-> (sql/insert-into :meta_data_people)
                      (sql/values [data])
                      (sql/returning :*)
                      sql-format)
        result (jdbc/execute-one! db sql-query)]

    ;(info "db-create-meta-data-people" "\npeople-data\n" data "\nresult\n" result)
    result))

(def MD_TYPE_PEOPLE "MetaDatum::People")
(def MD_KEY_PEOPLE :people)
(def MD_KEY_PEOPLE_DATA :md_people)
(def MD_KEY_PEOPLE_IDS :people_ids)

(defn create_md_and_people
  [mr meta-key-id person-id user-id tx]
  (try
    (catcher/with-logging {}
      (jdbc/with-transaction [tx tx]
        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
          ; already has meta-data
          (do
            (if-let [result (db-create-meta-data-people tx (:id meta-data) person-id user-id)]
              (do
                {:meta_data meta-data
                 MD_KEY_PEOPLE_DATA result})
              nil))

          ; create meta-data and md-people
          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_PEOPLE user-id)]
            (do
              (if-let [ip-result (db-create-meta-data-people tx (-> mdins-result :id str) person-id user-id)]
                (do
                  {:meta_data mdins-result
                   MD_KEY_PEOPLE_DATA ip-result})
                nil))
            nil))))
    (catch Exception _
      (error "Could not create md people" _)
      nil)))

; TODO tests, response coercion
(defn handle_create-meta-data-people
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            person-id (-> req :parameters :path :person_id)
            tx (:tx req)
            user-id (-> req :authenticated-entity :id)]

        (if-let [result (create_md_and_people mr meta-key-id person-id user-id tx)]
          ;((sd/logwrite req (str "handle_create-meta-data-people:"
          ;                       "mr-id: " (:id mr)
          ;                       "meta-key: " meta-key-id
          ;                       "person-id:" person-id
          ;                       "result: " result))
          (sd/response_ok result) ;)
          (if-let [retryresult (create_md_and_people mr meta-key-id person-id user-id tx)]
            ;((sd/logwrite req (str "handle_create-meta-data-people:"
            ;                       "mr-id: " (:id mr)
            ;                       "meta-key: " meta-key-id
            ;                       "person-id:" person-id
            ;                       "result: " retryresult))
            (sd/response_ok retryresult) ;)
            (sd/response_failed "Could not create md people" 406)))))
    (catch Exception ex (sd/response_exception ex))))

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

(defn handle_delete-meta-data-people
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            person-id (-> req :parameters :path :person_id)
            tx (:tx req)
            md (db-get-meta-data mr meta-key-id MD_TYPE_PEOPLE tx)
            md-id (-> md :id)
            sql-query (-> (sql/delete-from :meta_data_people)
                          (sql/where [:and
                                      [:= :meta_datum_id md-id]
                                      [:= :person_id person-id]])
                          sql-format)
            del-result (jdbc/execute-one! tx sql-query)]
        (sd/logwrite req (str "\nhandle_delete-meta-data-people:"
                              "\nmr-id: " (:id mr)
                              " meta-key: " meta-key-id
                              " person-id: " person-id
                              " result: " del-result))

        (if (= 1 (:next.jdbc/update-count del-result))
          (sd/response_ok {:meta_data md
                           MD_KEY_PEOPLE_DATA (db-get-meta-data-people md-id tx)})
          (sd/response_failed {:message "Failed to delete meta data people"} 406))))
    (catch Exception ex (sd/response_exception ex))))

(def MD_TYPE_ROLES "MetaDatum::Roles")
(def MD_KEY_ROLES :roles)
(def MD_KEY_ROLES_DATA :md_roles)
(def MD_KEY_ROLES_IDS :roles_ids)

(defn db-create-meta-data-roles
  [db md-id role-id person-id position]
  (let [data {:meta_datum_id md-id
              :person_id person-id
              :role_id role-id
              :position position}
        sql-query (-> (sql/insert-into :meta_data_roles)
                      (sql/values [data])
                      sql-format)
        result (jdbc/execute! db sql-query)]
    result))

(defn- create_md_and_role
  [mr meta-key-id role-id person-id position user-id tx]
  (try
    (catcher/with-logging {}
      (jdbc/with-transaction [tx tx]
        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
          ;already has meta-data
          (if-let [result (db-create-meta-data-roles tx (:id meta-data) role-id person-id position)]
            {:meta_data meta-data
             MD_KEY_ROLES_DATA result}
            nil)

          ;create meta-data and role
          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_ROLES user-id)]
            (if-let [ip-result (db-create-meta-data-roles
                                tx
                                (-> mdins-result :id str)
                                role-id person-id position)]
              {:meta_data mdins-result
               MD_KEY_ROLES_DATA ip-result}
              nil)
            nil))))
    (catch Exception ex
      (error "Could not create md role" ex)
      nil)))

(defn- handle_create-roles-success [req mr-id role-id person-id result]
  (sd/logwrite req (str "handle_create-meta-data-role:"
                        " mr-id: " mr-id
                        " role-id: " role-id
                        " person-id: " person-id
                        " result: " result))
  (sd/response_ok result))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-role
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            user-id (-> req :authenticated-entity :id)
            meta-key-id (-> req :parameters :path :meta_key_id)
            role-id (-> req :parameters :path :role_id)
            person-id (-> req :parameters :path :person_id)
            position (-> req :parameters :path :position)
            tx (:tx req)]

        (if-let [result (create_md_and_role mr meta-key-id role-id person-id position user-id tx)]
          (handle_create-roles-success req (:id mr) role-id person-id result)

          (if-let [retryresult (create_md_and_role mr meta-key-id role-id person-id position user-id tx)]
            (handle_create-roles-success req (:id mr) role-id person-id retryresult)
            (sd/response_failed "Could not create md role." 406)))))
    (catch Exception ex (sd/response_exception ex))))

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

; TODO del meta-data if md-roles is empty ? sql-trigger ?
(defn handle_delete-meta-data-role
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        role-id (-> req :parameters :path :role_id)
        person-id (-> req :parameters :path :person_id)
        tx (:tx req)
        md (db-get-meta-data mr meta-key-id MD_TYPE_ROLES tx)
        md-id (-> md :id)
        ;mdr (db-get-meta-data-roles md-id)
        del-clause (sd/sql-update-clause
                    "meta_datum_id" md-id
                    "role_id" role-id
                    "person_id" person-id)
        sql-query (-> (sql/delete-from :meta_data_roles)
                      (sql/where del-clause)
                      sql-format)
        del-result (jdbc/execute! tx sql-query)]

    (sd/logwrite req (str "handle_delete-meta-data-role:"
                          " mr-id: " (:id mr)
                          " meta-key: " meta-key-id
                          " role-id: " role-id
                          " person-id: " person-id
                          " clause: " del-clause
                          " result: " del-result))
    (if (< 1 (first del-result))
      (sd/response_ok {:meta_data md
                       MD_KEY_ROLES_DATA (db-get-meta-data-roles md-id tx)})
      (sd/response_failed "Could not delete meta-data role." 406))))

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

;(def schema_export_meta-datum
;  {:id s/Uuid
;   :meta_key_id s/Str
;   :type s/Str
;   :value (s/->Either [[{:id s/Uuid}] s/Str])
;   (s/optional-key :media_entry_id) s/Uuid
;   (s/optional-key :collection_id) s/Uuid})

; TODO response coercion
(def meta-data-routes
  ["/meta-data"
   {:swagger {:tags ["api/meta-data"]}}
   ["/:meta_datum_id" {:get {:handler meta-datum/get-meta-datum
                             :middleware [sd/ring-wrap-add-meta-datum-with-media-resource
                                          sd/ring-wrap-authorization-view]
                             :summary "Get meta-data for id"
                             :description "Get meta-data for id. TODO: should return 404, if no such meta-data role exists."
                             :coercion reitit.coercion.schema/coercion
                             :parameters {:path {:meta_datum_id s/Uuid}}
                             :responses {200 {:body (get-schema :meta-data-schema.schema_export_meta-datum)}
                                         401 {:body s/Any}
                                         403 {:body s/Any}
                                         500 {:body s/Any}}}}]

   ["/:meta_datum_id/data-stream" {:get {:handler meta-datum/get-meta-datum-data-stream
                                         ; TODO json meta-data: fix response conversion error
                                         :middleware [sd/ring-wrap-add-meta-datum-with-media-resource
                                                      sd/ring-wrap-authorization-view]
                                         :summary "Get meta-data data-stream."
                                         :description "Get meta-data data-stream."
                                         :coercion reitit.coercion.schema/coercion
                                         :parameters {:path {:meta_datum_id s/Uuid}}}}]
   ;:responses {200 {:body s/Any}
   ;422 {:body s/Any}}
   ])
;(def schema_export_mdrole
;  {:id s/Uuid
;   :meta_datum_id s/Uuid
;   :person_id s/Uuid
;   :role_id (s/maybe s/Uuid)
;   :position s/Int})

(def role-routes
  ["/meta-data-role"
   {:swagger {:tags ["api/meta-data-role"]}}
   ["/:meta_data_role_id"
    {:get {:summary " Get meta-data role for id "
           :handler meta-datum/handle_get-meta-datum-role
           :description " Get meta-datum-role for id. returns 404, if no such meta-data role exists. "
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:meta_data_role_id s/Str}}
           :responses {200 {:body (get-schema :meta-data-role-schema.schema_export_mdrole)}
                       404 {:body s/Any}}}}]])

(def collection-routes
  ["/collection"
   {:swagger {:tags ["api/collection"]}}
   ["/:collection_id/meta-data"
    {:get {:summary "Get meta-data for collection."
           :handler meta-data.index/get-index
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           ; TODO 401s test fails
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}
                        :query {(s/optional-key :updated_after) s/Inst
                                (s/optional-key :meta_keys) s/Str}}
           :responses {200 {:body s/Any}}}}]

   ["/:collection_id/meta-data-related"
    {:get {:summary "Get meta-data for collection."
           :handler handle_get-mr-meta-data-with-related
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           ; TODO 401s test fails
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}
                        :query {(s/optional-key :updated_after) s/Inst
                                (s/optional-key :meta_keys) s/Str}}
           :responses {200 {:body s/Any}}}}]

   ["/:collection_id/meta-datum"
    ["/:meta_key_id"
     {:get {:summary "Get meta-data for collection and meta-key."
            :handler handle_get-meta-key-meta-data

            :middleware [wrap-add-meta-key
                         wrap-check-vocab
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-view]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :meta_key_id s/Str}}
            :responses {200 {:body s/Any}}}

      :delete {:summary "Delete meta-data for collection and meta-key"
               :handler handle-delete-meta-data
               :middleware [sd/ring-wrap-add-media-resource
                            sd/ring-wrap-authorization-view
                            wrap-col-add-meta-data]
               :coercion reitit.coercion.schema/coercion
               :parameters {:path {:collection_id s/Uuid
                                   :meta_key_id s/Str}}
               :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/text"

     {:post {:summary "Create meta-data text for collection."
             :handler handle_create-meta-data-text
             :middleware [sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :accept "application/json"
             :content-type "application/json"
             :swagger {:produces "application/json" :consumes "application/json"}
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Uuid
                                 :meta_key_id s/Str}
                          :body {:string s/Str}}
             :responses {200 {:body s/Any}}}

      :put {:summary "Update meta-data text for collection."
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
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/text-date"
     {:post {:summary "Create meta-data json for collection."
             :handler handle_create-meta-data-text-date
             :middleware [sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Uuid
                                 :meta_key_id s/Str}
                          :body {:string s/Str}}
             :responses {200 {:body s/Any}}}
      :put {:summary "Update meta-data text-date for collection."
            :handler handle_update-meta-data-text-date
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :meta_key_id s/Str}
                         :body {:string s/Str}}
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/json"
     {:post {:summary "Create meta-data json for collection."
             :handler handle_create-meta-data-json
             :middleware [sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Uuid
                                 :meta_key_id s/Str}
                          :body {:json s/Any}}
             :responses {200 {:body s/Any}}}
      :put {:summary "Update meta-data json for collection."
            :handler handle_update-meta-data-json
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :meta_key_id s/Str}
                         :body {:json s/Any}}
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/keyword"
     {:get {:summary "Get meta-data keywords for collection meta-key"
            :handler handle_get-meta-data-keywords
            :middleware [;wrap-me-add-meta-data
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-view]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :meta_key_id s/Str}}
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/keyword/:keyword_id"
     {:post {:summary "Create meta-data keyword for collection."
             :handler handle_create-meta-data-keyword
             :middleware [;wrap-me-add-meta-data
                          wrap-add-keyword
                          sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Uuid
                                 :meta_key_id s/Str
                                 :keyword_id s/Uuid}}
             :responses {200 {:body s/Any}}}

      :delete {:summary "Delete meta-data keyword for collection."
               :handler handle_delete-meta-data-keyword
               :middleware [wrap-add-keyword
                            sd/ring-wrap-add-media-resource
                            sd/ring-wrap-authorization-edit-metadata]
               :coercion reitit.coercion.schema/coercion
               :parameters {:path {:collection_id s/Uuid
                                   :meta_key_id s/Str
                                   :keyword_id s/Uuid}}
               :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/people"
     {:get {:summary "Get meta-data people for collection meta-key."
            :handler handle_get-meta-data-people
            :middleware [;wrap-me-add-meta-data
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :meta_key_id s/Str}}
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/people/:person_id"
     {:post {:summary "Create meta-data people for media-entry"
             :handler handle_create-meta-data-people
             :middleware [;wrap-me-add-meta-data
                          wrap-add-person
                          sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Uuid
                                 :meta_key_id s/Str
                                 :person_id s/Uuid}}
             :responses {200 {:body s/Any}}}

      :delete {:summary "Delete meta-data people for collection."
               :handler handle_delete-meta-data-people
               :middleware [wrap-add-person
                            sd/ring-wrap-add-media-resource
                            sd/ring-wrap-authorization-edit-metadata]
               :coercion reitit.coercion.schema/coercion
               :parameters {:path {:collection_id s/Uuid
                                   :meta_key_id s/Str
                                   :person_id s/Uuid}}
               :responses {200 {:body s/Any}}}}]
    ; TODO meta-data roles
    ["/:meta_key_id/role/:role_id"
     {:post {:summary "Create meta-data role for media-entry"
             :handler handle_create-meta-data-role
             :middleware [wrap-add-role
                          sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Uuid
                                 :meta_key_id s/Str
                                 :role_id s/Uuid}}
             :responses {200 {:body s/Any}}}}]]])

(def media-entry-routes
  ["/media-entry"
   {:swagger {:tags ["api/media-entry"]}}
   ["/:media_entry_id/meta-data"
    {:get {:summary "Get meta-data for media-entry."
           :handler meta-data.index/get-index
           ; TODO 401s test fails
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}
                        :query {(s/optional-key :updated_after) s/Inst
                                (s/optional-key :meta_keys) s/Str}}
           :responses {200 {:body s/Any}}}}]

   ["/:media_entry_id/meta-data-related"
    {:get {:summary "Get meta-data for media-entry."
           :handler handle_get-mr-meta-data-with-related
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}
                        :query {(s/optional-key :updated_after) s/Inst
                                (s/optional-key :meta_keys) s/Str}}
           :responses {200 {:body s/Any}}}}]

   ["/:media_entry_id/meta-datum"
    ["/:meta_key_id"
     {:get {:summary "Get meta-data for media-entry and meta-key."
            :handler handle_get-meta-key-meta-data
            :middleware [wrap-add-meta-key
                         ;wrap-check-vocab
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-view]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :meta_key_id s/Str}}
            :responses {200 {:body s/Any}}}

      :delete
      {:summary "Delete meta-data for media-entry and meta-key"
       :handler handle-delete-meta-data
       :middleware [sd/ring-wrap-add-media-resource
                    sd/ring-wrap-authorization-view
                    wrap-me-add-meta-data]
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:media_entry_id s/Uuid
                           :meta_key_id s/Str}}
       :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/text"
     {:post {:summary "Create meta-data text for media-entry"
             :handler handle_create-meta-data-text
             :middleware [sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_entry_id s/Uuid
                                 :meta_key_id s/Str}
                          :body {:string s/Str}}
             :responses {200 {:body s/Any}}}

      :put {:summary "Update meta-data text for media-entry"
            :handler handle_update-meta-data-text
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :meta_key_id s/Str}
                         :body {:string s/Str}}
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/text-date"
     {:post {:summary "Create meta-data text-date for media-entry"
             :handler handle_create-meta-data-text-date
             :middleware [sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_entry_id s/Uuid
                                 :meta_key_id s/Str}
                          :body {:string s/Str}}
             :responses {200 {:body s/Any}}}
      :put {:summary "Update meta-data text-date for media-entry"
            :handler handle_update-meta-data-text-date
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :meta_key_id s/Str}
                         :body {:string s/Str}}
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/json"
     {:post {:summary "Create meta-data json for media-entry"
             :handler handle_create-meta-data-json
             :middleware [sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_entry_id s/Uuid
                                 :meta_key_id s/Str}
                          :body {:json s/Any}}
             :responses {200 {:body s/Any}}}

      :put {:summary "Update meta-data json for media-entry"
            :handler handle_update-meta-data-json
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :meta_key_id s/Str}
                         :body {:json s/Any}}
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/keyword"
     {:get {:summary "Get meta-data keywords for media-entries meta-key"
            :handler handle_get-meta-data-keywords
            :middleware [;wrap-me-add-meta-data
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-view]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :meta_key_id s/Str}}
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/keyword/:keyword_id"
     {:post {:summary "Create meta-data keyword for media-entry."
             :handler handle_create-meta-data-keyword
             :middleware [;wrap-me-add-meta-data
                          wrap-add-keyword
                          sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_entry_id s/Uuid
                                 :meta_key_id s/Str ;; is this meta_datum_id
                                 :keyword_id s/Uuid}}
             :responses {200 {:body s/Any}}}

      :delete {:summary "Delete meta-data keyword for media-entry."
               :handler handle_delete-meta-data-keyword
               :middleware [wrap-add-keyword
                            sd/ring-wrap-add-media-resource
                            sd/ring-wrap-authorization-edit-metadata]
               :coercion reitit.coercion.schema/coercion
               :parameters {:path {:media_entry_id s/Uuid
                                   :meta_key_id s/Str
                                   :keyword_id s/Uuid}}
               :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/people"
     {:get {:summary "Get meta-data people for media-entries meta-key."
            :handler handle_get-meta-data-people
            :middleware [;wrap-me-add-meta-data
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-view]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :meta_key_id s/Str}}
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/people/:person_id"
     {:post {:summary "Create meta-data people for a media-entries meta-key."
             :handler handle_create-meta-data-people
             :middleware [wrap-add-person
                          sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata
                          wrap-me-add-meta-data]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_entry_id s/Uuid
                                 :meta_key_id s/Str
                                 :person_id s/Uuid}}
             :responses {200 {:body s/Any}}}

      :delete {:summary "Delete meta-data people for media-entry"
               :handler handle_delete-meta-data-people
               :middleware [wrap-add-person
                            sd/ring-wrap-add-media-resource
                            sd/ring-wrap-authorization-edit-metadata]
               :coercion reitit.coercion.schema/coercion
               :parameters {:path {:media_entry_id s/Uuid
                                   :meta_key_id s/Str
                                   :person_id s/Uuid}}
               :responses {200 {:body s/Any}}}}]
    ["/:meta_key_id/role"
     {:get {:summary "Get meta-data role for media-entry."
            :handler handle_get-meta-data-roles
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-view]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid
                                :meta_key_id s/Str}}
            :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/role/:role_id/:person_id"
     {:delete {:summary "Delete meta-data role for media-entry."
               :handler handle_delete-meta-data-role
               :middleware [wrap-add-role
                            wrap-add-person
                            sd/ring-wrap-add-media-resource
                            sd/ring-wrap-authorization-edit-metadata]
               :coercion reitit.coercion.schema/coercion
               :parameters {:path {:media_entry_id s/Uuid
                                   :meta_key_id s/Str
                                   :role_id s/Uuid
                                   :person_id s/Uuid}}
               :responses {200 {:body s/Any}}}}]

    ["/:meta_key_id/role/:role_id/:person_id/:position"
     {:post {:summary "Create meta-data role for media-entry."
             :handler handle_create-meta-data-role
             :middleware [wrap-add-role
                          wrap-add-person
                          sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_entry_id s/Uuid
                                 :meta_key_id s/Str
                                 :role_id s/Uuid
                                 :person_id s/Uuid
                                 :position s/Int}}
             :responses {200 {:body s/Any}}}}]]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
