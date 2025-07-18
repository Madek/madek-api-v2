(ns madek.api.resources.favorite-collections
  (:require [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.authorization :as authorization]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS wrap-authorize-admin!]]
            [madek.api.utils.helper :refer [f normalize-fields]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [schema.core :as s]
            [taoensso.timbre :refer [info]]))

(def res-req-name :favorite_collection)
(def res-table-name "favorite_collections")
(def res-col-name :collection_id)

(defn handle_list-favorite_collection
  [req]
  (let [fields (normalize-fields req)
        col-sel (if (empty? fields) [:user_id] fields)
        db-result (dbh/query-find-all :favorite_collections col-sel (:tx req))]
    (sd/response_ok db-result)))

(defn handle_list-favorite_collection-by-user
  [req]
  (let [user-id (-> req :authenticated-entity :id)
        db-result (dbh/query-eq-find-all :favorite_collections :user_id user-id (:tx req))
        id-set (map :collection_id db-result)]
    ;(info "handle_list-favorite_collection-by-user" "\nresult\n" db-result "\nid-set\n" id-set)
    (sd/response_ok {:collection_ids id-set})))

(defn handle_get-favorite_collection
  [req]
  (let [favorite_collection (-> req res-req-name)]
    ;(info "handle_get-favorite_collection" favorite_collection)
    (sd/response_ok favorite_collection)))

; TODO log write
(defn handle_create-favorite_collection
  [req]
  (try
    (catcher/with-logging {}
      (let [user-id (or (-> req :user :id) (-> req :authenticated-entity :id))
            col-id (-> req :collection :id)
            data {:user_id user-id res-col-name col-id}
            sql-query (-> (sql/insert-into :favorite_collections) (sql/values [data]) sql-format)]
        (if-let [favorite_collection (-> req res-req-name)]
          ; already has favorite_collection
          (sd/response_ok favorite_collection)
          ; create favorite_collection entry
          (if-let [ins_res (first (jdbc/execute! (:tx req) sql-query))]
            (sd/response_ok ins_res)
            (sd/response_failed "Could not create favorite_collection." 406)))))
    (catch Exception ex (sd/response_exception ex))))

; TODO log write
(defn handle_delete-favorite_collection
  [req]
  (try
    (catcher/with-logging {}
      (let [favorite_collection (-> req res-req-name)
            user-id (:user_id favorite_collection)
            collection-id (res-col-name favorite_collection)
            sql-query (-> (sql/delete-from :favorite_collections)
                          (sql/where [:= :user_id user-id] [:= :collection_id collection-id])
                          (sql/returning :*)
                          sql-format)
            del-result (jdbc/execute-one! (:tx req) sql-query)]

        (if del-result
          (sd/response_ok favorite_collection)
          (sd/response_failed "Could not delete favorite collection: " 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn wwrap-find-favorite_collection [send404]
  (fn [handler]
    (fn [request]
      (sd/req-find-data2
       request handler
       :user_id :collection_id
       :favorite_collections
       :user_id :collection_id
       res-req-name
       send404))))

(defn wwrap-find-favorite_collection-by-auth [send404]
  (fn [handler]
    (fn [request]
      (let [user-id (-> request :authenticated-entity :id str)
            col-id (-> request :parameters :path :collection_id str)]
        (info "uid\n" user-id "col-id\n" col-id)
        (sd/req-find-data-search2
         request handler
         user-id col-id
         :favorite_collections
         :user_id :collection_id
         res-req-name
         send404)))))

(defn wwrap-find-user [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :users :id
                                    :user true))))

(defn wwrap-find-collection [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :collections :id
                                    :collection true))))

(def schema_favorite_collection_export
  {(s/optional-key :user_id) s/Uuid
   (s/optional-key :collection_id) s/Uuid
   (s/optional-key :updated_at) s/Any
   (s/optional-key :created_at) s/Any})

; TODO docu
; TODO tests
(def favorite-routes
  ["/favorites/collections/"
   {:openapi {:tags ["api/favorite"]}}
   {:get
    {:summary (sd/sum_usr "List users favorite_collections.")
     :handler handle_list-favorite_collection-by-user
     :middleware [authorization/wrap-authorized-user]
     :coercion reitit.coercion.schema/coercion
     :responses {200 {:description "Returns the favorite_collections."
                      :body {:collection_ids [s/Uuid]}}}}}])

; TODO media resource permissions?
; TODO docu
; TODO tests
(def collection-routes
  ["/collections/:collection_id/favorites"
   {:openapi {:tags ["api/collections"]}}
   {:post {:summary (sd/sum_usr "Create favorite_collection for authed user and collection.")
           :handler handle_create-favorite_collection
           :middleware [authorization/wrap-authorized-user
                        (wwrap-find-collection :collection_id)
                        (wwrap-find-favorite_collection-by-auth false)]
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:description "Returns the created favorite_collection."
                            :body schema_favorite_collection_export}
                       404 {:description "Collection not found."
                            :body s/Any}
                       406 {:description "Could not create favorite collection."
                            :body s/Any}}
           :parameters {:path {:collection_id s/Uuid}}}

    :get {:summary (sd/sum_usr "Get favorite_collection for authed user and collection id.")
          :handler handle_get-favorite_collection
          :middleware [authorization/wrap-authorized-user
                       (wwrap-find-favorite_collection-by-auth true)]
          :coercion reitit.coercion.schema/coercion
          :responses {200 {:description "Returns the favorite_collection."
                           :body schema_favorite_collection_export}
                      404 {:description "Favorite collection not found."
                           :body s/Any}
                      406 {:description "Could not get favorite collection."
                           :body s/Any}}
          :parameters {:path {:collection_id s/Uuid}}}

    :delete {:summary (sd/sum_usr "Delete favorite_collection for authed user and collection id.")
             :coercion reitit.coercion.schema/coercion
             :handler handle_delete-favorite_collection
             :middleware [authorization/wrap-authorized-user
                          (wwrap-find-favorite_collection-by-auth true)]
             :responses {200 {:description "Returns the deleted favorite_collection."
                              :body schema_favorite_collection_export}}
             :parameters {:path {:collection_id s/Uuid}}}}])

; TODO tests
(def admin-routes
  [["/favorites/"
    {:openapi {:tags ["admin/favorites/collections"] :security ADMIN_AUTH_METHODS}}
    ["collections/"
     {:get
      {:summary (sd/sum_adm (f "List favorite_collection users."))
       :handler handle_list-favorite_collection
       :middleware [wrap-authorize-admin!]
       :coercion reitit.coercion.schema/coercion
       :parameters {:query {(s/optional-key :fields) [(s/enum :user_id :collection_id :created_at :updated_at)]}}
       :responses {200 {:description "Returns the favorite_collections."
                        :body [schema_favorite_collection_export]}}}}]
    ; edit favorite collections for other users
    ["collections/:collection_id/users/:user_id"
     {:post {:summary (sd/sum_adm "Create favorite_collection for user and collection.")
             :handler handle_create-favorite_collection

             :middleware [wrap-authorize-admin!
                          (wwrap-find-user :user_id)
                          (wwrap-find-collection :collection_id)
                          (wwrap-find-favorite_collection false)]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:user_id s/Uuid
                                 :collection_id s/Uuid}}
             :responses {200 {:description "Returns the created favorite_collection."
                              :body schema_favorite_collection_export}
                         404 {:description "Favorite collection not found."
                              :body s/Any}
                         406 {:description "Could not create favorite collection."
                              :body s/Any}}}

      :get {:summary (sd/sum_adm "Get favorite_collection by user and collection id.")
            :handler handle_get-favorite_collection
            :middleware [wrap-authorize-admin!
                         (wwrap-find-favorite_collection true)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:user_id s/Uuid
                                :collection_id s/Uuid}}
            :responses {200 {:description "Returns the favorite_collection."
                             :body schema_favorite_collection_export}
                        404 {:description "Favorite collection not found."
                             :body s/Any}
                        406 {:description "Could not get favorite collection."
                             :body s/Any}}}

      :delete {:summary (sd/sum_adm "Delete favorite_collection by user and collection id.")
               :coercion reitit.coercion.schema/coercion
               :handler handle_delete-favorite_collection
               :middleware [wrap-authorize-admin!
                            (wwrap-find-favorite_collection true)]
               :parameters {:path {:user_id s/Uuid
                                   :collection_id s/Uuid}}
               :responses {200 {:description "Returns the deleted favorite_collection."
                                :body schema_favorite_collection_export}
                           404 {:description "Favorite collection not found."
                                :body s/Any}
                           406 {:description "Could not delete favorite collection."
                                :body s/Any}}}}]]])
