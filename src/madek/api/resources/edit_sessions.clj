(ns madek.api.resources.edit-sessions
    (:require
   [clojure.java.jdbc :as jdbc]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [reitit.coercion.schema]
   [schema.core :as s]
   
   [madek.api.pagination :as pagination]))

; TODO create edit session as timestamp for meta-data updates

(defn build-query [query-params]
  (let [col-sel (if (true? (-> query-params :full-data))
                  (sql/select :*)
                  (sql/select :id, :media_entry_id, :collection_id))]
    (-> col-sel
        (sql/from :edit_sessions)
        (sd/build-query-param query-params :id)
        (sd/build-query-param query-params :user_id)
        (sd/build-query-param query-params :collection_id)
        (sd/build-query-param query-params :media_entry_id)
        (pagination/add-offset-for-honeysql query-params)
        sql/format)))


(defn handle_adm_list-edit-sessions
  [req]
  (let [db-query (build-query (-> req :parameters :query))
        db-result (jdbc/query (get-ds) db-query)]
    ;(logging/info "handle_list-edit-sessions" "\ndb-query\n" db-query "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_usr_list-edit-sessions
  [req]
  (let [req-query (-> req :parameters :query)
        user-id (-> req :authenticated-entity :id)
        usr-query (assoc req-query :user_id user-id)
        db-query (build-query usr-query)
        db-result (jdbc/query (get-ds) db-query)]
    ;(logging/info "handle_usr_list-edit-sessions" "\ndb-query\n" db-query "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_adm_get-edit-session
  [req]
  (let [id (-> req :parameters :path :id)]
    (if-let [result (sd/query-eq-find-one :edit_sessions :id id)]
      (sd/response_ok result)
      (sd/response_not_found (str "No such edit_session for id: " id)))))

(defn handle_usr_get-edit-session
  [req]
  (let [id (-> req :parameters :path :id)
        u-id (-> req :authenticated-entity :id)]
    (if-let [result (sd/query-eq2-find-one :edit_sessions :id id :user_id u-id)]
      (sd/response_ok result)
      (sd/response_not_found (str "No such edit_session for id: " id)))))

(defn handle_usr_get-edit-sessions
  [req]
  (let [u-id (-> req :authenticated-entity :id)
        mr (-> req :media-resource)
        mr-type (-> mr :type)
        mr-id (-> mr :id str)
        col-key (if (= mr-type "MediaEntry") :media_entry_id :collection_id)]
    ;(logging/info "handle_get-edit-sessions" "\ntype\n" mr-type "\nmr-id\n" mr-id "\ncol-name\n" col-name)
    (if-let [result (sd/query-eq2-find-all :edit_sessions col-key mr-id :user_id u-id)]
      (sd/response_ok result)
      (sd/response_not_found (str "No such edit_session for " mr-type " with id: " mr-id)))
    ))

;(defn handle_usr_create-edit-sessions
;  [req]
;  (let [u-id (-> req :authenticated-entity :id)
;        mr (-> req :media-resource)
;        mr-type (-> mr :type)
;        mr-id (-> mr :id str)
;        data (-> req :parameters :body)
;        dwid (if (= mr-type "MediaEntry")
;               (assoc data :media_entry_id mr-id :user_id u-id)
;               (assoc data :collection_id mr-id :user_id u-id))
;        ]
;    ;(logging/info "handle_create-edit-sessions" "\ntype\n" mr-type "\nmr-id\n" mr-id "\ndwid\n" dwid)
;    (if-let [ins-res (first (jdbc/insert! (get-ds) :edit_sessions dwid))]
;      (sd/response_ok ins-res) 
;      (sd/response_failed "Could not create edit_session." 406))))


; TODO use wrapper
; TODO check if own entity or auth is admin
(defn handle_adm_delete-edit-sessions
  [req]
  (let [id (-> req :parameters :path :id)]
    (if-let [del-data (sd/query-eq-find-one :edit_sessions :id id)]
      (if (= 1 (first (jdbc/delete! (rdbms/get-ds) :edit_sessions (sd/sql-update-clause :id id))))
        (sd/response_ok del-data)
        (sd/response_failed (str "Failed delete edit_session: " id) 406))
      (sd/response_failed (str "No such edit_session : " id) 404))
    
    ))

;(defn handle_usr_delete-edit-sessions
;  [req]
;  (let [id (-> req :parameters :path :id)
;        u-id (-> req :authenticated-entity :id)
;        ]
;    (if-let [del-data (sd/query-eq2-find-one :edit_sessions :id id :user_id u-id)]
;      (if (= 1 (first (jdbc/delete! (rdbms/get-ds) :edit_sessions (sd/sql-update-clause :id id))))
;        (sd/response_ok del-data)
;        (sd/response_failed (str "Failed delete edit_session: " id) 406))
;      (sd/response_failed (str "No such edit_session : " id) 404))))

(def schema_usr_query_edit_session
  {(s/optional-key :full-data) s/Bool
   (s/optional-key :page) s/Int
   (s/optional-key :count) s/Int
   (s/optional-key :id) s/Uuid
   (s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :collection_id) s/Uuid})

(def schema_adm_query_edit_session

  {(s/optional-key :full-data) s/Bool
   (s/optional-key :page) s/Int
   (s/optional-key :count) s/Int
   (s/optional-key :id) s/Uuid
   (s/optional-key :user_id) s/Uuid
   (s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :collection_id) s/Uuid})

(def schema_export_edit_session
  {:id s/Uuid
   :user_id s/Uuid
   :created_at s/Any
   :media_entry_id (s/maybe s/Uuid)
   :collection_id (s/maybe s/Uuid)
   })

(def admin-routes
  ["/edit_sessions"
   ["/"
    {:get {:summary (sd/sum_adm "List edit_sessions.")
           :handler handle_adm_list-edit-sessions
           :coercion reitit.coercion.schema/coercion
           :parameters {:query schema_adm_query_edit_session}}}]
   ["/:id"
    {:get {:summary (sd/sum_adm "Get edit_session.")
           :handler handle_adm_get-edit-session
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}}
     :delete {:summary (sd/sum_adm "Delete edit_session.")
              :handler handle_adm_delete-edit-sessions
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:id s/Str}}
              :responses {200 {:body schema_export_edit_session}
                          404 {:body s/Any}}}
    
     }]])


(def query-routes
  ["/edit_sessions"
   ["/"
    {:get {:summary (sd/sum_usr "List authed users edit_sessions.")
           :handler handle_usr_list-edit-sessions
           :coercion reitit.coercion.schema/coercion
           :parameters {:query schema_usr_query_edit_session}}}]
   
   ["/:id"
    {:get {:summary (sd/sum_usr "Get edit_session.")
           :handler handle_usr_get-edit-session
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}}
     
     ;:delete {:summary (sd/sum_usr "Delete authed users edit_session for id.")
     ;         :handler handle_usr_delete-edit-sessions
     ;         :coercion reitit.coercion.schema/coercion
     ;         :parameters {:path {:id s/Str}}
     ;         :responses {200 {:body schema_export_edit_session}
     ;                     404 {:body s/Any}}}
     }]
   ])
     
(def media-entry-routes
  ["/media-entry/:media_entry_id/edit_session"
   {:get {:summary "Get user edit_session list for media entry."
          :handler handle_usr_get-edit-sessions
          :middleware [sd/ring-wrap-add-media-resource
                       sd/ring-wrap-authorization-view]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:media_entry_id s/Str}}
          :responses {200 {:body [schema_export_edit_session]}
                      404 {:body s/Any}}
          }
    
    ;:post {:summary (sd/sum_usr "Create edit_session for media entry.")
    ;       :handler handle_usr_create-edit-sessions
    ;       :middleware [sd/ring-wrap-add-media-resource
    ;                    sd/ring-wrap-authorization-view]
    ;       :coercion reitit.coercion.schema/coercion
    ;       :parameters {:path {:media_entry_id s/Str}}
    ;       :responses {200 {:body schema_export_edit_session}
    ;                   406 {:body s/Any}}}
    

    
    }])


(def collection-routes
  ["/collection/:collection_id/edit_session"
   {:get {:summary "Get authed users edit_session list for collection."
          :handler handle_usr_get-edit-sessions
          :middleware [sd/ring-wrap-add-media-resource
                       sd/ring-wrap-authorization-view]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:collection_id s/Str}}
          :responses {200 {:body [schema_export_edit_session]}
                      404 {:body s/Any}}}

    ;:post {:summary (sd/sum_usr "Create edit_session for collection.")
    ;       :handler handle_usr_create-edit-sessions
    ;       :middleware [sd/ring-wrap-add-media-resource
    ;                    sd/ring-wrap-authorization-view]
    ;       :coercion reitit.coercion.schema/coercion
    ;       :parameters {:path {:collection_id s/Str}}
    ;       :responses {200 {:body schema_export_edit_session}
    ;                   406 {:body s/Any}}}
    
   
    ;:delete {:summary (sd/sum_todo "Delete edit_session for collection.")
    ;         :handler handle_delete-edit-sessions
    ;         :middleware [sd/ring-wrap-add-media-resource
    ;                      sd/ring-wrap-authorization-view]
    ;         :coercion reitit.coercion.schema/coercion
    ;         :parameters {:path {:collection_id s/Str}}
    ;         :responses {200 {:body schema_export_edit_session}
    ;                     404 {:body s/Any}}}
    }])

; TODO tests