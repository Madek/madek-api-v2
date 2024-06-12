(ns madek.api.authentication.basic
  (:require
   [camel-snake-kebab.core :refer :all]
   [cider-ci.open-session.bcrypt :refer [checkpw]]
   [clojure.walk :refer [keywordize-keys]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [inflections.core :refer :all]
   [madek.api.authentication.token :as token-authentication]
   [madek.api.resources.shared.core :as sd]
   [next.jdbc :as jdbc]
   [taoensso.timbre :refer [debug warn]])
  (:import
   [java.util Base64]))

(defn- get-by-login [table-name login tx]
  (->> (jdbc/execute! tx (-> (sql/select :*) (sql/from table-name) (sql/where [:= :login login]) sql-format))
       (map #(assoc % :type (-> table-name ->PascalCase singular)))
       (map #(clojure.set/rename-keys % {:email :email_address}))
       first))

(defn- get-api-client-by-login [login tx]
  (->> (jdbc/execute! tx (-> (sql/select :*) (sql/from :api_clients) (sql/where [:= :login login]) sql-format))
       (map #(assoc % :type "ApiClient"))
       first))

(defn- get-user-by-login-or-email-address [login-or-email tx]
  (->> (jdbc/execute! tx (-> (sql/select :*)
                             (sql/from :users)
                             (sql/where [:or [:= :login login-or-email] [:= :email login-or-email]])
                             sql-format))
       (map #(assoc % :type "User"))
       (map #(clojure.set/rename-keys % {:email :email_address}))
       first))

(defn get-entity-by-login-or-email [login-or-email tx]
  (or (get-api-client-by-login login-or-email tx)
      (get-user-by-login-or-email-address login-or-email tx)))

(defn- get-auth-systems-user [userId tx]
  (jdbc/execute-one! tx (-> (sql/select :*)
                            (sql/from :auth_systems_users)
                            (sql/where [:= :user_id userId] [:= :auth_system_id "password"])
                            sql-format)))

(defn base64-decode [^String encoded]
  (String. (.decode (Base64/getDecoder) encoded)))

(defn extract [request]
  (debug 'extract request)
  (try (when-let [auth-header (-> request :headers keywordize-keys :authorization)]
         (when (re-matches #"(?i)^basic\s+.+$" auth-header)
           (let [decoded-val (base64-decode (last (re-find #"(?i)^basic (.*)$" auth-header)))
                 [username password] (clojure.string/split (str decoded-val) #":" 2)]
             {:username username :password password})))
       (catch Exception _
         (warn "failed to extract basic-auth properties because" _))))

(defn user-password-authentication [login-or-email password handler request]
  (let [tx (:tx request)
        entity (get-entity-by-login-or-email login-or-email tx)
        asuser (when entity (get-auth-systems-user (:id entity) tx))]

    (cond
      (not entity) {:status 401 :body (str "Neither User nor ApiClient exists for "
                                           {:login-or-email-address login-or-email})}
      (nil? (get asuser :data)) {:status 401 :body "Only password auth users supported for basic auth."}
      (or (nil? password) (not (checkpw password (:data asuser)))) {:status 401 :body (str "Password mismatch for "
                                                                                           {:login-or-email-address login-or-email})}
      :else (handler (assoc request
                            :authenticated-entity entity
                            :is_admin (sd/is-admin (or (:id entity) (:user_id entity)) tx)
                            :authentication-method "Basic Authentication")))))

(defn authenticate [request handler]
  "Authenticate with the following rules:
  * carry on of there is no auth header with request as is,
  * return 401 if there is a login but we don't find id in DB,
  * return 401 if there is a login and entity but the password doesn't match,
  * return 403 if we find the token but the scope does not suffice,
  * carry on by adding :authenticated-entity to the request."
  (let [{username :username password :password} (extract request)]
    (if-not username
      (handler request); carry on without authenticated entity
      (if-let [user-token (token-authentication/find-user-token-by-some-secret [username password] (:tx request))]
        (token-authentication/authenticate user-token handler request)
        (user-password-authentication username password handler request)))))

(defn wrap [handler]
  (fn [request]
    (authenticate request handler)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
