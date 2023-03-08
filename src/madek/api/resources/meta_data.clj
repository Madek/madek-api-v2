(ns madek.api.resources.meta-data
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.pagination :as pagination]
    [madek.api.resources.meta-data.index :as meta-data.index]
    [madek.api.resources.meta-data.meta-datum :as meta-datum]
    [madek.api.resources.shared :as sd]
    [madek.api.utils.rdbms :as rdbms]
    [reitit.coercion.schema]
    [schema.core :as s]
    ))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-text
  [req]
  (let [mr (-> req :media-resource)
        user-id (-> req :authenticated-entity :id str)
        mr-type (-> mr :type)
        meta-key-id (-> req :parameters :path :meta_key_id)
        text-data (-> req :body :text)
        ins-data {:meta_key_id meta-key-id
                  :type "MetaDatum::Text"
                  :string text-data
                  :media_entry_id (:id mr)
                  :created_by_id user-id}
        
        ]
    (logging/info "handle_create-meta-data-text"
                  "\nmr\n" mr
                  "\ntype\n" mr-type
                  "\nmeta-key-id\n" meta-key-id
                  "\nins-data\n" ins-data)
    (if-let [ins-result (jdbc/insert! (rdbms/get-ds) :meta_data ins-data)]
      (sd/response_ok ins-result)
      (sd/response_failed {:message "Failed to add meta data text"} 406))
    ))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-text-date
  [req]
  (let [mr (-> req :media-resource)
        user-id (-> req :authenticated-entity :id str)
        mr-type (-> mr :type)
        meta-key-id (-> req :parameters :path :meta_key_id)
        text-data (-> req :body :text)
        ins-data {:meta_key_id meta-key-id
                  :type "MetaDatum::TextDate"
                  :string text-data
                  :media_entry_id (:id mr)
                  :created_by_id user-id}]
    (logging/info "handle_create-meta-data-text-date"
                  "\nmr\n" mr
                  "\ntype\n" mr-type
                  "\nmeta-key-id\n" meta-key-id
                  "\nins-data\n" ins-data)
    (if-let [ins-result (jdbc/insert! (rdbms/get-ds) :meta_data ins-data)]
      (sd/response_ok ins-result)
      (sd/response_failed {:message "Failed to add meta data text-date"} 406))))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-json
  [req]
  (let [mr (-> req :media-resource)
        user-id (-> req :authenticated-entity :id str)
        mr-type (-> mr :type)
        meta-key-id (-> req :parameters :path :meta_key_id)
        json-data (-> req :body :json)
        ins-data {:meta_key_id meta-key-id
                  :type "MetaDatum::JSON"
                  :json json-data
                  :media_entry_id (:id mr)
                  :created_by_id user-id}]
    (logging/info "handle_create-meta-data-json"
                  "\nmr\n" mr
                  "\ntype\n" mr-type
                  "\nmeta-key-id\n" meta-key-id
                  "\nins-data\n" ins-data)
    (if-let [ins-result (jdbc/insert! (rdbms/get-ds) :meta_data ins-data)]
      (sd/response_ok ins-result)
      (sd/response_failed {:message "Failed to add meta data json"} 406))))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-keyword
  [req]
  (let [mr (-> req :media-resource)
        user-id (-> req :authenticated-entity :id str)
        mr-type (-> mr :type)
        meta-key-id (-> req :parameters :path :meta_key_id)
        keyword-data (-> req :keyword)
        ins-data {:meta_key_id meta-key-id
                  :type "MetaDatum::Keywords"
                  (if (= mr-type "MediaEntry") :media_entry_id :collection_id) (-> mr :id str)
                  :created_by_id user-id}]
    (logging/info "handle_create-meta-data-keyword"
                  "\nmr\n" mr
                  "\ntype\n" mr-type
                  "\nmeta-key-id\n" meta-key-id
                  "\nins-data\n" ins-data)

    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (if-let [ins-result (first (jdbc/insert! tx :meta_data ins-data))]

        (let [ip-data {:meta_datum_id (-> ins-result :id str)
                       :keyword_id (-> keyword-data :id str)
                       :created_by_id user-id}]
          (logging/info "handle_create-meta-data-keyword"
                        "\nins-result\n" ins-result
                        "\nip-data\n" ip-data)
          (if-let [ip-result (jdbc/insert! tx :meta_data_keywords ip-data)]
            (sd/response_ok {:meta_data ins-result :mdkeyword ip-result})
            (sd/response_failed {:message "Failed to add meta data keyword"} 406)))
        (sd/response_failed {:message "Failed to add meta data for keyword"} 406)))))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-people
  [req]
  (let [mr (-> req :media-resource)
        user-id (-> req :authenticated-entity :id str)
        mr-type (-> mr :type)
        meta-key-id (-> req :parameters :path :meta_key_id)
        person-data (-> req :person)
        ins-data {:meta_key_id meta-key-id
                  :type "MetaDatum::People"
                  (if (= mr-type "MediaEntry") :media_entry_id :collection_id) (-> mr :id str)
                  :created_by_id user-id}]
    ;(logging/info "handle_create-meta-data-people"
    ;              "\nmr\n" mr
    ;              "\ntype\n" mr-type
    ;              "\nmeta-key-id\n" meta-key-id
    ;              "\nins-data\n" ins-data)
    
    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (if-let [ins-result (first (jdbc/insert! tx :meta_data ins-data))]

        (let [ip-data {:meta_datum_id (-> ins-result :id str)
                       :person_id (-> person-data :id str)
                       :created_by_id user-id
                       }]
          (logging/info "handle_create-meta-data-people"
                        "\nins-result\n" ins-result
                        "\nip-data\n" ip-data)
          (if-let [ip-result (jdbc/insert! tx :meta_data_people ip-data)]
            (sd/response_ok {:meta_data ins-result :mdpeople ip-result})
            (sd/response_failed {:message "Failed to add meta data people"} 406)))
        (sd/response_failed {:message "Failed to add meta data for people"} 406)))))

; TODO tests, response coercion, error handling
(defn handle_create-meta-data-role
  [req]
  (let [mr (-> req :media-resource)
        user-id (-> req :authenticated-entity :id str)
        mr-type (-> mr :type)
        meta-key-id (-> req :parameters :path :meta_key_id)
        role-data (-> req :role)
        ins-data {:meta_key_id meta-key-id
                  :type "MetaDatum::Roles"
                  (if (= mr-type "MediaEntry") :media_entry_id :collection_id) (-> mr :id str)
                  :created_by_id user-id}]
    (logging/info "handle_create-meta-data-role"
                  "\nmr\n" mr
                  "\ntype\n" mr-type
                  "\nmeta-key-id\n" meta-key-id
                  "\nins-data\n" ins-data)

    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (if-let [ins-result (first (jdbc/insert! tx :meta_data ins-data))]

        (let [ip-data {:meta_datum_id (-> ins-result :id str)
                       :role_id (-> role-data :id str)
                       :created_by_id user-id}]
          (logging/info "handle_create-meta-data-role"
                        "\nins-result\n" ins-result
                        "\nip-data\n" ip-data)
          (if-let [ip-result (jdbc/insert! tx :meta_data_roles ip-data)]
            (sd/response_ok {:meta_data ins-result :mdkeyword ip-result})
            (sd/response_failed {:message "Failed to add meta data keyword"} 406)))
        (sd/response_failed {:message "Failed to add meta data for keyword"} 406)))))

(defn wrap-add-keyword [handler]
  (fn [request] (sd/req-find-data
                 request handler
                 :keyword_id
                 "keywords"
                 "id"
                 :keyword
                 true)))

(defn wrap-add-person [handler]
  (fn [request] (sd/req-find-data
                 request handler
                 :person_id
                 "people"
                 "id"
                 :person
                 true
                 )))

(defn wrap-add-role [handler]
  (fn [request] (sd/req-find-data
                 request handler
                 :role_id
                 "roles"
                 "id"
                 :role
                 true)))

(def routes
  (cpj/routes
    (cpj/GET "/media-entries/:media_entry_id/meta-data/" _ meta-data.index/get-index)
    (cpj/GET "/collections/:collection_id/meta-data/" _ meta-data.index/get-index)
    (cpj/GET "/meta-data/:meta_datum_id" _ meta-datum/get-meta-datum)
    (cpj/GET "/meta-data/:meta_datum_id/data-stream" _ meta-datum/get-meta-datum-data-stream)
    (cpj/GET "/meta-data-roles/:meta_datum_id" _ meta-datum/get-meta-datum-role)
    (cpj/ANY "*" _ sd/dead-end-handler)
    ))

(def ring-routes
  ["/meta-data"
   ["/:meta_datum_id" {:get {:handler meta-datum/get-meta-datum
                             :middleware [sd/ring-wrap-add-meta-datum-with-media-resource
                                          sd/ring-wrap-authorization]
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
                                                      sd/ring-wrap-authorization]
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
                        sd/ring-wrap-authorization]
                                                   ; TODO 401s test fails
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:collection_id/meta-data/:meta-key-id/text"
    {:post {:summary "Create meta-data text for collection."
            :handler handle_create-meta-data-text
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str}
                         :body {:text s/Str}}
            :responses {200 {:body s/Any}}}}]

   ["/:collection_id/meta-data/:meta-key-id/text-date"
    {:post {:summary "Create meta-data json for collection."
            :handler handle_create-meta-data-text-date
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str
                                :meta_key_id s/Str}
                         :body {:json s/Str}}
            :responses {200 {:body s/Any}}}}]

   ["/:collection_id/meta-data/:meta-key-id/json"
    {:post {:summary "Create meta-data json for collection."
            :handler handle_create-meta-data-json
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str
                                :meta_key_id s/Str}
                         :body {:json s/Str}}
            :responses {200 {:body s/Any}}}}]
   
   ["/:collection_id/meta-data/:meta-key-id/keyword/:keyword_id"
    {:post {:summary "Create meta-data keyword for collection."
            :handler handle_create-meta-data-keyword
            :middleware [wrap-add-keyword
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str
                                :meta_key_id s/Str
                                :keyword_id s/Str}}
            :responses {200 {:body s/Any}}}}]
   
   ["/:collection_id/meta-data/:meta-key-id/people/:person_id"
    {:post {:summary "Create meta-data people for media-entry"
            :handler handle_create-meta-data-people
            :middleware [wrap-add-person
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str
                                :person_id s/Str}}
            :responses {200 {:body s/Any}}}}]
   
   ["/:collection_id/meta-data/:meta-key-id/role/:role_id"
    {:post {:summary "Create meta-data role for media-entry"
            :handler handle_create-meta-data-role
            :middleware [wrap-add-role
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
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
                        sd/ring-wrap-authorization]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}}
           :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/text/"
    {:post {:summary "Create meta-data text for media-entry"
            :handler handle_create-meta-data-text
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str}
                         :body {:text s/Str}}
            :responses {200 {:body s/Any}}}
     }
    ]
   
   ["/:media_entry_id/meta-data/:meta_key_id/text-date/"
    {:post {:summary "Create meta-data json for media-entry"
            :handler handle_create-meta-data-text-date
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str}
                         :body {:json s/Str}}
            :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/json/"
    {:post {:summary "Create meta-data json for media-entry"
            :handler handle_create-meta-data-json
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str}
                         :body {:json s/Str}}
            :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/keyword/:keyword_id"
    {:post {:summary "Create meta-data keyword for media-entry"
            :handler handle_create-meta-data-keyword
            :middleware [wrap-add-keyword
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str
                                :keyword_id s/Str}}
            :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/people/:person_id"
    {:post {:summary "Create meta-data people for media-entry"
            :handler handle_create-meta-data-people
            :middleware [wrap-add-person
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str
                                :person_id s/Str}}
            :responses {200 {:body s/Any}}}}]
   
   ["/:media_entry_id/meta-data/:meta_key_id/role/:role_id"
    {:post {:summary "Create meta-data role for media-entry"
            :handler handle_create-meta-data-role
            :middleware [wrap-add-role
                         sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str
                                :meta_key_id s/Str
                                :role_id s/Str}}
            :responses {200 {:body s/Any}}}}]

   ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
