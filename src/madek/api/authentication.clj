(ns madek.api.authentication
  (:require
   [clojure.string :as str]
   [madek.api.authentication.basic :as basic-auth]
   [madek.api.authentication.session :as session-auth]
   [madek.api.authentication.token :as token-auth]
   [madek.api.features.ftr-rproxy-basic :refer [remove-rproxy-auth-for-swagger-resources-if-feature-deactivated]]
   [ring.util.request :as request]
   [taoensso.timbre :refer [debug]]))

(defn- add-www-auth-header-if-401 [response]
  (case (:status response)
    401 (assoc-in response [:headers "WWW-Authenticate"]
          (str "Basic realm=\"Madek ApiClient with password"
               " or User with token.\""))
    response))

(defn wrap-log [handler]
  (fn [request]
    (debug "wrap auth "
      " - method: " (:request-method request)
      " - path: " (request/path-info request)
      " - auth-method: " (-> request :authentication-method)
      " - type: " (-> request :authenticated-entity :type)
      " - is_admin: " (:is_admin request)
      " - auth-entity: " (-> request :authenticated-entity :id))
    (handler request)))

(defn wrap [handler]
  (fn [request]
    (let [request (remove-rproxy-auth-for-swagger-resources-if-feature-deactivated request)
          referer (get (:headers request) "referer")
          is-api-request? (and referer (str/ends-with? referer "api-docs/index.html"))
          response ((-> handler
                        session-auth/wrap
                        token-auth/wrap
                        basic-auth/wrap) request)]

      ; For incoming requests from swagger-ui to api-endpoint avoid returning of WWW-Authenticate
      ; to prevent triggering of basic-auth-popup in browser
      (if is-api-request?
        response
        (add-www-auth-header-if-401 response)))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
