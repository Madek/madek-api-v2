(ns madek.api.resources.media-resources.permissions
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.media-resources.core :as c]
   [madek.api.utils.helper :refer [convert-map-if-exist to-uuid]]
   [next.jdbc :as jdbc]
   [taoensso.timbre :refer [info]]))

(defn resource-permission-get-query
  ([media-resource tx]
   (case (:type media-resource)
     "MediaEntry" (resource-permission-get-query (:id media-resource) "media_entries" tx)
     "Collection" (resource-permission-get-query (:id media-resource) "collections" tx)))

  ([mr-id mr-table tx]
   (-> (jdbc/execute-one! tx
                          (-> (sql/select :*) (sql/from (keyword mr-table)) (sql/where [:= :id mr-id]) (sql-format))))))

; TODO try catch logwrite
(defn update-resource-permissions
  [resource perm-data tx]
  (let [mr-id (-> resource :id)
        tname (case (:type resource)
                "MediaEntry" :media_entries
                "Collection" :collections)
        update-stmt (-> (sql/update tname)
                        (sql/set (convert-map-if-exist perm-data))
                        (sql/where [:= :id (to-uuid mr-id)])
                        sql-format)
        upd-result (jdbc/execute-one! tx update-stmt)]
    (info "update resource permissions"
          "\ntable\n" tname
          "\nperm-data\n" perm-data)
    upd-result))

(defn query-list-user-permissions
  [resource mr-type tx]
  (->> (c/build-user-permission-list-query
        (:id resource) mr-type)
       (jdbc/execute! tx)))

(defn query-get-user-permission
  [resource mr-type user-id tx]
  (jdbc/execute-one! tx
                     (c/build-user-permission-get-query
                      (:id resource) mr-type user-id)))

;TODO logwrite
(defn create-user-permissions
  [resource mr-type user-id auth-entity-id data tx]
  ;(try
  ;  (catcher/with-logging {}
  (let [mr-id (:id resource)
        tname (c/user-table mr-type)
        insdata (assoc data
                       :user_id user-id
                       (c/resource-key mr-type) mr-id
                       :creator_id auth-entity-id)
        insert-stmt (-> (sql/insert-into tname)
                        (sql/values [insdata])
                        (sql/returning :*)
                        sql-format)
        ins-result (jdbc/execute-one! tx insert-stmt)]

    (info "create-user-permissions" mr-id mr-type user-id auth-entity-id tname insdata)
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
        tname (c/user-table mr-type)
        delete-stmt (-> (sql/delete-from tname)
                        (c/sql-cls-resource-and mr-type mr-id :user_id user-id)
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
  [resource mr-type user-id auth-entity-id perm-name perm-val tx]

  (let [mr-id (:id resource)
        tname (c/user-table mr-type)
        perm-data (assoc {(keyword perm-name) perm-val}
                         :updator_id auth-entity-id)
        update-stmt (-> (sql/update tname)
                        (sql/set perm-data)
                        (c/sql-cls-resource-and mr-type mr-id :user_id user-id)
                        sql-format)
        result (jdbc/execute-one! tx update-stmt)]
    (info "update user permissions"
          "\ntable\n" tname
          "\nperm-data\n" perm-data
          "\nresult:\n" result)
    result))

(defn query-get-group-permission
  [resource mr-type group-id tx]
  (jdbc/execute-one! tx
                     (c/build-group-permission-get-query
                      (:id resource) mr-type group-id)))

(defn query-list-group-permissions
  [resource mr-type tx]
  (->> (c/build-group-permission-list-query
        (:id resource) mr-type)
       (jdbc/execute! tx)))

(defn create-group-permissions
  [resource mr-type group-id auth-entity-id data tx]
  ;(try
  ;(catcher/with-logging {}
  (let [mr-id (:id resource)
        tname (c/group-table mr-type)
        insdata (assoc data
                       :group_id group-id
                       (c/resource-key mr-type) mr-id
                       :creator_id auth-entity-id)
        insert-stmt (-> (sql/insert-into tname)
                        (sql/values [(convert-map-if-exist insdata)])
                        (sql/returning :*)
                        sql-format)
        insresult (jdbc/execute-one! tx insert-stmt)]
    (info "create-group-permissions" mr-id mr-type group-id auth-entity-id tname insdata)
    (if-let [result insresult]
      result
      nil)))
;(catch Exception ex
;  (error "ERROR: Could not create resource group permissions." (ex-message ex)))))

(defn delete-group-permissions
  [resource mr-type group-id tx]
  ;(try
  ;  (catcher/with-logging {}
  (let [mr-id (:id resource)
        tname (c/group-table mr-type)
        delete-stmt (-> (sql/delete-from tname)
                        (c/sql-cls-resource-and mr-type mr-id :group_id group-id)
                        sql-format)
        delresult (jdbc/execute-one! tx delete-stmt)]
    (info "delete-group-permissions: " mr-id group-id delresult)
    (if (= 1 (::jdbc/update-count delresult))
      true
      false)))
;  (catch Exception ex
;    ((error "ERROR: Could not delete resource group permissions." (ex-message ex))
;     false))))

; TODO logwrite
(defn update-group-permissions
  [resource mr-type group-id auth-entity-id perm-name perm-val tx]
  (let [mr-id (:id resource)
        tname (c/group-table mr-type)
        perm-data (assoc {(keyword perm-name) perm-val}
                         :updator_id auth-entity-id)
        update-stmt (-> (sql/update tname)
                        (sql/set perm-data)
                        (c/sql-cls-resource-and mr-type mr-id :group_id group-id)
                        sql-format)
        result (jdbc/execute-one! tx update-stmt)]
    (info "update group permissions"
          "\ntable\n" tname
          "\nperm-data\n" perm-data
          "\nresult\n" result)
    result))

(defn permission-by-auth-entity? [resource auth-entity perm-name mr-type tx]
  (or (perm-name resource)
      (let [auth-entity-id (:id auth-entity)]
        (-> (case (:type auth-entity)
              "User" (or (= auth-entity-id (:responsible_user_id resource))
                         (some #(= (:responsible_delegation_id resource) %) (c/delegation-ids auth-entity-id tx))
                         (seq (c/query-user-permissions resource
                                                        auth-entity-id
                                                        perm-name mr-type tx))
                         (seq (c/query-group-permissions resource
                                                         auth-entity-id
                                                         perm-name mr-type tx)))
            ;"ApiClient" (seq (query-api-client-permissions resource
            ;                                               auth-entity-id
            ;                                               perm-name mr-type))
              )
            boolean)
        ; anonym user: check resource permission
        (true? ((keyword perm-name) resource))
        )))

(defn edit-permissions-by-auth-entity? [resource auth-entity mr-type tx]
  (let [auth-entity-id (:id auth-entity)]
    (-> (case (:type auth-entity)
          "User" (or (= auth-entity-id (:responsible_user_id resource))
                     (some #(= (:responsible_delegation_id resource) %) (c/delegation-ids auth-entity-id tx))
                     (seq (c/query-user-permissions resource
                                                    auth-entity-id
                                                    :edit_permissions
                                                    mr-type tx))))
        boolean)))

(defn viewable-by-auth-entity? [resource auth-entity mr-type tx]
  (permission-by-auth-entity? resource
                              auth-entity
                              :get_metadata_and_previews
                              mr-type
                              tx))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
