(ns madek.api.authentication.token
  (:require
   [clojure.string :as str]
   [clojure.walk :refer [keywordize-keys]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [next.jdbc :as jdbc]
   [pandect.algo.sha256 :as algo.sha256])
  (:import
   [java.util Base64]))

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
           (sql/order-by [:users.id :asc] [:api_tokens.id :asc])
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

(defn- create-response
  ([message]
   (create-response message 401))

  ([message status]
   {:status status
    :body {:message message}}))

(defn authenticate [user-token handler request]
  (let [is-admin? (sd/is-admin (:id user-token) (:tx request))]
    (cond
      (:token_revoked user-token) (create-response "The token has been revoked.")
      (violates-not-read? user-token request)
      (create-response (str "The token is not allowed to read"
                            " i.e. to use safe http verbs.") 403)
      (violates-not-write? user-token request)
      (create-response (str "The token is not allowed to write"
                            " i.e. to use unsafe http verbs.") 403)

      (and (str/includes? (:uri request) "/api-v2/admin/") (not is-admin?))
      (create-response "The token has no admin-privileges." 403)

      :else (handler
             (assoc request
                    :authenticated-entity (assoc user-token :type "User")
                    :authentication-method "Token"
                     ; TODO move into ae
                    :is_admin is-admin?)))))

(defn find-token-secret-in-header [request]
  (when-let [header-value (-> request :headers keywordize-keys :authorization)]
    (when (re-matches #"(?i)^token\s+.+$" header-value)
      (last (re-find #"(?i)^token\s+(.+)$" header-value)))))

(defn find-and-authenticate-token-secret-or-continue [handler request]
  (if-let [token-secret (find-token-secret-in-header request)]
    (if-let [user-token (find-user-token-by-some-secret [token-secret] (:tx request))]
      (authenticate user-token handler request)
      (create-response "Access denied due invalid token"))
    (handler request)))

(defn wrap [handler]
  (fn [request]
    (find-and-authenticate-token-secret-or-continue handler request)))

;### Debug ####################################################################
;(debug/wrap-with-log-debug #'authenticate-token-header)
;(debug/debug-ns *ns*)
