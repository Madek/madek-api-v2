(ns madek.api.authentication
  (:require
   [clojure.string :as str]
   [clojure.walk :refer [keywordize-keys]]
   [madek.api.authentication.session :as session-auth]
   [madek.api.authentication.token :as token-auth]
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

(defn remove-authorization-header [request]
  (let [headers (-> request :headers keywordize-keys)
        updated-headers (assoc headers :authorization "")]
    (assoc request :headers updated-headers)))

(defn wrap [handler]
  (fn [request]
    (let [referer (or (get (:headers request) "referer") (:referer (:headers request)))
          is-swagger-resource-request? (str/includes? (request/path-info request) "/api-docs/")
          is-swagger-request? (or (str/includes? (request/path-info request) "/api-docs/")
                                  (and referer (str/ends-with? referer "api-docs/index.html")))
          request (if is-swagger-resource-request? (remove-authorization-header request) request)
          response ((-> handler
                        session-auth/wrap
                        token-auth/wrap) request)]
      ; for swagger-ui avoid returning of WWW-Authenticate to prevent triggering of basic-auth-popup in browser
       response)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
