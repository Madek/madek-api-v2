(ns madek.api.resources.custom-urls 
  (:require
   [clojure.java.jdbc :as jdbc]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [reitit.coercion.schema]
   [schema.core :as s]
   [madek.api.resources.media-resources.permissions :as mr-permissions]))

; TODO query be collection or media_entry
(defn handle_list-custom-urls
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data))
        qd (if (true? full-data) :* :id) ; TODO [:id :media_entry_id :collection_id])
        db-result (sd/query-find-all :custom_urls qd)]
    (logging/info "handle_list-custom-urls" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-custom-url
  [req]
  (let [id (-> req :parameters :path :id)]
    (if-let [result (sd/query-eq-find-one "custom_urls" "id" id)]
      (sd/response_ok result)
      (sd/response_not_found (str "No such custom_url for id: " id)))
        ))

(defn handle_get-custom-urls
  [req]
  (let [mr (-> req :media-resource)
        mr-type (-> mr :type)
        mr-id (-> mr :id str)
        col-name (if (= mr-type "MediaEntry") "media_entry_id" "collection_id")
        ]
    
    (logging/info "handle_get-custom-urls"
                  "\ntype\n" mr-type
                  "\nid\n" mr-id
                  "\ncol-name\n" col-name)
    (if-let [result (sd/query-eq-find-one "custom_urls" col-name mr-id)]
      (sd/response_ok result)
      (sd/response_not_found (str "No such custom_url for " mr-type " with id: " mr-id)))
    ))

(defn handle_create-custom-urls
  [req]
  (let [mr (-> req :media-resource)
        mr-type (-> mr :type)
        mr-id (-> mr :id str)
        col-name (if (= mr-type "MediaEntry") "media_entry_id" "collection_id")
        data (-> req :parameters :body)
        dwid (if (= mr-type "MediaEntry")
               (assoc data :media_entry_id mr-id)
               (assoc data :collection_id mr-id))
        ]
    
    (logging/info "handle_create-custom-urls"
                  "\ntype\n" mr-type
                  "\nid\n" mr-id
                  "\ncol-name\n" col-name
                  "\ndwid\n" dwid)
    (if-let [ins-res (first (jdbc/insert! (rdbms/get-ds) :custom_urls dwid))]
      (sd/response_ok ins-res) 
      (sd/response_failed "Could not create custom_url." 406)
    )
  ))

(def schema_create_custom_url
  {:id s/Str
   :is_primary s/Bool
   :creator_id s/Uuid
   :updator_id s/Uuid})

(def schema_export_custom_url
  {:id s/Str
   :is_primary s/Bool
   :creator_id s/Uuid
   :updator_id s/Uuid
   :updated_at s/Any
   :created_at s/Any
   :media_entry_id (s/maybe s/Uuid)
   :collection_id (s/maybe s/Uuid)
   })

(def ring-routes
  ["/custom_urls"
   ["/"
    {:get {:summary (sd/sum_usr "List custom_urls.")
           :handler handle_list-custom-urls
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full-data) s/Bool
                                (s/optional-key :media_entry_id) s/Uuid
                                (s/optional-key :collection_id) s/Uuid}}}
    }]
   ["/:id"
    {:get {:summary (sd/sum_usr "Get custom_url.")
           :handler handle_get-custom-url
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}
           }}]
   ])
     
; TODO Q? custom_url without media-entry or collection ?? filter_set ?? ignore ??

(def media-entry-routes
  ["/media-entry/:media_entry_id/custom_url"
   {:get {:summary "Get custom_url for media entry."
          :handler handle_get-custom-urls
          :middleware [sd/ring-wrap-add-media-resource
                       sd/ring-wrap-authorization]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:media_entry_id s/Str}}
          :responses {200 {:body schema_export_custom_url}
                      404 {:body s/Any}}
          }
    
    :post {:summary (sd/sum_usr "Create custom_url for media entry.")
           :handler handle_create-custom-urls
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :body schema_create_custom_url}
           :responses {200 {:body schema_export_custom_url}
                       406 {:body s/Any}}}
    
    :delete {:summary (sd/sum_todo "Delete custom_url for media entry.")
             :handler (constantly sd/no_impl)}
    }])


(def collection-routes
  ["/collection/:collection_id/custom_url"
   {:get {:summary "Get custom_url for collection."
          :handler handle_get-custom-urls
          :middleware [sd/ring-wrap-add-media-resource
                       sd/ring-wrap-authorization]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:collection_id s/Str}}}

    :post {:summary (sd/sum_usr "Create custom_url for collection.")
           :handler handle_create-custom-urls
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Str}
                        :body schema_create_custom_url}
           :responses {200 {:body schema_export_custom_url}
                       406 {:body s/Any}}}
    
    :delete {:summary (sd/sum_todo "Delete custom_url for collection.")
             :handler (constantly sd/no_impl)}}])