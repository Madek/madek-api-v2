(ns madek.api.resources.collection-collection-arcs
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

(defn arc-query [request]
  (-> (sql/select :*)
      (sql/from :collection_collection_arcs)
      (sql/where [:= :id (-> request :parameters :path :id)])
      sql-format))

(defn handle_get-arc [req]
  (let [query (arc-query req)
        db-result (jdbc/execute! (:tx req) query)]
    (if-let [arc (first db-result)]
      (sd/response_ok arc)
      (sd/response_failed "No such collection-collection-arc" 404))))

(defn arc-query-by-parent-and-child [req]
  (-> (sql/select :*)
      (sql/from :collection_collection_arcs)
      (sql/where [:= :parent_id (-> req :parameters :path :parent_id)])
      (sql/where [:= :child_id (-> req :parameters :path :child_id)])
      sql-format))

(defn handle_arc-by-parent-and-child [req]
  (let [query (arc-query-by-parent-and-child req)
        db-result (jdbc/execute! (:tx req) query)]
    (if-let [arc (first db-result)]
      (sd/response_ok arc)
      (sd/response_failed "No such collection-collection-arc" 404))))

(defn arcs-query [query-params]
  (-> (sql/select :*)
      (sql/from :collection_collection_arcs)
      (sd/build-query-param query-params :child_id)
      (sd/build-query-param query-params :parent_id)
      (pagination/add-offset-for-honeysql query-params)
      sql-format))

(defn handle_query-arcs [req]
  (let [query (arcs-query (-> req :parameters :query))
        db-result (jdbc/execute! (:tx req) query)]
    (sd/response_ok {:collection-collection-arcs db-result})))

(defn handle_create-col-col-arc [req]
  (try
    (catcher/with-logging {}
      (let [parent-id (-> req :parameters :path :parent_id)
            child-id (-> req :parameters :path :child_id)
            data (-> req :parameters :body)
            ins-data (assoc data :parent_id parent-id :child_id child-id)
            sql-map {:insert-into :collection_collection_arcs
                     :values [ins-data]}
            sql (-> sql-map sql-format)]
        (if-let [ins-res (next.jdbc/execute! (:tx req) [sql ins-data])]
          (sd/response_ok ins-res)
          (sd/response_failed "Could not create collection-collection-arc" 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_update-arc [req]
  (try
    (catcher/with-logging {}
      (let [parent-id (-> req :parameters :path :parent_id)
            child-id (-> req :parameters :path :child_id)
            data (-> req :parameters :body)
            query (-> (sql/update :collection_collection_arcs)
                      (sql/set data)
                      (sql/where [:= :parent_id parent-id
                                  := :child_id child-id])
                      sql-format)
            tx (:tx req)
            result (next.jdbc/execute! tx query)]

        (if (= 1 (first result))
          (sd/response_ok (sd/query-eq-find-one
                           :collection_collection_arcs
                           :parent_id parent-id
                           :child_id child-id tx))
          (sd/response_failed "Could not update collection collection arc." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_delete-arc [req]
  (try
    (catcher/with-logging {}
      (let [parent-id (-> req :parameters :path :parent_id)
            child-id (-> req :parameters :path :child_id)
            tx (:tx req)
            ;; TODO: fetch old data by delete-query
            olddata (sd/query-eq-find-one
                     :collection_collection_arcs
                     :parent_id parent-id
                     :child_id child-id
                     tx)
            tx (:tx req)
            query (-> (sql/delete :collection_collection_arcs)
                      (sql/where [:= :parent_id parent-id
                                  := :child_id child-id])
                      sql-format)

            delresult (next.jdbc/execute! tx query)]

        (if (= 1 (first delresult))
          (sd/response_ok olddata)
          (sd/response_failed "Could not delete collection collection arc." 422))))
    (catch Exception e (sd/response_exception e))))

;;; not in use
;(def schema_collection-collection-arc-export
;  {:id s/Uuid
;   :parent_id s/Uuid
;   :child_id s/Uuid
;   :highlight s/Bool
;   :order s/Num
;   :position s/Int
;   :created_at s/Any
;   :updated_at s/Any})

;(def schema_collection-collection-arc-update
;  {;(s/optional-key :id) s/Uuid
;   ;(s/optional-key :parent_id) s/Uuid
;   ;(s/optional-key :child_id) s/Uuid
;   (s/optional-key :highlight) s/Bool
;   (s/optional-key :order) s/Num
;   (s/optional-key :position) s/Int
;   ;(s/optional-key :created_at) s/Any
;   ;(s/optional-key :updated_at) s/Any
;   })
;(def schema_collection-collection-arc-create
;  {;(s/optional-key :id) s/Uuid
;   ;(s/optional-key :parent_id) s/Uuid
;   ;(s/optional-key :child_id) s/Uuid
;
;   (s/optional-key :highlight) s/Bool
;   (s/optional-key :order) s/Num
;   (s/optional-key :position) s/Int})

; TODO add permission checks
(def ring-routes
  ["/collection-collection-arcs"
   {:swagger {:tags ["api/collection"]}}
   ["/"
    {:get
     {:summary "Query collection collection arcs."
      :handler handle_query-arcs
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:query {(s/optional-key :child_id) s/Uuid
                           (s/optional-key :parent_id) s/Uuid
                           (s/optional-key :page) s/Int
                           (s/optional-key :count) s/Int}}
      :responses {200 {:body s/Any}} ; TODO response coercion
      }}]
   ; TODO rename param to collection_id
   ; TODO add permission checks
   ["/:id"
    {:get
     {:summary "Get collection collection arcs."
      :handler handle_get-arc
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Str}}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}} ; TODO response coercion
      }}]])
; TODO rename param use middleware for permissions
(def collection-routes
  ["/collection/:parent_id"
   {:swagger {:tags ["api/collection"]}}
   ;["/collection-arcs"
   ; {:get
   ;  {:summary "List collection collection arcs."
   ;   :handler arcs
   ;   :swagger {:produces "application/json"}
   ;   :coercion reitit.coercion.schema/coercion
   ;   :parameters {:path {:parent_id s/Uuid}}
   ;   :responses {200 {:body s/Any}} ; TODO response coercion
   ;   }
   ; }
   ;]
   ["/collection-arc/:child_id"
    {:post
     {:summary (sd/sum_todo "Create collection collection arc")
      :handler handle_create-col-col-arc
      :swagger {:produces "application/json"
                :consumes "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:parent_id s/Uuid
                          :child_id s/Uuid}
                   :body (get-schema :collection_carcs.schema_collection-collection-arc-create)}
      :responses {200 {:body s/Any}
                  406 {:body s/Any}}}

     :get
     {:summary "Get collection collection arcs."
      :handler handle_arc-by-parent-and-child
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:parent_id s/Uuid
                          :child_id s/Uuid}}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}} ; TODO response coercion
      }

     ; TODO col col arc update tests
     :put
     {:summary (sd/sum_usr "Update collection collection arc")
      :handler handle_update-arc
      :swagger {:produces "application/json"
                :consumes "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:parent_id s/Uuid
                          :child_id s/Uuid}
                   :body (get-schema :collection_carcs.schema_collection-collection-arc-update)}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}
                  406 {:body s/Any}}}

     ; TODO col col arc delete tests
     :delete
     {:summary (sd/sum_usr "Delete collection collection arc")
      :handler handle_delete-arc
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:parent_id s/Uuid
                          :child_id s/Uuid}}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}
                  406 {:body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
