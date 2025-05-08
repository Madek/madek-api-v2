(ns madek.api.sign-in.core
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.walk :refer [keywordize-keys]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.debug :as debug]
   [madek.api.anti-csrf.back :refer [anti-csrf-token]]
   [madek.api.anti-csrf.csrf-handler :refer [convert-params]]
   [madek.api.anti-csrf.simple_login :refer [sign-in-view]]
   [madek.api.authentication.session :refer [token-hash]]
   [madek.api.json-protocol]
   [madek.api.resources]
   [madek.api.sign-in.back :as be]
   [madek.api.utils.html-utils :refer [add-csrf-tags]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.spec]
   [ring.middleware.json]
   [ring.util.response]
   [ring.util.response :as response]
   [schema.core :as s]))

(defn convert-to-map [dict]
  (into {} (map (fn [[k v]] [(clojure.core/keyword k) v]) dict)))

(defn get-sign-in [request]
  (let [mtoken (anti-csrf-token request)
        query (convert-to-map (:query-params request))
        params (-> {:authFlow {:returnTo (or (:return-to query) "/api-v2/api-docs/")}
                    :flashMessages []}
                   (assoc :csrfToken {:name "csrf-token" :value mtoken})
                   (cond-> (:message query)
                     (assoc :flashMessages [{:level "error" :messageID (:message query)}])))
        accept (get-in request [:headers "accept"])
        html (add-csrf-tags (sign-in-view params) params)]
    (if (str/includes? accept "application/json")
      {:status 200 :body params}
      {:status 200 :headers {"Content-Type" "text/html; charset=utf-8"} :body html})))

(def ACTIVATE-DEV-MODE-REDIRECT true)

(defn post-sign-in [request]
  (let [form-data (keywordize-keys (:form-params request))
        username (:user form-data)
        password (:password form-data)]
    (if (or (str/blank? username) (str/blank? password))
      (be/create-error-response username request)
      (let [request (if ACTIVATE-DEV-MODE-REDIRECT
                      (assoc-in request [:form-params :return-to] "/api-v2/api-docs/")
                      request)
            resp (be/routes (convert-params request))
            created-session (get-in resp [:cookies "madek-user-session" :value])]
        (assoc request :sessions created-session
               :cookies {"madek-user-session" {:value created-session}})
        resp))))

(defn parse-cookies [cookie-header]
  (->> (str/split cookie-header #";\s*")
       (map #(str/split % #"=" 2))
       (into {})))

(defn logout-handler [request]
  (let [user-id (-> request :authenticated-entity :id)
        cookie-header (get-in request [:headers "cookie"] "")
        cookies-map (parse-cookies cookie-header)
        session-id (get cookies-map "madek-session")
        hashed-token (token-hash session-id)
        delete-query (-> (sql/delete-from :user_sessions)
                         (sql/where [:= :token_hash hashed-token])
                         sql-format)
        delete-result (jdbc/execute! (:tx request) delete-query)]
    (try
      (if (> (:next.jdbc/update-count (first delete-result)) 0)
        (do
          (log/info "Successfully removed session for user_id:" user-id)
          (-> (response/response {:status "success" :message "User logged out successfully"})
              (response/set-cookie "madek-session" "" {:max-age 0 :path "/"})))
        (do
          (log/warn "No session found for user_id:" user-id)
          (response/status
           (response/response {:status "failure" :message "No active session found"}) 404)))
      (catch Exception e
        (log/error "Error in logout-handler:" e)
        (response/status (response/response {:message (.getMessage e)}) 400)))))

(def csrf-error-examples [{:summary "Has not been send"
                           :value {:msg "The x-csrf-token has not been send!"}}
                          {:summary "Session & x-csrf are not identical"
                           :value {:msg "The x-csrf-token is not equal to the anti-csrf cookie value."}}])

(def csrf-generic-responses {204 {:content
                                  {"application/json"
                                   {}}}
                             403 {:content
                                  {"application/json"
                                   {:schema {:msg s/Str}
                                    :examples csrf-error-examples}}}})

;### Debug ####################################################################
(debug/debug-ns *ns*)
