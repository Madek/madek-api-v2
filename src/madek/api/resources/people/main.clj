(ns madek.api.resources.people.main
  (:require
   [madek.api.resources.people.create :as create-person]
   [madek.api.resources.people.delete :as delete-person]
   [madek.api.resources.people.get :as get-person]
   [madek.api.resources.people.index :as index]
   [madek.api.resources.people.update :as update-person]))

(def user-routes
  ["/people"
   {:swagger {:tags ["people"]}}
   ["/"
    {;:get index/route
     }]
   ["/:id"
    {:get get-person/route}]])

(def admin-routes
  ["/people"
   {:swagger {:tags ["admin/people"] :security [{"auth" []}]}}
   ["/"
    {:get index/route
     :post create-person/route}]
   ["/:id"
    {:get get-person/route
     :patch update-person/route
     :delete delete-person/route}]])
