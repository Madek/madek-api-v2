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

(defn wrap [handler]
  (fn [request]
    (let [req-from-swagger-ui? (try
                                 (let [headers (:headers request)
                                       referer (get headers "referer")
                                       req-from-swagger-ui? (str/includes? referer "api-docs/index.html")]
                                   req-from-swagger-ui?)
                                 (catch Exception e false))
          request (assoc request :swagger-ui? req-from-swagger-ui?)
          response ((-> handler
                        session-auth/wrap
                        token-auth/wrap
                        basic-auth/wrap) request)]
      ; for swagger-ui avoid returning of WWW-Authenticate to prevent triggering of basic-auth-popup in browser
      (if req-from-swagger-ui?
        response
        (add-www-auth-header-if-401 response)))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
