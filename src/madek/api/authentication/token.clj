(ns madek.api.authentication.token
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [next.jdbc :as jdbc]
   [pandect.algo.sha256 :as algo.sha256])
  (:import
   (java.util Base64)))

(defn ^String base64-encode [^bytes bts]
  (String. (.encode (Base64/getEncoder) bts)))

(defn hash-string [s]
  (->> s
    algo.sha256/sha256-bytes
    base64-encode))

(defn find-user-token-by-some-secret [secrets tx]
  (->> (-> (sql/select :users.*
             [:scope_read :token_scope_read]
             [:scope_write :token_scope_write]
             [:revoked :token_revoked]
             [:description :token_description])
           (sql/from :api_tokens)
           (sql/where [:in :api_tokens.token_hash
                       (->> secrets
                         (filter identity)
                         (map hash-string))])
           (sql/where [:<> :api_tokens.revoked true])
           (sql/where [:raw "now() < api_tokens.expires_at"])
           (sql/join :users [:= :users.id :api_tokens.user_id])
           (sql-format))
    (jdbc/execute! tx)
    (map #(clojure.set/rename-keys % {:email :email_address}))
    first))

(defn violates-not-read? [user-token request]
  (and (not (:token_scope_read user-token))
    (#{:get :head :options}
     (:request-method request))))

(defn violates-not-write? [user-token request]
  (and (not (:token_scope_write user-token))
    (#{:delete :put :post :patch}
     (:request-method request))))

;(defn authenticate [user-token is-admin? handler request]
(defn authenticate [user-token handler request]
  (cond
    (:token_revoked user-token) {:status 401
                                 :body "The token has been revoked."}

    ;; TODO: reactivate it!!
    ;(violates-not-read?
    ;  user-token request) {:status 403
    ;                       :body (str "The token is not allowed to read"
    ;                                  " i.e. to use safe http verbs.")}
    ;(violates-not-write?
    ;  user-token request) {:status 403
    ;                       :body (str "The token is not allowed to write"
    ;                                  " i.e. to use unsafe http verbs.")}

    ;(and (str/includes? (:uri request) "admin/") (not is-admin?) ) {:status 401
    ;                             :body "The token has no admin-privileges."}

    :else (do
            (println ">o> set admin now to _> " (sd/is-admin (:user_id user-token) (:tx request)))
            (handler
              (assoc request
                     :authenticated-entity (assoc user-token :type "User")
                     ; TODO move into ae
                     :is_admin (sd/is-admin (:user_id user-token) (:tx request))))))) ;; TODO: db: admins

(defn find-token-secret-in-header [request]
  (when-let [header-value (-> request :headers keywordize-keys :authorization)]
    (when (re-matches #"(?i)^token\s+.+$" header-value)
      (last (re-find #"(?i)^token\s+(.+)$" header-value)))))


(defn- get-auth-systems-user [userId tx]
  (jdbc/execute-one! tx (-> (sql/select :*)
                            (sql/from :auth_systems_users)  ;; TODO:  db: auth_systems_users
                            (sql/where [:= :user_id userId] [:= :auth_system_id "password"])
                            sql-format)))

(defn find-and-authenticate-token-secret-or-continue [handler request]


  (println ">o> abc1")
  (if-let [token-secret (find-token-secret-in-header request)]

    (let [
          _ (println ">o> abc1, TS=" token-secret)
          user-token (find-user-token-by-some-secret [token-secret] (:tx request))
          p (println ">o> user-token=" user-token)


          ;(authenticate user-token handler request))

          is-admin-auth-sys-user? (get-auth-systems-user (:user_id user-token) (:tx request))
          p (println ">o> is-admin?=" is-admin-auth-sys-user?)

          ;is-admin-admins? (sd/is-admin (or (:id entity) (:user_id entity)) tx)
          ;p (println ">o> is-admin?=" is-admin-admins?)


          ;request (assoc request
          ;                                           ;:authenticated-entity entity
          ;                                           :is_admin (sd/is-admin (or (:id entity) (:user_id entity)) tx)
          ;                                           ;:authentication-method "Basic Authentication"
          ;               )


          ;      :else (handler (assoc request
          ;                            :authenticated-entity entity
          ;                            :is_admin (sd/is-admin (or (:id entity) (:user_id entity)) tx)
          ;                            :authentication-method "Basic Authentication")))))

          ]

      (if [user-token (find-user-token-by-some-secret [token-secret] (:tx request))]
        ;(authenticate user-token is-admin? handler request)
        (do
          (println ">o> authenticate, param set!!")
          (authenticate user-token handler request))
        {:status 401
         :body {:message "No token for this token-secret found!"}})

      )

    ;(if-let [user-token (find-user-token-by-some-secret [token-secret] (:tx request))]
    ;  (authenticate user-token handler request)
    ;  {:status 401
    ;   :body {:message "No token for this token-secret found!"}})

    (handler request)))

(defn wrap [handler]
  (fn [request]
    (println ">o> process token")
    (find-and-authenticate-token-secret-or-continue handler request)))

;### Debug ####################################################################
;(debug/wrap-with-log-debug #'authenticate-token-header)
;(debug/debug-ns *ns*)
