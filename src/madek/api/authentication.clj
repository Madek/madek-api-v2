(ns madek.api.authentication
  (:require
   [clojure.string :as str]
   [madek.api.authentication.basic :as basic-auth]
   [madek.api.authentication.session :as session-auth]
   [madek.api.authentication.token :as token-auth]
   [ring.util.request :as request]
   [taoensso.timbre :refer [info]]))

(defn- add-www-auth-header-if-401 [response]
  (case (:status response)
    401 (assoc-in response [:headers "WWW-Authenticate"]
          (str "Basic realm=\"Madek ApiClient with password"
               " or User with token.\""))
    response))

(defn wrap-log [handler]
  (fn [request]
    (info "wrap auth "
      " - method: " (:request-method request)
      " - path: " (request/path-info request)
      " - auth-method: " (-> request :authentication-method)
      " - type: " (-> request :authenticated-entity :type)
      " - is_admin: " (:is_admin request)
      " - auth-entity: " (-> request :authenticated-entity :id))
    (handler request)))


(defn wrap-detect-swagger [handler]
  (fn [request]
    (let [user-agent (get-in request [:headers "user-agent"])]
      (if (and user-agent (re-find #"Swagger" user-agent))
        ;; Handle Swagger UI request
        (handler (assoc request :swagger-ui? true))
        ;; Handle other requests
        (handler request)))))


(defn wrap [handler]
  (fn [request]
    (let [req-from-swagger-ui? (try
                                 (let [headers (:headers request)
                                       _ (println ">o> !!! 1request.ref _> " headers)
                                       _ (println ">o> !!! 2request.ref _> " (get headers "referer"))
                                       referer (get headers "referer")
                                       _ (println ">o> !!! 3request.ref _> " referer)
                                       req-from-swagger-ui? (str/includes? referer "api-docs/index.html")
                                       _ (println ">o> !!! 4request.ref _> " req-from-swagger-ui?)]
                                   req-from-swagger-ui?)
                                 (catch Exception e
                                   (println "Error processing request: " (.getMessage e))
                                   false))
          request (assoc request :swagger-ui? req-from-swagger-ui?)
          response ((-> handler
                        ; wrap-detect-swagger
                        session-auth/wrap
                        token-auth/wrap
                        basic-auth/wrap) request)]
      (if req-from-swagger-ui?
        response
        (add-www-auth-header-if-401 response))))) ; not needed because swagger-ui provides


;### Debug ####################################################################
;(debug/debug-ns *ns*)
