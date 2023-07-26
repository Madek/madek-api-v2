(ns madek.api.web
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.tools.logging :as logging]
    [clojure.walk :refer [keywordize-keys]]
    ;[compojure.core :as cpj :refer [defroutes GET PUT POST DELETE ANY]]
    [compojure.handler :refer [site]]
    ;[compojure.route :as route]
    [environ.core :refer [env]]
    [json-roa.ring-middleware.request :as json-roa_request]
    [json-roa.ring-middleware.response :as json-roa_response]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    [madek.api.authentication :as authentication]
    [madek.api.authorization :as authorization]
    [madek.api.json-protocol]
    [madek.api.json-roa]
    [madek.api.management :as management]
    [madek.api.resources]
    [madek.api.semver :as semver]
    [madek.api.utils.config :refer [get-config]]
    [madek.api.utils.http-server :as http-server]
    [madek.api.utils.status :as status]
    [madek.api.web.browser :as web.browser]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cors :as cors-middleware]
    [ring.middleware.json]

    [ring.middleware.reload :refer [wrap-reload]]
    [reitit.ring.spec :as rs]
    [reitit.ring :as rr]
    [reitit.ring.middleware.exception :as re]
    [reitit.ring.middleware.parameters :as rmp]
    [muuntaja.core :as m]
    [reitit.ring.middleware.muuntaja :as muuntaja]
   
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.coercion.spec]
   
    [reitit.ring.coercion :as rrc]
    [reitit.coercion.schema]
    [ring.middleware.defaults :as ring-defaults]
    
    [reitit.ring.middleware.multipart :as multipart]))

;### helper ###################################################################

(defn wrap-keywordize-request [handler]
  (fn [request]
    (-> request keywordize-keys handler)))



;### exeption #################################################################

(defonce last-ex* (atom nil))

; TODO Q? why not with msg/message
(defn- wrap-exception
  ([handler]
   (fn [request]
     (wrap-exception request handler)))
  ([request handler]
   (try
     (handler request)
     (catch clojure.lang.ExceptionInfo ei
       (reset! last-ex* ei)
       (logging/error "Cought ExceptionInfo in Webstack" (thrown/stringify ei))
       (if-let [status (-> ei ex-data :status)]
         {:status status
          :body {:msg (ex-message ei)}}
         {:status 500
          :body {:msg (ex-message ei)}}))
     (catch Exception ex
       (reset! last-ex* ex)
       (logging/error "Cought ExceptionInfo in Webstack" (thrown/stringify ex))
       {:status 500
        :body {:msg (ex-message ex)}}))))




;### wrap json encoded query params ###########################################

(defn try-as-json [value]
  (try (cheshire.core/parse-string value)
       (catch Exception _
         value)))

(defn- *wrap-parse-json-query-parameters [request handler]
  (handler (assoc request :query-params
                  (->> request :query-params
                       (map (fn [[k v]] [k (try-as-json v)] ))
                       (into {})))))

(defn- wrap-parse-json-query-parameters [handler]
  (fn [request]
    (*wrap-parse-json-query-parameters request handler)))

;### wrap CORS ###############################################################

(defn add-access-control-allow-credentials [response]
  (assoc-in response [:headers "Access-Control-Allow-Credentials"] true))

(defn wrap-with-access-control-allow-credentials [handler]
  (fn [request]
    (add-access-control-allow-credentials (handler request))))

(defn wrap-cors-if-configured [handler doit]
  (if doit
    (-> handler
        (cors-middleware/wrap-cors
          :access-control-allow-origin [#".*"]
          :access-control-allow-methods [:get :put :post :delete]
          :access-control-allow-headers ["Origin" "X-Requested-With" "Content-Type" "Accept" "Authorization"])
        wrap-with-access-control-allow-credentials)
    handler))

(defn ring-wrap-cors [handler]
  (-> handler
      (cors-middleware/wrap-cors
       :access-control-allow-origin [#".*"]
       :access-control-allow-methods [:get :put :post :delete]
       :access-control-allow-headers ["Origin" "X-Requested-With" "Content-Type" "Accept" "Authorization"])
      wrap-with-access-control-allow-credentials))

;##############################################################################

;(defn wrap-roa-req-if-configured [handler doit]
;  (if doit
;    (-> handler (json-roa_request/wrap madek.api.json-roa/handler))
;    handler));

;(defn wrap-roa-res-if-configured [handler doit]
;   (if doit
;     (-> handler json-roa_response/wrap)
;     handler))
 
;(defn build-site [context]
;  (I> wrap-handler-with-logging
;      dead-end-handler
;      ; madek.api.resources/wrap-api-routes
;      authorization/wrap-authorize-http-method
;      authentication/wrap
;      management/wrap
;      web.browser/wrap
;      wrap-public-routes
;      wrap-keywordize-request
;      (wrap-roa-req-if-configured (-> (get-config) :services :api :json_roa_enabled))
;      wrap-parse-json-query-parameters
;      (wrap-cors-if-configured (-> (get-config) :services :api :cors_enabled))
;      status/wrap
;      site
;      (wrap-context context)
;      (wrap-roa-res-if-configured (-> (get-config) :services :api :json_roa_enabled))
;      (ring.middleware.json/wrap-json-body {:keywords? true})
;      ring.middleware.json/wrap-json-response
;      wrap-exception
;      ))

(defn get-router-data []
  (let [result (->>
                [
                 (when (= true (-> (get-config) :services :api :user_enabled))
                   madek.api.resources/user-routes)
                 
                 (when (= true (-> (get-config) :services :api :admin_enabled))
                   madek.api.resources/admin-routes)
                 
                 (when (= true (-> (get-config) :services :api :mgmt_enabled))
                   management/api-routes)

                 ["/test"
                  ["/exception" {:get (fn [_] (throw (ex-info "test exception" {})))
                                 :skip-auth true}]
                  ["/ok" {:get (constantly {:status 200 :body {:ok "ok"}})
                          :skip-auth true}]]

       ;api/router
                 ["" {:no-doc true
                      :skip-auth true}
                  ["/swagger.json" {:get (swagger/create-swagger-handler)}]
                  ["/api-docs/*" {:get (swagger-ui/create-swagger-ui-handler)}]]]
                (filterv some?))
        ]
    ;(logging/info "get-router-data:"
    ;              " \n Config:\n" (get-config)
    ;              ;"\n result \n " result
    ;              )
    result))

(def get-router-data-all
  (->>
   [madek.api.resources/user-routes
    madek.api.resources/admin-routes
    management/api-routes
  
    ["/test"
     ["/exception" {:get (fn [_] (throw (ex-info "test exception" {})))
                    :skip-auth true}]
     ["/ok" {:get (constantly {:status 200 :body {:ok "ok"}})
             :skip-auth true}]]
  
         ;api/router
    ["" {:no-doc true
         :skip-auth true}
     ["/swagger.json" {:get (swagger/create-swagger-handler)}]
     ["/api-docs/*" {:get (swagger-ui/create-swagger-ui-handler)}]]]
   (filterv some?)))

(def get-router-options
  {:validate rs/validate
   #_#_:compile coercion/compile-request-coercers
   :data {:middleware [swagger/swagger-feature
  
                       ring-wrap-cors
                       rmp/parameters-middleware
                           ;ring-wrap-parse-json-query-parameters 
                       muuntaja/format-negotiate-middleware
                       muuntaja/format-response-middleware
                           ;(ring.middleware.json/wrap-json-body {:keywords? true})
                           ;ring.middleware.json/wrap-json-response
                       wrap-exception
                       muuntaja/format-request-middleware
                           ;auth/wrap-auth-madek-deps
                       authorization/wrap-authorize-http-method
                           ;authentication/wrap
                       rrc/coerce-exceptions-middleware
                       rrc/coerce-request-middleware
                       rrc/coerce-response-middleware
                       multipart/multipart-middleware]
          :muuntaja m/instance}})

(defn app []
  (rr/ring-handler
   (rr/router (get-router-data) get-router-options
    )
   (rr/routes
    (rr/redirect-trailing-slash-handler)
    (rr/create-default-handler))))

(def app-all 
  (rr/ring-handler
   (rr/router get-router-data-all get-router-options)
   (rr/routes
    (rr/redirect-trailing-slash-handler)
    (rr/create-default-handler))))

(def reloadable-app
  (wrap-reload (app))
  ;(wrap-reload #'app)
  )

(def api-defaults
  (-> ring-defaults/api-defaults
      (assoc :cookies true)
      #_(assoc-in [:params :urlencoded] false)
      #_(assoc-in [:params :keywordize] false)))

(defn- wrap-defaults [handler]
  #_handler
  (ring-defaults/wrap-defaults handler api-defaults))


;(defn- wrap-deps [handler db]
;  (fn [req]
;    (handler (assoc req :db db ))))

(defn middleware [handler]
  (-> handler wrap-defaults))
  ;[handler ds]
  ;(-> handler (wrap-deps ds) wrap-defaults))

;### server ###################################################################

;(defonce server (atom nil))

;(defn start-server [& [port]]
;  (catcher/with-logging {}
;    (when @server
;      (.stop @server)
;      (reset! server nil))
;    (let [port (Integer. (or port
;                             (env :http-port)
;                             (-> (get-config) :api_service :port)
;                             3100))]
;      (reset! server
;              (jetty/run-jetty (build-site)
;                               {:port port
;                                :host "localhost"
;                                :join? false})))))


(defn initialize []
  (let [http-conf (-> (get-config) :services :api :http)
        is_reloadable (-> (get-config) :services :api :is_reloadable)
        context (str (:context http-conf) (:sub_context http-conf))]
    (logging/info "initialize with "
                  " reload " (= true is_reloadable)
                  "\nconfig: \n" (get-config))
    ;(http-server/start http-conf (middleware app ds))
    (if (= true is_reloadable)
      (http-server/start http-conf (middleware (wrap-reload #'app-all))) ;(middleware reloadable-app ds))
      (http-server/start http-conf (app)) ;(middleware app ds)))
      
      )))
      

;### Debug ####################################################################
;(debug/debug-ns *ns*)
