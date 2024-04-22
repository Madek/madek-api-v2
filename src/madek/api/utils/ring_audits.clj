(ns madek.api.utils.ring-audits
  (:require
   [cuerdas.core :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.db.core :as db]
   [madek.api.utils.shared :refer [HTTP_SAFE_METHODS]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]
   [taoensso.timbre :refer [debug warn]])
  (:import (clojure.lang ExceptionInfo)))

(defn txid [tx]
  (->> ["SELECT txid() AS txid"]
       (jdbc-query tx)
       first :txid))

(defn persist-request [txid request]
  "Persist the request; does not use the main transation to avoid rollback"
  (-> (sql/insert-into :audited_requests)
      (sql/values [{:txid txid
                    :http_uid (-> request :headers (get "http-uid"))
                    :path (-> request :uri)
                    :user_id (-> request :authenticated-entity :user_id)
                    :method (-> request :request-method name str/upper)}])
      (sql-format)
      (#(jdbc/execute-one! (:tx request) % {:return-keys true}))))

(defn update-request-user-id-from-session [txid tx]
  "Set the user_id in audited_requests;  statement runs within the transaction
  otherwise it will not see the row in the user_sessions "
  (warn "TODO update-request-user-id-from-session"))

(defn persist-response [txid response tx]
  "Perstist the response, see persist-request for details."
  (-> (sql/insert-into :audited_responses)
      (sql/values [{:txid txid
                    :status (or (:status response) 500)}])
      (sql-format)
      (#(jdbc/execute-one! tx % {:return-keys true}))))

(defn wrap
  ([handler]
   (fn [request]
     (wrap handler request)))
  ([handler {handler-key :handler-key
             method :request-method
             tx :tx :as request}]
   (letfn [(audited-handler [request]
             (println ">o> within letfn!!!!")
             (let [txid (txid tx)]
               (persist-request txid request)
               (let [response (try (handler request)
                                   (catch Exception e
                                     (persist-response txid (if (instance? ExceptionInfo e)
                                                              (ex-data e)
                                                              (do (warn "Exception " e " can not be properly audited.")
                                                                  {:status 500})) tx)
                                     (throw e)))]
                 (persist-response txid response tx)
                 (when (#{:external-authentication-sign-in :sign-in} handler-key)
                   (update-request-user-id-from-session txid tx))
                 response)))]
     (debug request)
     (if (HTTP_SAFE_METHODS method)
       (handler request)
       (audited-handler request)))))

;#### debug ###################################################################
;(debug-ns *ns*)