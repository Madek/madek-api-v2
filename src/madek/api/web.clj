(ns madek.api.web
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [environ.core :refer [env]]
   [logbug.thrown :as thrown]
   [madek.api.authentication :as authentication]
   [madek.api.db.core :as db]
   [madek.api.http.server :as http-server]
   [madek.api.json-protocol]
   [madek.api.resources]
   [cider-ci.open-session.bcrypt :refer [checkpw hashpw]]

   [madek.api.authentication.session :refer [token-hash]]

   [madek.api.resources.auth-info :as auth-info]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.core :as sd]
   [next.jdbc :as jdbc]
   [honey.sql :refer [format] :rename {format sql-format}]
   [clojure.walk :as walk :refer [keywordize-keys]]
   [honey.sql.helpers :as sql]
   ;[madek.api.sign_in.back :as sign-in]
   [digest :as d]
   [madek.api.anti-csrf.csrf-handler :as csrf :refer [wrap-csrf]]

   [madek.api.utils.html-utils :refer [add-csrf-tags]]
   [madek.api.anti-csrf.simple_login :refer [sign-in-view]]
   [madek.api.anti-csrf.back :as anti-csrf :refer [anti-csrf-token]]

   [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS]]
   [madek.api.utils.cli :refer [long-opt-for-key]]
   [madek.api.utils.ring-audits :as ring-audits]
   [muuntaja.core :as m]
   [reitit.coercion.schema]
   [reitit.coercion.spec]
   [reitit.openapi :as openapi]
   [reitit.ring :as rr]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as rmp]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [reitit.ring.spec :as rs]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.middleware.cors :as cors-middleware]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.json]
   [ring.util.response :as response]
   [ring.util.response :refer [bad-request response status]]
   [ring.middleware.reload :refer [wrap-reload]]
   [schema.core :as s]
   [taoensso.timbre :refer [debug error warn]])
(:import (com.google.common.io BaseEncoding)
 (java.time Duration Instant)
 (java.util Base64 UUID)))

; changing DEBUG to true will wrap each middleware defined in this file with
; extended debug logging; this will increase LOGGING OUTPUT IMMENSELY and might
; have other undesired effects; make sure this is never enabled in production

;### exception ################################################################

(defonce last-ex* (atom nil))

(defn- server-error-response [exception]
  ; server-error should be an unexpected exception
  ; log message as error and log trace as warning
  (error "Exception" (ex-message exception))
  (warn "Exception" exception)
  {:status 500
   :body {:msg (ex-message exception)}})

(defn- status-error-response [status exception]
  ; status error response can be due to missing authorization etc
  ; log message as warn and trance as debug
  (warn "Exception" (ex-message exception))
  (debug "Exception" exception)
  {:status status
   :body {:msg (ex-message exception)}})

(defn- wrap-catch-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo ei
        (reset! last-ex* ei)
        (if-let [status (-> ei ex-data :status)]
          (do
            (warn "CAUGHT STATUS EXCEPTION" (ex-message ei))
            (status-error-response status ei))
          (do
            (error "CAUGHT EXCEPTION WO STATUS" (ex-message ei))
            (server-error-response ei))))
      (catch Exception ex
        (reset! last-ex* ex)
        (error "CAUGHT UNEXPECTED EXCEPTION" (ex-message ex))
        (server-error-response ex)))))

(defn wrap-debug [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception ex
        (error (ex-message ex))
        (debug ex)
        (throw ex)))))

;### wrap CORS ###############################################################

(defn add-access-control-allow-credentials [response]
  (assoc-in response [:headers "Access-Control-Allow-Credentials"] true))

(defn wrap-with-access-control-allow-credentials [handler]
  (fn [request]
    (add-access-control-allow-credentials (handler request))))

(defn ring-wrap-cors [handler]
  (-> handler
      (cors-middleware/wrap-cors
       :access-control-allow-origin [#".*"]
       :access-control-allow-methods [:options :get :put :post :delete]
       :access-control-allow-headers ["Origin" "X-Requested-With" "Content-Type" "Accept" "Authorization", "Credentials" "Cookie"])
      wrap-with-access-control-allow-credentials))

;### routes ###################################################################

(def ACTIVATE-SET-CSRF true)

(defn convert-to-map [dict]
  (into {} (map (fn [[k v]] [(clojure.core/keyword k) v]) dict)))

(defn get-sign-in [request]
  (let [mtoken (anti-csrf-token request)
        query (convert-to-map (:query-params request))
        ;params (-> {:authFlow {:returnTo (or (:return-to query) "/inventory/models")}
        params (-> {:authFlow {:returnTo (or (:return-to query) "/api-v2/api-docs/")}
                    :flashMessages []}
                   (assoc :csrfToken (when ACTIVATE-SET-CSRF
                                       {:name "csrf-token" :value mtoken}))
                   (cond-> (:message query)
                     (assoc :flashMessages [{:level "error" :messageID (:message query)}])))
        accept (get-in request [:headers "accept"])
        html (add-csrf-tags (sign-in-view params) params)]
    (if (str/includes? accept "application/json")
      {:status 200 :body params}
      {:status 200 :headers {"Content-Type" "text/html; charset=utf-8"} :body html})))


(def ACTIVATE-DEV-MODE-REDIRECT true)

;(defn create-error-response [user-param request]
;  {:status 401,
;   :headers {"Content-Type" "text/html"},
;   :body (render-sign-in-page
;           user-param
;           nil
;           request
;           {:flashMessages [{:messageID "sign_in_wrong_password_flash_message"
;                             :level "error"}]})})


(defn fetch-hashed-password [request login]
  (let [query (->
               (sql/select :users.id :users.login :authentication_systems_users.authentication_system_id :authentication_systems_users.data)
               (sql/from :authentication_systems_users)
               (sql/join :users [:= :users.id :authentication_systems_users.user_id])
               (sql/where [:= :users.login login]
                 [:= :authentication_systems_users.authentication_system_id "password"])
               sql-format)
        result (jdbc/execute-one! (:tx request) query)]
    (:data result)))

(defn verify-password [request login password]

  (if (or (nil? login) (nil? password))
    false
    (if-let [user (fetch-hashed-password request login)]
      (try
        (let [hashed-password user
              res (checkpw password hashed-password)]
          res)
        (catch Exception e
          (println "Error in verify-password:" e)
          false))
      false)))

(defn verify-password-entry [request login password]
  (let [verfication-ok (verify-password request login password)
        query "SELECT * FROM users u WHERE u.login = ?"
        result (jdbc/execute-one! (:tx request) [query login])]
    (if verfication-ok
      result
      nil)))



;(defn post-sign-in [request]
;  (let [
;        form-data (keywordize-keys (:form-params request))
;        username (:user form-data)
;        password (:password form-data)
;        csrf-token (:csrf-token form-data)
;
;        p (println ">o> abc.f" form-data)
;        p (println ">o> abc.u" username)
;        p (println ">o> abc.p" password)
;        ]
;    (if (or (str/blank? username) (str/blank? password))
;      ;(create-error-response username request)
;      ;(-> (response "error") (status 500))
;
;      ;(sd/response_ok upd-result)
;      (sd/response_failed (str "Failed to login " ) 403)
;
;      (let [request (if ACTIVATE-DEV-MODE-REDIRECT
;                      (assoc-in request [:form-params :return-to] "/api-v2")
;                      request)
;            ;resp (be/routes (convert-params request))
;
;
;            ;_ (sign-in/routes request)
;            ;resp {:status 200
;            ;      :headers {"Content-Type" "application/json"}
;            ;      :body (sd/response_ok "suppi")}
;
;      ;      resp (response "suppi")
;
;            created-session (get-in resp [:cookies "madek-session" :value])
;            request (assoc request :sessions created-session :cookies {"madek-session" {:value created-session}})
;       resp (sd/response_ok {:status 200
;                             :headers {"Content-Type" "application/json"}
;                             :body (sd/response_ok "suppi")})
;            ]
;        resp))))

;(defn get-sign-out [request]
;  (let [uuid (get-in request [:cookies constants/ANTI_CSRF_TOKEN_COOKIE_NAME :value])
;        params {:authFlow {:returnTo "/inventory/models"}
;                :csrfToken (when consts/ACTIVATE-SET-CSRF
;                             {:name "csrf-token" :value uuid})}
;        html (add-csrf-tags (slurp (io/resource "public/dev-logout.html")) params)]
;    {:status 200
;     :headers {"Content-Type" "text/html"}
;     :body html}))
;(defn post-sign-out [request]
;  (let [params (-> request
;                   convert-params
;                   (assoc-in [:accept :mime] :html))
;        accept (get-in params [:headers "accept"])]
;    (if (str/includes? accept "application/json")
;      {:status (if (so/routes params) 200 409)}
;      (so/routes params))))



(defn fetch-hashed-password [request login]
  (let [query (->
               (sql/select :users.id :users.login :auth_systems_users.auth_system_id :auth_systems_users.data)
               (sql/from :auth_systems_users)
               (sql/join :users [:= :users.id :auth_systems_users.user_id])
               (sql/where [:= :users.login login]
                 [:= :auth_systems_users.auth_system_id "password"])
               sql-format)
        result (jdbc/execute-one! (:tx request) query)]
    (:data result)))

(defn verify-password [request login password]

  (if (or (nil? login) (nil? password))
    false
    (if-let [user (fetch-hashed-password request login)]
      (try
        (let [hashed-password user
              res (checkpw password hashed-password)]
          res)
        (catch Exception e
          (println "Error in verify-password:" e)
          false))
      false)))

(defn verify-password-entry [request login password]
  (let [verfication-ok (verify-password request login password)
        query "SELECT * FROM users u WHERE u.login = ?"
        result (jdbc/execute-one! (:tx request) [query login])]
    (if verfication-ok
      result
      nil)))

(defn extract-basic-auth-from-header [request]
  (try
    (let [auth-header (get-in request [:headers "authorization"])
          res (if (nil? auth-header)
                (vector nil nil)
                (let [encoded-credentials (when auth-header
                                            (second (re-find #"(?i)^Basic (.+)$" auth-header)))
                      credentials (when encoded-credentials
                                    (String. (.decode (Base64/getDecoder) encoded-credentials)))
                      [login password] (str/split credentials #":")]
                  (vector login password)))]
      res)
    (catch Exception e
      (throw
        (ex-info "BasicAuth header not found."
          {:status 403})))))

(defn sha256-hash [token]
  (d/sha-256 token))

(defn authenticate-handler [request]
  (try
    (let [
          ;[login password] (extract-basic-auth-from-header request)

          form-data (keywordize-keys (:form-params request))
          username (:user form-data)
          password (:password form-data)
          csrf-token (:csrf-token form-data)


          user (verify-password-entry request username password)]
      (if user
        (let [token (str (UUID/randomUUID))
              ;hashed-token (sha256-hash token)

              hashed-token (token-hash token)

              token-part (apply str (take 5 token))
              auth-system-id "password"
              user-id (:id user)
              check-query (-> (sql/select :*)
                              (sql/from :user_sessions)
                              (sql/where [:= :user_id [:cast user-id :uuid]]
                                [:= :auth_system_id auth-system-id])
                              sql-format)
              existing-session (jdbc/execute-one! (:tx request) check-query)


              p (println ">o> abc.user-id" (:session request))
              p (println ">o> abc.user-id" user-id)
              p (println ">o> abc.existing-session" existing-session)
              ]

          (when existing-session
            (println "Hint: session already exists for user:" user-id))

          (let [insert-query (-> (sql/insert-into :user_sessions)
                                 (sql/values
                                   [{:token_hash hashed-token
                                     :user_id user-id
                                     :auth_system_id auth-system-id
                                     ;:expires_at expires-at
                                     :token_part token-part
                                     }])
                                 sql-format)
                insert-res (jdbc/execute! (:tx request) insert-query)])

          ;(let [max-age 3600
          ;      cookie {:http-only true
          ;              :secure true
          ;              :max-age max-age
          ;              :path "/"}]
          ;  (->
          ;   (response/response
          ;     {:status "success" :message "User authenticated successfully"})
          ;   ;(response/set-cookie "madek-user-session" token cookie {:max-age max-age :path "/"})
          ;   (response/set-cookie "madek-session" user {:max-age max-age :path "/"}))))

          (let [max-age 3600
                cookie {:http-only true
                        :secure true
                        :max-age max-age
                        :path "/"}]
            (->

             (response/redirect "/api-v2/api-docs/")

             ;(response/response
             ;  {:status "success" :message "User authenticated successfully"})

             (response/set-cookie "madek-session" token cookie {:max-age max-age :path "/"})
             (response/set-cookie "madek-user-session" user {:max-age max-age :path "/"}))))

        (response/status
          (response/response {:status "failure" :message "Invalid credentials"}) 403)))

    (catch Exception e
      (println "Error in authenticate-handler:" (.getMessage e))
      (response/status
        (response/response {:message (.getMessage e)}) 400))))

(def auth-info-route

  ["/"

   [["sign-in"
     {:swagger {:tags ["Login"]}
      :no-doc false

      :post {:accept "application/json"
             :description "Authenticate user by login (set cookie with token)\n- Expects 'user' and 'password'"
             ;:swagger {:produces ["application/multipart-form-data"]}
             :coercion reitit.coercion.schema/coercion
             ;:handler post-sign-in
             :handler authenticate-handler

             :responses {200 {:description "Login successful"
                      }
      }
             }

      :get {:summary "HTML | Get sign-in page"
            :accept "text/html"
            :swagger {:consumes ["text/html"]
                      :produces ["text/html" "application/json"]}
            ;:middleware [(restrict-uri-middleware ["/sign-in"])]
            :handler get-sign-in}}]

    ;["sign-out"
    ; {:swagger {:tags ["Login"]}
    ;  :no-doc false
    ;  :post {:accept "application/json"
    ;         :swagger {:produces ["text/html" "application/json"]}
    ;         :handler post-sign-out}
    ;  :get {:accept "text/html"
    ;        :summary "HTML | Get sign-out page"
    ;        :handler get-sign-out}}]

    ]
  
  ["api-v2"
   {:openapi {:tags ["api/auth-info"] :security ADMIN_AUTH_METHODS}}
   ["/auth-info"
    {:get
     {:summary (sd/?no-auth? "Authentication help and info.")
      :handler auth-info/auth-info
      :middleware [authentication/wrap]
      :coercion reitit.coercion.schema/coercion
      :responses {200 {:description "Authentication info."
                       :body {:type s/Str
                              (s/optional-key :id) s/Uuid
                              (s/optional-key :login) s/Str
                              (s/optional-key :created_at) s/Any
                              (s/optional-key :email_address) s/Str
                              (s/optional-key :authentication-method) s/Str
                              (s/optional-key :session-expires-at) s/Any}}
                  401 (sd/create-error-message-response "Creation failed." "Not authorized")}}}]

   ["/test-csrf"
    {:no-doc false
     :get {:accept "application/json"
           :description "Access allowed without x-csrf-token"
           :handler (fn [_] {:status 200})}
     :post {:accept "application/json"
            :description "Access denied without x-csrf-token"
            :handler (fn [_] {:status 200})}
     :put {:accept "application/json"
           :description "Access denied without x-csrf-token"
           :handler (fn [_] {:status 200})}
     :patch {:accept "application/json"
             :description "Access denied without x-csrf-token"
             :handler (fn [_] {:status 200})}
     :delete {:accept "application/json"
              :description "Access denied without x-csrf-token"
              :handler (fn [_] {:status 200})}}]

   ]])

(def swagger-routes
  ["/api-v2"
   {:no-doc false
    :openapi {:openapi "3.0.1"
              ;:openapi "3.1.0"                              ;;swagger error but config is 3.1.0 conform
              ;; swagger-ui: Unable to render this definition, requires swagger: "2.0" OR openapi: 3.0.n
              ;; https://github.com/api-platform/core/issues/4531
              ;; https://clojurians-log.clojureverse.org/reitit/2023-05-03
              :info {:title "Madek API v2"
                     :description (slurp (io/resource (sd/doc "md/api-description.md")))
                     :version "0.1"
                     :contact {:name "N/D"}}
              :components {:securitySchemes {:apiAuth {:type "apiKey"
                                                       :name "Authorization"
                                                       :in "header"}
                           :csrfToken {:type "apiKey" :name "x-csrf-token" :in "header"}
                                             }
                           }
              :security [{:apiAuth []} {:csrfToken []}]}}
   ["/api-docs/openapi.json" {:no-doc true :get (openapi/create-openapi-handler)}]])

(def get-router-data-all
  (->>
   [auth-info-route
    madek.api.resources/user-routes
    madek.api.resources/admin-routes
    ;management/api-routes
    ;test-routes
    swagger-routes]
   (filterv some?)))

(def get-router-data-user
  (->>
   [auth-info-route
    madek.api.resources/user-routes
    ;management/api-routes
    ;test-routes
    swagger-routes]
   (filterv some?)))

(def get-router-data-admin
  (->>
   [auth-info-route
    madek.api.resources/admin-routes
    ;management/api-routes
    ;test-routes
    swagger-routes]
   (filterv some?)))

(def ^:dynamic middlewares
  [swagger/swagger-feature
   ring-wrap-cors
   db/wrap-tx
   wrap-cookies
   ring-audits/wrap
   rmp/parameters-middleware
   muuntaja/format-negotiate-middleware
   muuntaja/format-response-middleware
   wrap-catch-exception
   wrap-debug

   ;madek.api.anti-csrf.csrf-handler

   ;csrf/extract-header

   csrf/wrap-csrf

   anti-csrf/wrap

   muuntaja/format-request-middleware
   authentication/wrap
   authentication/wrap-log
   rrc/coerce-exceptions-middleware
   rrc/coerce-request-middleware
   rrc/coerce-response-middleware
   multipart/multipart-middleware])

; TODO, QUESTION: the following will add the whole middleware stack to the data
; object in the request; is this in anyway usefull? e.g. debugging ??? if not
; so: can it be removed, it blows up the data for each request insanely

(def get-router-options
  {:validate rs/validate
   #_#_:compile coercion/compile-request-coercers
   :data {:middleware middlewares
          :muuntaja m/instance}})

(def swagger-handler
  (swagger-ui/create-swagger-ui-handler
   {:path "/api-v2/api-docs/"
    :config {:validatorUrl nil
             :urls [{:name "openapi" :url "openapi.json"}]
             :urls.primaryName "openapi"
             :operationsSorter "alpha"}}))

(def common-routes
  (rr/routes
   swagger-handler
   (rr/redirect-trailing-slash-handler)
   (rr/create-default-handler)))

(defn create-app [router-data]
  (rr/ring-handler
   (rr/router router-data get-router-options)
   common-routes))

(def app-all (create-app get-router-data-all))
(def app-user (create-app get-router-data-user))
(def app-admin (create-app get-router-data-admin))

(def api-defaults
  (-> ring-defaults/api-defaults
      (assoc :cookies true)
      #_(assoc-in [:params :urlencoded] false)
      #_(assoc-in [:params :keywordize] false)))

(defn- wrap-defaults [handler]
  #_handler
  (ring-defaults/wrap-defaults handler api-defaults))

(defn middleware [handler]
  (-> handler wrap-defaults))

;### server ###################################################################

; cli

(def http-resources-scope-key :http-resources-scope)

(def cli-options
  (concat http-server/cli-options
          [[nil (long-opt-for-key http-resources-scope-key)
            "Either ALL, ADMIN or USER"
            :default (or (some-> http-resources-scope-key env)
                         "ALL")
            :validate [#(some #{%} ["ALL" "ADMIN" "USER"]) "scope must be ALL, ADMIN or USER"]]]))

(defn initialize-all [http-conf is_reloadable]
  (if (true? is_reloadable)
    (http-server/start http-conf (middleware (wrap-reload #'app-all)))
    (http-server/start http-conf (middleware app-all))))

(defn initialize-adm [http-conf is_reloadable]
  (if (true? is_reloadable)
    (http-server/start http-conf (middleware (wrap-reload #'app-admin)))
    (http-server/start http-conf (middleware app-admin))))

(defn initialize-user [http-conf is_reloadable]
  (if (true? is_reloadable)
    (http-server/start http-conf (middleware (wrap-reload #'app-user)))
    (http-server/start http-conf (middleware app-user))))

(defn initialize [options]
  (let [handler (case (http-resources-scope-key options)
                  "ALL" (middleware (wrap-reload app-all))
                  "ADMIN" (middleware app-admin)
                  "USER" (middleware app-user))]
    (http-server/start handler options)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
