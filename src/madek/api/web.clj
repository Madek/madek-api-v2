(ns madek.api.web
  (:require
   [clojure.java.io :as io]
   [environ.core :refer [env]]
   [logbug.debug :as debug]
   [madek.api.anti-csrf.csrf-handler :as csrf]
   [madek.api.authentication :as authentication]
   [madek.api.db.core :as db]
   [madek.api.http.server :as http-server]
   [madek.api.json-protocol]
   [madek.api.resources]
   [madek.api.resources.auth-info :as auth-info]
   [madek.api.resources.shared.core :as sd]
   [madek.api.sign-in.core :as sign-in]
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
   [reitit.ring.spec :as rs]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.cors :as cors-middleware]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.json]
   [ring.middleware.reload :refer [wrap-reload]]
   [schema.core :as s]
   [taoensso.timbre :refer [debug error warn]]))

; changing DEBUG to true will wrap each middleware defined in this file with
; extended debug logging; this will increase LOGGING OUTPUT IMMENSELY and might
; have other undesired effects; make sure this is never enabled in production

(defonce ^:private DEBUG true)
(defonce CONST_ACTIVATE_STRICT_ENDPOINT_URL_404 true)

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

;###############################################################################

(defn wrap-empty-fields
  "Handle empty values if coercion.schema: If ?fields=& produced an empty string, convert it to an empty vector so
   Schema sees a valid [] instead of `\"\"`."
  [handler]
  (fn [request]
    (let [qp (:query-params request)
          raw-attr (get qp "fields")]
      (handler
       (if (and (string? raw-attr) (empty? raw-attr))
         (assoc-in request [:query-params "fields"] [])
         request)))))

;### routes ###################################################################

(def auth-info-route

  [""

   {:middleware [authentication/wrap]}

   [["/sign-in"
     {:swagger {:tags ["Login"] :security []}
      :no-doc true

      :post {:accept "application/json"
             :description "Authenticate user by login (set cookie with token)\n- Expects 'user' and 'password'"
             :coercion reitit.coercion.schema/coercion
             :handler sign-in/post-sign-in
             :responses {200 {:description "Login successful"}}}

      :get {:summary "HTML | Get sign-in page"
            :accept "text/html"
            :swagger {:consumes ["text/html"]
                      :produces ["text/html" "application/json"]}
            :handler sign-in/get-sign-in}}]]

   ["/api-v2"
    {:openapi {:tags ["api/auth-info"] :security ADMIN_AUTH_METHODS}}

    ["/sign-out/"
     {:swagger {:tags ["Logout"] :security []}
      :no-doc false
      :post {:accept "application/json"
             :swagger {:produces ["text/html" "application/json"]}
             :handler sign-in/logout-handler}}]

    ["/auth-info/"
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

    ["/test-csrf/"
     {:no-doc false
      :get {:accept "application/json"
            :description "Access allowed without x-csrf-token"
            :coercion reitit.coercion.schema/coercion
            :responses {204 {:content
                             {"application/json"
                              {}}}

                        403 {:content
                             {"application/json"
                              {:schema {:msg s/Str}

                               :examples sign-in/csrf-error-examples}}}}
            :handler (fn [_] {:status 204})}

      :post {:accept "application/json"
             :description "Access denied without x-csrf-token"
             :coercion reitit.coercion.schema/coercion
             :handler (fn [_] {:status 204})
             :responses sign-in/csrf-generic-responses}

      :put {:accept "application/json"
            :description "Access denied without x-csrf-token"
            :coercion reitit.coercion.schema/coercion
            :responses sign-in/csrf-generic-responses
            :handler (fn [_] {:status 204})}

      :patch {:accept "application/json"
              :description "Access denied without x-csrf-token"
              :coercion reitit.coercion.schema/coercion
              :responses sign-in/csrf-generic-responses
              :handler (fn [_] {:status 204})}

      :delete {:accept "application/json"
               :description "Access denied without x-csrf-token"
               :coercion reitit.coercion.schema/coercion
               :responses sign-in/csrf-generic-responses
               :handler (fn [_] {:status 204})}}]

    ["/csrf-token/"
     {:no-doc false
      :get {:summary "Retrieve X-CSRF-Token for request header"
            :accept "application/json"
            :swagger {:consumes ["application/json" "text/html"] :produces ["application/json"]}
            :coercion reitit.coercion.schema/coercion
            :responses {200
                        {:content
                         {"application/json"
                          {:schema {:authFlow {:returnTo s/Str}
                                    :flashMessages [s/Any]
                                    :csrfToken {:name s/Str :value s/Str}}

                           :examples
                           [{:summary "Extracted JSON-Data"
                             :value {:authFlow {:returnTo "/api-v2/api-docs/"}
                                     :flashMessages []
                                     :csrfToken {:name "csrf-token" :value "b766847d-93f4-4c2c-96c0-3410dce6f3d6"}}}]}}}}
            :handler sign-in/get-sign-in}}]]])

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
                                             :csrfToken {:type "apiKey" :name "x-csrf-token" :in "header"}}}
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
  [wrap-empty-fields
   swagger/swagger-feature
   ring-wrap-cors
   db/wrap-tx
   wrap-cookies
   ring-audits/wrap
   rmp/parameters-middleware
   muuntaja/format-negotiate-middleware
   muuntaja/format-response-middleware
   wrap-catch-exception
   wrap-debug
   muuntaja/format-request-middleware
   authentication/wrap
   csrf/wrap-csrf
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
   (when-not CONST_ACTIVATE_STRICT_ENDPOINT_URL_404
     (rr/redirect-trailing-slash-handler))
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
(debug/debug-ns *ns*)
