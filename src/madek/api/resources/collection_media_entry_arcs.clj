(ns madek.api.resources.collection-media-entry-arcs
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.collections.advanced-filter.permissions :as coll-perms]
   [madek.api.resources.media-entries.advanced-filter.permissions :as me-perms]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [madek.api.resources.shared.media_resource_helper :as mrh]
   [madek.api.utils.helper :refer [to-uuid sql-format-quoted]]
   [madek.api.utils.pagination :refer [pagination-handler]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

(defn- filter-by-viewable-resources
  "Restricts arcs to those whose collection and media-entry the client may
  view. Admins are not restricted."
  [sqlmap req]
  (if (true? (:is_admin req))
    sqlmap
    (sql/where sqlmap
               [:in :collection_media_entry_arcs.collection_id
                (-> (sql/select :collections.id)
                    (sql/from :collections)
                    (coll-perms/filter-by-view-permission req))]
               [:in :collection_media_entry_arcs.media_entry_id
                (-> (sql/select :media_entries.id)
                    (sql/from :media_entries)
                    (me-perms/filter-by-view-permission req))])))

(defn arc-query [id req]
  (-> (sql/select :*)
      (sql/from :collection_media_entry_arcs)
      (sql/order-by [:media_entry_id :asc] [:collection_id :asc])
      (sql/where [:= :id (to-uuid id)])
      (filter-by-viewable-resources req)
      sql-format))

(defn arc [req]
  (let [id (-> req :parameters :path :id)
        db-query (arc-query id req)
        db-result (jdbc/execute! (:tx req) db-query)]
    (if-let [arc (first db-result)]
      (sd/response_ok arc)
      (sd/response_not_found "No such collection-media-entry-arc"))))

; TODO test query and paging
(defn arcs-query [query-params req]
  (-> (sql/select :*)
      (sql/from :collection_media_entry_arcs)
      (sql/order-by [:media_entry_id :asc] [:collection_id :asc])
      (dbh/build-query-param query-params :collection_id)
      (dbh/build-query-param query-params :media_entry_id)
      (filter-by-viewable-resources req)))

(defn arcs [req]
  (let [query-params (-> req :parameters :query)
        path-params (-> req :parameters :path)
        params (merge
                (if (nil? query-params) {} query-params)
                (if (nil? path-params) {} path-params))
        db-query (arcs-query params req)
        db-result (pagination-handler req db-query :collection-media-entry-arcs)]
    (sd/response_ok db-result)))

(defn wrap-authorize-view-media-entry
  "Requires view rights on the media-entry given by :media_entry_id; the
  request passed on keeps its own :media-resource."
  [handler]
  (fn [request]
    (if-let [media-entry (mrh/get-media-resource request :media_entry_id "media_entries" "MediaEntry" (:tx request))]
      (mrh/authorize-request-for-media-resource
       (assoc request :media-resource media-entry)
       (fn [_] (handler request))
       :view)
      (sd/response_not_found "No such media-entry."))))

(defn create-col-me-arc
  ([col-id me-id data tx]
   (let [ins-data (cond-> (assoc data
                                 :collection_id col-id
                                 :media_entry_id me-id)
                    (some? (:order data)) (assoc :order (double (:order data))))
         sql (-> (sql/insert-into :collection_media_entry_arcs)
                 (sql/values [ins-data])
                 (sql/returning :*)
                 sql-format-quoted)
         ins-res (jdbc/execute-one! tx sql)]
     ins-res)))

(defn handle_create-col-me-arc [req]
  (try
    (catcher/with-logging {}
      (let [col-id (-> req :parameters :path :collection_id)
            me-id (-> req :parameters :path :media_entry_id)
            tx (:tx req)
            data (-> req :parameters :body)]
        (if-let [ins-res (create-col-me-arc col-id me-id data tx)]
          (sd/response_ok ins-res)
          (sd/response_failed "Could not create collection-media-entry-arc" 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_update-col-me-arc [req]
  (try
    (catcher/with-logging {}
      (let [col-id (-> req :parameters :path :collection_id)
            me-id (-> req :parameters :path :media_entry_id)
            data (-> req :parameters :body)
            tx (:tx req)
            sql (-> (sql/update :collection_media_entry_arcs)
                    (sql/set data)
                    (sql/where [:= :collection_id col-id]
                               [:= :media_entry_id me-id])
                    sql-format-quoted)
            result (jdbc/execute-one! (:tx req) sql)]

        (if (= 1 (::jdbc/update-count result))
          (sd/response_ok (dbh/query-eq-find-one
                           :collection_media_entry_arcs
                           :collection_id col-id
                           :media_entry_id me-id
                           tx))
          (sd/response_failed "Could not update collection entry arc." 422))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-col-me-arc [req]
  (try
    (catcher/with-logging {}
      (let [col-id (-> req :parameters :path :collection_id)
            me-id (-> req :parameters :path :media_entry_id)
            data (-> req :col-me-arc)
            tx (:tx req)
            sql (-> (sql/delete-from :collection_media_entry_arcs)
                    (sql/where [:= :collection_id col-id]
                               [:= :media_entry_id me-id])
                    sql-format)
            delresult (jdbc/execute-one! tx sql)]
        (if (= 1 (::jdbc/update-count delresult))
          (sd/response_ok data)
          (sd/response_failed "Could not delete collection entry arc." 422))))
    (catch Exception ex (sd/response_exception ex))))

(defn wrap-add-col-me-arc [handler]
  (fn [request] (sd/req-find-data2
                 request handler
                 :collection_id :media_entry_id
                 :collection-media-entry-arcs
                 :collection_id :media_entry_id
                 :col-me-arc true)))

(def schema_collection-media-entry-arc-export
  {:id s/Uuid
   :collection_id s/Uuid
   :media_entry_id s/Uuid

   :highlight s/Bool
   :cover (s/maybe s/Bool)
   :order (s/maybe s/Num)
   :position (s/maybe s/Int)

   :created_at s/Any
   :updated_at s/Any})

(def schema_collection-collection-arc-export
  {:id s/Uuid
   :child_id s/Uuid
   :parent_id s/Uuid
   :highlight s/Bool
   :order (s/maybe s/Num)
   :created_at s/Any
   :updated_at s/Any
   :position (s/maybe s/Int)})

(def schema_collection-media-entry-arc-update
  {(s/optional-key :highlight) s/Bool
   (s/optional-key :cover) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :position) s/Int})

(def schema_collection-media-entry-arc-response
  {(s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :highlight) s/Bool
   (s/optional-key :collection_id) s/Uuid
   (s/optional-key :cover) (s/maybe s/Bool)
   (s/optional-key :updated_at) s/Any
   (s/optional-key :id) s/Uuid
   (s/optional-key :position) (s/maybe s/Int)
   (s/optional-key :order) (s/maybe s/Num)
   (s/optional-key :created_at) s/Any})

(def schema_collection-media-entry-arc-create
  {(s/optional-key :highlight) s/Bool
   (s/optional-key :cover) (s/maybe s/Bool)
   (s/optional-key :position) (s/maybe s/Int)
   (s/optional-key :order) (s/maybe s/Num)})

(def ring-routes
  ["/collection-media-entry-arcs"
   {:openapi {:tags ["api/collections"]}}
   ["/" {:get {:summary (sd/?no-auth? "Query collection media-entry arcs.")
               :handler arcs
               :swagger {:produces "application/json"}
               :coercion reitit.coercion.schema/coercion
               :parameters {:query {(s/optional-key :collection_id) s/Uuid
                                    (s/optional-key :media_entry_id) s/Uuid}}
               :responses {200 {:description "Returns the collection media-entry arcs."
                                :body {:collection-media-entry-arcs [schema_collection-media-entry-arc-response]}}}}}]

   ["/:id" {:get {:summary (sd/?no-auth? "Get collection media-entry arc.")
                  :handler arc
                  :swagger {:produces "application/json"}
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Str}}
                  :responses {200 {:description "Returns the collection media-entry arc."
                                   :body schema_collection-media-entry-arc-response}
                              404 {:description "Collection media-entry arc not found."
                                   :body s/Any}}}}]])

(def collection-routes
  ["/collections/:collection_id"
   {:openapi {:tags ["api/collections"]}}
   ["/media-entry-arcs/"
    {:get
     {:summary (sd/?token? "Get collection media-entry arcs.")
      :handler arcs
      :middleware [jqh/ring-wrap-add-media-resource
                   jqh/ring-wrap-authorization-view]
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}
      :responses {200 {:description "Returns the collection media-entry arcs."
                       :body {:collection-media-entry-arcs [schema_collection-media-entry-arc-export]}}}}}]

   ["/media-entry-arcs/:media_entry_id"
    {:post
     {:summary (sd/?token? (sd/sum_usr "Create collection media-entry arc"))
      :handler handle_create-col-me-arc
      :middleware [jqh/ring-wrap-add-media-resource
                   jqh/ring-wrap-authorization-edit-metadata
                   wrap-authorize-view-media-entry]
      :swagger {:produces "application/json" :consumes "application/json"}
      :accept "application/json"
      :content-type "application/json"
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid
                          :media_entry_id s/Uuid}
                   :body schema_collection-media-entry-arc-create}
      :responses {200 {:description "Returns the created collection media-entry arc."
                       :body schema_collection-media-entry-arc-response}
                  404 {:description "Collection media-entry arc not found."
                       :body s/Any}
                  406 {:description "Could not create collection media-entry arc."
                       :body s/Any}
                  500 {:description "Could not create collection media-entry arc."
                       :body s/Any}}}

     :put
     {:summary (sd/?token? (sd/sum_usr "Update collection media-entry arc"))
      :handler handle_update-col-me-arc
      :middleware [wrap-add-col-me-arc
                   jqh/ring-wrap-add-media-resource
                   jqh/ring-wrap-authorization-edit-metadata]
      :swagger {:produces "application/json" :consumes "application/json"}
      :accept "application/json"
      :content-type "application/json"
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid
                          :media_entry_id s/Uuid}
                   :body schema_collection-media-entry-arc-update}
      :responses {200 {:description "Returns the updated collection media-entry arc."
                       :body schema_collection-media-entry-arc-response}
                  404 {:description "Collection media-entry arc not found."
                       :body s/Any}
                  406 {:description "Could not update collection media-entry arc."
                       :body s/Any}}}

     :delete
     {:summary (sd/?token? (sd/sum_usr "Delete collection media-entry arc"))
      :handler handle_delete-col-me-arc
      :middleware [wrap-add-col-me-arc
                   jqh/ring-wrap-add-media-resource
                   jqh/ring-wrap-authorization-edit-metadata]
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid
                          :media_entry_id s/Uuid}}
      :responses {200 {:description "Returns the deleted collection media-entry arc."
                       :body schema_collection-media-entry-arc-response}
                  404 {:description "Collection media-entry arc not found."
                       :body s/Any}
                  406 {:description "Could not delete collection media-entry arc."
                       :body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
