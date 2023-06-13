(ns madek.api.resources.media-resources.permissions
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    
    ))


;(defn- build-api-client-permissions-query
;  [media-resource-id api-client-id perm-name mr-type]
;  (-> (sql/select :*)
;      (sql/from (keyword (str mr-type "_api_client_permissions")))
;      (sql/where [:= (keyword (str mr-type "_id")) media-resource-id]
;                 [:= :api_client_id api-client-id]
;                 [:= perm-name true])
;      (sql/format)))

;(defn- build-api-client-permission-list-query
;  [media-resource-id mr-type]
;  (-> (sql/select :*)
;      (sql/from (keyword (str mr-type "_api_client_permissions")))
;      (sql/where [:= (keyword (str mr-type "_id")) media-resource-id])
;      (sql/format)))

(defn resource-permission-get-query
  ([media-resource]
  (case (:type media-resource)
    "MediaEntry" (resource-permission-get-query (:id media-resource) "media_entries")
    "Collection" (resource-permission-get-query (:id media-resource) "collections")
    ))
  
  ([mr-id mr-table]
  (-> (jdbc/query (rdbms/get-ds)
                  [(str "SELECT * FROM " mr-table " WHERE id = ?") mr-id]) first)
   ))

(defn- build-user-permissions-query
  [media-resource-id user-id perm-name mr-type]
  (-> (sql/select :*)
      (sql/from (keyword (str mr-type "_user_permissions")))
      (sql/where [:= (keyword (str mr-type "_id")) media-resource-id]
                 [:= :user_id user-id]
                 [:= perm-name true])
      (sql/format)))


(defn- build-user-permission-get-query
  [media-resource-id mr-type user-id]
  (-> (sql/select :*)
      (sql/from (keyword (str mr-type "_user_permissions")))
      (sql/where [:= (keyword (str mr-type "_id")) media-resource-id]
                 [:= :user_id user-id])
      (sql/format)))

(defn- build-user-permission-list-query
  [media-resource-id mr-type]
  (-> (sql/select :*)
      (sql/from (keyword (str mr-type "_user_permissions")))
      (sql/where [:= (keyword (str mr-type "_id")) media-resource-id])
      (sql/format)))

(defn- build-user-groups-query [user-id]
  (-> (sql/select :groups.*)
      (sql/from :groups)
      (sql/merge-join :groups_users [:= :groups.id :groups_users.group_id])
      (sql/where [:= :groups_users.user_id user-id])
      (sql/format)))

(defn- query-user-groups [user-id]
  (->> (build-user-groups-query user-id)
       (jdbc/query (rdbms/get-ds))))

(defn- build-group-permissions-query
  [media-resource-id group-ids perm-name mr-type]
  (-> (sql/select :*)
      (sql/from (keyword (str mr-type "_group_permissions")))
      (sql/where [:= (keyword (str mr-type "_id")) media-resource-id]
                 [:in :group_id group-ids]
                 [:= perm-name true])
      (sql/format)))

(defn- build-group-permission-get-query
  [media-resource-id mr-type group-id]
  (-> (sql/select :*)
      (sql/from (keyword (str mr-type "_group_permissions")))
      (sql/where [:= (keyword (str mr-type "_id")) media-resource-id]
                 [:= :group_id group-id])
      (sql/format)))

(defn- build-group-permission-list-query
  [media-resource-id mr-type]
  (-> (sql/select :*)
      (sql/from (keyword (str mr-type "_group_permissions")))
      (sql/where [:= (keyword (str mr-type "_id")) media-resource-id])
      (sql/format)))

; ============================================================

(defn- delegation-ids [user_id]
  (let [query {:union [
                (-> (sql/select :delegation_id)
                    (sql/from :delegations_groups)
                    (sql/where [:in :delegations_groups.group_id (->
                      (sql/select :group_id)
                      (sql/from :groups_users)
                      (sql/where [:= :groups_users.user_id user_id]))]))
                (-> (sql/select :delegation_id)
                    (sql/from :delegations_users)
                    (sql/where [:= :delegations_users.user_id user_id]))]}]
    (map #(:delegation_id %) (jdbc/query (rdbms/get-ds) (sql/format query)))
  )
)

;(defn- query-api-client-permissions
;  [resource api-client-id perm-name mr-type]
;  (->> (build-api-client-permissions-query
;         (:id resource) api-client-id perm-name mr-type)
;       (jdbc/query (rdbms/get-ds))))

;(defn query-list-api-client-permissions
;  [resource mr-type]
;  (->> (build-api-client-permission-list-query
;        (:id resource) mr-type)
;       (jdbc/query (rdbms/get-ds))))



(defn update-resource-permissions
  [resource perm-data]
  (let [mr-id (-> resource :id str)
        tname (case (:type resource)
                "MediaEntry" :media_entries
                "Collection" :collections)
        whcl (-> (sql/where [:= :id mr-id])
                 (sql/format)
                 (update-in [0] #(clojure.string/replace % "WHERE" "")))
        ;perm-data {(keyword perm-name) perm-val}
        ]
    (logging/info "update resource permissions"
                  "\ntable\n" tname
                  "\nwhcl\n" whcl
                  "\nperm-data\n" perm-data)
    (jdbc/update! (rdbms/get-ds) tname perm-data whcl))
  )

(defn- query-user-permissions
  [resource user-id perm-name mr-type]
  (->> (build-user-permissions-query
         (:id resource) user-id perm-name mr-type)
       (jdbc/query (rdbms/get-ds))))

(defn query-list-user-permissions
  [resource mr-type]
  (->> (build-user-permission-list-query
        (:id resource) mr-type)
       (jdbc/query (rdbms/get-ds))))

(defn query-get-user-permissions
  [resource mr-type user-id]
  (->> (build-user-permission-get-query
        (:id resource) mr-type user-id)
       (jdbc/query (rdbms/get-ds))))

(defn create-user-permissions
  [resource mr-type user-id data])

(defn delete-user-permissions
  [resource mr-type user-id])

(defn create-group-permissions
  [resource mr-type group-id data])

(defn delete-group-permissions
  [resource mr-type group-id])

(defn update-user-permissions
  [resource mr-type user-id perm-name perm-val]
  (let [mr-id (:id resource)
        tname (keyword (str mr-type "_user_permissions"))
        whcl (-> (sql/where [:= (keyword (str mr-type "_id")) mr-id]
                            [:= :user_id user-id])
                 (sql/format)
                 (update-in [0] #(clojure.string/replace % "WHERE" "")))
        perm-data {(keyword perm-name) perm-val}
        ]
    (logging/info "update user permissions"
                  "\ntable\n" tname
                  "\nwhcl\n" whcl
                  "\nperm-data\n" perm-data)
    (jdbc/update! (rdbms/get-ds) tname perm-data whcl))
  )

(defn- query-group-permissions
  [resource user-id perm-name mr-type]
  (if-let [user-groups (seq (query-user-groups user-id))]
    (->> (build-group-permissions-query
           (:id resource) (map :id user-groups) perm-name mr-type)
         (jdbc/query (rdbms/get-ds)))))

(defn query-get-group-permissions
  [resource mr-type group-id]
  (->> (build-group-permission-get-query
        (:id resource) mr-type group-id)
       (jdbc/query (rdbms/get-ds))))


(defn query-list-group-permissions
  [resource mr-type]
  (->> (build-group-permission-list-query
          (:id resource) mr-type)
         (jdbc/query (rdbms/get-ds))))

(defn update-group-permissions
  [resource mr-type group-id perm-name perm-val]
  (let [mr-id (:id resource)
        tname (keyword (str mr-type "_group_permissions"))
        whcl (-> (sql/where [:= (keyword (str mr-type "_id")) mr-id]
                            [:= :group_id group-id])
                 (sql/format)
                 (update-in [0] #(clojure.string/replace % "WHERE" "")))
        perm-data {(keyword perm-name) perm-val}]
    (logging/info "update group permissions"
                  "\ntable\n" tname
                  "\nwhcl\n" whcl
                  "\nperm-data\n" perm-data)
    (jdbc/update! (rdbms/get-ds) tname perm-data whcl)))

(defn permission-by-auth-entity? [resource auth-entity perm-name mr-type]
  (or (perm-name resource)
      (let [auth-entity-id (:id auth-entity)]
        (-> (case (:type auth-entity)
              "User" (or (= auth-entity-id (:responsible_user_id resource))
                         (some #(= (:responsible_delegation_id resource) %) (delegation-ids auth-entity-id))
                         (seq (query-user-permissions resource
                                                      auth-entity-id
                                                      perm-name mr-type))
                         (seq (query-group-permissions resource
                                                       auth-entity-id
                                                       perm-name mr-type))
                         )
              ;"ApiClient" (seq (query-api-client-permissions resource
              ;                                               auth-entity-id
              ;                                               perm-name mr-type))
              )
            boolean))))

(defn edit-permissions-by-auth-entity? [resource auth-entity mr-type]
  (let [auth-entity-id (:id auth-entity)]
    (-> (case (:type auth-entity)
          "User" (or (= auth-entity-id (:responsible_user_id resource))
                     (some #(= (:responsible_delegation_id resource) %) (delegation-ids auth-entity-id))
                     (seq (query-user-permissions resource
                                                  auth-entity-id
                                                  :edit_permissions
                                                  mr-type))))
        boolean)))

(defn viewable-by-auth-entity? [resource auth-entity mr-type]
  (permission-by-auth-entity? resource
                              auth-entity
                              :get_metadata_and_previews 
                              mr-type))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
