(ns madek.api.sign-in.back
  (:refer-clojure :exclude [keyword])
  (:require
   [clojure.string]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.anti-csrf.back :refer [anti-csrf-props]]
   [madek.api.anti-csrf.constants :as constants]
   [madek.api.anti-csrf.core :refer [str presence]]
   [madek.api.authentication.session :refer [token-hash]]
   [madek.api.sign-in.shared :refer [auth-system-user-base-query
                                     merge-identify-user]]
   [madek.api.sign_in.password-authentication.core :refer [password-checked-user]]
   [madek.api.sign_in.simple-login :as simple-login]
   [next.jdbc.sql :as jdbc]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]
   [pandect.core]
   [ring.util.response :refer [redirect]]
   [taoensso.timbre :refer [debug]])
  (:import (java.util UUID)))

(defn auth-system-query [user-id]
  (-> auth-system-user-base-query
      (sql/select :auth_systems.id
                  :auth_systems.type
                  :auth_systems.name
                  :auth_systems.description
                  :auth_systems.external_sign_in_url)
      (sql/where [:= :users.id user-id])
      sql-format))

(defn auth-systems-for-user [tx {user-id :id}]
  (if-not user-id
    []
    (->> user-id
         auth-system-query
         (jdbc-query tx))))

(defn pwd-auth-system [tx]
  (-> (sql/select :*)
      (sql/from :auth_systems)
      (sql/where [:= :type "password"])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn user-with-unique-id [tx user-unique-id]
  (-> (sql/select :*)
      (sql/from :users)
      (merge-identify-user user-unique-id)
      sql-format
      (->> (jdbc-query tx))
      first))

(def sign-in-page-renderer* (atom nil))
(defn use-sign-in-page-renderer [renderer]
  (reset! sign-in-page-renderer* renderer))

(defn render-sign-in-page
  ([user-param user request] (render-sign-in-page user-param user request {}))
  ([user-param
    user
    {:keys [tx pwd-auth-system-enabled] :as request}
    {auth-systems :authSystems :as extra-props}]
   (let [user-password (some #(-> % :type (= "password")) auth-systems)
         sign-in-page-params
         (merge-with into
                     {;:navbar (navbar-props request),
                      :authFlow {:user user-param,
                                 :showPasswordSection (-> user-password nil? not)
                                 :passwordButtonText (if user-password
                                                       "password_forgot_button_text"
                                                       "password_create_button_text")
                                 :passwordLink "/forgot-password"}}
                     (anti-csrf-props request)
                     extra-props)]
     (debug 'sign-in-page-params sign-in-page-params)
     (if (some? @sign-in-page-renderer*)
       (@sign-in-page-renderer* sign-in-page-params)
       (simple-login/sign-in-view sign-in-page-params)))))

(defn render-sign-in-page-for-invalid-user [user-param user request]
  (render-sign-in-page
   user-param
   user
   request
   {:flashMessages [{:messageID "sign_in_invalid_user_flash_message"
                     :level "error"}]}))

(defn sign-up-auth-systems [tx user-email]
  (->> (-> (sql/select :*)
           (sql/from :auth_systems)
           (sql/where [:<> :auth_systems.email_or_login_match nil])
           (sql-format))
       (jdbc-query tx)))

(defn render-sign-in [user-unique-id
                      user
                      auth-systems
                      {tx :tx settings :settings {return-to :return-to} :params :as request}]
  (let [render-sign-in-page-fn #(render-sign-in-page
                                 user-unique-id
                                 user
                                 request
                                 {:authSystems auth-systems
                                  :authFlow {:returnTo return-to}})]
    (if (and (= (count auth-systems) 1)
             (not (:password_sign_in_enabled user)))
      (let [auth-system (first auth-systems)]
        (if (= (:type auth-system) "external")

          (render-sign-in-page-fn)))
      (render-sign-in-page-fn))))

(defn sign-in-redirect
  [auth-system user-unique-id {tx :tx settings :settings :as request}]
  (redirect "/api-v2/api-docs/"))

(defn handle-first-step
  "Try to find a user account from the user param, then find all the availabe auth systems.
   1. If there is no user given, render initial page again.
   2. - If user does not exist or
      - user's account is disabled or
      - has no auth systems and his password sign in is disabled or
      - has no auth systems, his password sign in is enabled, but he doesn't have email nor password
      => show error
   3. If there is only an external auth system and password sign is is disabled, redirect to it.
   4. Otherwise show a form with all auth systems."
  [{tx :tx, {user-param :user return-to :return-to} :params :as request}]
  (let [user-unique-id (presence user-param)
        user (user-with-unique-id tx user-unique-id)
        user-auth-systems (auth-systems-for-user tx user)
        sign-up-auth-systems (->> (sign-up-auth-systems tx user-unique-id)
                                  (filter (fn [sign-up-auth-system]
                                            (if (some #(= (:id sign-up-auth-system) %) (map :id user-auth-systems))
                                              false
                                              true))))
        all-available-auth-systems (concat user-auth-systems sign-up-auth-systems)
        with-default-response #(if (:status %) % {:status 200,
                                                  :headers {"Content-Type" "text/html"}
                                                  :body %})]

    (debug 'user user 'user-auth-systems user-auth-systems 'sign-up-auth-systems sign-up-auth-systems)

    (with-default-response
      (cond

      ; there is not even an login/e-mail/org-id
        (nil? user-unique-id) (render-sign-in-page user-unique-id
                                                   user
                                                   request
                                                   {:authFlow {:returnTo return-to}})

      ; no user found and no sign-up-auth-systems
        (and (not user)
             (empty? sign-up-auth-systems)) (render-sign-in-page-for-invalid-user
                                             user-unique-id
                                             user
                                             request)

      ; no user but at least one matching sing up system
        (and (not user)
             (not-empty sign-up-auth-systems)) (render-sign-in-page
                                                user-unique-id
                                                user
                                                request
                                                {:authSystems sign-up-auth-systems})

      ; we have a matching user for all the remaining cases

      ; the user is not enabled
        (-> user :password_sign_in_enabled not) (render-sign-in-page-for-invalid-user
        ;(-> user :account_enabled not) (render-sign-in-page-for-invalid-user
                                                 user-unique-id
                                                 user
                                                 request)

      ; the user is enabled but there no available sign-in or sign-up systems and
      ; his password sign in is not enabled or it is enabled but he doesn't have an email
        (and (empty? user-auth-systems)
             (empty? sign-up-auth-systems)
             (or (-> user :password_sign_in_enabled not)
                 (and (-> user :password_sign_in_enabled)
                      (-> user :email not))))

        (render-sign-in-page-for-invalid-user user-unique-id user request)

      ; the single available auth system is external and the user can not reset the password
        (and (= 1 (count all-available-auth-systems))
             (= (-> all-available-auth-systems first :type) "external")
             (not (:password_sign_in_enabled user))) (sign-in-redirect
                                                      (first all-available-auth-systems)
                                                      user-unique-id
                                                      request)

      ; else continue with sign-in / sign-up
        :else (render-sign-in user-unique-id
                              user
                              all-available-auth-systems
                              (assoc request
                                     :pwd-auth-system-enabled
                                     (:enabled (pwd-auth-system tx))))))))

(defn create-error-response [user-param request]
  {:status 401,
   :headers {"Content-Type" "text/html"},
   :body (render-sign-in-page
          user-param
          nil
          request
          {:flashMessages [{:messageID "sign_in_wrong_password_flash_message"
                            :level "error"}]})})

(defn create-user-session
  [user authentication_system_id
   {:as request tx :tx settings :settings}
   & {:keys [user-session]
      :or {user-session {}}}]
  "Create and returns the user_session. The map includes additionally
  the original token to be used as the value of the session cookie."
  (when (:sessions_force_uniqueness settings)
    (jdbc/delete! tx :user_sessions ["user_id = ?" (:id user)]))

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
                          (jdbc/insert! tx :user_sessions))]

    (assoc user-session :token token)))

(defn handle-second-step
  "validate given user and password params.
  on success, set cookie and redirect, otherwise render page again with error.
  param `invisible-pw` signals that password has been autofilled,
  in which case an error is ignored and it is handled like first step"
  [{tx :tx, {user-param :user,
             password :password,
             invisible-pw :invisible-password,
             return-to :return-to,
             :as form-params} :form-params-raw,
    settings :settings,
    :as request}]
  (if-let [user (password-checked-user user-param password)]
    (let [user-session (create-user-session
                        user
                        constants/PASSWORD_AUTHENTICATION_SYSTEM_ID
                        request)
          location (or (presence return-to) "/api-v2/api-docs/")
          cookies {:cookies {constants/USER_SESSION_COOKIE_NAME
                             {:value (:token user-session),
                              :http-only true,
                              :path "/",
                              :secure (:sessions_force_secure settings)}}}
          response (if (= (-> request :accept :mime) :json)
                     (merge {:status 200, :body {:location location}} cookies)
                     (merge {:status 302, :headers {"Location" location}} cookies))]
      response)
    (if (not (nil? invisible-pw))
      (handle-first-step request)
      (create-error-response user-param request))))

(defn sign-in-get
  [{tx :tx, {return-to :return-to} :params :as request}]
  (if-let [user (:authenticated-entity request)]
    (redirect (or (presence return-to) "/api-v2/api-docs/"))
    (handle-first-step request)))

(defn sign-in-post
  [{tx :tx,
    {user-param :user, password :password return-to :return-to} :form-params,
    :as request}]

  (if-let [user (:authenticated-entity request)]
    (redirect (or (presence return-to) "/api-v2/api-docs/"))
    (if (or (nil? user-param) (nil? password))
      ((handle-first-step request))
      (handle-second-step request))))

(defn routes [request]
  (case (:request-method request)
    :get (sign-in-get request)
    :post (sign-in-post request)))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
