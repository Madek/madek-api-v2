(ns madek.api.anti-csrf.csrf-handler
;(ns leihs.inventory.server.utils.csrf-handler
  (:require
   [byte-streams :as bs]
   [clojure.string :as str]
   [clojure.walk :refer [keywordize-keys]]
   [madek.api.anti-csrf.constants :as constants]
   [madek.api.anti-csrf.core :refer [keyword str presence]]
   [cheshire.core :refer [generate-string] :rename {generate-string to-json}]

   [madek.api.utils.html-utils :refer [add-csrf-tags]]
   [madek.api.anti-csrf.simple_login :refer [sign-in-view]]
   [madek.api.anti-csrf.back :as anti-csrf :refer [anti-csrf-token]]

   ;[madek.api.web :refer [get-sign-in]]

   [madek.api.anti-csrf.constants :as constants]
   ;[leihs.core.anti-csrf.back :as anti-csrf]
   ;[leihs.core.constants :as constants]
   ;[leihs.core.core :refer [presence]]
   ;[leihs.core.db :as db]
   ;[leihs.core.json :refer [to-json]]
   ;[leihs.core.ring-audits :as ring-audits]
   ;[leihs.core.routing.back :as core-routing]
   ;[leihs.core.routing.dispatch-content-type :as dispatch-content-type]
   ;[leihs.core.sign-in.back :as be]
   ;[leihs.inventory.server.constants :as consts]
   ;[leihs.inventory.server.utils.response_helper :as rh]
   [ring.util.codec :as codec]
   [ring.util.response :as response]))

(def WHITELIST-URIS-FOR-API ["/sign-in" "/sign-out"])

(defn browser-request-matches-javascript? [request]
  (boolean (or (= (-> request :accept :mime) :javascript)
               (re-find #".+\\.js$" (or (-> request :uri presence) "")))))

;(defn wrap-dispatch-content-type
;  ([handler]
;   (fn [request]
;     (wrap-dispatch-content-type handler request)))
;  ([handler request]
;   (cond
;     (some #(= % (:uri request)) WHITELIST-URIS-FOR-API) (handler request)
;     (= (-> request :accept :mime) :json) (or (handler request)
;                                              (throw (ex-info "This resource does not provide a json response."
;                                                              {:status 404})))
;     (and (= (-> request :accept :mime) :html)
;          (#{:get :head} (:request-method request))
;          (not (browser-request-matches-javascript? request))) (rh/index-html-response request 404)
;     :else (let [response (handler request)]
;             (if (and (nil? response)
;                      (not (#{:post :put :patch :delete} (:request-method request)))
;                      (= (-> request :accept :mime) :html)
;                      (not (browser-request-matches-javascript? request)))
;               (rh/index-html-response request 404)
;               response)))))

(defn parse-cookies [cookie-header]
  (->> (str/split cookie-header #"; ")
       (map #(str/split % #"="))
       (map (fn [[k v]] [(keyword k) {:value v}]))
       (into {})))

(defn add-cookies-to-request [request]
  (let [cookie-header (get-in request [:headers "cookie"])
        parsed-cookies (when cookie-header (parse-cookies cookie-header))]
    (assoc request :cookies parsed-cookies)))

;(alter-var-root #'constants/ANTI_CSRF_TOKEN_COOKIE_NAME (constantly (keyword "madek-anti-csrf-token")))
;(alter-var-root #'constants/USER_SESSION_COOKIE_NAME (constantly (keyword "madek-session")))

(def ACTIVATE-CSRF true)

(when (not ACTIVATE-CSRF)
  (alter-var-root #'constants/HTTP_UNSAVE_METHODS (constantly #{}))
  (alter-var-root #'constants/HTTP_SAVE_METHODS (constantly #{:get :head :options :trace :delete :patch :post :put})))

(defn convert-params [request]
  (let [converted-form-params (into {} (map (fn [[k v]] [(keyword k) v]) (:form-params request)))]
    (-> request
        (assoc :form-params converted-form-params)
        (assoc :form-params-raw converted-form-params))))

(defn extract-form-params [stream]
  (try
    (let [body-str (bs/to-string stream)
          params (codec/form-decode body-str)
          keyword-params (keywordize-keys params)]
      keyword-params)
    (catch Exception _ nil)))

(defn extract-header [handler]
  (fn [request]
    (let [
          content-type (get-in request [:headers "content-type"])
          token (get-in request [:form-params "csrf-token"])

          p (println ">o> abc.token" token)

          is-accept-json? (str/includes? (str (get-in request [:headers "accept"])) "application/json")
          x-csrf-token (get-in request [:headers "x-csrf-token"])

          p (println ">o> abc.extract-form-params" (some-> (:body request) extract-form-params))
          p (println ">o> abc.content-type" content-type)
          p (println ">o> abc" (:body request))
          p (println ">o> abc" request)

          request (-> request
                      (cond-> (= content-type "application/x-www-form-urlencoded")
                        (assoc :form-params (some-> (:body request) extract-form-params)))
                      add-cookies-to-request
                      convert-params)]
      (try
        (handler request)
        (catch Throwable e
          (println ">o> abc" e)
          (if (instance? Throwable e)
            ;(if (str/includes? (:uri request) "/sign-in")
            ;  ;(get-sign-in request)
            ;  (response/redirect "/sign-in?return-to=%2Finventory&message=Error")
            ;  (-> (response/response {:status "failure"
            ;                          :message "CSRF-Token/Session not valid"
            ;                          :detail (.getMessage e)})
            ;      (response/status 403)))
            (response/status 404))))))) ;; coercion error for undefined urls

(defn wrap-csrf [handler]
  (fn [request]
    (let [referer (get-in request [:headers "referer"])
          uri (:uri request)
          api-request? (and uri (str/includes? uri "/api-docs/"))

          p (println ">o> api-request?" api-request?)
          p (println ">o> (:uri request)" (:uri request))
          ]
      (if api-request?
        (handler request)
        (if (some #(= % (:uri request)) ["/sign-in" "/sign-out" "/inventory/login" "/inventory/csrf-token"])
          (try
            ((anti-csrf/wrap handler) request)
            (catch Exception e
              (println ">o> abc" e)
              (let [uri (:uri request)]
                ;(if (str/includes? uri "/sign-in")
                ;  (response/redirect "/sign-in?return-to=%2Finventory&message=CSRF-Token/Session not valid")
                  {:status 400
                   :headers {"Content-Type" "application/json"}
                   :body (to-json {:message "Error updating password"
                                   :detail (str "error: " (.getMessage e))})}
                  ;)
                )))
          (handler request))))))
