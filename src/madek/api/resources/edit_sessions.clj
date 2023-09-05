(ns madek.api.resources.edit-sessions
  (:require
   [clojure.java.jdbc :as jdbc]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [clojure.tools.logging :as logging]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.resources.shared :as sd]
   [reitit.coercion.schema]
   [schema.core :as s]
   [madek.api.pagination :as pagination]
   [madek.api.authorization :as authorization]
   [logbug.catcher :as catcher]))


(defn build-query [query-params]
  (let [col-sel (if (true? (-> query-params :full_data))
                  (sql/select :*)
                  (sql/select :id, :media_entry_id, :collection_id, :created_at))]
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

(defn handle_get-edit-sessions
  [req]
  (let [mr (-> req :media-resource)
        mr-type (-> mr :type)
        mr-id (-> mr :id str)
        col-key (if (= mr-type "MediaEntry") :media_entry_id :collection_id)]
    ;(logging/info "handle_get-edit-sessions" "\ntype\n" mr-type "\nmr-id\n" mr-id "\ncol-name\n" col-name)
    (if-let [result (sd/query-eq-find-all :edit_sessions col-key mr-id)]
      (sd/response_ok result)
      (sd/response_not_found (str "No such edit_session for " mr-type " with id: " mr-id)))))

(defn handle_authed-usr_get-edit-sessions
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



(defn handle_create-edit-session
  [req]
  (try
    (catcher/with-logging {}
      (let [u-id (-> req :authenticated-entity :id)
            mr (-> req :media-resource)
            mr-type (-> mr :type)
            mr-id (-> mr :id str)
            data {:user_id u-id}
            dwid (if (= mr-type "MediaEntry")
                   (assoc data :media_entry_id mr-id)
                   (assoc data :collection_id mr-id))
            ins-result (jdbc/insert! (get-ds) :edit_sessions dwid)]

        (sd/logwrite req (str "handle_create-edit-session:" "\nnew-data: " dwid "\nresult: " ins-result))

        (if-let [result (first ins-result)]
          (sd/response_ok result)
          (sd/response_failed "Could not create edit session." 406))))
    (catch Exception ex (sd/response_exception ex))))


(defn handle_adm_delete-edit-sessions
  [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)]
        (if-let [del-data (sd/query-eq-find-one :edit_sessions :id id)]
          (let [del-clause (sd/sql-update-clause "id" id)
                del-result (jdbc/delete! (rdbms/get-ds) :edit_sessions del-clause)]

            (sd/logwrite req (str "handle_adm_delete-edit-sessions:" "\ndelete data: " del-data "\nresult: " del-result))

            (if (= 1 (first del-result))
              (sd/response_ok del-data)
              (sd/response_failed (str "Failed delete edit_session: " id) 406)))
          (sd/response_failed (str "No such edit_session : " id) 404))))
    (catch Exception ex (sd/response_exception ex))))


(def schema_usr_query_edit_session
  {(s/optional-key :full_data) s/Bool
   (s/optional-key :page) s/Int
   (s/optional-key :count) s/Int
   (s/optional-key :id) s/Uuid
   (s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :collection_id) s/Uuid})

(def schema_adm_query_edit_session

  {(s/optional-key :full_data) s/Bool
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
           :middleware [wrap-authorize-admin!]
           :coercion reitit.coercion.schema/coercion
           :parameters {:query schema_adm_query_edit_session}}}]
   ["/:id"
    {:get {:summary (sd/sum_adm "Get edit_session.")
           :handler handle_adm_get-edit-session
           :middleware [wrap-authorize-admin!]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}}
     :delete {:summary (sd/sum_adm "Delete edit_session.")
              :handler handle_adm_delete-edit-sessions
              :middleware [wrap-authorize-admin!]
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
           :middleware [authorization/wrap-authorized-user]
           :coercion reitit.coercion.schema/coercion
           :parameters {:query schema_usr_query_edit_session}}}]
   
   ["/:id"
    {:get {:summary (sd/sum_usr "Get edit_session.")
           :handler handle_usr_get-edit-session
           :middleware [authorization/wrap-authorized-user]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}}
     
     
     }]
   ])
     
(def media-entry-routes
  ["/media-entry/:media_entry_id/edit_sessions"
   {:get {:summary (sd/sum_usr_pub "Get edit_session list for media entry.")
          :handler handle_get-edit-sessions
          :middleware [sd/ring-wrap-add-media-resource
                       sd/ring-wrap-authorization-view]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:media_entry_id s/Uuid}}
          :responses {200 {:body [schema_export_edit_session]}
                      404 {:body s/Any}}
          }
    :post {:summary (sd/sum_usr "Create edit session for media entry and authed user.")
           :handler handle_create-edit-session
           :middleware [;authorization/wrap-authorized-user
                        sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}}
           :responses {200 {:body schema_export_edit_session}
                       404 {:body s/Any}}}
    }])


(def collection-routes
  ["/collection/:collection_id/edit_sessions"
   {:get {:summary (sd/sum_usr_pub "Get edit_session list for collection.")
          :handler handle_get-edit-sessions
          :middleware [sd/ring-wrap-add-media-resource
                       sd/ring-wrap-authorization-view]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:collection_id s/Uuid}}
          :responses {200 {:body [schema_export_edit_session]}
                      404 {:body s/Any}}}
    
    :post {:summary (sd/sum_usr "Create edit session for collection and authed user.")
           :handler handle_create-edit-session
           :middleware [;authorization/wrap-authorized-user
                        sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}}
           :responses {200 {:body schema_export_edit_session}
                       404 {:body s/Any}}}
    }])
