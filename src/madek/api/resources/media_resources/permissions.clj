(ns madek.api.resources.media-resources.permissions
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.utils.helper :refer [convert-map-if-exist to-uuid]]
   [next.jdbc :as jdbc]
   [taoensso.timbre :refer [info]]))

(defn- user-table
  [mr-type]
  (keyword (str mr-type "_user_permissions")))

(defn- group-table
  [mr-type]
  (keyword (str mr-type "_group_permissions")))

(defn- resource-key
  [mr-type]
  (keyword (str mr-type "_id")))

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
  ([media-resource ds]
   (case (:type media-resource)
     "MediaEntry" (resource-permission-get-query (:id media-resource) "media_entries" ds)
     "Collection" (resource-permission-get-query (:id media-resource) "collections" ds)))

  ([mr-id mr-table ds]
   (-> (jdbc/execute-one! ds
         (-> (sql/select :*) (sql/from (keyword mr-table)) (sql/where [:= :id mr-id]) (sql-format))))))

(defn- build-user-permissions-query
  [media-resource-id user-id perm-name mr-type]
  (-> (sql/select :*)
      (sql/from (user-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id]
        [:= :user_id user-id]
        [:= perm-name true])
      (sql-format)))

(defn- build-user-permission-get-query
  [media-resource-id mr-type user-id]
  (-> (sql/select :*)
      (sql/from (user-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id]
        [:= :user_id user-id])
      (sql-format)))

(defn- build-user-permission-list-query
  [media-resource-id mr-type]
  (-> (sql/select :*)
      (sql/from (user-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id])
      (sql-format)))

(defn- build-user-groups-query [user-id]
  (-> (sql/select :groups.*)
      (sql/from :groups)
      (sql/join :groups_users [:= :groups.id :groups_users.group_id])
      (sql/where [:= :groups_users.user_id user-id])
      (sql-format)))

(defn- query-user-groups [user-id ds]
  (->> (build-user-groups-query user-id)
    (jdbc/execute! ds)))

(defn- build-group-permissions-query
  [media-resource-id group-ids perm-name mr-type]
  (-> (sql/select :*)
      (sql/from (group-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id]
        [:in :group_id group-ids]
        [:= perm-name true])
      (sql-format)))

(defn- build-group-permission-get-query
  [media-resource-id mr-type group-id]
  (-> (sql/select :*)
      (sql/from (group-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id]
        [:= :group_id group-id])
      (sql-format)))

(defn- build-group-permission-list-query
  [media-resource-id mr-type]
  (-> (sql/select :*)
      (sql/from (group-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id])
      (sql-format)))

; ============================================================

(defn- delegation-ids [user_id ds]
  (let [query {:union [(-> (sql/select :delegation_id)
                           (sql/from :delegations_groups)
                           (sql/where [:in :delegations_groups.group_id (->
                                                                         (sql/select :group_id)
                                                                         (sql/from :groups_users)
                                                                         (sql/where [:= :groups_users.user_id user_id]))]))
                       (-> (sql/select :delegation_id)
                           (sql/from :delegations_users)
                           (sql/where [:= :delegations_users.user_id user_id]))]}]
    (map #(:delegation_id %) (jdbc/execute! ds (sql-format query)))))

;(defn- query-api-client-permissions
;  [resource api-client-id perm-name mr-type]
;  (->> (build-api-client-permissions-query
;         (:id resource) api-client-id perm-name mr-type)
;       (jdbc/query ds)))

;(defn query-list-api-client-permissions
;  [resource mr-type]
;  (->> (build-api-client-permission-list-query
;        (:id resource) mr-type)
;       (jdbc/query ds)))

; TODO try catch logwrite
(defn update-resource-permissions
  [resource perm-data ds]
  (let [mr-id (-> resource :id)
        tname (case (:type resource)
                "MediaEntry" :media_entries
                "Collection" :collections)
        update-stmt (-> (sql/update tname)
                        (sql/set (convert-map-if-exist perm-data))
                        (sql/where [:= :id (to-uuid mr-id)])
                        sql-format)
        upd-result (jdbc/execute-one! ds update-stmt)]
    (info "update resource permissions"
      "\ntable\n" tname
      "\nperm-data\n" perm-data)
    upd-result))

(defn- query-user-permissions
  [resource user-id perm-name mr-type ds]
  (->> (build-user-permissions-query (:id resource) user-id perm-name mr-type)
    (jdbc/execute! ds)))

(defn query-list-user-permissions
  [resource mr-type ds]
  (->> (build-user-permission-list-query
         (:id resource) mr-type)
    (jdbc/execute! ds)))

(defn query-get-user-permission
  [resource mr-type user-id ds]
  (jdbc/execute-one! ds
    (build-user-permission-get-query
      (:id resource) mr-type user-id)))

(defn- sql-cls-resource-and
  [stmt mr-type mr-id and-key and-id]
  (-> stmt
      (sql/where [:= (resource-key mr-type) mr-id]
        [:= and-key and-id])))

;TODO logwrite
(defn create-user-permissions
  [resource mr-type user-id data ds]
  ;(try
  ;  (catcher/with-logging {}
  (let [mr-id (:id resource)
        tname (user-table mr-type)
        insdata (assoc data :user_id user-id (resource-key mr-type) mr-id)
        insert-stmt (-> (sql/insert-into tname)
                        (sql/values [insdata])
                        (sql/returning :*)
                        sql-format)
        ins-result (jdbc/execute-one! ds insert-stmt)]

    (info "create-user-permissions" mr-id mr-type user-id tname insdata)
    (if-let [result ins-result]
      result
      nil)))
; (catch Exception ex
;   (error "Could not create resource user permissions." (ex-message ex)))))

; TODO logwrite
(defn delete-user-permissions
  [resource mr-type user-id tx]
  ;(try
  ;  (catcher/with-logging {}
  (let [mr-id (:id resource)
        tname (user-table mr-type)
        delete-stmt (-> (sql/delete-from tname)
                        (sql-cls-resource-and mr-type mr-id :user_id user-id)
                        sql-format)
        delresult (jdbc/execute-one! tx delete-stmt)]
    (info "delete-user-permissions: " mr-id user-id delresult)
    (if (= 1 (::jdbc/update-count delresult))
      true
      false)))
;(catch Exception ex
;  ((error "Could not delete resource user permissions." (ex-message ex))
;     false))))

; TODO logwrite
(defn update-user-permissions
  [resource mr-type user-id perm-name perm-val tx]

  (let [mr-id (:id resource)
        tname (user-table mr-type)
        perm-data {(keyword perm-name) perm-val}
        update-stmt (-> (sql/update tname)
                        (sql/set perm-data)
                        (sql-cls-resource-and mr-type mr-id :user_id user-id)
                        sql-format)
        result (jdbc/execute-one! tx update-stmt)]
    (info "update user permissions"
      "\ntable\n" tname
      "\nperm-data\n" perm-data
      "\nresult:\n" result)
    result))

(defn- query-group-permissions
  [resource user-id perm-name mr-type ds]
  (if-let [user-groups (seq (query-user-groups user-id ds))]
    (->> (build-group-permissions-query
           (:id resource) (map :id user-groups) perm-name mr-type)
      (jdbc/execute! ds))))

(defn query-get-group-permission
  [resource mr-type group-id tx]
  (jdbc/execute-one! tx
    (build-group-permission-get-query
      (:id resource) mr-type group-id)))

(defn query-list-group-permissions
  [resource mr-type ds]
  (->> (build-group-permission-list-query
         (:id resource) mr-type)
    (jdbc/execute! ds)))

(defn create-group-permissions
  [resource mr-type group-id data ds]
  ;(try
  ;(catcher/with-logging {}
  (let [mr-id (:id resource)
        tname (group-table mr-type)
        insdata (assoc data :group_id group-id (resource-key mr-type) mr-id)
        insert-stmt (-> (sql/insert-into tname)
                        (sql/values [(convert-map-if-exist insdata)])
                        (sql/returning :*)
                        sql-format)
        insresult (jdbc/execute-one! ds insert-stmt)]
    (info "create-group-permissions" mr-id mr-type group-id tname insdata)
    (if-let [result insresult]
      result
      nil)))
;(catch Exception ex
;  (error "ERROR: Could not create resource group permissions." (ex-message ex)))))

(defn delete-group-permissions
  [resource mr-type group-id ds]
  ;(try
  ;  (catcher/with-logging {}
  (let [mr-id (:id resource)
        tname (group-table mr-type)
        delete-stmt (-> (sql/delete-from tname)
                        (sql-cls-resource-and mr-type mr-id :group_id group-id)
                        sql-format)
        delresult (jdbc/execute-one! ds delete-stmt)]
    (info "delete-group-permissions: " mr-id group-id delresult)
    (if (= 1 (::jdbc/update-count delresult))
      true
      false)))
;  (catch Exception ex
;    ((error "ERROR: Could not delete resource group permissions." (ex-message ex))
;     false))))

; TODO logwrite
(defn update-group-permissions
  [resource mr-type group-id perm-name perm-val ds]
  (let [mr-id (:id resource)
        tname (group-table mr-type)
        perm-data {(keyword perm-name) perm-val}
        update-stmt (-> (sql/update tname)
                        (sql/set perm-data)
                        (sql-cls-resource-and mr-type mr-id :group_id group-id)
                        sql-format)
        result (jdbc/execute-one! ds update-stmt)]
    (info "update group permissions"
      "\ntable\n" tname
      "\nperm-data\n" perm-data
      "\nresult\n" result)
    result))

(defn permission-by-auth-entity? [resource auth-entity perm-name mr-type ds]
  (or (perm-name resource)
    (let [auth-entity-id (:id auth-entity)]
      (-> (case (:type auth-entity)
            "User" (or (= auth-entity-id (:responsible_user_id resource))
                     (some #(= (:responsible_delegation_id resource) %) (delegation-ids auth-entity-id ds))
                     (seq (query-user-permissions resource
                            auth-entity-id
                            perm-name mr-type ds))
                     (seq (query-group-permissions resource
                            auth-entity-id
                            perm-name mr-type ds)))
            ;"ApiClient" (seq (query-api-client-permissions resource
            ;                                               auth-entity-id
            ;                                               perm-name mr-type))
            )
          boolean))))

(defn edit-permissions-by-auth-entity? [resource auth-entity mr-type ds]
  (let [auth-entity-id (:id auth-entity)]
    (-> (case (:type auth-entity)
          "User" (or (= auth-entity-id (:responsible_user_id resource))
                   (some #(= (:responsible_delegation_id resource) %) (delegation-ids auth-entity-id ds))
                   (seq (query-user-permissions resource
                          auth-entity-id
                          :edit_permissions
                          mr-type ds))))
        boolean)))

(defn viewable-by-auth-entity? [resource auth-entity mr-type ds]
  (permission-by-auth-entity? resource
    auth-entity
    :get_metadata_and_previews
    mr-type
    ds))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
