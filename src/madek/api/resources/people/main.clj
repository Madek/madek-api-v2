(ns madek.api.resources.people.main
  (:require
   [logbug.debug :as debug]
    ;[madek.api.resources.people.create :as create-person]
    ;[madek.api.resources.people.delete :as delete-person]
   [madek.api.resources.people.get :as get-person]
   ;[madek.api.resources.people.index :as index]
    ;[madek.api.resources.people.update :as update-person]
   [madek.api.utils.logging :as logging]
   [taoensso.timbre :refer [debug error info spy warn]]))

(def user-routes
  ["/people"
   ["/"
    {;:get index/route
     ;:post create-person/route
     }]
   ["/:id"
    {:get get-person/route}]])


(def admin-routes
  ["/people"
   ["/"
    {;:get index/route
     ;:post create-person/route
     }]
   ["/:id"
    {:get get-person/route}]])
