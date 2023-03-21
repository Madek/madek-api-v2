(ns madek.api.resources.meta-keys
  (:require [compojure.core :as cpj]
            [madek.api.resources.meta-keys.index :refer [get-index]]
            [madek.api.resources.meta-keys.meta-key :refer [get-meta-key]]
            [madek.api.resources.shared :as shared]
            [reitit.coercion.schema]
            [schema.core :as s]
            
            [madek.api.resources.shared :as sd]))

(def routes
  (cpj/routes
    (cpj/GET "/meta-keys/" _ get-index)
    (cpj/GET "/meta-keys/:id" _ get-meta-key)
    (cpj/ANY "*" _ shared/dead-end-handler)
    ))

; TODO meta_keys post, patch, delete
; TODO tests
(def ring-routes
  ["/meta-keys"
   ["/" {:get {:summary "Get all meta-key ids"
               :description "Get list of meta-key ids. Paging is used as you get a limit of 100 entries."
               :handler get-index
               :swagger {:produces "application/json"}
               :parameters {:query {(s/optional-key :page) s/Int
                                    (s/optional-key :full-data) s/Bool
                                    (s/optional-key :vocabulary_id) s/Str}} ; TODO test
               :content-type "application/json"
               :coercion reitit.coercion.schema/coercion
               ; TODO response coercion for full data
               ; TODO or better own link
               :responses {200 {:body {:meta-keys [{:id s/Str}]}}}}
         ; TODO
         :post {:summary (sd/sum_todo "Create meta-key.")
                :handler (constantly sd/no_impl)}
         }]

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
