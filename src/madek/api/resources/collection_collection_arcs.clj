(ns madek.api.resources.collection-collection-arcs
  (:require
   [clojure.java.jdbc :as jdbc]
   [logbug.catcher :as catcher]
   [madek.api.pagination :as pagination]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]
   [madek.api.resources.shared :as sd]
   ))


(defn arc-query [request]
  (-> (sql/select :*)
      (sql/from :collection_collection_arcs)
      (sql/merge-where [:= :id (-> request :parameters :path :id)])
      sql/format))

(defn handle_get-arc [request]
  (let [query (arc-query request)
        db-result (jdbc/query (rdbms/get-ds) query)]
    (if-let [arc (first db-result)]
      (sd/response_ok arc)
      (sd/response_failed "No such collection-collection-arc" 404))))

(defn arc-query-by-parent-and-child [request]
  (-> (sql/select :*)
      (sql/from :collection_collection_arcs)
      (sql/merge-where [:= :parent_id (-> request :parameters :path :parent_id)])
      (sql/merge-where [:= :child_id (-> request :parameters :path :child_id)]) 
      sql/format))

(defn handle_arc-by-parent-and-child [request]
  (let [query (arc-query-by-parent-and-child request)
        db-result (jdbc/query (rdbms/get-ds) query)]
    (if-let [arc (first db-result)]
      (sd/response_ok arc)
      (sd/response_failed "No such collection-collection-arc" 404))))

(defn arcs-query [query-params]
  (-> (sql/select :*)
      (sql/from :collection_collection_arcs)
      (sd/build-query-param query-params :child_id)
      (sd/build-query-param query-params :parent_id)
      (pagination/add-offset-for-honeysql query-params)
      sql/format))

(defn handle_query-arcs [request]
  (let [query (arcs-query (-> request :parameters :query))
        db-result (jdbc/query (get-ds) query)]
    (sd/response_ok {:collection-collection-arcs db-result})))


(defn handle_create-col-col-arc [req]
  (catcher/with-logging {}
    (try
      (let [parent-id (-> req :parameters :path :parent_id)
            child-id (-> req :parameters :path :child_id)

            data (-> req :parameters :body)
            ins-data (assoc data :parent_id parent-id :child_id child-id)]
        (if-let [ins-res (jdbc/insert! (rdbms/get-ds) :collection_collection_arcs ins-data)]
          (sd/response_ok ins-res)
          (sd/response_failed "Could not create collection-collection-arc" 406)))
      (catch Exception e (sd/response_exception e)))))

(defn- sql-cls-update [parent-id child-id]
  (-> (sql/where [:= :parent_id parent-id]
                 [:= :child_id child-id])
      (sql/format)
      (update-in [0] #(clojure.string/replace % "WHERE" ""))))

(defn handle_update-arc [req]
  (catcher/with-logging {}
     (try
       (let [parent-id (-> req :parameters :path :parent_id)
             child-id (-> req :parameters :path :child_id)
             data (-> req :parameters :body)
             whcl (sql-cls-update parent-id child-id)
             result (jdbc/update! (rdbms/get-ds)
                                  :collection_collection_arcs
                                  data whcl)]
         (if (= 1 (first result))
           (sd/response_ok (sd/query-eq2-find-one
                            :collection_collection_arcs
                            :parent_id parent-id
                            :child_id child-id))
           (sd/response_failed "Could not update collection collection arc." 422)))
       (catch Exception e (sd/response_exception e)))))

(defn handle_delete-arc [req]
  (catcher/with-logging {}
    (try
      (let [parent-id (-> req :parameters :path :parent_id)
            child-id (-> req :parameters :path :child_id) 
            olddata (sd/query-eq2-find-one
                     :collection_collection_arcs
                     :parent_id parent-id
                     :child_id child-id)
            delquery (sql-cls-update parent-id child-id)
            delresult (jdbc/delete! (get-ds) :collection_collection_arcs delquery)]
        (if (= 1 (first delresult))
          (sd/response_ok olddata)
          (sd/response_failed "Could not delete collection collection arc." 422))
        )
      (catch Exception e (sd/response_exception e)))))

(def schema_collection-collection-arc-export
  {
   :id s/Uuid
   :parent_id s/Uuid
   :child_id s/Uuid
   :highlight s/Bool
   :order s/Num
   :position s/Int
   :created_at s/Any
   :updated_at s/Any
   
  })

(def schema_collection-collection-arc-update
  {
   ;(s/optional-key :id) s/Uuid
   ;(s/optional-key :parent_id) s/Uuid
   ;(s/optional-key :child_id) s/Uuid
   (s/optional-key :highlight) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :position) s/Int
   ;(s/optional-key :created_at) s/Any
   ;(s/optional-key :updated_at) s/Any
   
   })

(def schema_collection-collection-arc-create
  {
   ;(s/optional-key :id) s/Uuid
   ;(s/optional-key :parent_id) s/Uuid
   ;(s/optional-key :child_id) s/Uuid
   
   (s/optional-key :highlight) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :position) s/Int})

; TODO add permission checks
(def ring-routes
  ["/collection-collection-arcs"
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
      }
     
    }
   ]
   
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
      }
    
    }] 
   
   ])

; TODO rename param use middleware for permissions
(def collection-routes
  ["/collection/:parent_id"
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
   ["/collection-arcs/:child_id"
    {:post
     {:summary (sd/sum_todo "Create collection collection arc")
      :handler handle_create-col-col-arc
      :swagger {:produces "application/json"
                :consumes "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:parent_id s/Uuid
                          :child_id s/Uuid}
                   :body schema_collection-collection-arc-create}
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
                   :body schema_collection-collection-arc-update}
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
                  406 {:body s/Any}}}}
   ]
  ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
