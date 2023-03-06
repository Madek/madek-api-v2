(ns madek.api.resources.meta-keys
  (:require [compojure.core :as cpj]
            [madek.api.resources.meta-keys.index :refer [get-index]]
            [madek.api.resources.meta-keys.meta-key :refer [get-meta-key]]
            [madek.api.resources.shared :as shared]
            [reitit.coercion.schema]
            [schema.core :as s]
            ))

(def routes
  (cpj/routes
    (cpj/GET "/meta-keys/" _ get-index)
    (cpj/GET "/meta-keys/:id" _ get-meta-key)
    (cpj/ANY "*" _ shared/dead-end-handler)
    ))

(def ring-routes
  ["/meta-keys"
   ["/" {:get {:summary "Get all meta-key ids"
               :description "Get list of meta-key ids. Paging is used as you get a limit of 100 entries."
               :handler get-index
               :swagger {:produces "application/json"}
               :parameters {:query {(s/optional-key :page) s/Int}}
               :content-type "application/json"
               :coercion reitit.coercion.schema/coercion
               :responses {200 {:body {:meta-keys [{:id s/Str}]}}}}}]

   ["/:id" {:get {:summary "Get meta-key by id"
                  :description "Get meta-key by id. Returns 404, if no such person exists."
                  :swagger {:produces "application/json"}
                  :content-type "application/json"
                  :accept "application/json"
                  :handler get-meta-key
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Str}}
                  :responses {200 {:body s/Str}
                              404 {:body {:message s/Str}}
                              422 {:body {:message s/Str}}}}}]])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
