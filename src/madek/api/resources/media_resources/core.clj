(ns madek.api.resources.media-resources.core
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc :as jdbc]))

(defn user-table
  [mr-type]
  (keyword (str mr-type "_user_permissions")))

(defn group-table
  [mr-type]
  (keyword (str mr-type "_group_permissions")))

(defn resource-key
  [mr-type]
  (keyword (str mr-type "_id")))

(defn resource-permission-get-query
  ([media-resource tx]
   (case (:type media-resource)
     "MediaEntry" (resource-permission-get-query (:id media-resource) "media_entries" tx)
     "Collection" (resource-permission-get-query (:id media-resource) "collections" tx)))

  ([mr-id mr-table tx]
   (-> (jdbc/execute-one! tx
                          (-> (sql/select :*) (sql/from (keyword mr-table)) (sql/where [:= :id mr-id]) (sql-format))))))

(defn build-user-permissions-query
  [media-resource-id user-id perm-name mr-type]
  (-> (sql/select :*)
      (sql/from (user-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id]
                 [:= :user_id user-id]
                 [:= perm-name true])
      (sql-format)))

(defn build-user-permission-get-query
  [media-resource-id mr-type user-id]
  (-> (sql/select :*)
      (sql/from (user-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id]
                 [:= :user_id user-id])
      (sql-format)))

(defn build-user-permission-list-query
  [media-resource-id mr-type]
  (-> (sql/select :*)
      (sql/from (user-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id])
      (sql-format)))

(defn build-user-groups-query [user-id]
  (-> (sql/select :groups.*)
      (sql/from :groups)
      (sql/join :groups_users [:= :groups.id :groups_users.group_id])
      (sql/where [:= :groups_users.user_id user-id])
      (sql-format)))

(defn query-user-groups [user-id tx]
  (->> (build-user-groups-query user-id)
       (jdbc/execute! tx)))

(defn build-group-permissions-query
  [media-resource-id group-ids perm-name mr-type]
  (-> (sql/select :*)
      (sql/from (group-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id]
                 [:in :group_id group-ids]
                 [:= perm-name true])
      (sql-format)))

(defn build-group-permission-get-query
  [media-resource-id mr-type group-id]
  (-> (sql/select :*)
      (sql/from (group-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id]
                 [:= :group_id group-id])
      (sql-format)))

(defn build-group-permission-list-query
  [media-resource-id mr-type]
  (-> (sql/select :*)
      (sql/from (group-table mr-type))
      (sql/where [:= (resource-key mr-type) media-resource-id])
      (sql-format)))

; ============================================================

(defn delegation-ids [user_id tx]
  (let [query {:union [(-> (sql/select :delegation_id)
                           (sql/from :delegations_groups)
                           (sql/where [:in :delegations_groups.group_id (->
                                                                         (sql/select :group_id)
                                                                         (sql/from :groups_users)
                                                                         (sql/where [:= :groups_users.user_id user_id]))]))
                       (-> (sql/select :delegation_id)
                           (sql/from :delegations_users)
                           (sql/where [:= :delegations_users.user_id user_id]))]}]
    (map #(:delegation_id %) (jdbc/execute! tx (sql-format query)))))

(defn query-user-permissions
  [resource user-id perm-name mr-type tx]
  (->> (build-user-permissions-query (:id resource) user-id perm-name mr-type)
       (jdbc/execute! tx)))

(defn sql-cls-resource-and
  [stmt mr-type mr-id and-key and-id]
  (-> stmt
      (sql/where [:= (resource-key mr-type) mr-id]
                 [:= and-key and-id])))

(defn query-group-permissions
  [resource user-id perm-name mr-type tx]
  (if-let [user-groups (seq (query-user-groups user-id tx))]
    (->> (build-group-permissions-query
          (:id resource) (map :id user-groups) perm-name mr-type)
         (jdbc/execute! tx))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
