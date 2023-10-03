(ns madek.api.web
  (:require
    ;[clojure.data.json :as json]
    ;[clojure.java.io :as io]
    [clojure.tools.logging :as logging]
    [clojure.walk :refer [keywordize-keys]]
    ;[environ.core :refer [env]]
    
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    [madek.api.authentication :as authentication]
    ;[madek.api.authorization :as authorization]
    [madek.api.resources.auth-info :as auth-info]
    [madek.api.json-protocol]
    
    [madek.api.management :as management]
    [madek.api.resources]
    ;[madek.api.semver :as semver]
    [madek.api.utils.config :refer [get-config]]
    [madek.api.utils.http-server :as http-server]
        
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

;(defn wrap-keywordize-request [handler]
;  (fn [request]
;    (-> request keywordize-keys handler)))



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

(defn ring-wrap-cors [handler]
  (-> handler
      (cors-middleware/wrap-cors
       :access-control-allow-origin [#".*"]
       :access-control-allow-methods [:options :get :put :post :delete]
       :access-control-allow-headers ["Origin" "X-Requested-With" "Content-Type" "Accept" "Authorization", "Credentials" "Cookie"])
      wrap-with-access-control-allow-credentials))

;##############################################################################

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



(def auth-info-route
  ["/api/auth-info" 
   {:get
    {:summary "Authentication help and info."
     :handler auth-info/auth-info
     :middleware [authentication/wrap]
     }}])

(def test-routes
  ["/test"
   ["/exception"
    {:get (fn [_] (throw (ex-info "test exception" {})))
     :skip-auth true}]
   ["/ok" 
    {:get (constantly {:status 200 :body {:ok "ok"}})
     :skip-auth true}]])

(def swagger-routes
  [""
   {:no-doc true
    :skip-auth true}
   ["/swagger.json" {:get (swagger/create-swagger-handler)}]
   ["/api-docs/*" {:get (swagger-ui/create-swagger-ui-handler)}]])

(def get-router-data-all
  (->>
   [auth-info-route
    madek.api.resources/user-routes
    madek.api.resources/admin-routes
    management/api-routes
    ;(when (true? (-> (get-config) :services :api :user_enabled))
    ;  madek.api.resources/user-routes)
    
    ;(when (= true (-> (get-config) :services :api :admin_enabled))
    ;  madek.api.resources/admin-routes)
    
    ;(when (= true (-> (get-config) :services :api :mgmt_enabled))
    ;  management/api-routes)
  
    test-routes
    swagger-routes
    ]
   (filterv some?)))

(def get-router-data-user
  (->>
   [auth-info-route
    madek.api.resources/user-routes
    management/api-routes

    test-routes
    swagger-routes
    ]
   (filterv some?)))

(def get-router-data-admin
  (->>
   [auth-info-route
    madek.api.resources/admin-routes
    management/api-routes

    test-routes
    swagger-routes
    ]
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
                       ;authorization/wrap-authorize-http-method 
                       
                       authentication/wrap
                       authentication/wrap-audit

                       rrc/coerce-exceptions-middleware
                       rrc/coerce-request-middleware
                       rrc/coerce-response-middleware
                       multipart/multipart-middleware]
          :muuntaja m/instance}})


(def app-all 
  (rr/ring-handler
   (rr/router get-router-data-all get-router-options)
   (rr/routes
    (rr/redirect-trailing-slash-handler)
    (rr/create-default-handler))))

(def app-user
  (rr/ring-handler
   (rr/router get-router-data-user get-router-options)
   (rr/routes
    (rr/redirect-trailing-slash-handler)
    (rr/create-default-handler))))

(def app-admin
  (rr/ring-handler
   (rr/router get-router-data-admin get-router-options)
   (rr/routes
    (rr/redirect-trailing-slash-handler)
    (rr/create-default-handler))))



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

(defn initialize []
  (let [http-conf (-> (get-config) :services :api :http)
        is_reloadable (-> (get-config) :services :api :is_reloadable)
        context (str (:context http-conf) (:sub_context http-conf))
        is_start_user (-> (get-config) :services :api :user_enabled)
        is_start_adm (-> (get-config) :services :api :admin_enabled)
        is_app_all (and is_start_user is_start_adm)
        ]
    (logging/info "initialize with "
                  " reload " is_reloadable
                  " start adm " is_start_adm
                  " start user " is_start_user
                  " start all " is_app_all
                  "\nconfig: \n" (get-config))
    ;(http-server/start http-conf (middleware app ds))
    (if (true? is_app_all)
      ; start all
      (initialize-all http-conf is_reloadable)
      (if (true? is_start_adm)
        (initialize-adm http-conf is_reloadable)
        (initialize-user http-conf is_reloadable)))
  ))
      

;### Debug ####################################################################
;(debug/debug-ns *ns*)
