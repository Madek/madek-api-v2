(ns madek.api.sign-in.shared
  (:require
   [clojure.string :as str]
   [honey.sql.helpers :as sql]))

(def authentication-systems-users-sql-expr
  [:or
   [:exists
    (-> (sql/select true)
        (sql/from :auth_systems_users)
        (sql/where [:= :auth_systems_users.user_id :users.id])
        (sql/where [:= :auth_systems.id
                    :auth_systems_users.auth_system_id])
        (sql/where [:or
                    [:<> :auth_systems.type "password"]
                    [:= :users.password_sign_in_enabled true]]))]
   [:exists
    (-> (sql/select true)
        (sql/from [:auth_systems :asxs])
        (sql/join :auth_systems_groups
                  [:and [:= :asxs.id :auth_systems_groups.auth_system_id]])
        (sql/join :groups_users [:and
                                 [:= :auth_systems_groups.group_id :groups_users.group_id]
                                 [:= :auth_systems_groups.group_id :groups_users.group_id]
                                 [:= :groups_users.user_id :users.id]])
        (sql/where [:= :asxs.id :auth_systems.id]))]])

; FIXME: needs `DISTINCT`!!! (otherwise when user is nil, returns 1 entry per
(def auth-system-user-base-query
  (-> (sql/from :auth_systems :users)
      (sql/where [:= :auth_systems.enabled true])
      (sql/where [:= :users.password_sign_in_enabled true])
      (sql/where authentication-systems-users-sql-expr)
      (sql/order-by [:auth_systems.priority :desc] [:auth_systems.id :asc])))

(defn merge-identify-user [sqlmap unique-id]
  (sql/where sqlmap
             [:or
              [:= :users.login unique-id]
              [:= [:lower :users.email] (-> unique-id (or "") str/lower-case)]]))

(defn auth-system-base-query-for-unique-id
  ([unique-id]
   (-> auth-system-user-base-query
       (merge-identify-user unique-id)))
  ([user-unique-id authentication-system-id]
   (-> (auth-system-base-query-for-unique-id user-unique-id)
       (sql/where [:= :auth_systems.id authentication-system-id]))))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
