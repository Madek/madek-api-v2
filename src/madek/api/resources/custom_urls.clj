(ns madek.api.resources.custom-urls
  (:require [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.resources.shared.json_query_param_helper :as jqh]
            [madek.api.utils.helper :refer [normalize-fields]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [schema.core :as s]
            [taoensso.timbre :refer [info]]))

(defn build-query [req]
  (let [fields (normalize-fields req)
        query-params (-> req :parameters :query)
        col-sel (if (empty? fields)
                  (sql/select :id, :media_entry_id, :collection_id)
                  (apply sql/select fields))]
    (-> col-sel
        (sql/from :custom_urls)
        (dbh/build-query-param-like query-params :id)
        (dbh/build-query-param query-params :collection_id)
        (dbh/build-query-param query-params :media_entry_id)
        sql-format)))

(defn handle_list-custom-urls
  [req]
  (let [db-query (build-query req)
        db-result (jdbc/execute! (:tx req) db-query)]
    (info "handle_list-custom-urls" "\ndb-query\n" db-query "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-custom-url
  [req]
  (let [id (-> req :parameters :path :id)]
    (if-let [result (dbh/query-eq-find-one :custom_urls :id id (:tx req))]
      (sd/response_ok result)
      (sd/response_not_found (str "No such custom_url for id: " id)))))

(defn handle_get-custom-urls
  [req]
  (let [mr (-> req :media-resource)
        mr-type (-> mr :type)
        mr-id (-> mr :id str)
        col-name (if (= mr-type "MediaEntry") :media_entry_id :collection_id)]

    (info "handle_get-custom-urls"
          "\ntype: " mr-type
          "\nmr-id: " mr-id
          "\ncol-name: " col-name)
    (if-let [result (dbh/query-eq-find-one :custom_urls col-name mr-id (:tx req))]
      (sd/response_ok result)
      (sd/response_not_found (str "No such custom_url for " mr-type " with id: " mr-id)))))

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
            sql (-> (sql/insert-into :custom_urls)
                    (sql/values [dwid])
                    sql-format)
            ins-res (jdbc/execute! (:tx req) sql)]

        (sd/logwrite req (str "handle_create-custom-urls"
                              "\nmr-type: " mr-type
                              "\nmr-id: " mr-id
                              "\nnew-dat: " dwid
                              "\nresult: " ins-res))

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
            col-name (if (= mr-type "MediaEntry") :media_entry_id :collection_id)
            data (-> req :parameters :body)
            dwid (if (= mr-type "MediaEntry")
                   (assoc data :media_entry_id mr-id :updator_id u-id)
                   (assoc data :collection_id mr-id :updator_id u-id))
            tx (:tx req)
            sql (-> (sql/update :custom_urls)
                    (sql/set dwid)
                    (sql/where [:= col-name mr-id])
                    sql-format)
            upd-result (jdbc/execute! tx sql)]

        (sd/logwrite req (str "handle_update-custom-urls"
                              "\nmr-type: " mr-type
                              "\nmr-id: " mr-id
                              "\nnew-data\n" dwid
                              "\nresult:\n" upd-result))

        (if (= 1 (first upd-result))
          (sd/response_ok (dbh/query-eq-find-one :custom_urls col-name mr-id tx))
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
                       :collection_id)
            tx (:tx req)]
        (if-let [del-data (dbh/query-eq-find-one :custom_urls col-name mr-id tx)]
          (let [sql (-> (sql/delete-from :custom_urls)
                        (sql/where [:= col-name mr-id])
                        sql-format)
                del-result (jdbc/execute! tx sql)]

            (sd/logwrite req (str "handle_delete-custom-urls"
                                  "\nmr-type: " mr-type
                                  "\nmr-id: " mr-id
                                  "\nresult: " del-result))

            (if (= 1 (first del-result))
              (sd/response_ok del-data)
              (sd/response_failed (str "Could not delete custom_url " col-name " : " mr-id) 406)))
          (sd/response_failed (str "No such custom_url " col-name " : " mr-id) 404))))
    (catch Exception ex (sd/response_exception ex))))

(def schema_create_custom_url
  {:id s/Str
   :is_primary s/Bool})

(def schema_update_custom_url
  {(s/optional-key :id) s/Str
   (s/optional-key :is_primary) s/Bool})

(def schema_export_custom_url
  {:id s/Str
   :is_primary s/Bool
   :creator_id (s/maybe s/Uuid)
   :updator_id s/Uuid
   :updated_at s/Any
   :created_at s/Any
   :media_entry_id (s/maybe s/Uuid)
   :collection_id (s/maybe s/Uuid)})

; TODO custom urls response coercion
(def query-routes
  ["/"
   {:openapi {:tags ["api/custom_urls"]}}
   ["custom_urls/"
    {:get {:summary (sd/sum_usr "Query and list custom_urls.")
           :handler handle_list-custom-urls
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:description "Returns the custom_urls."
                            :body [{(s/optional-key :id) s/Str
                                    (s/optional-key :media_entry_id) (s/maybe s/Uuid)
                                    (s/optional-key :collection_id) (s/maybe s/Uuid)
                                    (s/optional-key :is_primary) s/Bool
                                    (s/optional-key :creator_id) s/Uuid
                                    (s/optional-key :updator_id) s/Uuid
                                    (s/optional-key :created_at) s/Any
                                    (s/optional-key :updated_at) s/Any}]}
                       404 {:description "Not found."
                            :body s/Any}}
           :parameters {:query {(s/optional-key :fields) [(s/enum :media_entry_id :collection_id :is_primary
                                                                  :creator_id :updator_id :created_at :updated_at)]
                                (s/optional-key :id) s/Str
                                (s/optional-key :media_entry_id) s/Uuid
                                (s/optional-key :collection_id) s/Uuid}}}}]
   ["custom_urls/:id"
    {:get {:summary (sd/sum_usr "Get custom_url.")
           :handler handle_get-custom-url
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:description "Returns the custom_url."
                            :body schema_export_custom_url}
                       404 {:description "Not found."
                            :body s/Any}}
           :parameters {:path {:id s/Str}}}}]])

; TODO Q? custom_url without media-entry or collection ?? filter_set ?? ignore ??

(def media-entry-routes
  ["/media-entries/:media_entry_id/custom_urls/"
   {:openapi {:tags ["api/media-entries"]}}
   {:get {:summary "Get custom_url for media entry."
          :handler handle_get-custom-urls
          :middleware [jqh/ring-wrap-add-media-resource
                       jqh/ring-wrap-authorization-view]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:media_entry_id s/Str}}
          :responses {200 {:description "Returns the custom_url."
                           :body schema_export_custom_url}
                      404 {:description "Not found."
                           :body s/Any}}}
    ; TODO db schema allows multiple entries for multiple users
    :post {:summary (sd/sum_usr "Create custom_url for media entry.")
           :handler handle_create-custom-urls
           :middleware [jqh/ring-wrap-add-media-resource
                        jqh/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :body schema_create_custom_url}
           :responses {200 {:description "Returns the created custom_url."
                            :body schema_export_custom_url}
                       406 {:description "Creation failed."
                            :body s/Any}}}

    :put {:summary (sd/sum_usr "Update custom_url for media entry.")
          :handler handle_update-custom-urls
          :middleware [jqh/ring-wrap-add-media-resource
                       jqh/ring-wrap-authorization-edit-metadata]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:media_entry_id s/Str}
                       :body schema_update_custom_url}
          :responses {200 {:description "Returns the updated custom_url."
                           :body schema_export_custom_url}
                      406 {:description "Update failed."
                           :body s/Any}}}

    :delete {:summary (sd/sum_todo "Delete custom_url for media entry.")
             :handler handle_delete-custom-urls
             :middleware [jqh/ring-wrap-add-media-resource
                          jqh/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_entry_id s/Str}}
             :responses {200 {:description "Returns the deleted custom_url."
                              :body schema_export_custom_url}
                         404 {:description "Not found."
                              :body s/Any}}}}])

(def collection-routes
  ["/collections/:collection_id/custom_urls"
   {:openapi {:tags ["api/collections"]}}
   {:get {:summary "Get custom_url for collection."
          :handler handle_get-custom-urls
          :middleware [jqh/ring-wrap-add-media-resource
                       jqh/ring-wrap-authorization-view]
          :coercion reitit.coercion.schema/coercion
          :responses {200 {:description "Returns the custom_url."
                           :body schema_export_custom_url}
                      404 {:description "Not found."
                           :body s/Any}}
          :parameters {:path {:collection_id s/Str}}}

    :post {:summary (sd/sum_usr "Create custom_url for collection.")
           :handler handle_create-custom-urls
           :middleware [jqh/ring-wrap-add-media-resource
                        jqh/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Str}
                        :body schema_create_custom_url}
           :responses {200 {:description "Returns the created custom_url."
                            :body schema_export_custom_url}
                       406 {:description "Creation failed."
                            :body s/Any}}}

    :put {:summary (sd/sum_usr "Update custom_url for collection.")
          :handler handle_update-custom-urls
          :middleware [jqh/ring-wrap-add-media-resource
                       jqh/ring-wrap-authorization-edit-metadata]
          :coercion reitit.coercion.schema/coercion
          :parameters {:path {:collection_id s/Str}
                       :body schema_update_custom_url}
          :responses {200 {:description "Returns the updated custom_url."
                           :body schema_export_custom_url}
                      406 {:description "Update failed."
                           :body s/Any}}}

    :delete {:summary (sd/sum_todo "Delete custom_url for collection.")
             :handler handle_delete-custom-urls
             :middleware [jqh/ring-wrap-add-media-resource
                          jqh/ring-wrap-authorization-edit-metadata]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Str}}
             :responses {200 {:description "Returns the deleted custom_url."
                              :body schema_export_custom_url}
                         404 {:description "Not found."
                              :body s/Any}}}}])
