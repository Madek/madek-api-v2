(ns madek.api.resources.admins
  (:require
   [madek.api.resources.shared :as sd]
   [clojure.tools.logging :as logging]
   [logbug.catcher :as catcher]
   [clojure.java.jdbc :as jdbc]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [reitit.coercion.schema]
   [schema.core :as s]
   ))


(def ring-routes
  ["/admins"
   ["/" {; admin create
         :post {:summary (sd/sum_adm_todo "Create admin user.")
                :handler (constantly sd/no_impl)}
                ; admin list / query
         :get {:summary  (sd/sum_adm_todo "List admin users.")
               :handler (constantly sd/no_impl)}}]

   ["/:id" {:get {:summary (sd/sum_adm_todo "Get admin user by id.")
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Uuid}}
                  :handler sd/show-params}

            :delete {:summary (sd/sum_adm_todo "Delete admin user by id.")
                     :coercion reitit.coercion.schema/coercion
                     :parameters {:path {:id s/Uuid}}
                     :handler sd/show-params}}]

   ["/by-user-id/:user-id/" {:get {:summary (sd/sum_cnv_adm_todo "Get admin user by user-id.")
                                   :coercion reitit.coercion.schema/coercion
                                   :parameters {:path {:user-id s/Uuid}}
                                   :handler sd/show-params}

                             :delete {:summary (sd/sum_cnv_adm_todo "Delete admin user by user-id.")
                                      :coercion reitit.coercion.schema/coercion
                                      :parameters {:path {:user-id s/Uuid}}
                                      :handler sd/show-params}}]])