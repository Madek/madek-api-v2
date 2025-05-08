(ns madek.api.sign_in.external-authentication.back
  (:refer-clojure :exclude [str keyword cond])
  (:require
   [better-cond.core :refer [cond]]
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.anti-csrf.constants :as constants]
   [madek.api.anti-csrf.core :refer [keyword str presence]]
   [madek.api.authentication.session :refer [token-hash]]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]
   [ring.util.response :refer [redirect]]
   [taoensso.timbre :refer [debug]]))

(defn claims! [user authentication-system settings return-to]
  {:email (when (:send_email authentication-system) (:email user))
   :login (when (:send_login authentication-system) (:login user))
   :org_id (when (:send_org_id authentication-system) (:org_id user))
   :server_base_url (:external_base_url settings)
   :return_to (presence return-to)})

(defn authentication-system [tx authentication-system-id]
  (->> (-> (sql/select :*)
           (sql/from :auth_systems)
           (sql/where [:= :id authentication-system-id])
           (sql-format))
       (jdbc-query tx) first))

(defn user [tx user-unique-id]
  (->> (-> user-unique-id
           user-query-for-unique-id
           sql-format)
       (jdbc-query tx) first))

(defn ext-auth-system-token-url
  ([tx user-unique-id authentication-system-id settings]
   (ext-auth-system-token-url tx user-unique-id authentication-system-id settings nil))
  ([tx user-unique-id authentication-system-id settings return-to]
   (cond
     :let [authentication-system (authentication-system tx authentication-system-id)
           user (or (user tx user-unique-id)
                    {:email user-unique-id})
           claims (claims! user authentication-system settings return-to)
           token (create-signed-token claims authentication-system)]
     (str (:external_sign_in_url authentication-system) "?token=" token))))

(defn authentication-request
  [{tx :tx :as request
    settings :settings
    {authentication-system-id :authentication-system-id} :route-params
    {user-unique-id :user-unique-id return-to :return-to} :params}]
  (redirect (ext-auth-system-token-url tx
                                       user-unique-id
                                       authentication-system-id
                                       settings
                                       return-to)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn authentication-system! [id tx]
  (or (->> (-> (sql/select :auth_systems.*)
               (sql/from :auth_systems)
               (sql/where [:= :auth_systems.id id])
               sql-format)
           (jdbc-query tx) first)
      (throw (ex-info "Authentication-System not found!" {:status 400}))))

(defn user-for-sign-in-token-query [sign-in-token authentication-system-id]
  (let [unique-ids [:email :login :org_id]
        unique-id (some sign-in-token unique-ids)
        base-query (auth-system-base-query-for-unique-id unique-id authentication-system-id)]
    (when-not unique-id
      (throw (ex-info
              "The sign-in token must at least submit one of email, org_id or login"
              {:status 400})))
    ; extending the base-query with the actual unique id(s) submitted makes this more stringent
    (as-> base-query query
      (if-let [email (:email sign-in-token)]
        (sql/where query [:= [:raw "lower(users.email)"] (str/lower-case email)])
        query)
      (if-let [org-id (:org_id sign-in-token)]
        (sql/where query [:= :users.org_id org-id])
        query)
      (if-let [login (:login sign-in-token)]
        (sql/where query [:= :users.login login])
        query)
      (sql/select query :users.*)
      (sql-format query))))

(defn user-for-sign-in-token [sign-in-token authentication-system-id tx]
  (let [query (user-for-sign-in-token-query sign-in-token authentication-system-id)
        resultset (jdbc-query tx query)]
    (when (> (count resultset) 1)
      (throw (ex-info
              "More than one user matched the sign-in request."
              {:status 400})))
    (or (first resultset)
        (throw (ex-info
                "No valid user account could be identified for this sign-in request."
                {:status 400})))))

(defn create-user-session
  [user authentication_system_id
   {:as request tx :tx settings :settings}
   & {:keys [user-session]
      :or {user-session {}}}]
  "Create and returns the user_session. The map includes additionally
  the original token to be used as the value of the session cookie."
  (when (:sessions_force_uniqueness settings)
    (jdbc-delete! tx :user_sessions ["user_id = ?" (:id user)]))
  (let [token (str (UUID/randomUUID))
        token-hash (token-hash token)
        user-session (->> (merge
                           user-session
                           {:user_id (:id user)
                            :token_hash token-hash
                            :token_part (apply str (take 5 token))
                            :auth_system_id authentication_system_id
                            :meta_data {:user_agent (get-in request [:headers "user-agent"])
                                        :remote_addr (get-in request [:remote-addr])}})
                          (jdbc-insert! tx :user_sessions))]
    (assoc user-session :token token)))

(defn authentication-sign-in
  [{{authentication-system-id :authentication-system-id} :route-params
    {token :token} :query-params-raw
    tx :tx request-method :request-method
    :as request}]
  (let [authentication-system (authentication-system! authentication-system-id tx)
        sign-in-token (unsign-external-token token authentication-system)
        sign-in-request-token (unsign-internal-token
                               (:sign_in_request_token sign-in-token)
                               authentication-system)]
    (debug 'sign-in-token sign-in-token)
    (if-not (:success sign-in-token)
      {:status 400
       :headers (case request-method
                  :get {"Content-Type" "text/plain"}
                  :post {})
       :body (:error_message sign-in-token)}
      (if-let [user (user-for-sign-in-token sign-in-token authentication-system-id tx)]
        (let [user-session (create-user-session
                            user authentication-system-id request
                            :user-session (select-keys sign-in-token [:external_session_id]))]
          {:body user
           :status (case request-method
                     :post 200
                     :get 302)
           :headers (case request-method
                      :post {}
                      :get {"Location" (:return_to sign-in-request-token)})
           :cookies {constants/USER_SESSION_COOKIE_NAME
                     {:value (:token user-session)
                      :http-only true
                      :max-age (* 10 356 24 60 60)
                      :path "/"
                      :secure (:sessions_force_secure (:settings request))}}})
        {:status 404}))))

(defn routes [request]
  (case (:request-method request)
    :post (authentication-request request)
    (authentication-sign-in request)))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
