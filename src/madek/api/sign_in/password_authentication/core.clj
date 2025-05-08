(ns madek.api.sign_in.password-authentication.core
  (:refer-clojure :exclude [str keyword])
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.anti-csrf.core :refer [str]]
   [madek.api.db.core :as db]
   [next.jdbc :as jdbc]))

(def user-select
  (sql/select :users.id
              :users.email
              :users.first_name
              :users.last_name
              :users.login
              :users.institutional_id
              :users.institution
              [:users.id :user_id]))

(defn where-unique-user [query user-uid]
  (sql/where
   query
   [:or
    [:= [:lower :users.login] [:lower user-uid]]
    [:= [:lower :users.email] [:lower user-uid]]]))

(defn join-password-auth [query]
  (-> query
      (sql/select [:auth_systems_users.data :password_hash])
      (sql/join :auth_systems_users
                [:= :auth_systems_users.user_id :users.id])
      (sql/join :auth_systems
                [:= :auth_systems.id
                 :auth_systems_users.auth_system_id])
      (sql/where [:= :auth_systems.type "password"])
      (sql/where [:<> nil :auth_systems_users.data])
      (sql/where [:< [:now] :users.active_until])
      (sql/where [:= :users.password_sign_in_enabled true])))

(defn user-query [user-uid]
  (-> user-select
      (sql/from :users)
      (where-unique-user user-uid)
      (join-password-auth)))

(defn check-password
  [password password-hash & {:keys [tx]
                             :or {tx (db/get-ds)}}]
  (-> (sql/select [[:= password-hash [:crypt (str password) password-hash]]
                   :password_is_ok])
      (sql-format)
      (#(jdbc/execute-one! tx % db/builder-fn-options-default))
      :password_is_ok))

(defn password-checked-user
  "Returns the user iff the account exists, is allowed to sign in
  and the password matches. Returns nil if the user is not found, has no
  password or is not allowed to sign in. Returns false if the user is
  found but the password does not match.
  The tx parameter is optional and in general not to be used
  since this never causes a mutation and the db operation is costly. "
  [user-uid password & {:keys [tx]
                        :or {tx (db/get-ds)}}]
  (when-let [user (-> (user-query user-uid)
                      (sql-format)
                      (#(jdbc/execute-one! tx % db/builder-fn-options-default)))]
    (and (check-password password (:password_hash user))
         (dissoc user :password_hash))))
