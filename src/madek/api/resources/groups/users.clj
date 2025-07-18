(ns madek.api.resources.groups.users
  (:require
   [clj-uuid]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.groups.shared :as groups]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.helper :refer [convert-groupid-userid to-uuid]]
   [madek.api.utils.pagination :refer [pagination-handler]]
   [next.jdbc :as jdbc]
   [schema.core :as s]
   [taoensso.timbre :refer [info]]))

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
   (if (instance? java.util.UUID id)
     (sql/where sql-map [:or
                         [:= :users.id id]
                         [:= :users.institutional_id (str id)]
                         [:= :users.email (str id)]])
     (sql/where sql-map [:or
                         [:= :users.institutional_id (str id)]
                         [:= :users.email (str id)]]))))

(defn find-user-sql [some-id]
  (-> (sql-select)
      (sql-merge-user-where-id some-id)
      (sql/from :users)
      (sql/order-by [:last_name :desc] [:first_name :desc] [:id :asc])
      sql-format))

(defn find-user [some-id tx]
  (->> some-id find-user-sql
       (jdbc/execute-one! tx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn group-user-query [group-id user-id]
  (-> (sql/select {} :users.id :users.institutional_id :users.email :users.person_id)
      (sql/from :users)
      (sql/order-by [:users.id :asc] [:groups_users.group_id :asc] [:groups.name :asc])
      (sql/join :groups_users [:= :users.id :groups_users.user_id])
      (sql/join :groups [:= :groups.id :groups_users.group_id])
      (sql-merge-user-where-id user-id)
      (groups/sql-merge-where-id group-id)
      sql-format))

(defn find-group-user [group-id user-id tx]
  (->> (group-user-query group-id user-id)
       (jdbc/execute-one! tx)))

(defn get-group-user [group-id user-id tx]
  (if-let [user (find-group-user group-id user-id tx)]
    (sd/response_ok user)
    (sd/response_failed "No such group or user." 404)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;TODO test paging
(defn group-users-query [group-id request]
  (-> (sql/select :users.id :users.institutional_id :users.email :users.person_id)
      (sql/from :users)
      (sql/join :groups_users [:= :users.id :groups_users.user_id])
      (sql/join :groups [:= :groups.id :groups_users.group_id])
      (sql/order-by [:users.id :asc])
      (groups/sql-merge-where-id group-id)))

(defn group-users [group-id request]

  (let [base-query (group-users-query group-id request)]
    (pagination-handler request base-query :users)))

(defn get-group-users [group-id request]
  (sd/response_ok (group-users group-id request)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-user [group-id user-id req]
  (info "add-user" group-id ":" user-id)
  (if-let [user (find-group-user group-id user-id (:tx req))]
    (sd/response_ok (group-users group-id req))
    (let [tx (:tx req)
          group (groups/find-group group-id tx)
          user (find-user user-id (:tx req))]
      (if-not (and group user)
        (sd/response_not_found "No such user or group.")
        (do (jdbc/execute! tx
                           (-> (sql/insert-into :groups_users)
                               (sql/values [{:group_id (:id group)
                                             :user_id (:id user)}])
                               sql-format))
            (sd/response_ok (group-users group-id req)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn remove-user [group-id user-id req]
  (let [tx (:tx req)]
    (if-let [user (find-group-user group-id user-id (:tx req))]
      (if-let [group (groups/find-group group-id tx)]
        (let [delok (jdbc/execute! (:tx req)
                                   (-> (sql/delete-from :groups_users)
                                       (sql/where [:= :group_id (:id group)] [:= :user_id (:id user)])
                                       sql-format))]
          (sd/response_ok (group-users group-id req)))
        (sd/response_not_found "No such group"))
      (sd/response_not_found "No such group or user."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn current-group-users-ids [tx group-id]
  (let [res (jdbc/execute! tx (-> (sql/select-distinct :user_id)
                                  (sql/from :groups_users)
                                  (sql/where [:= :group_id group-id])
                                  sql-format))
        res (set (map :groups_users/user_id res))] res))

(defn target-group-users-query [users]
  (-> (sql/select :id)
      (sql/from :users)
      (sql/order-by [:users.id :asc])
      (sql/where ;[:or
       [:in :users.id (->> users (map #(-> % :id to-uuid)) (filter identity))]
        ;[:in :users.institutional_id (->> users (map #(-> % :institutional_id str)) (filter identity))]
        ;[:in :users.email (->> users (map :email) (filter identity))]
        ;]
       )
      sql-format))

(defn target-group-users-ids [tx users]
  (let [res (jdbc/execute! tx (target-group-users-query users))]
    (set (map :users/id res))))

(defn update-delete-query [group-id ids]
  (-> (sql/delete-from :groups_users)
      (sql/where [:= :groups_users.group_id group-id])
      (sql/where [:in :groups_users.user_id ids])
      sql-format))

(defn update-insert-query [group-id ids]
  (-> (sql/insert-into :groups_users)
      (sql/columns :group_id :user_id)
      (sql/values (->> ids (map (fn [id] [group-id id]))))
      sql-format))

(defn update-group-users [group-id data tx]
  (jdbc/with-transaction [tx tx]
    (let [current-group-users-ids (current-group-users-ids tx group-id)
          target-group-users-ids (if (first (:users data))
                                   (target-group-users-ids tx (:users data))
                                   [])
          del-users (clojure.set/difference current-group-users-ids target-group-users-ids)
          ins-users (clojure.set/difference target-group-users-ids current-group-users-ids)
          del-query (update-delete-query group-id del-users)
          ins-query (update-insert-query group-id ins-users)
          data (-> (group-users-query group-id nil)
                   sql-format)
          res (jdbc/execute! tx data jdbc/unqualified-snake-kebab-opts)]
      ;(info "update-group-users" "\ncurr\n" current-group-users-ids "\ntarget\n" target-group-users-ids )
      ;(info "update-group-users" "\ndel-u\n" del-users)
      ;(info "update-group-users" "\nins-u\n" ins-users)
      ;(info "update-group-users" "\ndel-q\n" del-query)
      ;(info "update-group-users" "\nins-q\n" ins-query)
      (when (first del-users)
        (jdbc/execute!
         tx
         del-query))
      (when (first ins-users)
        (jdbc/execute!
         tx
         ins-query))
      ;; FIXME: bug when PUT
      (sd/response_ok {:users res}))))

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
   [{(s/required-key :id) s/Uuid
     (s/optional-key :institutional_id) s/Uuid
     (s/optional-key :email) s/Str}]})

(defn handle_get-group-user [req]
  (let [group-id (-> req :parameters :path :group-id)
        user-id (-> req :parameters :path :user-id)
        converted (convert-groupid-userid group-id user-id)
        tx (:tx req)

        ;; TODO: test already exists?
        _ (if (-> converted :is_userid_valid)
            (sd/response_failed "Invalid user-id." 400))

        group-id (-> converted :group-id)
        user-id (-> converted :user-id)]

    (info "handle_get-group-user" "\ngroup-id\n" group-id "\nuser-id\n" user-id)
    (get-group-user group-id user-id tx)))

(defn handle_delete-group-user [req]
  (let [group-id (-> req :parameters :path :group-id)
        user-id (-> req :parameters :path :user-id)]
    (info "handle_delete-group-user" "\ngroup-id\n" group-id "\nuser-id\n" user-id)
    (remove-user group-id user-id req)))

(defn handle_get-group-users [request]
  (let [id (-> request :parameters :path :group-id)]
    (get-group-users id request)))

(defn handle_update-group-users [req]
  (let [id (-> req :parameters :path :group-id)
        data (-> req :parameters :body)
        tx (:tx req)]
    (info "handle_update-group-users" "\nid\n" id "\ndata\n" data)
    (update-group-users id data tx)))

(defn handle_add-group-user [req]
  (let [group-id (-> req :parameters :path :group-id)
        user-id (-> req :parameters :path :user-id)
        converted (convert-groupid-userid group-id user-id)
        group-id (-> converted :group-id)
        user-id (-> converted :user-id)]
    (add-user group-id user-id req)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
