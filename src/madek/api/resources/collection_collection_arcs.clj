(ns madek.api.resources.collection-collection-arcs
  (:require
   [clojure.java.jdbc :as jdbc]
   [compojure.core :as cpj]
   [madek.api.constants :refer [presence]]
   [madek.api.pagination :as pagination]
   [madek.api.utils.rdbms :as rdbms]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]
   [madek.api.resources.shared :as sd]
   ))


(defn arc-query [request]
  (-> (sql/select :*)
      (sql/from :collection_collection_arcs)
      (sql/merge-where [:= :id (or (-> request :params :id) (-> request :parameters :path :id))])
      sql/format))

(defn arc [request]
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
  (let [child-id (-> query-params :child_id presence)
        parent-id (-> query-params :parent_id presence)]
    (-> (sql/select :*)
        (sql/from :collection_collection_arcs)
        (#(if child-id
            (sql/merge-where % [:= :child_id child-id]) %))
        (#(if parent-id
            (sql/merge-where % [:= :parent_id parent-id]) %))
        (pagination/add-offset-for-honeysql query-params)
        sql/format)))

(defn arcs [request]
  (let [query (arcs-query (-> request :parameters :query))
        db-result (jdbc/query (rdbms/get-ds) query)]
    (sd/response_ok {:collection-collection-arcs db-result})))

(defn handle_create-col-me-arc [req]
  (let [parent-id (-> req :parameters :path :parent_id)
        child-id (-> req :parameters :path :child_id)
        
        data (-> req :parameters :body)
        ins-data (assoc data :parent_id parent-id :child_id child-id)]
    (if-let [ins-res (jdbc/insert! (rdbms/get-ds) "collection_collection_arcs" ins-data)]
      (sd/response_ok ins-res)
      (sd/response_failed "Could not create collection-collection-arc" 406))
    ))

(def schema_collection-collection-arc-export
  {
   :id s/Uuid
   :collection_id s/Uuid
   :media_entry_id s/Uuid
   :highlight s/Bool
   :order s/Num
   :created_at s/Any
   :updated_at s/Any
   :position s/Int
  })

(def schema_collection-collection-arc-update
  {
   (s/optional-key :id) s/Uuid
   (s/optional-key :collection_id) s/Uuid
   (s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :highlight) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any
   (s/optional-key :position) s/Int
   })

(def schema_collection-collection-arc-create
  {
   ;(s/optional-key :id) s/Uuid
   ;(s/optional-key :collection_id) s/Uuid
   ;(s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :highlight) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :position) s/Int})

(def ring-routes
  ["/collection-collection-arcs"
   ["/" 
    {:get
     {:summary "Query collection collection arcs."
      :handler arcs
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:query {(s/optional-key :child_id) s/Uuid
                           (s/optional-key :parent_id) s/Uuid}}
      :responses {200 {:body s/Any}} ; TODO response coercion
      }
    }
   ]

   ["/:id"
    {:get
     {:summary "Get collection collection arcs."
      :handler arc
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Str}}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}} ; TODO response coercion
      }
            ; TODO
     :patch
     {:summary (sd/sum_todo "Update collection collection arc")
      :handler (constantly sd/no_impl)
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
                  ;:parameters {:path {:id s/Str}
                  ;             :body schema_collection-collection-arc-update}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}
                  406 {:body s/Any}}}
            ; TODO
     :delete
     {:summary (sd/sum_todo "Delete collection collection arc")
      :handler (constantly sd/no_impl)
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Str}}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}
                  406 {:body s/Any}}}}] 
   
   ])

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
      :handler handle_create-col-me-arc
      :swagger {:produces "application/json"}
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
            ; TODO
     :patch
     {:summary (sd/sum_todo "Update collection collection arc")
      :handler (constantly sd/no_impl)
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
                  ;:parameters {:path {:id s/Str}
                  ;             :body schema_collection-collection-arc-update}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}
                  406 {:body s/Any}}}
            ; TODO
     :delete
     {:summary (sd/sum_todo "Delete collection collection arc")
      :handler (constantly sd/no_impl)
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Str}}
      :responses {200 {:body s/Any}
                  404 {:body s/Any}
                  406 {:body s/Any}}}}
   ]
  ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
