(ns madek.api.resources.custom-urls 
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [logbug.catcher :as catcher]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [madek.api.utils.sql :as sql]
            [reitit.coercion.schema]
            [schema.core :as s]))


(defn build-query [query-params]
  (let [col-sel (if (true? (-> query-params :full_data))
                  (sql/select :*)
                  (sql/select :id, :media_entry_id, :collection_id))]
    (-> col-sel
        (sql/from :custom_urls)
        (sd/build-query-param-like query-params :id)
        (sd/build-query-param query-params :collection_id)
        (sd/build-query-param query-params :media_entry_id)
        sql/format)))


(defn handle_list-custom-urls
  [req]
  (let [db-query (build-query (-> req :parameters :query))
        db-result (jdbc/query (get-ds) db-query)]
    (logging/info "handle_list-custom-urls" "\ndb-query\n" db-query "\nresult\n" db-result)
    (sd/response_ok db-result)))


(defn handle_get-custom-url
  [req]
  (let [id (-> req :parameters :path :id)]
    (if-let [result (sd/query-eq-find-one :custom_urls :id id)]
      (sd/response_ok result)
      (sd/response_not_found (str "No such custom_url for id: " id)))))

(defn handle_get-custom-urls
  [req]
  (let [mr (-> req :media-resource)
        mr-type (-> mr :type)
        mr-id (-> mr :id str)
        col-name (if (= mr-type "MediaEntry") :media_entry_id :collection_id)]
    
    (logging/info "handle_get-custom-urls"
                  "\ntype\n" mr-type
                  "\nmr-id\n" mr-id
                  "\ncol-name\n" col-name)
    (if-let [result (sd/query-eq-find-one :custom_urls col-name mr-id)]
      (sd/response_ok result)
      (sd/response_not_found (str "No such custom_url for " mr-type " with id: " mr-id)))
    ))

(defn handle_create-custom-urls
  [req]
  (try
    (catcher/with-logging {}
      (let [u-id (-> req :authenticated-entity :id)
            mr (-> req :media-resource)
            mr-type (-> mr :type)
            mr-id (-> mr :id str)
            data (-> req :parameters :body)
            dwid (if (= mr-type "MediaEntry")
                   (assoc data :media_entry_id mr-id :creator_id u-id :updator_id u-id)
                   (assoc data :collection_id mr-id :creator_id u-id :updator_id u-id))
            ins-res (jdbc/insert! (rdbms/get-ds) :custom_urls dwid)]

        (sd/logwrite req (str "handle_create-custom-urls"
                              "mr-type: " mr-type
                              "mr-id: " mr-id
                              "\nnew-data\n" dwid
                              "\nresult:\n" ins-res))

        (if-let [result (first ins-res)]
          (sd/response_ok result)
          (sd/response_failed "Could not create custom_url." 406))))
    (catch Exception ex (sd/response_exception ex))))


; TODO check if own entity or auth is admin
(defn handle_update-custom-urls
  [req]
  (try
    (catcher/with-logging {}
      (let [u-id (-> req :authenticated-entity :id)
            mr (-> req :media-resource)
            mr-type (-> mr :type)
            mr-id (-> mr :id str)
            col-name (if (= mr-type "MediaEntry")
                       :media_entry_id
                       :collection_id)
            data (-> req :parameters :body)
            dwid (if (= mr-type "MediaEntry")
                   (assoc data :media_entry_id mr-id :updator_id u-id)
                   (assoc data :collection_id mr-id :updator_id u-id))
            upd-result (jdbc/update! (get-ds) :custom_urls dwid (sd/sql-update-clause col-name mr-id))]

        (sd/logwrite req (str "handle_update-custom-urls"
                              "\nmr-type: " mr-type
                              "\nmr-id: " mr-id
                              "\nnew-data\n" dwid
                              "\nresult:\n" upd-result))

        (if (= 1 (first upd-result))
          (sd/response_ok (sd/query-eq-find-one :custom_urls col-name mr-id))
          (sd/response_failed "Could not update custom_url." 406))))
    (catch Exception ex (sd/response_exception ex))))

; TODO use wrapper? no
; TODO check if own entity or auth is admin
(defn handle_delete-custom-urls
  [req]
  (try
    (catcher/with-logging {}
      (let [u-id (-> req :authenticated-entity :id)
            mr (-> req :media-resource)
            mr-type (-> mr :type)
            mr-id (-> mr :id str)
            col-name (if (= mr-type "MediaEntry")
                       :media_entry_id
                       :collection_id)]
        (if-let [del-data (sd/query-eq-find-one :custom_urls col-name mr-id)]
          (let [del-result (jdbc/delete! (rdbms/get-ds)
                                         :custom_urls
                                         (sd/sql-update-clause col-name mr-id))]

            (sd/logwrite req (str "handle_delete-custom-urls:"
                                  "\nmr-type: " mr-type
                                  "\nmr-id: " mr-id
                                  "\nresult:\n" del-result))

            (if (= 1 (first del-result))
              (sd/response_ok del-data)
              (sd/response_failed (str "Could not delete custom_url " col-name " : " mr-id) 406)))
          (sd/response_failed (str "No such custom_url " col-name " : " mr-id) 404))))
    (catch Exception ex (sd/response_exception ex))))

(def schema_create_custom_url
  {:id s/Str
   :is_primary s/Bool
   })

(def schema_update_custom_url
  {(s/optional-key :id) s/Str
   (s/optional-key :is_primary) s/Bool
   })

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

; TODO custom urls response coercion
(def query-routes
  ["/custom_urls"
   ["/"
    {:get {:summary (sd/sum_usr "Query and list custom_urls.")
           :handler handle_list-custom-urls
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full_data) s/Bool
                                (s/optional-key :id) s/Str
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
                       sd/ring-wrap-authorization-view]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:media_entry_id s/Str}}
          :responses {200 {:body schema_export_custom_url}
                      404 {:body s/Any}}
          }
    ; TODO db schema allows multiple entries for multiple users
    :post {:summary (sd/sum_usr "Create custom_url for media entry.")
           :handler handle_create-custom-urls
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :body schema_create_custom_url}
           :responses {200 {:body schema_export_custom_url}
                       406 {:body s/Any}}}
    
    :put {:summary (sd/sum_usr "Update custom_url for media entry.")
           :handler handle_update-custom-urls
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :body schema_update_custom_url}
           :responses {200 {:body schema_export_custom_url}
                       406 {:body s/Any}}}
    
    :delete {:summary (sd/sum_todo "Delete custom_url for media entry.")
             :handler handle_delete-custom-urls
             :middleware [sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_entry_id s/Str}}
             :responses {200 {:body schema_export_custom_url}
                         404 {:body s/Any}}}
    }])


(def collection-routes
  ["/collection/:collection_id/custom_url"
   {:get {:summary "Get custom_url for collection."
          :handler handle_get-custom-urls
          :middleware [sd/ring-wrap-add-media-resource
                       sd/ring-wrap-authorization-view]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:collection_id s/Str}}}

    :post {:summary (sd/sum_usr "Create custom_url for collection.")
           :handler handle_create-custom-urls
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Str}
                        :body schema_create_custom_url}
           :responses {200 {:body schema_export_custom_url}
                       406 {:body s/Any}}}

    :put {:summary (sd/sum_usr "Update custom_url for collection.")
            :handler handle_update-custom-urls
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str}
                         :body schema_update_custom_url}
            :responses {200 {:body schema_export_custom_url}
                        406 {:body s/Any}}}

    :delete {:summary (sd/sum_todo "Delete custom_url for collection.")
             :handler handle_delete-custom-urls
             :middleware [sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Str}}
             :responses {200 {:body schema_export_custom_url}
                         404 {:body s/Any}}}}])