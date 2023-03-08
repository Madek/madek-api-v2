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
  (when-let [arc (->> (arc-query request)
                      (jdbc/query (rdbms/get-ds))
                      first)]
    {:body arc}))

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
  {:body {:collection-media-entry-arcs
          (jdbc/query (rdbms/get-ds)
                      (arcs-query (:query-params request)))}})

(def routes
  (cpj/routes
    (cpj/GET "/collection-media-entry-arcs/:id" [] #'arc)
    (cpj/GET "/collection-media-entry-arcs/" [] #'arcs)
    ))

(def ring-routes
  ["/collection-media-entry-arcs"
   ["/" {:get {:summary "Get collection media-entry arcs."
               :handler arcs
               :swagger {:produces "application/json"}
               :coercion reitit.coercion.schema/coercion
               :responses {200 {:body s/Any}}}
         ; TODO
         :post {:summary (sd/sum_todo "Create collection media-entry arc")
                :handler (constantly sd/no_impl)}
         }] ; TODO response coercion

   ["/:id" {:get {:summary "Get collection media-entry arcs."
                  :handler arc
                  :swagger {:produces "application/json"}
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Str}}
                  :responses {200 {:body s/Any}}}}] ; TODO response coercion
   ])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
