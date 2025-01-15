(ns madek.api.resources.auth-info
  (:require
   [clojure.tools.logging :as logging]
   [logbug.debug :as debug]))



(defn pr [str fnc]
  ;(println ">oo> HELPER / " str fnc)(println ">oo> HELPER / " str fnc)
  (println ">oo> " str fnc)
  fnc
  )
(defn auth-info [request]
  (if-let [auth-entity (pr ">o> auth-ent?" (:authenticated-entity request))

           ;p (println ">o> auth-entity?" auth-entity)
           ]
    {:status 200 :body (merge {}
                              (select-keys auth-entity [:type :id :login :created_at :email_address])
                              (select-keys request [:authentication-method :session-expires-at]))}
    {:status 401 :body {:message "Not authorized3"}}))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
