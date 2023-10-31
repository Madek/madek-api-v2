(ns madek.api.authentication.session
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc :as njdbc]
   [buddy.core.codecs :refer [bytes->b64 bytes->str]]
   [buddy.core.hash :as hash]
   [clj-time.core :as time]
   [clj-time.format :as time-format]
   [clojure.java.jdbc :as jdbc]
   [clojure.walk :refer [keywordize-keys]]
   [logbug.catcher :as catcher]
   [madek.api.legacy.session.encryptor :refer [decrypt]]
   [madek.api.legacy.session.signature :refer [valid?]]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.config :refer [get-config
                                   parse-config-duration-to-seconds]]
   [madek.api.utils.rdbms :as rdbms]

   [taoensso.timbre :refer [debug spy]]
   [clojure.tools.logging :as logging]))

(defn- get-session-secret []
  (-> (get-config) :madek_master_secret))

(defn- get-user [user-id]
  (when-let [user (-> (jdbc/query (rdbms/get-ds)
                                  ["SELECT * FROM users WHERE id = ? " user-id])
                      first)]
    (assoc user :type "User")))

(defn- get-madek-session-cookie-name []
  (or (-> (get-config) :madek_session_cookie_name keyword)
      (throw (IllegalStateException.
              "The  madek_session_cookie_name is not configured."))))

(defn- session-signature-valid? [user session-object]
  (valid? (-> session-object :signature)
          (get-session-secret)
          (-> user :password_digest)))

(defn- decrypt-cookie [cookie-value]
  (catcher/snatch {}
    (decrypt (get-session-secret) cookie-value)))

(defn- get-validity-duration-secs []
  (or (catcher/snatch {}
        ((memoize #(parse-config-duration-to-seconds
                     :madek_session_validity_duration))))
      3))

(defn session-expiration-time [session-object validity-duration-secs]
  (if-let [issued-at (-> session-object :issued_at time-format/parse)]
    (let [valid-for-secs (->> [validity-duration-secs
                               (:max_duration_secs session-object)]
                              (filter identity)
                              (#(if (empty? %) [0] %))
                              (apply min))]
      (time/plus issued-at (time/seconds valid-for-secs)))
    (time/now)))

(defn- session-not-expired? [session-object]
  (when-let [issued-at (-> session-object :issued_at time-format/parse)]
    (time/before? (time/now)
                  (session-expiration-time session-object
                                           (get-validity-duration-secs)))))

(defn- token-hash [token]
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
   [expiration-sql-expr :session_expires_at]
   ])

(defn user-session-query [token-hash]
  (-> (apply sql/select selects)
      (sql/from :user_sessions)
      (sql/join :users [:= :user_sessions.user_id :users.id])
      (sql/join :people [:= :people.id :users.person_id])
      (sql/join :auth_systems [:= :user_sessions.auth_system_id :auth_systems.id])
      (sql/where [:= :user_sessions.token_hash token-hash])
      (sql/where [:<= [:now] expiration-sql-expr])
      ))

(defn user-session [token-hash ]
  (-> token-hash
      user-session-query
      (sql-format :inline true)
      spy
      (#(njdbc/execute! (rdbms/get-ds) %))))

(defn- session-enbabled? []
  (-> (get-config) :madek_api_session_enabled boolean))

(defn- get-cookie-value [request]
  (-> request keywordize-keys :cookies
      (get (get-madek-session-cookie-name)) :value))

(defn in-seconds [from to]
  (time/in-seconds (time/interval from to)))

(defn- handle [request handler]
  (debug 'handle request)
  (if-let [cookie-value (and (session-enbabled?) (get-cookie-value request))]
    (let [token-hash (token-hash cookie-value)]
      (if-let [user-session (first (user-session token-hash))]
          (let [user-id (:users/user_id user-session)
                expires-at (:session_expires_at user-session)
                user (assoc (sd/query-eq-find-one :users :id user-id) :type "User")]
            #_(logging/info "handle session: "
                          "\nfound user session:\n " user-session
                          "\n user-id:  " user-id
                          "\n expires-at: " expires-at
                          "\n user: " user)
            (handler (assoc request
                            :authenticated-entity user
                            :is_admin (sd/is-admin user-id)
                            :authentication-method "Session"
                            :session-expires-at expires-at
                            ;:session-expiration-seconds (in-seconds (time/now) expires-at)
                            
                            )))
          {:status 401 :body {:message "The session is invalid or expired!"}}
        )
    )   
        
     ;   {:status 401 :body {:message "The session is invalid!"}}
    
      ;)
   
    (handler request)
  )
)

(defn wrap [handler]
  (fn [request]
    (handle request handler)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
