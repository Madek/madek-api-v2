(ns madek.api.resources.keywords
  (:require [madek.api.resources.keywords.keyword :as kw]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.auth :refer [wrap-authorize-admin!]]
            [reitit.coercion.schema]
            [schema.core :as s]))


; TODO keyword external_uris
(def schema_create_keyword
  {
   :meta_key_id s/Str
   :term s/Str
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :position) (s/maybe s/Int)
   (s/optional-key :external_uris) [s/Str]
   
   (s/optional-key :rdf_class) s/Str
   ;:creator_id (s/maybe s/Uuid)
  })

; TODO keyword external_uris
(def schema_update_keyword
  {(s/optional-key :meta_key_id) s/Str
   (s/optional-key :term) s/Str
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :position) s/Int
   (s/optional-key :external_uris) [s/Str]
   (s/optional-key :rdf_class) s/Str
  })

(def schema_export_keyword_usr
  {:id s/Uuid
   :meta_key_id s/Str
   :term s/Str
   :description (s/maybe s/Str)
   :position (s/maybe s/Int)
   :external_uris [s/Any]
   :external_uri (s/maybe s/Str)
   :rdf_class s/Str
   
   })

(def schema_export_keyword_adm
  {:id s/Uuid
   :meta_key_id s/Str
   :term s/Str
   :description (s/maybe s/Str)
   :position (s/maybe s/Int)
   :external_uris [s/Any]
   :external_uri (s/maybe s/Str)
   :rdf_class s/Str
   :creator_id (s/maybe s/Uuid)
   :created_at s/Any
   :updated_at s/Any})



(defn user-export-keyword [keyword]
  (->
   keyword
   ;(select-keys
   ; [:id :meta_key_id :term :description :external_uris :rdf_class
   ;  :created_at])
   (dissoc :creator_id :created_at :updated_at)
   (assoc ; support old (singular) version of field
    :external_uri (first (keyword :external_uris)))))

(defn adm-export-keyword [keyword]
  (->
   keyword
   (assoc ; support old (singular) version of field
    :external_uri (first (keyword :external_uris)))))

(defn handle_adm-get-keyword
  [request]
  (let [id (-> request :parameters :path :id)]
    (if-let [keyword (kw/db-keywords-get-one id)]
      (sd/response_ok (adm-export-keyword keyword))
      (sd/response_not_found (str "No such keyword (" id ")")))))

(defn handle_usr-get-keyword
  [request]
  (let [id (-> request :parameters :path :id)]
    (if-let [keyword (kw/db-keywords-get-one id)]
      (sd/response_ok (user-export-keyword keyword))
      (sd/response_not_found (str "No such keyword (" id ")"))
      )))


(defn handle_usr-query-keywords [request]
  (let [rq (-> request :parameters :query)
        db-result (kw/db-keywords-query rq)
        result (map user-export-keyword db-result)]
    (sd/response_ok {:keywords result})
    ))

(defn handle_adm-query-keywords [request]
  (let [rq (-> request :parameters :query)
        db-result (kw/db-keywords-query rq)
        result (map adm-export-keyword db-result)]
    (sd/response_ok {:keywords result})))

(defn handle_create-keyword [req]
  (let [uid (-> req :authenticated-entity :id)
        data (-> req :parameters :body)
        dwid (assoc data :creator_id uid 
                    ;:rdf_class "Keyword"
                    )]
    (if-let [db-result (kw/db-keywords-create dwid)]
      (sd/response_ok (adm-export-keyword db-result))
      (sd/response_failed "Could not create keyword" 406))))

; TODO use wrapper to preload data or 404
(defn handle_update-keyword [req]
  (let [id (-> req :parameters :path :id)
        data (-> req :parameters :body)]
    (if-let [db-result (kw/db-keywords-update id data)]
      (sd/response_ok (adm-export-keyword db-result))
      (sd/response_failed "Could not update keyword" 406))))

; TODO use wrapper to preload data or 404
(defn handle_delete-keyword [req]
  (let [id (-> req :parameters :path :id)]
    (if-let [data (kw/db-keywords-delete id)]
      (sd/response_ok (adm-export-keyword data))
      (sd/response_failed "Could not delete keyword" 406))))


; TODO response coercion
; TODO keyword 
(def query-routes
  ["/keywords"
   ["/" 
    {:get {:summary "Query keywords."
           :handler handle_usr-query-keywords
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :id) s/Uuid
                                (s/optional-key :meta_key_id) s/Str
                                (s/optional-key :term) s/Str
                                (s/optional-key :description) s/Str
                                (s/optional-key :page) s/Int
                                (s/optional-key :count) s/Int}}
           :responses {200 {:body {:keywords [ schema_export_keyword_usr ]}}}
           :description "Get keywords id list."}
         }]

   ["/:id"
    {:get {:summary "Get keyword for id."
           :handler handle_usr-get-keyword
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body schema_export_keyword_usr}
                       404 {:body s/Any}}
           :description "Get keyword for id. Returns 404, if no such keyword exists."}}]])

; TODO wrap auth admin
(def admin-routes
  [
   ["/keywords/"
   {:get {:summary "Query keywords"
          :handler handle_adm-query-keywords
          :middleware [wrap-authorize-admin!]
          :coercion reitit.coercion.schema/coercion
          :parameters {:query {(s/optional-key :id) s/Uuid
                               (s/optional-key :meta_key_id) s/Str
                               (s/optional-key :term) s/Str
                               (s/optional-key :description) s/Str
                               (s/optional-key :page) s/Int
                               (s/optional-key :count) s/Int}}
          :responses {200 {:body {:keywords [schema_export_keyword_adm]}}}
          :description "Get keywords id list. TODO query parameters and paging. TODO get full data."}
    
    :post {:summary (sd/sum_adm "Create keyword.")
           :coercion reitit.coercion.schema/coercion
           :handler handle_create-keyword
           :middleware [wrap-authorize-admin!]
           :parameters {:body schema_create_keyword}
           :responses {200 {:body s/Any}
                       406 {:body s/Any}}}}]
   ["/keywords/:id"
    {:get {:summary "Get keyword for id"
           :handler handle_adm-get-keyword
           :middleware [wrap-authorize-admin!]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body schema_export_keyword_adm}
                       404 {:body {:msg s/Str}}}
           :description "Get keyword for id. Returns 404, if no such keyword exists."}
     
     :patch {:summary (sd/sum_adm "Update keyword.")
             :handler handle_update-keyword
             :middleware [wrap-authorize-admin!]
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:id s/Uuid}
                          :body schema_update_keyword}
             :responses {200 {:body s/Any}
                         404 {:body {:msg s/Str}}
                         406 {:body s/Any}}}
     :delete {:summary (sd/sum_adm "Delete keyword.")
              :handler handle_delete-keyword
              :middleware [wrap-authorize-admin!]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:body s/Any}
                          404 {:body {:msg s/Str}}
                          406 {:body s/Any}}}}]
   ])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
