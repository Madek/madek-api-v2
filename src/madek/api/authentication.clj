(ns madek.api.authentication
  (:require
   [clojure.string :as str]
   [clojure.walk :refer [keywordize-keys]]
   [madek.api.authentication.session :as session-auth]
   [madek.api.authentication.token :as token-auth]
   [ring.util.request :as request]
   [taoensso.timbre :refer [debug]]))

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
    (let [is-swagger-resource-request? (str/includes? (request/path-info request) "/api-docs/")
          request (if is-swagger-resource-request? (remove-authorization-header request) request)
          response ((-> handler
                        session-auth/wrap
                        token-auth/wrap) request)]
      response)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
