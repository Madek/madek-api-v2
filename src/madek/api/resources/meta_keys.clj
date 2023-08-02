(ns madek.api.resources.meta-keys
  (:require
   [madek.api.resources.meta-keys.index :as mkindex]
   [reitit.coercion.schema]
   [schema.core :as s]
   [madek.api.resources.shared :as sd]
   [madek.api.resources.meta-keys.meta-key :as mk]
   [clojure.java.jdbc :as jdbc]
   [madek.api.utils.rdbms :as rdbms]
   [clojure.tools.logging :as logging]))

(defn adm-export-meta-key [meta-key]
  (-> meta-key

      (assoc :hints (sd/transform_ml (:hints meta-key))
             :labels (sd/transform_ml (:labels meta-key))
             :descriptions (sd/transform_ml (:descriptions meta-key))
             :documentation_urls (sd/transform_ml (:documentation_urls meta-key))

             ;:labels_2 (sd/transform_ml (:labels_2 meta-key))
             ;:descriptions_2 (sd/transform_ml (:descriptions_2 meta-key))
             )))

(defn adm-export-meta-key-list [meta-key]
  (-> meta-key

      (assoc :hints (sd/transform_ml (:hints meta-key))
             :labels (sd/transform_ml (:labels meta-key))
             :descriptions (sd/transform_ml (:descriptions meta-key))
             :documentation_urls (sd/transform_ml (:documentation_urls meta-key))

             :labels_2 (sd/transform_ml (:labels_2 meta-key))
             :descriptions_2 (sd/transform_ml (:descriptions_2 meta-key))
             )))

(defn user-export-meta-key [meta-key]
  (-> meta-key
      (dissoc :admin_comment :admin_comment_2)
      (assoc :hints (sd/transform_ml (:hints meta-key))
             :labels (sd/transform_ml (:labels meta-key))
             :descriptions (sd/transform_ml (:descriptions meta-key))
             :documentation_urls (sd/transform_ml (:documentation_urls meta-key))

             ;:labels_2 (sd/transform_ml (:labels_2 meta-key))
             ;:descriptions_2 (sd/transform_ml (:descriptions_2 meta-key))
             )))

(defn user-export-meta-key-list [meta-key]
  (-> meta-key
      (dissoc :admin_comment :admin_comment_2)
      (assoc :hints (sd/transform_ml (:hints meta-key))
             :labels (sd/transform_ml (:labels meta-key))
             :descriptions (sd/transform_ml (:descriptions meta-key))
             :documentation_urls (sd/transform_ml (:documentation_urls meta-key))

             :labels_2 (sd/transform_ml (:labels_2 meta-key))
             :descriptions_2 (sd/transform_ml (:descriptions_2 meta-key))
             )))

(defn handle_adm-query-meta-keys [req]
  (let [db-result (mkindex/db-query-meta-keys req) 
        result (map adm-export-meta-key-list db-result)]
    (sd/response_ok {:meta-keys result})))

(defn handle_usr-query-meta-keys [req]
  (let [db-result (mkindex/db-query-meta-keys req)
        result (map user-export-meta-key-list db-result)]
    (sd/response_ok {:meta-keys result})))

(defn handle_adm-get-meta-key [req]
  (let [mk (-> req :meta_key)
        result (mk/include-io-mappings
                (adm-export-meta-key mk) (:id mk))]
    (sd/response_ok result)))

(defn handle_usr-get-meta-key [req]
  (let [mk (-> req :meta_key)
        result (mk/include-io-mappings
                (user-export-meta-key mk) (:id mk))]
    (sd/response_ok result)))


(defn handle_create_meta-key [req]
  (let [data (-> req :parameters :body)
        db-result (jdbc/insert! (rdbms/get-ds) :meta_keys data)]
    
    (sd/response_ok (first db-result))
    ))

(defn handle_update_meta-key [req]
  (let [;old-data (-> req :meta_key)
        data (-> req :parameters :body)
        id (-> req :parameters :path :id)
        dwid (assoc data :id id) 
        ]
    (logging/info "handle_update_meta-key:"
                  "\nid: " id
                  "\ndwid\n" dwid)
    (if-let [db-result (jdbc/update! (rdbms/get-ds)
                        :meta_keys dwid ["id = ?" id])]
      (let [new-data (sd/query-eq-find-one :meta_keys :id id)]
        (logging/info "handle_update_meta-key:" 
                      "\ndb-result:\n" db-result
                      "\nnew-data:\n" new-data)
        (sd/response_ok new-data))
      (sd/response_failed "Could not update meta_key." 406))
  ))

(defn handle_delete_meta-key [req]
  (let [meta-key (-> req :meta_key)
        db-result (jdbc/delete! (rdbms/get-ds) :meta_keys ["id = ?" (:id meta-key)])]
    (if (= 1 (first db-result))
      (sd/response_ok meta-key)
      (sd/response_failed "Could not delete meta-key." 406))
    ))

(def schema_create-meta-key
  {:id s/Str
   :is_extensible_list s/Bool
   :meta_datum_object_type (s/enum "MetaDatum::Text"
                                   "MetaDatum::TextDate"
                                   "MetaDatum::JSON"
                                   "MetaDatum::Keywords"
                                   "MetaDatum::People"
                                   "MetaDatum::Roles")
   :keywords_alphabetical_order s/Bool
   :position s/Int
   :is_enabled_for_media_entries s/Bool
   :is_enabled_for_collections s/Bool
   :vocabulary_id s/Str

   (s/optional-key :allowed_people_subtypes) [(s/enum "People" "PeopleGroup")] ; TODO check more people subtypes?!?
   :text_type s/Str
   :allowed_rdf_class (s/maybe s/Str)

   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)
   :hints (s/maybe sd/schema_ml_list)
   :documentation_urls (s/maybe sd/schema_ml_list)

   :admin_comment (s/maybe s/Str)

   })

(def schema_update-meta-key
  {;:id s/Str
   (s/optional-key :is_extensible_list) s/Bool
   ;:meta_datum_object_type (s/enum "MetaDatum::Text"
   ;                                "MetaDatum::TextDate"
   ;                                "MetaDatum::JSON"
   ;                                "MetaDatum::Keywords"
   ;                                "MetaDatum::People"
   ;                                "MetaDatum::Roles")
   (s/optional-key :keywords_alphabetical_order) s/Bool
   (s/optional-key :position) s/Int
   (s/optional-key :is_enabled_for_media_entries) s/Bool
   (s/optional-key :is_enabled_for_collections) s/Bool
   ;:vocabulary_id s/Str

   (s/optional-key :allowed_people_subtypes) [(s/enum "People" "PeopleGroup")] ; TODO check more people subtypes?!?
   (s/optional-key :text_type) s/Str ; TODO enum
   (s/optional-key :allowed_rdf_class) (s/maybe s/Str)

   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
   (s/optional-key :hints) (s/maybe sd/schema_ml_list)
   (s/optional-key :documentation_urls) (s/maybe sd/schema_ml_list)

   (s/optional-key :admin_comment) (s/maybe s/Str)})

(def schema_export-meta-key-usr 
  {:id s/Str
   :is_extensible_list s/Bool
   :meta_datum_object_type s/Str
   :keywords_alphabetical_order s/Bool
   :position s/Int
   :is_enabled_for_media_entries s/Bool
   :is_enabled_for_collections s/Bool
   :vocabulary_id s/Str

   :allowed_people_subtypes [s/Str]
   :text_type s/Str
   :allowed_rdf_class (s/maybe s/Str)

   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)
   :hints (s/maybe sd/schema_ml_list)
   :documentation_urls (s/maybe sd/schema_ml_list)

   ;:admin_comment (s/maybe s/Str)

   (s/optional-key :io_mappings) s/Any

   (s/optional-key :enabled_for_public_use) s/Bool
   (s/optional-key :enabled_for_public_view) s/Bool
   (s/optional-key :position_2) s/Int
   (s/optional-key :labels_2) s/Any
   (s/optional-key :descriptions_2) s/Any
   (s/optional-key :id_2) s/Str
   ;:admin_comment_2 (s/maybe s/Str)
   })

(def schema_export-meta-key-adm
  (assoc schema_export-meta-key-usr
         :admin_comment (s/maybe s/Str)
         (s/optional-key :admin_comment_2) (s/maybe s/Str)))

(defn wwrap-find-meta_key [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler
                                    param
                                    :meta_keys colname
                                    :meta_key send404))))


(def admin-routes
  ["/meta-keys"
   ["/"
    {:get {:summary (sd/sum_adm "Get all meta-key ids")
           :description "Get list of meta-key ids. Paging is used as you get a limit of 100 entries."
           :handler handle_adm-query-meta-keys
           
           :swagger {:produces "application/json"}
           :parameters {:query {(s/optional-key :page) s/Int
                                (s/optional-key :count) s/Int
                                    ;(s/optional-key :full-data) s/Bool
                                (s/optional-key :vocabulary_id) s/Str}} ; TODO test
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion
               ; TODO response coercion for full data
               ; TODO or better own link
           :responses {200 {:body {:meta-keys [schema_export-meta-key-adm]}}}}
         ; TODO
     :post {:summary (sd/sum_todo "Create meta-key.")
            :handler handle_create_meta-key
            :swagger {:produces "application/json" :consumes "application/json"}
            :parameters {:body schema_create-meta-key}
            :content-type "application/json"
            :coercion reitit.coercion.schema/coercion
            :responses {200 {:body s/Any } ; TODO response coersion
                        406 {:body s/Any }}
            }
              
     }]

   ["/:id"
    {:get {:summary (sd/sum_adm "Get meta-key by id")
           :description "Get meta-key by id. Returns 404, if no such meta-key exists."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :accept "application/json"
           :middleware [(sd/wrap-check-valid-meta-key :id)
                        (wwrap-find-meta_key :id :id true)]
           :handler handle_adm-get-meta-key
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}
           :responses {200 {:body schema_export-meta-key-adm}
                       404 {:body {:message s/Str}}
                       422 {:body {:message s/Str}}}}
     
     :put {:summary (sd/sum_todo "Update meta-key.")
           :handler handle_update_meta-key
           :swagger {:produces "application/json" :consumes "application/json"}
           :middleware [(sd/wrap-check-valid-meta-key :id)
                        (wwrap-find-meta_key :id :id true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}
                        :body schema_update-meta-key}
           :responses {200 {:body s/Any} ; TODO response coercion
                       404 {:body {:message s/Str}}
                       422 {:body {:message s/Str}}}
           }
     
     :delete {:summary (sd/sum_todo "Delete meta-key.")
              :handler handle_delete_meta-key
              :swagger {:produces "application/json" :consumes "application/json"}
              :middleware [(sd/wrap-check-valid-meta-key :id)
                           (wwrap-find-meta_key :id :id true)]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:id s/Str}}
              :responses {200 {:body schema_export-meta-key-adm}
                          404 {:body {:message s/Str}}
                          422 {:body {:message s/Str}}}
              }
     
     }]])

; TODO meta_keys post, patch, delete
; TODO tests
(def query-routes
  ["/meta-keys"
   ["/" 
    {:get {:summary (sd/sum_usr "Get all meta-key ids")
           :description "Get list of meta-key ids. Paging is used as you get a limit of 100 entries."
           :handler handle_usr-query-meta-keys
           :swagger {:produces "application/json"}
           :parameters {:query {(s/optional-key :page) s/Int
                                (s/optional-key :count) s/Int
                                    ;(s/optional-key :full-data) s/Bool
                                (s/optional-key :vocabulary_id) s/Str}} ; TODO test
           :content-type "application/json"
           :coercion reitit.coercion.schema/coercion

               ; TODO or better own link
           :responses {200 {:body {:meta-keys [schema_export-meta-key-usr]}}}}
         
         
         }]

   ["/:id" 
    {:get {:summary (sd/sum_usr "Get meta-key by id")
           :description "Get meta-key by id. Returns 404, if no such meta-key exists."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :accept "application/json"
           :handler handle_usr-get-meta-key
           :middleware [(sd/wrap-check-valid-meta-key :id)
                        (wwrap-find-meta_key :id :id true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}
           :responses {200 {:body schema_export-meta-key-usr}
                       404 {:body {:message s/Str}}
                       422 {:body {:message s/Str}}}}}]])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
