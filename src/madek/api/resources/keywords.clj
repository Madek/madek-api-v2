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


(def schema_create_keyword
  {
   :meta_key_id s/Str
   :term s/Str
   (s/optional-key :description) (s/maybe s/Str)
   :position s/Int
   ;(s/optional-key :external_uris) s/Any
   ;:external_uri s/Str
   (s/optional-key :rdf_class) s/Str
   ;:creator_id (s/maybe s/Uuid)
   ;:created_at s/Any
   ;:updated_at s/Any ; TODO use s/Inst
  })

(def schema_update_keyword
  {(s/optional-key :meta_key_id) s/Str
   (s/optional-key :term) s/Str
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :position) s/Int
   ;(s/optional-key :external_uris) s/Any
   ;:external_uri s/Str
   (s/optional-key :rdf_class) s/Str
   ;:creator_id (s/maybe s/Uuid)
   
   ;:updated_at s/Any ; TODO use s/Inst
  })

(def schema_export_keyword
  {:id s/Uuid
   :meta_key_id s/Str
   :term s/Str
   :description (s/maybe s/Str)
   :position s/Int
   :external_uris s/Any
   :external_uri s/Str
   :rdf_class s/Str
   :creator_id (s/maybe s/Uuid)
   :created_at s/Any
   :updated_at s/Any
   }) ; TODO use s/Inst

(defn handle_get-keyword
  [request]
  (let [id (-> request :parameters :path :id)]
    (if-let [keyword (kw/db-keywords-get-one id)]
      (sd/response_ok (kw/export-keyword keyword))
      (sd/response_not_found (str "No such keyword (" id ")"))
      )))

(defn handle_query-keywords [request]
  (let [rq (-> request :parameters :query)
        result (kw/db-keywords-query rq)]
    (sd/response_ok {:keywords result})))

(defn handle_create-keyword [req])

(defn handle_update-keyword [req])

(defn handle_delete-keyword [req])


; TODO paging
; TODO keyword post, patch, delete
(def query-routes
  ["/keywords"
   ["/" {:get {:summary "Query keywords"
               :handler handle_query-keywords
               :coercion reitit.coercion.schema/coercion
               :parameters {:query {(s/optional-key :full-data) s/Bool
                                    ;:id s/Uuid
                                    (s/optional-key :meta_key_id) s/Str
                                    (s/optional-key :term) s/Str
                                    (s/optional-key :description) s/Str
                                    (s/optional-key :page) s/Int
                                    (s/optional-key :count) s/Int
                                    }}
               :responses {200 {:body s/Any}}
               :description "Get keywords id list. TODO query parameters and paging. TODO get full data."}
         ; TODO
         
         }]

   ["/:id" {:get {:summary "Get keyword for id"
                  :handler handle_get-keyword
                  :coercion reitit.coercion.schema/coercion
                  :parameters {:path {:id s/Uuid}}
                  :responses {200 {:body s/Any} ;schema_export_keyword}
                              404 {:body {:msg s/Str}}}
                  :description "Get keyword for id. Returns 404, if no such keyword exists."}
            
            }]])

(def admin-routes
  [
   ["/keyword"
   {:post {:summary (sd/sum_adm "Create keyword.")
           :coercion reitit.coercion.schema/coercion
           :handler handle_create-keyword
           :parameters {:body schema_create_keyword}
           :responses {200 {:body s/Any}
                       406 {:body s/Any}}}}]
   ["/keyword/:id"
    {:patch {:summary (sd/sum_todo "Update keyword.")
             :handler handle_update-keyword
             :coercion reitit.coercion.schema/coercion
             :parameters {:body schema_update_keyword}
             :responses {200 {:body s/Any}
                         404 {:body {:msg s/Str}}
                         406 {:body s/Any}}}
     :delete {:summary (sd/sum_todo "Delete keyword.")
              :handler handle_delete-keyword
              :coercion reitit.coercion.schema/coercion
              :responses {200 {:body s/Any}
                          404 {:body {:msg s/Str}}
                          406 {:body s/Any}}}}]
   ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
