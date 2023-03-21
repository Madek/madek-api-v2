(ns madek.api.resources.keywords
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.pagination :as pagination]
    [madek.api.resources.keywords.keyword :as kw]
    [madek.api.resources.shared :as shared]
    [madek.api.utils.rdbms :as rdbms]
    [reitit.coercion.schema]
    [schema.core :as s]
    
    [madek.api.resources.shared :as sd]))


(def routes
  (cpj/routes
    (cpj/GET "/keywords/:id" _ kw/get-keyword)
    (cpj/ANY "*" _ shared/dead-end-handler)
    ))


(def schema_export_keyword
  {:id s/Uuid
   :meta_key_id s/Str
   :term s/Str
   :description (s/maybe s/Str)
   :external_uris s/Any
   :external_uri s/Str
   :rdf_class s/Str
   :created_at s/Any}) ; TODO use s/Inst

(defn handle_get-keyword
  [request]
  (let [id (shared/get-path-params request :id)
        keyword (kw/db-keywords-get-one id)]
    (if (= keyword nil)
      {:status 404 :body {:msg (apply str ["No such keyword (" id ")"])}}
      {:status 200 :body (kw/export-keyword keyword)})))

(defn handle_query-keywords [request]
  (let [ds (:db request)
        result (kw/db-keywords-query ds)]
    {:status 200 :body {:keywords result}}))

; TODO keyword post, patch, delete
(def ring-routes
  ["/keywords"
   ["/" {:get {:summary "Get all keywords ids"
               :handler handle_query-keywords
               :coercion reitit.coercion.schema/coercion
               :responses {200 {:body {:keywords [{:id s/Uuid}]}}}
               :description "Get keywords id list. TODO query parameters and paging. TODO get full data."}
         ; TODO
         :post {:summary (sd/sum_todo "Create keyword.")
                :handler (constantly sd/no_impl)}
         }]

   ["/:id" {:get {:summary "Get keyword for id"
                  :handler handle_get-keyword
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Uuid}}
                  :responses {200 {:body schema_export_keyword}
                              404 {:body {:msg s/Str}}}
                  :description "Get keyword for id. Returns 404, if no such keyword exists."}}]])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
