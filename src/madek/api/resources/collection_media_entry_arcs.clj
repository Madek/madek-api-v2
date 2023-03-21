(ns madek.api.resources.collection-media-entry-arcs
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
      (sql/from :collection_media_entry_arcs)
      (sql/merge-where [:= :id (or (-> request :params :id) (-> request :parameters :path :id))])
      sql/format))

(defn arc [request]
  (let [query (arc-query request)
        db-result (jdbc/query (rdbms/get-ds) query)]
    (if-let [arc (first db-result)]
      (sd/response_ok arc)
      (sd/response_failed "No such collection-media-entry-arc" 404))))

(defn arcs-query [query-params]
  (let [collection-id (-> query-params :collection_id presence)
        media-entry-id (-> query-params :media_entry_id presence)]
    (-> (sql/select :*)
        (sql/from :collection_media_entry_arcs)
        (#(if collection-id
            (sql/merge-where % [:= :collection_id collection-id]) %))
        (#(if media-entry-id
            (sql/merge-where % [:= :media_entry_id media-entry-id]) %))
        (pagination/add-offset-for-honeysql query-params)
        sql/format)))

(defn arcs [request]
  (let [query (arcs-query (or (-> request :parameters :query) (-> request :parameters :path)))
        db-result (jdbc/query (rdbms/get-ds) query)]
    (sd/response_ok {:collection-media-entry-arcs db-result})))

(def routes
  (cpj/routes
    (cpj/GET "/collection-media-entry-arcs/:id" [] #'arc)
    (cpj/GET "/collection-media-entry-arcs/" [] #'arcs)
    ))

(defn create-col-me-arc 
  ([col-id me-id data]
    (create-col-me-arc col-id me-id data (rdbms/get-ds)))

  ([col-id me-id data tx]
    (let [ins-data (assoc data :collection_id col-id :media_entry_id me-id)
          ins-res (first (jdbc/insert! tx "collection_media_entry_arcs" ins-data))]
      ins-res))
  
  )

(defn handle_create-col-me-arc [req]
  (let [col-id (-> req :parameters :path :collection_id)
        me-id (-> req :parameters :path :media_entry_id)
        data (-> req :parameters :body)]
    (if-let [ins-res (create-col-me-arc col-id me-id data )]
      (sd/response_ok ins-res)
      (sd/response_failed "Could not create collection-media-entry-arc" 406))
    ))

(def schema_collection-media-entry-arc-export
  {
   :id s/Uuid
   :collection_id s/Uuid
   :media_entry_id s/Uuid
   :highlight s/Bool
   :cover s/Bool
   :order s/Num
   :created_at s/Any
   :updated_at s/Any
   :position s/Int
  })

(def schema_collection-media-entry-arc-update
  {
   (s/optional-key :id) s/Uuid
   (s/optional-key :collection_id) s/Uuid
   (s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :highlight) s/Bool
   (s/optional-key :cover) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any
   (s/optional-key :position) s/Int
   })

(def schema_collection-media-entry-arc-create
  {
   ;(s/optional-key :id) s/Uuid
   ;(s/optional-key :collection_id) s/Uuid
   ;(s/optional-key :media_entry_id) s/Uuid
   (s/optional-key :highlight) s/Bool
   (s/optional-key :cover) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :position) s/Int})

(def ring-routes
  ["/collection-media-entry-arcs"
   ["/" {:get {:summary "Query collection media-entry arcs."
               :handler arcs
               :swagger {:produces "application/json"}
               :coercion reitit.coercion.schema/coercion
               ; TODO puery params
               :parameters {:query {(s/optional-key :collection_id) s/Uuid
                                    (s/optional-key :media_entry_id) s/Uuid}}
               :responses {200 {:body s/Any}} ; TODO response coercion
               }
         }]

   ["/:id" {:get {:summary "Get collection media-entry arc."
                  :handler arc
                  :swagger {:produces "application/json"}
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Str}}
                  :responses {200 {:body s/Any}
                              404 {:body s/Any}} ; TODO response coercion
                  }
            ; TODO
            :patch {:summary (sd/sum_todo "Update collection media-entry arc")
                  :handler (constantly sd/no_impl)
                  :swagger {:produces "application/json"}
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Str}
                               :body schema_collection-media-entry-arc-update}
                  :responses {200 {:body s/Any}
                              404 {:body s/Any}
                              406 {:body s/Any}}
                  }
            ; TODO
            :delete {:summary (sd/sum_todo "Delete collection media-entry arc")
                     :handler (constantly sd/no_impl)
                     :swagger {:produces "application/json"}
                     :coercion reitit.coercion.schema/coercion
                     :parameters {:path {:id s/Str}}
                     :responses {200 {:body s/Any}
                                 404 {:body s/Any}
                                 406 {:body s/Any}}
                     }
            }] 
   
   ])

(def collection-routes
  ["/collection/:collection_id"
    ["/media-entry-arcs" 
     {:get
      {:summary "Get collection media-entry arcs."
       :handler arcs
       :swagger {:produces "application/json"}
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:collection_id s/Uuid}}
       :responses {200 {:body s/Any}} ; TODO response coercion
       }}]
    ["/media-entry-arcs/:media_entry_id"
     {:post
      {:summary (sd/sum_usr "Create collection media-entry arc")
       :handler handle_create-col-me-arc
       :swagger {:produces "application/json"}
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:collection_id s/Uuid
                           :media_entry_id s/Uuid}
                    :body schema_collection-media-entry-arc-create}
       :responses {200 {:body s/Any}
                   406 {:body s/Any}}}
       ; TODO
      :patch
      {:summary (sd/sum_todo "Update collection media-entry arc")
       :handler (constantly sd/no_impl)
       :swagger {:produces "application/json"}
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:collection_id s/Uuid
                           :media_entry_id s/Uuid}
                    :body schema_collection-media-entry-arc-update}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}
                   406 {:body s/Any}}}
            ; TODO
      :delete
      {:summary (sd/sum_todo "Delete collection media-entry arc")
       :handler (constantly sd/no_impl)
       :swagger {:produces "application/json"}
       :coercion reitit.coercion.schema/coercion
       :parameters {:path {:collection_id s/Uuid
                           :media_entry_id s/Uuid}}
       :responses {200 {:body s/Any}
                   404 {:body s/Any}
                   406 {:body s/Any}}}}
    
   ]
  ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
