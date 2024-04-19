(ns madek.api.resources.favorite-media-entries
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.authorization :as authorization]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [t]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [error info]]))

(def res-req-name :favorite_media_entry)
(def res-table-name "favorite_media_entries")
(def res-col-name :media_entry_id)

(defn handle_list-favorite_media_entries
  [req]
  (let [db-result (sd/query-find-all :favorite_media_entries :* (:tx req))]
    ;(info "handle_list-favorite_media_entry" "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_list-favorite_media_entries-by-user
  [req]
  (let [;full-data (true? (-> req :parameters :query :full-data))
        user-id (-> req :authenticated-entity :id)
        db-result (sd/query-eq-find-all :favorite_media_entries :user_id user-id (:tx req))
        id-set (map :media_entry_id db-result)]
    ;(info "handle_list-favorite_media_entry" "\nresult\n" db-result "\nid-set\n" id-set)
    (sd/response_ok {:media_entry_ids id-set})))

(defn handle_get-favorite_media_entry
  [req]
  (let [favorite_me (-> req res-req-name)]
    ;(info "handle_get-favorite_media_entry" favorite_collection)
    (sd/response_ok favorite_me)))

; TODO logwrite
(defn handle_create-favorite_media_entry
  [req]
  (try
    (catcher/with-logging {}
      (let [user-id (or (-> req :user :id) (-> req :authenticated-entity :id))
            media_entry (-> req :media_entry)
            data {:user_id user-id :media_entry_id (:id media_entry)}]
        (if-let [favorite_media_entry (-> req res-req-name)]
          ; already has favorite_media_entry
          (sd/response_ok favorite_media_entry)
          (let [sql-query (-> (sql/insert-into :favorite_media_entries)
                              (sql/values [data])
                              sql-format)
                ins-res (jdbc/execute-one! (:tx req) sql-query)]
            (if ins-res
              (sd/response_ok ins-res)
              (sd/response_failed "Could not create favorite_media_entry." 406))))))

    (catch Exception ex (sd/response_exception ex))))

; TODO logwrite
(defn handle_delete-favorite_media_entry
  [req]
  (try
    (catcher/with-logging {}
      (let [favorite_media_entry (-> req res-req-name)
            user-id (:user_id favorite_media_entry)
            media_entry-id (res-col-name favorite_media_entry)]
        (let [sql-query (-> (sql/delete-from :favorite_media_entries)
                            (sql/where [:= :user_id user-id] [:= :media_entry_id media_entry-id])
                            sql-format)
              del-result (jdbc/execute-one! (:tx req) sql-query)]
          (if (= 1 del-result)
            (sd/response_ok favorite_media_entry)
            (error "Failed delete favorite_media_entry "
                   "user-id: " user-id "media_entry-id: " media_entry-id)))))
    (catch Exception ex (sd/response_exception ex))))

(defn wwrap-find-favorite_media_entry [send404]
  (fn [handler]
    (fn [request]
      (sd/req-find-data2
       request handler
       :user_id :media_entry_id
       :favorite_media_entries
       :user_id :media_entry_id
       res-req-name
       send404))))

(defn wwrap-find-favorite_media_entry-by-auth [send404]
  (fn [handler]
    (fn [request]
      (let [user-id (-> request :authenticated-entity :id str)
            me-id (-> request :parameters :path :media_entry_id str)]
        (info "uid\n" user-id "meid\n" me-id)
        (sd/req-find-data-search2
         request handler
         user-id me-id
         :favorite_media_entries
         :user_id :media_entry_id
         res-req-name
         send404)))))

(defn wwrap-find-user [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :users :id
                                    :user true))))

(defn wwrap-find-media_entry [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :media_entries :id
                                    :media_entry true))))

(def schema_favorite_media_entries_export
  {:user_id s/Uuid
   :media_entry_id s/Uuid
   :updated_at s/Any
   :created_at s/Any})

; TODO docu
; TODO tests
; user self edit favorites
(def favorite-routes
  ["/favorite/media-entries"
   {:swagger {:tags ["api/favorite"]}}
   {:get
    {:summary (sd/sum_usr "List users favorites media_entries ids.")
     :handler handle_list-favorite_media_entries-by-user
     :middleware [authorization/wrap-authorized-user]
     :swagger {:produces "application/json"}
     :coercion reitit.coercion.schema/coercion
     :responses {200 {:body {:media_entry_ids [s/Uuid]}}}}}])

(def media-entry-routes
  ["/media-entry/:media_entry_id/favorite"
   {:swagger {:tags ["api/media-entry"]}}
   {:post {:summary (sd/sum_usr "Create favorite_media_entry for authed user and media-entry.")
           :handler handle_create-favorite_media_entry

           :middleware [authorization/wrap-authorized-user
                        (wwrap-find-media_entry :media_entry_id)
                        (wwrap-find-favorite_media_entry-by-auth false)]
           :swagger {:produces "application/json"}
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}}
           :responses {200 {:body schema_favorite_media_entries_export}
                       404 {:body s/Any}
                       406 {:body s/Any}}}

    :get {:summary (sd/sum_usr "Get favorite_media_entry for authed user and media-entry.")
          :handler handle_get-favorite_media_entry

          :middleware [authorization/wrap-authorized-user
                       (wwrap-find-media_entry :media_entry_id)
                       (wwrap-find-favorite_media_entry-by-auth true)]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:media_entry_id s/Uuid}}
          :responses {200 {:body schema_favorite_media_entries_export}
                      404 {:body s/Any}
                      406 {:body s/Any}}}

    :delete {:summary (sd/sum_usr "Delete favorite_media_entry for authed user and media-entry.")
             :coercion reitit.coercion.schema/coercion
             :handler handle_delete-favorite_media_entry

             :middleware [authorization/wrap-authorized-user
                          (wwrap-find-media_entry :media_entry_id)
                          (wwrap-find-favorite_media_entry-by-auth true)]
             :parameters {:path {:media_entry_id s/Uuid}}
             :responses {200 {:body schema_favorite_media_entries_export}
                         404 {:body s/Any}
                         406 {:body s/Any}}}}])

(def admin-routes
  [["/favorite/media-entries"
    {:swagger {:tags ["admin/favorite/media-entries"] :security [{"auth" []}]}}
    ["/"
     {:get
      {:summary (sd/sum_adm "Query favorite_media_entries.")
       :handler handle_list-favorite_media_entries
       :middleware [wrap-authorize-admin!]
       :coercion reitit.coercion.schema/coercion}}]

    ["/favorite/media_entries/:media_entry_id/:user_id"
     {:post
      {:summary (sd/sum_adm "Create favorite_media-entry for user and media-entry.")
       :handler handle_create-favorite_media_entry
       :middleware [wrap-authorize-admin!
                    (wwrap-find-user :user_id)
                    (wwrap-find-media_entry :media_entry_id)
                    (wwrap-find-favorite_media_entry false)]
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:user_id s/Uuid
                           :media_entry_id s/Uuid}}}

      :get
      {:summary (sd/sum_adm "Get favorite_media-entry for user and media-entry.")
       :handler handle_get-favorite_media_entry
       :middleware [wrap-authorize-admin!
                    (wwrap-find-favorite_media_entry true)]
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:user_id s/Uuid
                           :media_entry_id s/Uuid}}}

      :delete
      {:summary (sd/sum_adm "Delete favorite_media-entry for user and media-entry.")
       :coercion reitit.coercion.schema/coercion
       :handler handle_delete-favorite_media_entry
       :middleware [wrap-authorize-admin!
                    (wwrap-find-favorite_media_entry true)]
       :parameters {:path {:user_id s/Uuid
                           :media_entry_id s/Uuid}}}}]]])
