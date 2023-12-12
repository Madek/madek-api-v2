(ns madek.api.resources.collection-media-entry-arcs
  (:require
   [clojure.java.jdbc :as jdbc]
   [logbug.catcher :as catcher]
   [madek.api.pagination :as pagination]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]))

(defn arc-query [id]
  (-> (sql/select :*)
      (sql/from :collection_media_entry_arcs)
      (sql/merge-where [:= :id id])
      sql/format))

(defn arc [request]
  (let [id (-> request :parameters :path :id)
        db-query (arc-query id)
        db-result (jdbc/query (rdbms/get-ds) db-query)]
    (if-let [arc (first db-result)]
      (sd/response_ok arc)
      (sd/response_not_found "No such collection-media-entry-arc"))))

; TODO test query and paging
(defn arcs-query [query-params path-params]
  (-> (sql/select :*)
      (sql/from :collection_media_entry_arcs)
      (sd/build-query-param query-params :collection_id)
      (sd/build-query-param query-params :media_entry_id)
      (sd/build-query-param path-params :collection_id)
      (sd/build-query-param path-params :media_entry_id)
      (pagination/add-offset-for-honeysql query-params)
      sql/format))

(defn arcs [request]
  (let [query-params (-> request :parameters :query)
        path-params (-> request :parameters :path)
        db-query (arcs-query query-params path-params)
        db-result (jdbc/query (rdbms/get-ds) db-query)]
    (sd/response_ok {:collection-media-entry-arcs db-result})))

(defn handle_arc-by-col-and-me [request]
  (if-let [result (-> request :col-me-arc)]
    (sd/response_ok result)
    (sd/response_not_found "No such collection media-entry arc.")))

(defn create-col-me-arc
  ([col-id me-id data]
   (create-col-me-arc col-id me-id data (rdbms/get-ds)))

  ([col-id me-id data tx]
   (let [ins-data (assoc data :collection_id col-id :media_entry_id me-id)
         ins-res (first (jdbc/insert! tx "collection_media_entry_arcs" 
                                       ins-data
                                       {:entities (jdbc/quoted \")}))]
     ins-res)))

(defn handle_create-col-me-arc [req]
  (try
    (catcher/with-logging {}
      (let [col-id (-> req :parameters :path :collection_id)
            me-id (-> req :parameters :path :media_entry_id)
            data (-> req :parameters :body)]
        (if-let [ins-res (create-col-me-arc col-id me-id data)]
          (sd/response_ok ins-res)
          (sd/response_failed "Could not create collection-media-entry-arc" 406))))
    (catch Exception e (sd/response_exception e))))

(defn- sql-cls-update [col-id me-id]
  (-> (sql/where [:= :collection_id col-id]
                 [:= :media_entry_id me-id])
      (sql/format)
      (update-in [0] #(clojure.string/replace % "WHERE" ""))))

(defn handle_update-col-me-arc [req]
  (try
    (catcher/with-logging {}
      (let [col-id (-> req :parameters :path :collection_id)
            me-id (-> req :parameters :path :media_entry_id)
            data (-> req :parameters :body)
            whcl (sql-cls-update col-id me-id)
            result (jdbc/update! (rdbms/get-ds)
                                 :collection_media_entry_arcs
                                 data whcl
                                 {:entities (jdbc/quoted \")})]
        (if (= 1 (first result))
          (sd/response_ok (sd/query-eq-find-one
                           :collection_media_entry_arcs
                           :collection_id col-id
                           :media_entry_id me-id))
          (sd/response_failed "Could not update collection entry arc." 422))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-col-me-arc [req]
  (try
    (catcher/with-logging {}
      (let [col-id (-> req :parameters :path :collection_id)
            me-id (-> req :parameters :path :media_entry_id)
            data (-> req :col-me-arc)
            delquery (sql-cls-update col-id me-id)
            delresult (jdbc/delete! (rdbms/get-ds) :collection_media_entry_arcs delquery)]
        (if (= 1 (first delresult))
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

   :highlight (s/maybe s/Bool)
   :cover (s/maybe s/Bool)
   :order (s/maybe s/Num)
   :position (s/maybe s/Int)

   :created_at s/Any
   :updated_at s/Any})

(def schema_collection-media-entry-arc-update
  {;(s/optional-key :id) s/Uuid
   ;(s/optional-key :collection_id) s/Uuid
   ;(s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :highlight) s/Bool
   (s/optional-key :cover) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :position) s/Int
   ;(s/optional-key :created_at) s/Any
   ;(s/optional-key :updated_at) s/Any
   })
(def schema_collection-media-entry-arc-create
  {;(s/optional-key :id) s/Uuid
   ;(s/optional-key :collection_id) s/Uuid
   ;(s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :highlight) s/Bool
   (s/optional-key :cover) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :position) s/Int})

(def ring-routes
  ["/collection-media-entry-arcs"
   ["/" {:get {:summary (sd/sum_pub "Query collection media-entry arcs.")
               :handler arcs
               :coercion reitit.coercion.schema/coercion
               :parameters {:query {(s/optional-key :collection_id) s/Uuid
                                    (s/optional-key :media_entry_id) s/Uuid
                                    (s/optional-key :page) s/Int
                                    (s/optional-key :count) s/Int}}
               :responses {200 {:body {:collection-media-entry-arcs [schema_collection-media-entry-arc-export]}}}
               }}]
   ["/:id" {:get {:summary (sd/sum_pub "Get collection media-entry arc.")
                  :handler arc
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Uuid}}
                  :responses {200 {:body schema_collection-media-entry-arc-export}
                              404 {:body s/Any}}
                  }}]])
(def collection-routes
  ["/collection/:collection_id"
    ["/media-entry-arcs" 
     {:get
      {:summary (sd/sum_usr "Get collection media-entry arcs.")
       :handler arcs
       ; checks for collection resource!
       :middleware [sd/ring-wrap-add-media-resource
                    sd/ring-wrap-authorization-view]
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:collection_id s/Uuid}}
       :responses {200 {:body {:collection-media-entry-arcs [schema_collection-media-entry-arc-export]}}}
       }}]
    ["/media-entry-arc/:media_entry_id"
     {:post
      {:summary (sd/sum_usr "Create collection media-entry arc")
       :handler handle_create-col-me-arc
       ; checks for collection resource and its access!
       ; not the media entry edit md
       :middleware [sd/ring-wrap-add-media-resource
                    sd/ring-wrap-authorization-edit-metadata]
       :accept "application/json"
       :content-type "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:collection_id s/Uuid
                           :media_entry_id s/Uuid}
                    :body schema_collection-media-entry-arc-create}
       :responses {200 {:body schema_collection-media-entry-arc-export}
                   404 {:body s/Any}
                   406 {:body s/Any}
                   }}
       
      :get
      {:summary (sd/sum_usr "Get collection media-entry arc.")
       :handler handle_arc-by-col-and-me
       :middleware [sd/ring-wrap-add-media-resource
                    sd/ring-wrap-authorization-view
                    wrap-add-col-me-arc]
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:collection_id s/Uuid
                           :media_entry_id s/Uuid}}
       :responses {200 {:body schema_collection-media-entry-arc-export}
                   404 {:body s/Any}}}

      :put
      {:summary (sd/sum_usr "Update collection media-entry arc")
       :handler handle_update-col-me-arc
       :middleware [wrap-add-col-me-arc
                    sd/ring-wrap-add-media-resource
                    sd/ring-wrap-authorization-edit-metadata]
       :accept "application/json"
       :content-type "application/json"
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:collection_id s/Uuid
                           :media_entry_id s/Uuid}
                    :body schema_collection-media-entry-arc-update}
       :responses {200 {:body schema_collection-media-entry-arc-export}
                   404 {:body s/Any}
                   406 {:body s/Any}}}
      
      :delete
      {:summary (sd/sum_usr "Delete collection media-entry arc")
       :handler handle_delete-col-me-arc
       :middleware [wrap-add-col-me-arc
                    sd/ring-wrap-add-media-resource
                    sd/ring-wrap-authorization-edit-metadata]
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:collection_id s/Uuid
                           :media_entry_id s/Uuid}}
       :responses {200 {:body schema_collection-media-entry-arc-export}
                   404 {:body s/Any}
                   406 {:body s/Any}}}}
    
   ]
  ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
