(ns madek.api.resources.meta-data
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    
    [madek.api.resources.meta-data.index :as meta-data.index]
    [madek.api.resources.meta-data.meta-datum :as meta-datum]
    [madek.api.resources.shared :as sd]
    [madek.api.utils.rdbms :as rdbms]
    [reitit.coercion.schema]
    [reitit.coercion.spec]
    [schema.core :as s]
    [cheshire.core :as cheshire]
    
    [madek.api.utils.sql :as sql]
    ))

; TODO create edit session as timestamp for meta-data updates

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
  [mr mk-id md-type]
  (let [mr-id (str (-> mr :id))
        mr-key (col-key-for-mr-type mr)
        db-result (sd/query-eq2-find-one
                   :meta_data 
                   mr-key mr-id
                   :meta_key_id mk-id)
        db-type (:type db-result)]
    (if (or (= nil md-type) (= md-type db-type))
      db-result
      ;(((logging/error "db-get-meta-data: type mismatch: requested [" md-type "] but got [" db-type "]"
      ;                 "\nmr-id: " mr-id
      ;                 "\nmr-key: " mr-key
      ;                 "\nmk-id: " mk-id
      ;                 "\ndb-result: " db-result))
      ; nil)
      nil
      )
    ))

(defn- db-create-meta-data
  ([db meta-data]
   (logging/info "db-create-meta-data: " meta-data)
(let [result (first (jdbc/insert! db :meta_data meta-data))]
  (logging/info "db-create-meta-data: " result)
  result))

  ([db mr meta-key-id md-type user-id]
   (logging/info "db-create-meta-data: " "MK-ID: " meta-key-id "Type:" md-type "User: "user-id)
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
     (db-create-meta-data db md))
   )

  )

(defn- handle_update-meta-data-text-base
  [req md-type upd-data]
  (let [mr (-> req :media-resource)
        upd-data2 (assoc upd-data (col-key-for-mr-type mr) (:id mr))
        meta-key-id (-> req :parameters :path :meta_key_id)
        upd-clause (sql-cls-upd-meta-data-typed-id mr meta-key-id md-type)
        upd-result (jdbc/update! (rdbms/get-ds) :meta_data upd-data2 upd-clause)
        result-data (db-get-meta-data mr meta-key-id md-type)
        ]

    (logging/info "handle_update-meta-data-text-base "
                  "\nmd-type\n" md-type
                  "\nmeta-key-id\n" meta-key-id
                  "\nupd-data\n" upd-data2
                  "\nupd-clause\n" upd-clause
                  "\nupd-result\n" upd-result
                  )
    (if (= 1 (first upd-result))
      (sd/response_ok {:result result-data})
      (sd/response_failed {:message "Failed to update meta data text base"} 406))))



; TODO tests, response coercion, error handling
(defn handle_create-meta-data-text
  [req]
  (let [mr (-> req :media-resource)
        user-id (-> req :authenticated-entity :id str)
        meta-key-id (-> req :parameters :path :meta_key_id)
        text-data (-> req :parameters :body :string)
        md-type "MetaDatum::Text"
        ;mdnew (-> (fabric-meta-data mr meta-key-id md-type user-id)
        ;          (assoc  :string text-data))
        mdnew {:string text-data}
        ins-result (db-create-meta-data (rdbms/get-ds) mr meta-key-id md-type user-id mdnew)]
    (logging/info "handle_create-meta-data-text"
                  "\nmr-id\n" (:id mr) "\nmeta-key-id\n" meta-key-id
                  "\ninserted:\n" ins-result)
    (if (= md-type (:type ins-result))
     (sd/response_ok ins-result)
      (sd/response_failed {:message "Failed to add meta data text"} 406))))

(defn handle_update-meta-data-text
  [req]
  (let [text-data (-> req :parameters :body :string)
        upd-data {:string text-data }
        md-type "MetaDatum::Text"]
        
    (logging/info "handle_update-meta-data-text" "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))

; TODO tests, response coercion, error handling
; TODO multi line ? or other md params
(defn handle_create-meta-data-text-date
  [req]
  (let [mr (-> req :media-resource)
        user-id (-> req :authenticated-entity :id str)
        meta-key-id (-> req :parameters :path :meta_key_id)
        text-data (-> req :parameters :body :string)
        md-type "MetaDatum::TextDate"
        ;mdnew (-> (fabric-meta-data mr meta-key-id md-type user-id)
        ;          (assoc  :string text-data))
        mdnew {:string text-data}
        ins-result (db-create-meta-data (rdbms/get-ds) mr meta-key-id md-type user-id mdnew)]
    (logging/info "handle_create-meta-data-text-date"
                  "\nmr-id\n" (:id mr) "\nmeta-key-id\n" meta-key-id
                  "\ninserted:\n" ins-result)
    (if (= md-type (:type ins-result))
      (sd/response_ok ins-result)
      (sd/response_failed {:message "Failed to add meta data text-date"} 406))))

(defn handle_update-meta-data-text-date
  [req]
  (let [text-data (-> req :parameters :body :string)
        upd-data {:string text-data}
        ; TODO multi line ? or other params
        md-type "MetaDatum::TextDate"]
    (logging/info "handle_update-meta-data-text-date" "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-json
  [req]
  (let [mr (-> req :media-resource)
        user-id (-> req :authenticated-entity :id str)
        meta-key-id (-> req :parameters :path :meta_key_id)
        json-data (-> req :parameters :body :json)
        json-parsed (cheshire/parse-string json-data)
        md-type "MetaDatum::JSON"
        ;mdnew (-> (fabric-meta-data mr meta-key-id md-type user-id)
        ;          (assoc  :json json-parsed))
        mdnew {:json json-parsed}
        ins-result (db-create-meta-data (rdbms/get-ds) mr meta-key-id md-type user-id mdnew)
        ]
    (logging/info "handle_create-meta-data-json" 
                  "\nmr-id\n" (:id mr) "\nmeta-key-id\n" meta-key-id
                  "\ninserted:\n" ins-result)
    ;(if-let [ins-result (first (jdbc/insert! (rdbms/get-ds) :meta_data mdnew))]  
    (if (= md-type (:type ins-result))    
      (sd/response_ok ins-result)
      (sd/response_failed {:message "Failed to add meta data json"} 406))))

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
    (logging/info "db-create-meta-data-keyword"
                  "\nkw-data\n" data
                  "\nresult\n" result)
    result))

(defn- db-delete-meta-data-keyword
  [db md-id kw-id]
  (let [query ["meta_datum_id = ? AND keyword_id = ?" md-id kw-id]
        result (jdbc/delete! db :meta_data_keywords query)]
    (logging/info "db-delete-meta-data-keyword"
                  "\nmd-id\n" md-id
                  "\nkw-id\n" kw-id
                  "\nresult\n" result)
    result
    )
  )

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-keyword
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        kw-id (-> req :parameters :path :keyword_id)
        user-id (-> req :authenticated-entity :id str)
        md-type "MetaDatum::Keywords"
        meta-data (db-get-meta-data mr meta-key-id nil)
        ;mdnew-data (fabric-meta-data mr meta-key-id md-type user-id)
        ]
    (logging/info "handle_create-meta-data-keyword"
                  "\nmr-id\n" (:id mr) 
                  "\nmeta-data\n" meta-data)

    (if-let [meta-data (db-get-meta-data mr meta-key-id md-type)]
      ;[md-id (:id meta-data)]
      ; already has meta-data
      (if-let [result (db-create-meta-data-keyword (rdbms/get-ds) (:id meta-data) kw-id user-id)]
        (sd/response_ok {:meta_data meta-data :mdkeyword result})
        (sd/response_failed {:message "Failed to add meta data keyword"} 406))
      ; create meta-data and meta-data-people
      (jdbc/with-db-transaction [tx (rdbms/get-ds)]
        (if-let [mdins-result (db-create-meta-data tx mr meta-key-id md-type user-id)]
          (if-let [ip-result (db-create-meta-data-keyword tx (-> mdins-result :id str) kw-id user-id)]
            (sd/response_ok {:meta_data mdins-result :mdkeyword ip-result})
            (sd/response_failed {:message "Failed to add meta data keyword"} 406))
          (sd/response_failed {:message "Failed to add meta data for keyword"} 406))))))

   
(defn db-get-meta-data-keywords 
  [md-id]
  (sd/query-eq-find-all :meta_data_keywords :meta_datum_id md-id))

(defn handle_get-meta-data-keywords
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        full-data (-> req :parameters :path :full_data)
        md-type "MetaDatum::Keywords"]
    (if-let [md (db-get-meta-data mr meta-key-id md-type)]
      (let [md-id (-> md :id)
            mdr (db-get-meta-data-keywords md-id)
            mdr-ids (map (-> :keyword_id) mdr)
            keywords (map #(sd/query-eq-find-one :keywords :id %) mdr-ids)
            result {:meta_data md
                    :keyword_ids2 mdr-ids
                    ;:meta-data-keywords mdr
                    :keywords keywords}]
        (sd/response_ok result))
      (sd/response_failed "Not found" 404))
    ))

(defn handle_delete-meta-data-keyword
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        mdkw-id (-> req :parameters :path :keyword_id)
        md-type "MetaDatum::Keywords"
        md (db-get-meta-data mr meta-key-id md-type)
        md-id (-> md :id)
        
        delete-result (db-delete-meta-data-keyword (rdbms/get-ds) md-id mdkw-id)
        mdr (db-get-meta-data-keywords md-id)]

    (logging/info "handle_delete-meta-data-keyword"
                  "\ndelete-result\n" delete-result
                  "\nmeta-data\n" md
                  "\nmeta-data-keywords\n" mdr
                  )
    (sd/response_ok {:meta_data md :meta-data-keywords mdr})))
    
    ;(jdbc/with-db-transaction [tx (rdbms/get-ds)]
    ;  (if-let [mdr-del (= 1 (first (jdbc/delete! tx :meta_data_keywords mdr-clause)))]
    ;    (if-let [md-del (= 1 (first (jdbc/delete! tx :meta_data md-clause)))]
    ;      (sd/response_ok {:meta_data md :meta-data-role mdr})
    ;      (sd/response_failed {:message "Failed to delete meta data role"} 406))
    ;    (sd/response_failed {:message "Failed to delete meta data for role"} 406)))))



(defn- db-create-meta-data-people 
  [db md-id person-id user-id]
  (let [data {:meta_datum_id md-id
              :person_id person-id
              :created_by_id user-id}
        result (jdbc/insert! db :meta_data_people data)]
        (logging/info "db-create-meta-data-people"
                      "\npeople-data\n" data
                      "\nresult\n" result)
        result))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-people
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        person-id (-> req :parameters :path :person_id)
        user-id (-> req :authenticated-entity :id str)
        md-type "MetaDatum::People"
        meta-data (db-get-meta-data mr meta-key-id md-type)
        meta-data2 (-> req :meta-data)
        ]
    (logging/info "handle_create-meta-data-people"
                  "\nmr\n" mr
                  "\nmeta-data\n" meta-data
                  "\nmeta-data2\n" meta-data2)
    (if-let [md-id (:id meta-data)]
      ; already has meta-data
      (if-let [result (db-create-meta-data-people (rdbms/get-ds) (:id meta-data) person-id user-id)]
        (sd/response_ok {:meta_data meta-data :mdpeople result})
        (sd/response_failed {:message "Failed to add meta data people"} 406))
      ; create meta-data and meta-data-people
      (jdbc/with-db-transaction [tx (rdbms/get-ds)]
        (if-let [mdins-result (db-create-meta-data tx mr meta-key-id md-type user-id)]
            (if-let [ip-result (db-create-meta-data-people tx (-> mdins-result :id str) person-id user-id)]
              (sd/response_ok {:meta_data mdins-result :mdpeople ip-result})
              (sd/response_failed {:message "Failed to add meta data people"} 406))
          (sd/response_failed {:message "Failed to add meta data for people"} 406))
        )
      )))

(defn db-get-meta-data-people 
  [md-id]
  (sd/query-eq-find-all :meta_data_people :meta_datum_id md-id))

(defn handle_get-meta-data-people
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        md-type "MetaDatum::People"
        md (db-get-meta-data mr meta-key-id md-type)
        md-id (-> md :id)
        mdr (db-get-meta-data-people md-id)
        mdr-ids (map (-> :person_id) mdr)
        people (map #(sd/query-eq-find-one :people :id %) mdr-ids)]
    (sd/response_ok {:meta_data md :people_ids mdr-ids :meta-data-people mdr :people people})
    ))


(defn handle_delete-meta-data-people
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        person-id (-> req :parameters :path :person_id)
        md-type "MetaDatum::People"
        md (db-get-meta-data mr meta-key-id md-type)
        md-id (-> md :id)
        mdr-clause ["meta_datum_id = ? AND person_id = ?" md-id person-id]]

    (logging/info "handle_delete-meta-data-people"
                  "\nmeta-data\n" md)
    (if (= 1 (first (jdbc/delete! (rdbms/get-ds) :meta_data_people mdr-clause)))
      (sd/response_ok {:meta_data md :meta-data-people (db-get-meta-data-people md-id)})
      (sd/response_failed {:message "Failed to delete meta data people"} 406))
    ))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-role
  [req]
  (let [mr (-> req :media-resource)
        user-id (-> req :authenticated-entity :id str)
        meta-key-id (-> req :parameters :path :meta_key_id)
        role-id (-> req :parameters :path :role_id)
        ;person_id
        ;position
        md-type "MetaDatum::Roles"
        mdnew-data (fabric-meta-data mr meta-key-id md-type user-id)]
    
    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (if-let [mdins-result (first (jdbc/insert! tx :meta_data mdnew-data))]

        (let [ir-data {:meta_datum_id (-> mdins-result :id str)
                       :role_id role-id
                       :created_by_id user-id}]
          (logging/info "handle_create-meta-data-role"
                        "\nmeta-data-result\n" mdins-result
                        "\nrole-data\n" ir-data)
          (if-let [ip-result (jdbc/insert! tx :meta_data_roles ir-data)]
            (sd/response_ok {:meta_data mdins-result :mdkeyword ip-result})
            (sd/response_failed {:message "Failed to add meta data role"} 406)))
        (sd/response_failed {:message "Failed to add meta data for role"} 406)))))

(defn db-get-meta-data-role [md-id]
  (sd/query-eq-find-all :meta_data_roles :meta_datum_id md-id))

(defn handle_delete-meta-data-role
  [req]
  (let [mr (-> req :media-resource)
        meta-key-id (-> req :parameters :path :meta_key_id)
        ;role-id (-> req :parameters :path :role_id)
        md-type "MetaDatum::Roles"
        md (db-get-meta-data mr meta-key-id md-type)
        md-id (-> md :id)
        mdr (db-get-meta-data-role md-id)
        md-clause (sd/sql-update-clause :id md-id)
        mdr-clause (sd/sql-update-clause :meta_datum_id md-id)
        ]
    
    (logging/info "handle_delete-meta-data-role"
                  "\nmeta-data\n" md
                  "\nmeta-data-role\n" mdr)
    
    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (if-let [mdr-del (= 1 (first (jdbc/delete! tx :meta_data_roles mdr-clause)))]
        (if-let [md-del (= 1 (first (jdbc/delete! tx :meta_data md-clause)))]
          (sd/response_ok {:meta_data md :meta-data-role mdr})
          (sd/response_failed {:message "Failed to delete meta data role"} 406))
        (sd/response_failed {:message "Failed to delete meta data for role"} 406)
        )
      )
    ))

(defn- add-meta-data-extra [result]
    (let [md-id (:id result)
          md-type (:type result)
            
          md-type-kw (case md-type
                       "MetaDatum::Keywords" "md_keywords"
                       "MetaDatum::People" "md_people"
                       "MetaDatum::Roles" "md_roles"
                       "default" )
  
          md-type-kw-data (apply str md-type-kw "_data")
  
          mde (case md-type
                "MetaDatum::Keywords" (db-get-meta-data-keywords md-id)
                "MetaDatum::People" (db-get-meta-data-people md-id)
                "MetaDatum::Roles" (db-get-meta-data-role md-id)
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
                      (keyword md-type) mde
                      (keyword md-type-kw) mde
                      (keyword md-type-kw-data) mde-data
                      (keyword (apply str md-type "-data")) mde-data}]
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
  (fn [request] (sd/req-find-data-search2
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


(def ring-routes
  ["/meta-data"
   ["/:meta_datum_id" {:get {:handler meta-datum/get-meta-datum
                             :middleware [sd/ring-wrap-add-meta-datum-with-media-resource
                                          sd/ring-wrap-authorization-view]
                             :summary "Get meta-data for id"
                             :description "Get meta-data for id. TODO: should return 404, if no such meta-data role exists."
                             :coercion reitit.coercion.schema/coercion
                             :parameters {:path {:meta_datum_id s/Str}}
                              ; TODO coercion
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
                        sd/ring-wrap-authorization-edit-metadata]
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
            :middleware [wrap-add-keyword
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
            :middleware [wrap-add-person
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
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :query {(s/optional-key :updated_after) s/Str
                                (s/optional-key :meta_keys) s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id"
    {:get {:summary "Get meta-data for media-entry and meta-key."
           :handler handle_get-meta-key-meta-data
                                                 
           :middleware [wrap-add-meta-key
                        wrap-check-vocab
                        sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata
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
           :middleware [wrap-me-add-meta-data
                        sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str
                               :meta_key_id s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/keyword/:keyword_id"
    {:post {:summary "Create meta-data keyword for media-entry."
            :handler handle_create-meta-data-keyword
            :middleware [wrap-add-keyword
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
           :middleware [wrap-me-add-meta-data
                        sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str
                               :meta_key_id s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/people/:person_id"
    {:post {:summary "Create meta-data people for a media-entries meta-key."
            :handler handle_create-meta-data-people
            :middleware [;wrap-me-add-meta-data
                         wrap-add-person
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
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
   ["/:media_entry_id/meta-data/:meta_key_id/role/:role_id"
    {:post {:summary "Create meta-data role for media-entry."
            :handler handle_create-meta-data-role
            :middleware [wrap-add-role
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str
                                :role_id s/Str}}
            :responses {200 {:body s/Any}}}
     
     :delete {:summary "Delete meta-data role for media-entry."
              :handler handle_delete-meta-data-role
              :middleware [wrap-add-role
                           sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-metadata]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:media_entry_id s/Str
                                  :meta_key_id s/Str
                                  :role_id s/Str}}
              :responses {200 {:body s/Any}}}
     
     }]

   ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
