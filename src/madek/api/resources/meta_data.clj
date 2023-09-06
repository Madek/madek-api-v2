(ns madek.api.resources.meta-data
  (:require [cheshire.core :as cheshire]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [logbug.catcher :as catcher]
            [madek.api.resources.meta-data.index :as meta-data.index]
            [madek.api.resources.meta-data.meta-datum :as meta-datum]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.rdbms :as rdbms]
            [madek.api.utils.sql :as sql]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [schema.core :as s]))

(defn- col-key-for-mr-type [mr]
  (let [mr-type (-> mr :type)]
    (if (= mr-type "Collection")
      :collection_id
      :media_entry_id)))

(defn- assoc-media-resource-typed-id [mr ins-data]
    (assoc ins-data
           (col-key-for-mr-type mr)
           (str (-> mr :id))))

        
(defn sql-cls-upd-meta-data-typed-id [mr mk-id md-type]
  (let [md-sql (-> (sql/where [:and
                               [:= :meta_key_id mk-id]
                               [:= :type md-type]
                               [:= (col-key-for-mr-type mr) (str (-> mr :id))]])
                   sql/format
                   sd/hsql-upd-clause-format )] 
    md-sql))

(defn- fabric-meta-data 
  [mr meta-key-id md-type user-id]
  (let [data {:meta_key_id meta-key-id
              :type md-type
              :created_by_id user-id}]
    (assoc-media-resource-typed-id mr data)))


(defn db-get-meta-data

  ([mr mk-id md-type]
   (db-get-meta-data mr mk-id md-type (rdbms/get-ds)))
  
  ([mr mk-id md-type db]
   (let [mr-id (str (-> mr :id))
         mr-key (col-key-for-mr-type mr)
         db-query (-> (sql/select :*)
                      (sql/from :meta_data)
                      (sql/where [:and
                                  [:= :meta_key_id mk-id]
                                  [:= mr-key mr-id]])
                      sql/format)
         db-result (first (jdbc/query db db-query))
         db-type (:type db-result)]

     (if (or (= nil md-type) (= md-type db-type))
       db-result
       nil))))

(defn- db-create-meta-data
  ([db meta-data]
   (logging/info "db-create-meta-data: " meta-data)
   (if-let [result (first (jdbc/insert! db :meta_data meta-data))]
     result
     nil))

  ([db mr meta-key-id md-type user-id]
   (logging/info "db-create-meta-data: " "MK-ID: " meta-key-id "Type:" md-type "User: " user-id)
   (db-create-meta-data db (fabric-meta-data mr meta-key-id md-type user-id)))

  ([db mr meta-key-id md-type user-id meta-data]
   (logging/info "db-create-meta-data: " "MK-ID: " meta-key-id "Type:" md-type "User: " user-id "MD: " meta-data)
   (let [md (merge (fabric-meta-data mr meta-key-id md-type user-id) meta-data)]
     (logging/info "db-create-meta-data: "
                   "MK-ID: " meta-key-id
                   "Type:" md-type
                   "User: " user-id
                   "MD: " meta-data
                   "MD-new: " md)
     (db-create-meta-data db md))))

(defn- handle_update-meta-data-text-base
  [req md-type upd-data]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            upd-data2 (assoc upd-data (col-key-for-mr-type mr) (:id mr))
            meta-key-id (-> req :parameters :path :meta_key_id)
            upd-clause (sql-cls-upd-meta-data-typed-id mr meta-key-id md-type)
            upd-result (jdbc/update! (rdbms/get-ds) :meta_data upd-data2 upd-clause)
            result-data (db-get-meta-data mr meta-key-id md-type)]

        (sd/logwrite req (str "handle_update-meta-data-text-base:"
                              " mr-id: " (:id mr)
                              " mr-type: " (:type mr)
                              " md-type: " md-type
                              " meta-key-id: " meta-key-id
                              " upd-result: " upd-result))
        
        (if (= 1 (first upd-result))
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
            mdnew {:string text-data}
            ins-result (db-create-meta-data (rdbms/get-ds) mr meta-key-id md-type user-id mdnew)]

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
        upd-data {:string text-data }
        md-type "MetaDatum::Text"]
        
    (logging/info "handle_update-meta-data-text" "\nupd-data\n" upd-data)
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
            ins-result (db-create-meta-data (rdbms/get-ds) mr meta-key-id md-type user-id mdnew)]

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
    (logging/info "handle_update-meta-data-text-date" "\nupd-data\n" upd-data)
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
            mdnew {:json json-parsed}
            ins-result (db-create-meta-data (rdbms/get-ds) mr meta-key-id md-type user-id mdnew)]
        
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
        upd-data {:json json-parsed}
        md-type "MetaDatum::JSON"]
    (logging/info "handle_update-meta-data-json"
                  "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))


(defn- db-create-meta-data-keyword
  [db md-id kw-id user-id]
  (let [data {:meta_datum_id md-id
              :keyword_id kw-id
              :created_by_id user-id}
        result (jdbc/insert! db :meta_data_keywords data)]
    ;(logging/info "db-create-meta-data-keyword"
    ;              "\nkw-data\n" data
    ;              "\nresult\n" result)
    result))

(defn- db-delete-meta-data-keyword
  [db md-id kw-id]
  (let [query ["meta_datum_id = ? AND keyword_id = ?" md-id kw-id]
        result (jdbc/delete! db :meta_data_keywords query)]
    ;(logging/info "db-delete-meta-data-keyword"
    ;              "\nmd-id\n" md-id
    ;              "\nkw-id\n" kw-id
    ;              "\nresult\n" result)
    result
    )
  )

(def MD_TYPE_KEYWORDS "MetaDatum::Keywords")
(def MD_KEY_KWS :keywords)
(def MD_KEY_KW_DATA :md_keywords)
(def MD_KEY_KW_IDS :keywords_ids)

(defn create_md_and_keyword
  [mr meta-key-id kw-id user-id]

  (try
    (catcher/with-logging {}
      (jdbc/with-db-transaction [tx (rdbms/get-ds)]
        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
        ; already has meta-data
          (if-let [result (db-create-meta-data-keyword tx (:id meta-data) kw-id user-id)]
            {:meta_data meta-data
             MD_KEY_KW_DATA result}
            nil)

        ; create meta-data and md-kw
          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_KEYWORDS user-id)]
            (if-let [ip-result (db-create-meta-data-keyword tx (-> mdins-result :id str) kw-id user-id)]
              {:meta_data mdins-result
               MD_KEY_KW_DATA ip-result}
              nil)
            nil))))
    (catch Exception _
      (logging/error "Could not create md keyword" _)
      nil)))


; TODO tests, response coercion
(defn handle_create-meta-data-keyword
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            kw-id (-> req :parameters :path :keyword_id)
            user-id (-> req :authenticated-entity :id str)]

        (if-let [result (create_md_and_keyword mr meta-key-id kw-id user-id)]
          ((sd/logwrite req  (str "handle_create-meta-data-keyword:" "mr-id: " (:id mr) "kw-id: " kw-id "result: " result))
           (sd/response_ok result))
          (if-let [retryresult (create_md_and_keyword mr meta-key-id kw-id user-id)]
            ((sd/logwrite req  (str "handle_create-meta-data-keyword:" "mr-id: " (:id mr) "kw-id: " kw-id "result: " retryresult))
             (sd/response_ok retryresult))
            (sd/response_failed "Could not create md keyword" 406)))))
    (catch Exception ex (sd/response_exception ex))))

   
(defn db-get-meta-data-keywords 
  [md-id]
  (sd/query-eq-find-all :meta_data_keywords :meta_datum_id md-id))

; TODO only some results
(defn handle_get-meta-data-keywords
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)]

    (if-let [md (db-get-meta-data mr meta-key-id MD_TYPE_KEYWORDS)]
      (let [md-id (-> md :id)
            mdr (db-get-meta-data-keywords md-id)
            mdr-ids (map (-> :keyword_id) mdr)
            keywords (map #(sd/query-eq-find-one :keywords :id %) mdr-ids)
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
            mdkw-id (-> req :parameters :path :keyword_id)
            md (db-get-meta-data mr meta-key-id MD_TYPE_KEYWORDS)
            md-id (-> md :id)
            delete-result (db-delete-meta-data-keyword (rdbms/get-ds) md-id mdkw-id)
            mdr (db-get-meta-data-keywords md-id)]

        (sd/logwrite req (str "handle_delete-meta-data-keyword:"
                              "mr-id: " (:id mr)
                              "meta-key: " meta-key-id
                              "keyword-id: " mdkw-id
                              "result: " delete-result))

        (if (= 1 (first delete-result))
          (sd/response_ok {:meta_data md
                           MD_KEY_KW_DATA mdr})
          (sd/response_failed "Could not delete md keyword." 406))))
    (catch Exception ex (sd/response_exception ex))))


(defn- db-create-meta-data-people 
  [db md-id person-id user-id]
  (let [data {:meta_datum_id md-id
              :person_id person-id
              :created_by_id user-id}
        result (jdbc/insert! db :meta_data_people data)]
        ;(logging/info "db-create-meta-data-people" "\npeople-data\n" data "\nresult\n" result)
        result))

(def MD_TYPE_PEOPLE "MetaDatum::People")
(def MD_KEY_PEOPLE :people)
(def MD_KEY_PEOPLE_DATA :md_people)
(def MD_KEY_PEOPLE_IDS :people_ids)

(defn create_md_and_people
  [mr meta-key-id person-id user-id ]
  (try 
    (catcher/with-logging {}
      (jdbc/with-db-transaction [tx (rdbms/get-ds)]
        (if-let [meta-data (db-get-meta-data mr meta-key-id nil tx)]
        ; already has meta-data
          (if-let [result (db-create-meta-data-people tx (:id meta-data) person-id user-id)]
            {:meta_data meta-data
             MD_KEY_PEOPLE_DATA result}
            nil)

        ; create meta-data and md-people
          (if-let [mdins-result (db-create-meta-data tx mr meta-key-id MD_TYPE_PEOPLE user-id)]
            (if-let [ip-result (db-create-meta-data-people tx (-> mdins-result :id str) person-id user-id)]
              {:meta_data mdins-result
               MD_KEY_PEOPLE_DATA ip-result}
              nil)
            nil))))
    (catch Exception _
      (logging/error "Could not create md people" _)
      nil
      )
  ))

; TODO tests, response coercion
(defn handle_create-meta-data-people
  [req]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            person-id (-> req :parameters :path :person_id)
            user-id (-> req :authenticated-entity :id str)]

        (if-let [result (create_md_and_people mr meta-key-id person-id user-id)]
          ((sd/logwrite req (str "handle_create-meta-data-people:"
                                 "mr-id: " (:id mr)
                                 "meta-key: " meta-key-id
                                 "person-id:" person-id
                                 "result: " result))
           (sd/response_ok result))
          (if-let [retryresult (create_md_and_people mr meta-key-id person-id user-id)]
            ((sd/logwrite req (str "handle_create-meta-data-people:"
                                   "mr-id: " (:id mr)
                                   "meta-key: " meta-key-id
                                   "person-id:" person-id
                                   "result: " retryresult))
             (sd/response_ok retryresult))
            (sd/response_failed "Could not create md people" 406)))))
    (catch Exception ex (sd/response_exception ex))))


(defn db-get-meta-data-people 
  [md-id]
  (sd/query-eq-find-all :meta_data_people :meta_datum_id md-id))

; TODO only some results
(defn handle_get-meta-data-people
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)]

    (if-let [md (db-get-meta-data mr meta-key-id MD_TYPE_PEOPLE)]
      (let [md-id (-> md :id)
            mdr (db-get-meta-data-people md-id)
            mdr-ids (map (-> :person_id) mdr)
            people (map #(sd/query-eq-find-one :people :id %) mdr-ids)
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
          ;md-type MD_TYPE_PEOPLE
            md (db-get-meta-data mr meta-key-id MD_TYPE_PEOPLE)
            md-id (-> md :id)
            mdr-clause ["meta_datum_id = ? AND person_id = ?" md-id person-id]
            del-result (jdbc/delete! (rdbms/get-ds) :meta_data_people mdr-clause)]

        (sd/logwrite req (str "handle_delete-meta-data-people:"
                              "mr-id: " (:id mr)
                              "meta-key: " meta-key-id
                              "person-id:" person-id
                              "result: " del-result))

        (if (= 1 (first del-result))
          (sd/response_ok {:meta_data md
                           MD_KEY_PEOPLE_DATA (db-get-meta-data-people md-id)})
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
        result (jdbc/insert! db :meta_data_roles data)]
    result))

(defn- create_md_and_role
  [mr meta-key-id role-id person-id position user-id]
  (try
    (catcher/with-logging {}
      (jdbc/with-db-transaction [tx (rdbms/get-ds)]
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
      (logging/error "Could not create md role" ex)
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
            user-id (-> req :authenticated-entity :id str)
            meta-key-id (-> req :parameters :path :meta_key_id)
            role-id (-> req :parameters :path :role_id)
            person-id (-> req :parameters :path :person_id)
            position (-> req :parameters :path :position)]

        (if-let [result (create_md_and_role mr meta-key-id role-id person-id position user-id)]
          (handle_create-roles-success req (:id mr) role-id person-id result)

          (if-let [retryresult (create_md_and_role mr meta-key-id role-id person-id position user-id)]
            (handle_create-roles-success req (:id mr) role-id person-id retryresult)
            (sd/response_failed "Could not create md role." 406)))))
    (catch Exception ex (sd/response_exception ex))))

(defn db-get-meta-data-roles [md-id]
  (sd/query-eq-find-all :meta_data_roles :meta_datum_id md-id))

(defn handle_get-meta-data-roles
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)]
  
    (if-let [md (db-get-meta-data mr meta-key-id MD_TYPE_ROLES)]
      (let [md-id (-> md :id)
            mdr (db-get-meta-data-roles md-id)
            mdr-rids (map (-> :role_id) mdr)
            mdr-pids (map (-> :person_id) mdr)
            roles (map #(sd/query-eq-find-one :roles :id %) mdr-rids)
            people (map #(sd/query-eq-find-one :people :id %) mdr-pids)
            result {:meta_data md
                    
                    MD_KEY_ROLES_IDS mdr-rids
                    MD_KEY_PEOPLE_IDS mdr-pids
                    MD_KEY_ROLES_DATA mdr
                    MD_KEY_ROLES roles
                    MD_KEY_PEOPLE people
                    }]
        (sd/response_ok result))
      (sd/response_failed "Not found" 404))))

; TODO del meta-data if md-roles is empty ? sql-trigger ?
(defn handle_delete-meta-data-role
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        role-id (-> req :parameters :path :role_id)
        person-id (-> req :parameters :path :person_id)
        md (db-get-meta-data mr meta-key-id MD_TYPE_ROLES)
        md-id (-> md :id)
        ;mdr (db-get-meta-data-roles md-id)
        del-clause (sd/sql-update-clause
                    "meta_datum_id" md-id
                    "role_id" role-id
                    "person_id" person-id)
        del-result (jdbc/delete! (rdbms/get-ds) :meta_data_roles del-clause)
        ]

    (sd/logwrite req (str "handle_delete-meta-data-role:"
                          " mr-id: " (:id mr)
                          " meta-key: " meta-key-id
                          " role-id: " role-id
                          " person-id: " person-id
                          " clause: " del-clause
                          " result: " del-result))
    (if ( < 1 (first del-result))
      (sd/response_ok {:meta_data md
                       MD_KEY_ROLES_DATA (db-get-meta-data-roles md-id)})
      (sd/response_failed "Could not delete meta-data role." 406))))

(defn- add-meta-data-extra [result]
    (let [md-id (:id result)
          md-type (:type result)

          md-type-kw (case md-type
                       "MetaDatum::Keywords" "md_keywords"
                       "MetaDatum::People" "md_people"
                       "MetaDatum::Roles" "md_roles"
                       "default")

          md-type-kw-data (apply str md-type-kw "_data")

          mde (case md-type
                "MetaDatum::Keywords" (db-get-meta-data-keywords md-id)
                "MetaDatum::People" (db-get-meta-data-people md-id)
                "MetaDatum::Roles" (db-get-meta-data-roles md-id)
                "default")

          mde-data (case md-type
                     "MetaDatum::Keywords" (->>
                                            mde
                                            (map (-> :keyword_id))
                                            (map #(sd/query-eq-find-one :keywords :id %)))
                     "MetaDatum::People" (->>
                                          mde
                                          (map (-> :person_id))
                                          (map #(sd/query-eq-find-one :people :id %)))
                     "MetaDatum::Roles" (->>
                                         mde
                                         (map (-> :role_id))
                                         (map #(sd/query-eq-find-one :roles :id %)))
                     "default")
          mde-result {:meta-data result
                      ;(keyword md-type) mde
                      (keyword md-type-kw) mde
                      (keyword md-type-kw-data) mde-data
                      ;(keyword (apply str md-type "-data")) mde-data
                      }
          ]
      ;(logging/info "handle_get-meta-key-meta-data"
      ;              "\nmedia id " md-id
      ;              "meta-data " mde-result)
      mde-result)
)

(defn handle_get-meta-key-meta-data
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)]
    
    (if-let [result (db-get-meta-data mr meta-key-id nil)]
      (let [extra-result (add-meta-data-extra result)]
        ;(logging/info "handle_get-meta-key-meta-data"
        ;              "\nmeta-key-id " meta-key-id
        ;              "meta-data " extra-result)
        (sd/response_ok extra-result))
      
      (sd/response_failed "No such meta data" 404))
  )
)


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
                 true
                 )))

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
          user-vocab-query (meta-data.index/md-vocab-where-clause user-id)
          
          vocab-clause (-> (sql/select :*)
                           (sql/from :vocabularies)
                           (sql/merge-where [:= :id (:vocabulary_id meta-key)])
                           (sql/merge-where user-vocab-query)
                           (sql/format))
          result (jdbc/query (rdbms/get-ds) vocab-clause)]
      
      ;(logging/info "wrap-check-vocab"
      ;              "\nmeta-key-id" (:id meta-key)
      ;              "\nvocab-clause" vocab-clause
      ;              ;"\nresult" result
      ;              )
      
      (if (= 0 (count result))
        (sd/response_not_found "Invalid meta-key, or no vocabulary access.")
        (handler req)
        ))))


; TODO response coercion
(def ring-routes
  ["/meta-data"
   ["/:meta_datum_id" {:get {:handler meta-datum/get-meta-datum
                             :middleware [sd/ring-wrap-add-meta-datum-with-media-resource
                                          sd/ring-wrap-authorization-view]
                             :summary "Get meta-data for id"
                             :description "Get meta-data for id. TODO: should return 404, if no such meta-data role exists."
                             :coercion reitit.coercion.schema/coercion
                             :parameters {:path {:meta_datum_id s/Str}}
                              ; TODO response coercion
                             :responses {200 {:body s/Any}
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
                                         :parameters {:path {:meta_datum_id s/Str}}}}]
                                          ;:responses {200 {:body s/Any}
                                                      ;422 {:body s/Any}}
   ["/:meta_datum_id/role" {:get {:summary "Get meta-data role for id"
                                  :handler meta-datum/handle_get-meta-datum-role
                                  :description "Get meta-data role for id. TODO: should return 404, if no such meta-data role exists."
                                  :coercion reitit.coercion.schema/coercion
                                  :parameters {:path {:meta_datum_id s/Str}}
                                  :responses {200 {:body s/Any}}}}]
  ])
   
(def collection-routes
  ["/collection"
   ["/:collection_id/meta-data"
    {:get {:summary "Get meta-data for collection."
           :handler meta-data.index/get-index
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
                                                   ; TODO 401s test fails
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Str}
                        :query {(s/optional-key :updated_after) s/Str
                                (s/optional-key :meta_keys) s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:collection_id/meta-data/:meta_key_id"
    {:get {:summary "Get meta-data for collection and meta-key."
           :handler handle_get-meta-key-meta-data

           :middleware [wrap-add-meta-key
                        wrap-check-vocab
                        sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Str
                               :meta_key_id s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:collection_id/meta-data/:meta_key_id/text"
    
    {:post {:summary "Create meta-data text for collection."
            :handler handle_create-meta-data-text
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata
                         ]
            :accept "application/json"
            :content-type "application/json"
            :swagger {:produces "application/json" :consumes "application/json"}
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid
                                :meta_key_id s/Str}
                         :body {:string s/Str}
                         }
            :responses {200 {:body s/Any}}}
     
     :put {:summary "Update meta-data text for collection."
           :handler handle_update-meta-data-text
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata
                        ]
           :accept "application/json"
           :content-type "application/json"
           :swagger {:produces "application/json" :consumes "application/json"}
           :coercion reitit.coercion.schema/coercion 
           :parameters {:path {:collection_id s/Uuid
                               :meta_key_id s/Str
                               }
                        :body {:string s/Str} 
                        }
           ;:coercion reitit.coercion.spec/coercion
           ;:parameters {:path {:collection_id string?
           ;                    :meta_key_id string?}
           ;             :body {:string string?}}
           :responses {200 {:body s/Any}}}
     }]

   ["/:collection_id/meta-data/:meta_key_id/text-date"
    {:post {:summary "Create meta-data json for collection."
            :handler handle_create-meta-data-text-date
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str
                                :meta_key_id s/Str}
                         :body {:string s/Str}}
            :responses {200 {:body s/Any}}}
     :put {:summary "Update meta-data text-date for collection."
           :handler handle_update-meta-data-text-date
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Str
                               :meta_key_id s/Str}
                        :body {:string s/Str}}
           :responses {200 {:body s/Any}}}
     }]

   ["/:collection_id/meta-data/:meta_key_id/json"
    {:post {:summary "Create meta-data json for collection."
            :handler handle_create-meta-data-json
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str
                                :meta_key_id s/Str}
                         :body {:json s/Str}}
            :responses {200 {:body s/Any}}}
     :put {:summary "Update meta-data json for collection."
           :handler handle_update-meta-data-json
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Str
                               :meta_key_id s/Str}
                        :body {:json s/Str}}
           :responses {200 {:body s/Any}}}
     }]
   
   ["/:collection_id/meta-data/:meta_key_id/keyword/:keyword_id"
    {:post {:summary "Create meta-data keyword for collection."
            :handler handle_create-meta-data-keyword
            :middleware [;wrap-me-add-meta-data
                         wrap-add-keyword
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str
                                :meta_key_id s/Str
                                :keyword_id s/Str}}
            :responses {200 {:body s/Any}}}
     :delete {:summary "Delete meta-data keyword for collection."
              :handler handle_delete-meta-data-keyword
              :middleware [wrap-add-keyword
                           sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-metadata]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:collection_id s/Str
                                  :meta_key_id s/Str
                                  :keyword_id s/Str}}
              :responses {200 {:body s/Any}}}
     }]
   
   ["/:collection_id/meta-data/:meta_key_id/people/:person_id"
    {:post {:summary "Create meta-data people for media-entry"
            :handler handle_create-meta-data-people
            :middleware [;wrap-me-add-meta-data
                         wrap-add-person
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str
                                :meta_key_id s/Str
                                :person_id s/Str}}
            :responses {200 {:body s/Any}}}
     
     :delete {:summary "Delete meta-data people for collection."
              :handler handle_delete-meta-data-people
              :middleware [wrap-add-person
                           sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-metadata]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:collection_id s/Str
                                  :meta_key_id s/Str
                                  :person_id s/Str}}
              :responses {200 {:body s/Any}}}
     }]
   ; TODO meta-data roles
   ["/:collection_id/meta-data/:meta_key_id/role/:role_id"
    {:post {:summary "Create meta-data role for media-entry"
            :handler handle_create-meta-data-role
            :middleware [wrap-add-role
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str
                                :role_id s/Str}}
            :responses {200 {:body s/Any}}}}]
  ])
   
(def media-entry-routes
  ["/media-entry"
   ["/:media_entry_id/meta-data"
    {:get {:summary "Get meta-data for media-entry."
           :handler meta-data.index/get-index
                                                      ; TODO 401s test fails
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view
                        ]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :query {(s/optional-key :updated_after) s/Str
                                (s/optional-key :meta_keys) s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id"
    {:get {:summary "Get meta-data for media-entry and meta-key."
           :handler handle_get-meta-key-meta-data
                                                 
           :middleware [wrap-add-meta-key
                        ;wrap-check-vocab
                        sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view
                        ]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str
                               :meta_key_id s/Str}
                        }
           :responses {200 {:body s/Any}}}}]
   ["/:media_entry_id/meta-data/:meta_key_id/text/"
    {:post {:summary "Create meta-data text for media-entry"
            :handler handle_create-meta-data-text
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata
                         ]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str}
                         :body {:string s/Str}}
            :responses {200 {:body s/Any}}}
     
     :put {:summary "Update meta-data text for media-entry"
           :handler handle_update-meta-data-text
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata
                        ]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str
                               :meta_key_id s/Str}
                        :body {:string s/Str}}
           :responses {200 {:body s/Any}}}
     }
    ]
   
   ["/:media_entry_id/meta-data/:meta_key_id/text-date/"
    {:post {:summary "Create meta-data text-date for media-entry"
            :handler handle_create-meta-data-text-date
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str}
                         :body {:string s/Str}}
            :responses {200 {:body s/Any}}}
     :put {:summary "Update meta-data text-date for media-entry"
           :handler handle_update-meta-data-text-date
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str
                               :meta_key_id s/Str}
                        :body {:string s/Str}}
           :responses {200 {:body s/Any}}}
     }]
   
   ["/:media_entry_id/meta-data/:meta_key_id/json/"
    {:post {:summary "Create meta-data json for media-entry"
            :handler handle_create-meta-data-json
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str}
                         :body {:json s/Str}}
            :responses {200 {:body s/Any}}}
     
     :put {:summary "Update meta-data json for media-entry"
           :handler handle_update-meta-data-json
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str
                               :meta_key_id s/Str}
                        :body {:json s/Str}}
           :responses {200 {:body s/Any}}}
     }]
   
   ["/:media_entry_id/meta-data/:meta_key_id/keyword"
    {:get {:summary "Get meta-data keywords for media-entries meta-key"
           :handler handle_get-meta-data-keywords
           :middleware [;wrap-me-add-meta-data
                        sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str
                               :meta_key_id s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/keyword/:keyword_id"
    {:post {:summary "Create meta-data keyword for media-entry."
            :handler handle_create-meta-data-keyword
            :middleware [;wrap-me-add-meta-data
                         wrap-add-keyword
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str
                                :keyword_id s/Str}}
            :responses {200 {:body s/Any}}}
     
     :delete {:summary "Delete meta-data keyword for media-entry."
              :handler handle_delete-meta-data-keyword
              :middleware [wrap-add-keyword
                           sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-metadata]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:media_entry_id s/Str
                                  :meta_key_id s/Str
                                  :keyword_id s/Str}}
              :responses {200 {:body s/Any}}}
     }]
   
   ["/:media_entry_id/meta-data/:meta_key_id/people"
    {:get {:summary "Get meta-data people for media-entries meta-key."
           :handler handle_get-meta-data-people
           :middleware [;wrap-me-add-meta-data
                        sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str
                               :meta_key_id s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/people/:person_id"
    {:post {:summary "Create meta-data people for a media-entries meta-key."
            :handler handle_create-meta-data-people
            :middleware [wrap-add-person
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata
                         wrap-me-add-meta-data
                         ]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str
                                :person_id s/Str}}
            :responses {200 {:body s/Any}}}
     
     :delete {:summary "Delete meta-data people for media-entry"
              :handler handle_delete-meta-data-people
              :middleware [wrap-add-person
                           sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-metadata]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:media_entry_id s/Str
                                  :meta_key_id s/Str
                                  :person_id s/Str}}
              :responses {200 {:body s/Any}}}
     }]
   ; TODO meta-data roles
   ["/:media_entry_id/meta-data/:meta_key_id/role"
    {:get {:summary "Get meta-data role for media-entry."
           :handler handle_get-meta-data-roles
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str
                               :meta_key_id s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/role/:role_id/:person_id"
    {:delete {:summary "Delete meta-data role for media-entry."
              :handler handle_delete-meta-data-role
              :middleware [wrap-add-role
                           wrap-add-person
                           sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-metadata]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:media_entry_id s/Str
                                  :meta_key_id s/Str
                                  :role_id s/Uuid
                                  :person_id s/Uuid}}
              :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/role/:role_id/:person_id/:position"
    {:post {:summary "Create meta-data role for media-entry."
            :handler handle_create-meta-data-role
            :middleware [wrap-add-role
                         wrap-add-person
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str
                                :role_id s/Uuid
                                :person_id s/Uuid
                                :position s/Int
                                }}
            :responses {200 {:body s/Any}}}
     }]

   ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
