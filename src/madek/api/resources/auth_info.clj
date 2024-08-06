(ns madek.api.resources.auth-info
  (:require
   [clojure.tools.logging :as logging]
   [logbug.debug :as debug]))

(defn auth-info [request]

  (println ">o> 0auth-info" request)
  (println ">o> 1auth-info" (:authentication-method request))
  (println ">o> 2auth-info" (:authentication_method request))
  (println ">o> 3auth-info" (:session-expires-at request))
  (println ">o> 4auth-info" (:session_expires_at request))

  (if-let [auth-entity (:authenticated-entity request)]
    {:status 200 :body (merge {}
                              (select-keys auth-entity [:type :id :user_id :login :created_at :email_address])
                              (select-keys request [:authentication-method :session-expires-at]))}
    {:status 401 :body {:message "Not authorized"}}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
