(ns madek.api.anti-csrf.csrf-handler
  (:require
   [clojure.string :as str]
   [madek.api.anti-csrf.back :as anti-csrf]
   [madek.api.anti-csrf.constants :as constants]))

(def ACTIVATE-CSRF true)

(when (not ACTIVATE-CSRF)
  (alter-var-root #'constants/HTTP_UNSAVE_METHODS (constantly #{}))
  (alter-var-root #'constants/HTTP_SAVE_METHODS (constantly #{:get :head :options :trace :delete :patch :post :put})))

(defn convert-params [request]
  (let [converted-form-params (into {} (map (fn [[k v]] [(keyword k) v]) (:form-params request)))]
    (-> request
        (assoc :form-params converted-form-params)
        (assoc :form-params-raw converted-form-params))))

(defn wrap-csrf [handler]
  (fn [request]
    (let [uri (:uri request)
          api-request? (and uri (str/includes? uri "/api-docs/"))
          auth (get-in request [:headers "authorization"])
          token-auth-header? (fn [hdr]
                               (and (string? hdr)
                                    (str/starts-with? (str/lower-case hdr) "token ")))]
      (if (or api-request? (token-auth-header? auth))
        (handler request)
        (if (and (not (some #(= % uri) ["/sign-in"]))
                 (not (token-auth-header? auth)))
          ((anti-csrf/wrap handler) request)
          (handler request))))))
