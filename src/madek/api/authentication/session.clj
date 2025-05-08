(ns madek.api.authentication.session
  (:require
   [buddy.core.codecs :refer [bytes->b64 bytes->str]]
   [buddy.core.hash :as hash]
   [clojure.walk :refer [keywordize-keys]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.utils.config :refer [get-config]]
   [next.jdbc :as jdbc]
   [pandect.core]
   [taoensso.timbre :refer [debug]]))

(defn- get-madek-session-cookie-name []
  (or (-> (get-config) :madek_session_cookie_name keyword)
      :madek-session))

(defn token-hash [token]
  (-> token hash/sha256 bytes->b64 bytes->str))

(def expiration-sql-expr
  [:+ :user_sessions.created_at
   [:* :auth_systems.session_max_lifetime_hours [:raw "INTERVAL '1 hour'"]]])

(def selects
  [[:auth_systems.id :auth_system_id]
   [:auth_systems.name :auth_system_name]
   [:people.first_name :person_first_name]
   [:people.institutional_id :person_institutional_id]
   [:people.last_name :person_last_name]
   [:people.pseudonym :person_pseudonym]
   [:user_sessions.created_at :session_created_at]
   [:user_sessions.id :session_id]
   [:users.email :user_email]
   [:users.id :user_id]
   [:users.institutional_id :user_institutional_id]
   [:users.login :user_login]
   [expiration-sql-expr :session_expires_at]])

(defn user-session-query [token-hash]
  (-> (apply sql/select selects)
      (sql/from :user_sessions)
      (sql/join :users [:= :user_sessions.user_id :users.id])
      (sql/join :people [:= :people.id :users.person_id])
      (sql/join :auth_systems [:= :user_sessions.auth_system_id :auth_systems.id])
      (sql/where [:= :user_sessions.token_hash token-hash])
      (sql/where [:<= [:now] expiration-sql-expr])))

(defn user-session [token-hash tx]
  (-> token-hash
      user-session-query
      (sql-format :inline false)
      (#(jdbc/execute! tx %))))

(defn- session-enbabled? []
  (or (-> (get-config) :madek_api_session_enabled boolean) true))

(defn- get-cookie-value [request]
  (-> request keywordize-keys :cookies
      (get (get-madek-session-cookie-name)) :value))

(defn- handle [request handler]
  (debug 'handle request)
  (if-let [cookie-value (and (session-enbabled?) (get-cookie-value request))]
    (let [token-hash (token-hash cookie-value)
          tx (:tx request)]
      (if-let [user-session (first (user-session token-hash tx))]
        (let [user-id (:user_id user-session)
              expires-at (:session_expires_at user-session)
              user (assoc (dbh/query-eq-find-one :users :id user-id tx) :type "User")]
          (handler (assoc request
                          :authenticated-entity user
                          :is_admin (sd/is-admin user-id tx)
                          :authentication-method "Session"
                          :session-expires-at expires-at)))
        {:status 401 :body {:message "The session is invalid or expired!"}}))
    (handler request)))

(defn wrap [handler]
  (fn [request]
    (if (some #(= (:uri request) %) ["/api-v2/api-docs/openapi.json" "/sign-in"])
      (handler request)
      (handle request handler))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
