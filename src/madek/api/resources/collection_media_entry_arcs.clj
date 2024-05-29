(ns madek.api.resources.collection-media-entry-arcs
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.pagination :as pagination]
   [madek.api.resources.shared :as sd]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]))

(defn arc-query [id]
  (-> (sql/select :*)
      (sql/from :collection_media_entry_arcs)
      (sql/where [:= :id id])
      sql-format))

(defn arc [req]
  (let [id (-> req :parameters :path :id)
        db-query (arc-query id)
        db-result (jdbc/execute! (:tx req) db-query)]
    (if-let [arc (first db-result)]
      (sd/response_ok arc)
      (sd/response_not_found "No such collection-media-entry-arc"))))

; TODO test query and paging
(defn arcs-query [query-params]
  (-> (sql/select :*)
      (sql/from :collection_media_entry_arcs)
      (sd/build-query-param query-params :collection_id)
      (sd/build-query-param query-params :media_entry_id)
      (pagination/add-offset-for-honeysql query-params)
      sql-format))

(defn arcs [req]
  (let [query-params (-> req :parameters :query)
        db-query (arcs-query query-params)
        db-result (jdbc/execute! (:tx req) db-query)]
    (sd/response_ok {:collection-media-entry-arcs db-result})))

(defn create-col-me-arc
  ([col-id me-id data tx]
   (let [ins-data (assoc data :collection_id col-id :media_entry_id me-id)
         sql (-> (sql/insert-into :collection_media_entry_arcs)
                 (sql/values [ins-data])
                 sql-format)
         ins-res (jdbc/execute! tx sql)]
     ins-res)))

; TODO logwrite
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

;; TODO: not in use?
;(defn- sql-cls-update [col-id me-id]
;  (-> (sql/where [:= :collection_id col-id]
;                 [:= :media_entry_id me-id])
;      sql-format
;      (update-in [0] #(clojure.string/replace % "WHERE" ""))))

; TODO logwrite
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
                    sql-format)
            result (jdbc/execute! (:tx req) sql)]
        (if (= 1 (first result))
          (sd/response_ok (sd/query-eq-find-one
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
            sql (-> (sql/delete :collection_media_entry_arcs)
                    (sql/where [:= :collection_id col-id]
                               [:= :media_entry_id me-id])
                    sql-format)
            delresult (jdbc/execute! tx sql)]
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

;(def schema_collection-media-entry-arc-export
;  {:id s/Uuid
;   :collection_id s/Uuid
;   :media_entry_id s/Uuid
;
;   :highlight s/Bool
;   :cover (s/maybe s/Bool)
;   :order (s/maybe s/Num)
;   :position (s/maybe s/Int)
;
;   :created_at s/Any
;   :updated_at s/Any})
;
;(def schema_collection-media-entry-arc-update
;  {;(s/optional-key :id) s/Uuid
;   ;(s/optional-key :collection_id) s/Uuid
;   ;(s/optional-key :media_entry_id) s/Uuid
;   (s/optional-key :highlight) s/Bool
;   (s/optional-key :cover) s/Bool
;   (s/optional-key :order) s/Num
;   (s/optional-key :position) s/Int
;   ;(s/optional-key :created_at) s/Any
;   ;(s/optional-key :updated_at) s/Any
;   })
;(def schema_collection-media-entry-arc-create
;  {;(s/optional-key :id) s/Uuid
;   ;(s/optional-key :collection_id) s/Uuid
;   ;(s/optional-key :media_entry_id) s/Uuid
;   (s/optional-key :highlight) s/Bool
;   (s/optional-key :cover) s/Bool
;   (s/optional-key :order) s/Num
;   (s/optional-key :position) s/Int})

(def ring-routes
  ["/collection-media-entry-arcs"
   {:swagger {:tags ["api/collection"]}}
   ["/" {:get {:summary "Query collection media-entry arcs."
               :handler arcs
               :swagger {:produces "application/json"}
               :coercion reitit.coercion.schema/coercion
               ; TODO puery params
               :parameters {:query {(s/optional-key :collection_id) s/Uuid
                                    (s/optional-key :media_entry_id) s/Uuid}}
               :responses {200 {:body s/Any}} ; TODO response coercion
               }}]
   ["/:id" {:get {:summary "Get collection media-entry arc."
                  :handler arc
                  :swagger {:produces "application/json"}
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Str}}
                  :responses {200 {:body s/Any}
                              404 {:body s/Any}} ; TODO response coercion
                  }}]])
(def collection-routes
  ["/collection/:collection_id"
   {:swagger {:tags ["api/collection"]}}
   ["/media-entry-arcs"
    {:get
     {:summary "Get collection media-entry arcs."
      :handler arcs
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-view]
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid}}
      :responses {200 {:body {:collection-media-entry-arcs [(get-schema :collection_mea.schema_collection-media-entry-arc-export)]}}}}}]
   ["/media-entry-arc/:media_entry_id"
    {:post
     {:summary (sd/sum_usr "Create collection media-entry arc")
      :handler handle_create-col-me-arc
      ; TODO check: if collection edit md and relations is allowed checked
      ; not the media entry edit md
      :middleware [sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-edit-metadata]
      :swagger {:produces "application/json" :consumes "application/json"}
      :accept "application/json"
      :content-type "application/json"
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid
                          :media_entry_id s/Uuid}
                   :body (get-schema :collection_mea.schema_collection-media-entry-arc-create)}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}
                  406 {:body s/Any}
                  500 {:body s/Any}}}

     :put
     {:summary (sd/sum_usr "Update collection media-entry arc")
      :handler handle_update-col-me-arc
      :middleware [wrap-add-col-me-arc
                   sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-edit-metadata]
      :swagger {:produces "application/json" :consumes "application/json"}
      :accept "application/json"
      :content-type "application/json"
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid
                          :media_entry_id s/Uuid}
                   :body (get-schema :collection_mea.schema_collection-media-entry-arc-update)}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}
                  406 {:body s/Any}}}

     :delete
     {:summary (sd/sum_usr "Delete collection media-entry arc")
      :handler handle_delete-col-me-arc
      :middleware [wrap-add-col-me-arc
                   sd/ring-wrap-add-media-resource
                   sd/ring-wrap-authorization-edit-metadata]
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:collection_id s/Uuid
                          :media_entry_id s/Uuid}}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}
                  406 {:body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
