(ns madek.api.resources.edit-sessions
  (:require
   [clojure.spec.alpha :as sa]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.authorization :as authorization]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS]]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.coercion.spec-alpha-definition-nil :as sp-nil]
   [madek.api.utils.pagination :refer [pagination-handler]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]
   [schema.core :as s]
   [spec-tools.core :as st]))

(defn build-query [query-params]
  (let [col-sel (if (true? (-> query-params :full_data))
                  (sql/select :*)
                  (sql/select :id))]
    (-> col-sel
        (sql/from :edit_sessions)
        (dbh/build-query-param query-params :id)
        (dbh/build-query-param query-params :user_id)
        (dbh/build-query-param query-params :collection_id)
        (dbh/build-query-param query-params :media_entry_id))))

(defn handle_adm_list-edit-sessions
  [req]
  (let [db-query (build-query (-> req :parameters :query))
        db-result (pagination-handler req db-query)]
    ;(info "handle_list-edit-sessions" "\ndb-query\n" db-query "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_usr_list-edit-sessions
  [req]
  (let [req-query (-> req :parameters :query)
        user-id (-> req :authenticated-entity :id)
        usr-query (assoc req-query :user_id user-id)
        db-query (build-query usr-query)
        db-result (pagination-handler req db-query)]
    ;(info "handle_usr_list-edit-sessions" "\ndb-query\n" db-query "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_adm_get-edit-session
  [req]
  (let [id (-> req :parameters :path :id)]
    (if-let [result (dbh/query-eq-find-one :edit_sessions :id id (:tx req))]
      (sd/response_ok result)
      (sd/response_not_found (str "No such edit_session for id: " id)))))

(defn handle_usr_get-edit-session
  [req]
  (let [id (-> req :parameters :path :id)
        u-id (-> req :authenticated-entity :id)]
    (if-let [result (dbh/query-eq-find-one :edit_sessions :id id :user_id u-id (:tx req))]
      (sd/response_ok result)
      (sd/response_not_found (str "No such edit_session for id: " id)))))

(defn handle_get-edit-sessions
  [req]
  (let [mr (-> req :media-resource)
        mr-type (-> mr :type)
        mr-id (-> mr :id str)
        col-key (if (= mr-type "MediaEntry") :media_entry_id :collection_id)]
    ;(info "handle_get-edit-sessions" "\ntype\n" mr-type "\nmr-id\n" mr-id "\ncol-name\n" col-name)
    (if-let [result (dbh/query-eq-find-all :edit_sessions col-key mr-id (:tx req))]
      (sd/response_ok result)
      (sd/response_not_found (str "No such edit_session for " mr-type " with id: " mr-id)))))

(defn handle_authed-usr_get-edit-sessions
  [req]
  (let [u-id (-> req :authenticated-entity :id)
        mr (-> req :media-resource)
        mr-type (-> mr :type)
        tx (:tx req)
        mr-id (-> mr :id str)
        col-key (if (= mr-type "MediaEntry") :media_entry_id :collection_id)]
    ;(info "handle_get-edit-sessions" "\ntype\n" mr-type "\nmr-id\n" mr-id "\ncol-name\n" col-name)
    (if-let [result (dbh/query-eq-find-all :edit_sessions col-key mr-id :user_id u-id tx)]
      (sd/response_ok result)
      (sd/response_not_found (str "No such edit_session for " mr-type " with id: " mr-id)))))

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
            sql-query (-> (sql/insert-into :edit_sessions) (sql/values [dwid]) sql-format)
            ins-result (jdbc/execute-one! (:tx req) sql-query)]

        (sd/logwrite req (str "handle_create-edit-session:" "\nnew-data: " dwid))

        (if-let [ins-result ins-result]
          (sd/response_ok ins-result)
          (sd/response_failed "Could not create edit session." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_adm_delete-edit-sessions
  [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)]
        (if-let [del-data (dbh/query-eq-find-one :edit_sessions :id id (:tx req))]
          (let [sql-query (-> (sql/delete-from :edit_sessions)
                              (sql/where [:= :id id])
                              (sql/returning :*)
                              sql-format)
                del-result (jdbc/execute-one! (:tx req) sql-query)]

            (sd/logwrite req (str "handle_adm_delete-edit-sessions:" "\ndelete data: " del-data "\nresult: " del-result))

            (if del-result
              (sd/response_ok del-result)
              (sd/response_failed (str "Failed delete edit_session: " id) 406)))
          (sd/response_failed (str "No such edit_session : " id) 404))))
    (catch Exception ex (sd/parsed_response_exception ex))))

(sa/def ::query-usr-def (sa/keys :opt-un [::sp/id ::sp/full_data ::sp/media_entry_id ::sp/collection_id ::sp/page ::sp/size]))

(sa/def ::query-def (sa/keys :opt-un [::sp/id ::sp/full_data ::sp/user_id ::sp/media_entry_id ::sp/collection_id ::sp/page ::sp/size]))

(sa/def ::session-adm-def (sa/keys :req-un [::sp/id] :opt-un [::sp/user_id ::sp/created_at ::sp-nil/media_entry_id ::sp-nil/collection_id]))

(sa/def :list/session (st/spec {:spec (sa/coll-of ::session-adm-def)
                                :description "A list of sessions"}))

(sa/def :list/edit-session-both (sa/keys :opt-un [::sp/data ::sp/pagination]))

(def schema_export_edit_session
  {:id s/Uuid
   :user_id s/Uuid
   :created_at s/Any
   :media_entry_id (s/maybe s/Uuid)
   :collection_id (s/maybe s/Uuid)})

(def admin-routes
  ["/"
   {:openapi {:tags ["admin/edit_sessions"] :security ADMIN_AUTH_METHODS}}
   ["edit_sessions"
    {:get {:summary (sd/sum_adm "List edit_sessions.")
           :handler handle_adm_list-edit-sessions
           :middleware [wrap-authorize-admin!]
           :coercion spec/coercion
           :responses {200 {:description "Returns the edit sessions."
                            :body (sa/or :flat :list/session :paginated :list/edit-session-both)}}
           :parameters {:query ::query-def}}}]

   ["edit_sessions/:id"
    {:get {:summary (sd/sum_adm "Get edit_session.")
           :handler handle_adm_get-edit-session
           :middleware [wrap-authorize-admin!]
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:description "Returns the edit session."
                            :body schema_export_edit_session}
                       404 {:description "Edit session not found."
                            :body s/Any}}
           :parameters {:path {:id s/Uuid}}}
     :delete {:summary (sd/sum_adm "Delete edit_session.")
              :handler handle_adm_delete-edit-sessions
              :middleware [wrap-authorize-admin!]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:description "Returns the deleted edit session."
                               :body schema_export_edit_session}
                          404 {:description "Edit session not found."
                               :body s/Any}}}}]])

(def query-routes
  ["/"
   {:openapi {:tags ["edit_sessions"]}}
   ["edit_sessions"
    {:get {:summary (sd/sum_usr "List authed users edit_sessions.")
           :handler handle_usr_list-edit-sessions
           :middleware [authorization/wrap-authorized-user]
           :coercion spec/coercion
           :responses {200 {:description "Returns the edit sessions."
                            :body (sa/or :flat :list/session :paginated :list/edit-session-both)}}
           :parameters {:query ::query-usr-def}}}]

   ["edit_sessions/:id"
    {:get {:summary (sd/sum_usr "Get edit_session.")
           :handler handle_usr_get-edit-session
           :middleware [authorization/wrap-authorized-user]
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:description "Returns the edit session."
                            :body schema_export_edit_session}
                       404 {:description "Edit session not found."
                            :body s/Any}}
           :parameters {:path {:id s/Uuid}}}}]])

(def media-entry-routes
  ["/media-entry/:media_entry_id/edit_sessions"
   {:openapi {:tags ["api/media-entry"]}}
   {:get {:summary (sd/sum_usr_pub "Get edit_session list for media entry.")
          :handler handle_get-edit-sessions
          :middleware [jqh/ring-wrap-add-media-resource
                       jqh/ring-wrap-authorization-view]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:media_entry_id s/Uuid}}
          :responses {200 {:description "Returns the edit sessions."
                           :body [schema_export_edit_session]}
                      404 {:description "Media entry not found."
                           :body s/Any}}}
    :post {:summary (sd/sum_usr "Create edit session for media entry and authed user.")
           :handler handle_create-edit-session
           :middleware [;authorization/wrap-authorized-user
                        jqh/ring-wrap-add-media-resource
                        jqh/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}}
           :responses {200 {:description "Returns the created edit session."
                            :body schema_export_edit_session}
                       404 {:description "Media entry not found."
                            :body s/Any}}}}])

(def collection-routes
  ["/collection/:collection_id/edit_sessions"
   {:openapi {:tags ["api/collection"]}}
   {:get {:summary (sd/sum_usr_pub "Get edit_session list for collection.")
          :handler handle_get-edit-sessions
          :middleware [jqh/ring-wrap-add-media-resource
                       jqh/ring-wrap-authorization-view]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:collection_id s/Uuid}}
          :responses {200 {:description "Returns the edit sessions."
                           :body [schema_export_edit_session]}
                      404 {:description "Collection not found."
                           :body s/Any}}}

    :post {:summary (sd/sum_usr "Create edit session for collection and authed user.")
           :handler handle_create-edit-session
           :middleware [;authorization/wrap-authorized-user
                        jqh/ring-wrap-add-media-resource
                        jqh/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}}
           :responses {200 {:description "Returns the created edit session."
                            :body schema_export_edit_session}
                       404 {:description "Collection not found."
                            :body s/Any}}}}])
