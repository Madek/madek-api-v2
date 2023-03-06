(ns madek.api.resources.collections
  (:require
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.resources.collections.collection :refer [get-collection]]
    [madek.api.resources.collections.index :refer [get-index]]
    [madek.api.resources.shared :as sd]
    [reitit.coercion.schema]
    [schema.core :as s]
    ))

(def routes
  (cpj/routes
    (cpj/GET "/collections/" _ get-index)
    (cpj/GET "/collections/:id" _ get-collection)
    (cpj/ANY "*" _ sd/dead-end-handler)))

(defn handle_get-collection [req]
  (get-collection req))

(defn handle_get-index [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (logging/info "handle_get-index" "\nquery-params\n" query-params)
    (get-index qreq)
    )
  )

(def ring-routes
  ["/collections"
   ["/" {:get {:handler handle_get-index
               :summary "Get collection ids"
               :description "Get collection id list."
               :swagger {:produces "application/json"}
               :parameters {:query {(s/optional-key :page) s/Str
                                    (s/optional-key :collection_id) s/Str
                                    (s/optional-key :order) s/Str
                                    (s/optional-key :me_get_metadata_and_previews) s/Bool
                                    (s/optional-key :public_get_metadata_and_previews) s/Bool
                                    (s/optional-key :me_get_full_size) s/Bool}}
               :coercion reitit.coercion.schema/coercion
               :responses {200 {:body {:collections [{:id s/Uuid :created_at s/Inst}]}}}}}]

   ["/:collection_id" {:get {:handler handle_get-collection
                             :middleware [sd/ring-wrap-add-media-resource
                                          sd/ring-wrap-authorization]
                             :summary "Get collection for id."
                             :swagger {:produces "application/json"}
                             :coercion reitit.coercion.schema/coercion
                             :parameters {:path {:collection_id s/Str}}
                             :responses {200 {:body s/Any}}}}] ; TODO response coercion
   ])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
