(ns madek.api.resources.favorite-media-entries
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]))


(def res-req-name :favorite_media_entry)
(def res-table-name "favorite_media_entries")
(def res-col-name :media_entry_id)

(defn handle_list-favorite_media_entries
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data))
        db-result (sd/query-find-all res-table-name :*)]
    ;(->> db-result (map :id) set)
    ;(logging/info "handle_list-favorite_media_entry" "\nresult\n" db-result)
    (sd/response_ok db-result)))


(defn handle_get-favorite_media_entry
  [req]
  (let [favorite_collection (-> req res-req-name)]
    ;(logging/info "handle_get-favorite_collection" favorite_collection)
    ; TODO hide some fields
    (sd/response_ok favorite_collection)))


(defn handle_create-favorite_media_entry
  [req]
  (let [user (or (-> req :user) (-> req :authenticated-entity))
        media_entry (-> req :media_entry)
        data {:user_id (:id user) :media_entry_id (:id media_entry)}]
    (if-let [favorite_media_entry (-> req res-req-name)]
      ; already has favorite_media_entry
      (sd/response_ok favorite_media_entry)
      ; create favorite_media_entry entry
      (if-let [ins_res (jdbc/insert! (rdbms/get-ds) res-table-name data)]
        ; TODO clean result
        (sd/response_ok ins_res)
        (sd/response_failed "Could not create favorite_media_entry." 406)))))


(defn handle_delete-favorite_media_entry
  [req]
  (let [favorite_media_entry (-> req res-req-name)
        user-id (:user_id favorite_media_entry)
        media_entry-id (res-col-name favorite_media_entry)]
    (if (= 1 (first (jdbc/delete! (rdbms/get-ds) res-table-name ["user_id = ? AND media_entry_id = ?" user-id media_entry-id])))
      (sd/response_ok favorite_media_entry)
      (logging/error "Failed delete favorite_media_entry "
                     "user-id: " user-id "media_entry-id: " media_entry-id))))


(defn wwrap-find-favorite_media_entry [send404]
  (fn [handler]
    (fn [request] 
      (sd/req-find-data2
       request handler
       :user_id :media_entry_id
       res-table-name
       "user_id" "media_entry_id"
       res-req-name
       send404))))

(defn wwrap-find-favorite_media_entry-by-auth [send404]
  (fn [handler]
    (fn [request]
      (let [user-id (-> request :authenticated-entity :id str)
            me-id (-> request :parameters :path :media_entry_id str)]
        (logging/info "uid\n" user-id "meid\n" me-id)
        (sd/req-find-data-search2
         request handler
         user-id me-id
         res-table-name
         "user_id" "media_entry_id"
         res-req-name
         send404))
      )))

(defn wwrap-find-user [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param "users" "id" :user true))))

(defn wwrap-find-media_entry [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param "media_entries" "id" :media_entry true))))

; TODO response coercion
; TODO docu
; TODO tests
(def ring-routes

  ["/favorite_media_entries"
    ["/"
     {; favorite_collection list / query
      ; TODO query params
      :get {:summary  (sd/sum_adm "List favorite_media_entries.")
            :handler handle_list-favorite_media_entries
            :coercion reitit.coercion.schema/coercion
            :parameters {:query {(s/optional-key :user_id) s/Uuid
                                 (s/optional-key :media_entry_id) s/Uuid
                                 (s/optional-key :full-data) s/Bool}}}}]

    ; user self edit favorites 
   ["/:media_entry_id"
    {:post {:summary (sd/sum_cnv "Create favorite_media_entry for authed user and media-entry.")
            :handler handle_create-favorite_media_entry
            :middleware [(wwrap-find-media_entry :media_entry_id)
                         (wwrap-find-favorite_media_entry-by-auth false)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid}}}

     :get {:summary (sd/sum_cnv "Get favorite_media_entry for authed user and media-entry.")
           :handler handle_get-favorite_media_entry
           :middleware [(wwrap-find-media_entry :media_entry_id)
                        (wwrap-find-favorite_media_entry-by-auth true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}}}

     :delete {:summary (sd/sum_cnv "Delete favorite_media_entry for authed user and media-entry.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-favorite_media_entry
              :middleware [(wwrap-find-media_entry :media_entry_id)
                           (wwrap-find-favorite_media_entry-by-auth true)]
              :parameters {:path {:media_entry_id s/Uuid}}}}]
    ; admin edit favorites
    ["/:media_entry_id/:user_id" 
     {:post {:summary (sd/sum_adm "Create favorite_media-entry for user and media-entry.")
             :handler handle_create-favorite_media_entry
             :middleware [(wwrap-find-user :user_id)
                          (wwrap-find-media_entry :media_entry_id)
                          (wwrap-find-favorite_media_entry false)]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:user_id s/Uuid
                                 :media_entry_id s/Uuid}}}

      :get {:summary (sd/sum_adm "Get favorite_media-entry for user and media-entry.")
            :handler handle_get-favorite_media_entry
            :middleware [
                         (wwrap-find-favorite_media_entry true)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:user_id s/Uuid
                                :media_entry_id s/Uuid}}}

      :delete {:summary (sd/sum_adm "Delete favorite_media-entry for user and media-entry.")
               :coercion reitit.coercion.schema/coercion
               :handler handle_delete-favorite_media_entry
               :middleware [(wwrap-find-favorite_media_entry true)]
               :parameters {:path {:user_id s/Uuid
                                   :media_entry_id s/Uuid}}}}]
  ])