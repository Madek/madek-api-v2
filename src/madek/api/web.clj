(ns madek.api.web
  (:require
   [clojure.java.io :as io]
   [environ.core :refer [env]]
   [logbug.thrown :as thrown]
   [madek.api.authentication :as authentication]
   [madek.api.db.core :as db]
   [madek.api.http.server :as http-server]
   [madek.api.json-protocol]
   [madek.api.resources]
   [madek.api.resources.auth-info :as auth-info]
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
   [ring.middleware.cors :as cors-middleware]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.json]
   [ring.middleware.reload :refer [wrap-reload]]
   [schema.core :as s]
   [taoensso.timbre :refer [debug error warn]]))

; changing DEBUG to true will wrap each middleware defined in this file with
; extended debug logging; this will increase LOGGING OUTPUT IMMENSELY and might
; have other undesired effects; make sure this is never enabled in production

(defonce ^:private DEBUG false)

;### exception ################################################################

(defonce last-ex* (atom nil))

(defn- server-error-response [exception]
  ; server-error should be an unexpected exception
  ; log message as error and log trace as warning
  (error "Exception" (ex-message exception))
  (warn "Exception" (thrown/stringify exception))
  {:status 500
   :body {:msg (ex-message exception)}})

(defn- status-error-response [status exception]
  ; status error response can be due to missing authorization etc
  ; log message as warn and trance as debug
  (warn "Exception" (ex-message exception))
  (debug "Exception" (thrown/stringify exception))
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

(def auth-info-route
  ["/api-v2"
   {:openapi {:tags ["api/auth-info *"] :security ADMIN_AUTH_METHODS}}
   ["/auth-info"
    {:get
     {:summary "Authentication help and info."
      :handler auth-info/auth-info
      :middleware [authentication/wrap]
      :coercion reitit.coercion.schema/coercion
      :responses {200 {:description "Authentication info."
                       :body {:type s/Str
                                :id s/Uuid
                                :login s/Str
                                :created_at s/Any
                                :email_address s/Str
                                (s/optional-key :authentication-method) s/Str}}
                  401 {:description "Creation failed."
                       :body s/Str
                       :examples {"application/json" {:message "Not authorized1"}}}}}}]])

(def swagger-routes
  ["/api-v2"
   {:no-doc false
    :openapi {:openapi "3.0.1"
              ;:openapi "3.1.0"                              ;;swagger error but config is 3.1.0 conform
              ;; swagger-ui: Unable to render this definition, requires swagger: "2.0" OR openapi: 3.0.n
              ;; https://github.com/api-platform/core/issues/4531
              ;; https://clojurians-log.clojureverse.org/reitit/2023-05-03
              :info {:title "Madek API v2"
                     :description (slurp (io/resource "md/api-description.md"))
                     :version "0.1"
                     :contact {:name "N/D"}}
              :components {:securitySchemes {:apiAuth {:type "apiKey"
                                                       :name "Authorization"
                                                       :in "header"}}}
              :security [{:apiAuth []}]}}
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
   ring-audits/wrap
   rmp/parameters-middleware
   muuntaja/format-negotiate-middleware
   muuntaja/format-response-middleware
   wrap-catch-exception
   muuntaja/format-request-middleware
   authentication/wrap
   authentication/wrap-log
   rrc/coerce-exceptions-middleware
   rrc/coerce-request-middleware
   rrc/coerce-response-middleware
   multipart/multipart-middleware])

(when DEBUG
  (def ^:dynamic debug-last-ex nil)
  (defn wrap-debug [handler]
    (fn [request]
      (let [wrap-debug-level (or (:wrap-debug-level request) 0)]
        (try
          (debug "RING-LOGGING-WRAPPER"
                 {:wrap-debug-level wrap-debug-level
                  :request request})
          (let [response (handler
                          (assoc request :wrap-debug-level (inc wrap-debug-level)))]
            (debug "RING-LOGGING-WRAPPER"
                   {:wrap-debug-level wrap-debug-level
                    :response response})
            response)
          (catch Exception ex
            (def ^:dynamic debug-last-ex ex)
            (error "RING-LOGGING-WRAPPER CAUGHT EXCEPTION "
                   {:wrap-debug-level wrap-debug-level} (ex-message ex))
            (error "RING-LOGGING-WRAPPER CAUGHT EXCEPTION " (thrown/stringify ex))
            (throw ex))))))
  (let [mws middlewares]
    (def ^:dynamic middlewares
      (into [] (interpose wrap-debug mws)))))

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
