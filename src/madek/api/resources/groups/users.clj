(ns madek.api.resources.groups.users
  (:require
   [clj-uuid]
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [logbug.debug :as debug]
   [madek.api.pagination :as pagination]
   [madek.api.resources.groups.shared :as groups]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms]
   [madek.api.utils.sql :as sql]
   [schema.core :as s]))

;;; temporary users stuff ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sql-select
  ([] (sql-select {}))
  ([sql-map]
   (sql/select sql-map :*
               ;:users.id :users.email :users.institutional_id :users.login
               ;:users.created_at :users.updated_at
               ;:users.person_id
               )))

(defn sql-merge-user-where-id
  ([id] (sql-merge-user-where-id {} id))
  ([sql-map id]
   (if (re-matches
        #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
        id)
     (sql/merge-where sql-map [:or
                               [:= :users.id id]
                               [:= :users.institutional_id id]
                               [:= :users.email id]])
     (sql/merge-where sql-map [:or
                               [:= :users.institutional_id id]
                               [:= :users.email id]]))))

(defn find-user-sql [some-id]
  (-> (sql-select)
      (sql-merge-user-where-id some-id)
      (sql/merge-where [:= :is_deactivated false])
      (sql/from :users)
      sql/format))

(defn find-user [some-id]
  (->> some-id find-user-sql
       (jdbc/query (rdbms/get-ds)) first))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn group-user-query [group-id user-id]
  (-> ;(users/sql-select)
   (sql/select {} :users.id :users.institutional_id :users.email :users.person_id)
   (sql/from :users)
   (sql/merge-join :groups_users [:= :users.id :groups_users.user_id])
   (sql/merge-join :groups [:= :groups.id :groups_users.group_id])
   (sql-merge-user-where-id user-id)
   (groups/sql-merge-where-id group-id)
   sql/format))

(defn find-group-user [group-id user-id]
  ;(logging/info "find-group-user" group-id ":" user-id)
  (->> (group-user-query group-id user-id)
       (jdbc/query (rdbms/get-ds))
       first))

(defn get-group-user [group-id user-id]
  (if-let [user (find-group-user group-id user-id)]
    (sd/response_ok user)
    (sd/response_failed "No such group user," 404)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;TODO test paging
(defn group-users-query [group-id request]
  (-> (sql/select :users.id :users.institutional_id :users.email :users.person_id)
      (sql/from :users)
      (sql/merge-join :groups_users [:= :users.id :groups_users.user_id])
      (sql/merge-join :groups [:= :groups.id :groups_users.group_id])
      (sql/order-by [:users.id :asc])
      (groups/sql-merge-where-id group-id)
      (pagination/add-offset-for-honeysql (-> request :parameters :query))
      sql/format))

(defn group-users [group-id request]
  (jdbc/query (rdbms/get-ds)
              (group-users-query group-id request)))

(defn get-group-users [group-id request]
  (sd/response_ok {:users (group-users group-id request)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-user [group-id user-id]
  (logging/info "add-user" group-id ":" user-id)
  (if-let [user (find-group-user group-id user-id)]
    (sd/response_ok {:users (group-users group-id nil)})
    (let [group (groups/find-group group-id)
          user (find-user user-id)]
      (if-not (and group user)
        (sd/response_not_found "No such user or group.")
        (do (jdbc/insert! (rdbms/get-ds)
                          :groups_users {:group_id (:id group)
                                         :user_id (:id user)})
            (sd/response_ok {:users (group-users group-id nil)}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn remove-user [group-id user-id]
  (if-let [user (find-group-user group-id user-id)]
    (if-let [group (groups/find-group group-id)]
      (let [delok (jdbc/delete! (rdbms/get-ds)
                                :groups_users
                                ["group_id = ? AND user_id = ?"
                                 (:id group) (:id user)])]
        (sd/response_ok {:users (group-users group-id nil)}))
      (sd/response_not_found "No such group"))
    (sd/response_not_found "No such group user.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn current-group-users-ids [tx group-id]
  (->> (-> (sql/select [:user_id :id])
           (sql/from :groups_users)
           (sql/where [:= :groups_users.group_id group-id])
           sql/format)
       (jdbc/query tx)
       (map :id)
       set))

(defn target-group-users-query [users]
  (-> (sql/select :id)
      (sql/from :users)
      (sql/where ;[:or
       [:in :users.id (->> users (map #(-> % :id str)) (filter identity))]
                  ;[:in :users.institutional_id (->> users (map #(-> % :institutional_id str)) (filter identity))]
                  ;[:in :users.email (->> users (map :email) (filter identity))]
                  ;]
       )
      sql/format))

(defn target-group-users-ids [tx users]
  (->> (target-group-users-query users)
       (jdbc/query tx)
       (map :id)
       set))

(defn update-delete-query [group-id ids]
  (-> (sql/delete-from :groups_users)
      (sql/merge-where [:= :groups_users.group_id group-id])
      (sql/merge-where [:in :groups_users.user_id ids])
      sql/format))

(defn update-insert-query [group-id ids]
  (-> (sql/insert-into :groups_users)
      (sql/columns :group_id :user_id)
      (sql/values (->> ids (map (fn [id] [group-id id]))))
      sql/format))

(defn update-group-users [group-id data]
  (jdbc/with-db-transaction [tx (rdbms/get-ds)]
    (let [current-group-users-ids (current-group-users-ids tx group-id)
          target-group-users-ids (if (first (:users data))
                                   (target-group-users-ids tx (:users data))
                                   [])
          del-users (clojure.set/difference current-group-users-ids target-group-users-ids)
          ins-users (clojure.set/difference target-group-users-ids current-group-users-ids)
          del-query (update-delete-query group-id del-users)
          ins-query (update-insert-query group-id ins-users)]
      ;(logging/info "update-group-users" "\ncurr\n" current-group-users-ids "\ntarget\n" target-group-users-ids )
      ;(logging/info "update-group-users" "\ndel-u\n" del-users)
      ;(logging/info "update-group-users" "\nins-u\n" ins-users)
      ;(logging/info "update-group-users" "\ndel-q\n" del-query)
      ;(logging/info "update-group-users" "\nins-q\n" ins-query)
      (when (first del-users)
        (jdbc/execute!
         tx
         del-query))
      (when (first ins-users)
        (jdbc/execute!
         tx
         ins-query))

      ;{:status 200}
      (sd/response_ok {:users (jdbc/query tx
                                          (group-users-query group-id nil))}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def schema_export-group-user
  {:id s/Uuid
   :email (s/maybe s/Str)
   :institutional_id (s/maybe s/Str)
   :login (s/maybe s/Str)
   :created_at s/Any
   :updated_at s/Any
   :person_id s/Uuid})

(def schema_export-group-user-simple
  {:id s/Uuid
   :email (s/maybe s/Str)
   :institutional_id (s/maybe s/Str)
   ;:login (s/maybe s/Str)
   ;:created_at s/Any
   ;:updated_at s/Any
   :person_id (s/maybe s/Uuid)})

(def schema_update-group-user-list
  {:users
   [{(s/optional-key :id) s/Uuid
     (s/optional-key :institutional_id) s/Uuid
     (s/optional-key :email) s/Str}]})

(defn handle_get-group-user [req]
  (let [group-id (-> req :parameters :path :group-id)
        user-id (-> req :parameters :path :user-id)]
    (logging/info "handle_get-group-user" "\ngroup-id\n" group-id "\nuser-id\n" user-id)
    (get-group-user group-id user-id)))

(defn handle_delete-group-user [req]
  (let [group-id (-> req :parameters :path :group-id)
        user-id (-> req :parameters :path :user-id)]
    (logging/info "handle_delete-group-user" "\ngroup-id\n" group-id "\nuser-id\n" user-id)
    (remove-user group-id user-id)))

(defn handle_get-group-users [request]
  (let [id (-> request :parameters :path :group-id)]
    (get-group-users id request)))

(defn handle_update-group-users [req]
  (let [id (-> req :parameters :path :group-id)
        data (-> req :parameters :body)]
    (logging/info "handle_update-group-users" "\nid\n" id "\ndata\n" data)
    (update-group-users id data)))

(defn handle_add-group-user [req]
  (let [group-id (-> req :parameters :path :group-id)
        user-id (-> req :parameters :path :user-id)]
    (add-user group-id user-id)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
