(ns madek.api.features.ftr-rproxy-basic
  (:require
   [camel-snake-kebab.core :refer :all]
   [clojure.string :as str]
   [clojure.walk :refer [keywordize-keys]]
   [inflections.core :refer :all]
   [madek.api.authentication.rproxy-auth-helper :refer [verify-password]]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.env :as env]
   [ring.util.request :as request]))

(def RPROXY_BASIC_FEATURE_ENABLED? (env/get-env-bool "RPROXY_BASIC_FEATURE_ENABLED" false))

(defn continue-if-rproxy-basic-user-for-swagger-ui-is-valid [request login-or-email password]
  (println ">o> RPROXY_BASIC_FEATURE_ENABLED?=" RPROXY_BASIC_FEATURE_ENABLED?)
  (let [referer (get (:headers request) "referer")
        is-rproxy-basic-user? (verify-password login-or-email password)]
    (and RPROXY_BASIC_FEATURE_ENABLED? is-rproxy-basic-user? (or
                                                              (str/includes? (request/path-info request) "/api-docs/")
                                                              (and referer (str/ends-with? referer "api-docs/index.html"))))))

(defn abort-if-no-rproxy-basic-user-for-swagger-ui
  "After rproxy basic auth, only /api-docs/* with forward rproxy-basicAuth but not:
     - /openapi.json
     - <public api-endpoints> which have referer set _> not 401
  "
  [handler request]
  (let [referer (get (:headers request) "referer")]
    (if (and RPROXY_BASIC_FEATURE_ENABLED?
             (str/includes? (request/path-info request) "/api-v2/")
             (not (and referer (str/ends-with? referer "api-docs/index.html"))))
      (sd/response_failed "Not authorized" 401)
      (handler request))))

(defn remove-authorization-header [request]
  (let [headers (-> request :headers keywordize-keys)
        updated-headers (assoc headers :authorization "")]
    (assoc request :headers updated-headers)))

(defn remove-rproxy-auth-for-swagger-resources-if-feature-deactivated [request]
  (let [_ (println ">o> request >> " (request/path-info request))

        is-swagger-ui? (str/includes? (request/path-info request) "/api-docs/")
        request (if (and (not RPROXY_BASIC_FEATURE_ENABLED?) is-swagger-ui?) (do
                                                                               (println ">o> remove basic auth")
                                                                               (remove-authorization-header request)) request)] request))

;### Debug ####################################################################
;(debug/debug-ns *ns*)