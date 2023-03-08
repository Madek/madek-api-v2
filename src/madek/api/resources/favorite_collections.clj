(ns madek.api.resources.favorite-collections
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]))


(def res-req-name :favorite_collection)
(def res-table-name "favorite_collections")
(def res-col-name :collection_id)

(defn handle_list-favorite_collection
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data))
        db-result (sd/query-find-all res-table-name :*)]
    ;(->> db-result (map :id) set)
    (logging/info "handle_list-favorite_collection" "\nresult\n" db-result)
    (sd/response_ok db-result)))


(defn handle_get-favorite_collection
  [req]
  (let [favorite_collection (-> req res-req-name)]
    ;(logging/info "handle_get-favorite_collection" favorite_collection)
    ; TODO hide some fields
    (sd/response_ok favorite_collection)))


(defn handle_create-favorite_collection
  [req]
  (let [user (-> req :user)
        collection (-> req :collection)
        data {:user_id (:id user) res-col-name (:id collection)}]
    (if-let [favorite_collection (-> req res-req-name)]
      ; already has favorite_collection
      (sd/response_ok favorite_collection)
      ; create favorite_collection entry
      (if-let [ins_res (jdbc/insert! (rdbms/get-ds) res-table-name data)]
        ; TODO clean result
        (sd/response_ok ins_res)
        (sd/response_failed "Could not create favorite_collection." 406)))))


(defn handle_delete-favorite_collection
  [req]
  (let [favorite_collection (-> req res-req-name)
        user-id (:user_id favorite_collection)
        collection-id (res-col-name favorite_collection)]
    (if (= 1 (first (jdbc/delete! (rdbms/get-ds) res-table-name ["user_id = ? AND collection_id = ?" user-id collection-id])))
      (sd/response_ok favorite_collection)
      (logging/error "Failed delete favorite_collection "
                     "user-id: " user-id "collection-id: " collection-id))))


(defn wwrap-find-favorite_collection [send404]
  (fn [handler]
    (fn [request] 
      (sd/req-find-data2
       request handler
       :user_id :collection_id
       "favorite_collections"
       "user_id" "collection_id"
       res-req-name
       send404))))

(defn wwrap-find-user [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param "users" "id" :user true))))

(defn wwrap-find-collection [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param "collections" "id" :collection true))))

; TODO response coercion
; TODO docu
; TODO tests
(def ring-routes

  ["/favorite_collections"
    ["/"
     {; favorite_collection list / query
      ; TODO query params
      :get {:summary  (sd/sum_adm "List favorite_collection users.")
            :handler handle_list-favorite_collection
            :coercion reitit.coercion.schema/coercion
            :parameters {:query {(s/optional-key :user_id) s/Uuid
                                 (s/optional-key :collection_id) s/Uuid
                                 (s/optional-key :full-data) s/Bool}}}}]
    ; edit favorite_collection
    ["/:user_id/:collection_id" 
     {:post {:summary (sd/sum_adm "Create favorite_collection for user and collection.")
             :handler handle_create-favorite_collection
             :middleware [(wwrap-find-user :user_id)
                          (wwrap-find-collection :collection_id)
                          (wwrap-find-favorite_collection false)]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:user_id s/Uuid
                                 :collection_id s/Uuid}}}

      :get {:summary (sd/sum_adm "Get favorite_collection by user and collection id.")
            :handler handle_get-favorite_collection
            :middleware [
                         (wwrap-find-favorite_collection true)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:user_id s/Uuid
                                :collection_id s/Uuid}}}

      :delete {:summary (sd/sum_adm "Delete favorite_collection by user and collection id.")
               :coercion reitit.coercion.schema/coercion
               :handler handle_delete-favorite_collection
               :middleware [(wwrap-find-favorite_collection true)]
               :parameters {:path {:user_id s/Uuid
                                   :collection_id s/Uuid}}}}]
    ; convenience to access by user id
  ])