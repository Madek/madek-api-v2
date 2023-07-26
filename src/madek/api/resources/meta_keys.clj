(ns madek.api.resources.meta-keys
  (:require
   [madek.api.resources.meta-keys.index :as mkindex]
   [reitit.coercion.schema]
   [schema.core :as s]
   [madek.api.resources.shared :as sd]
   [madek.api.resources.meta-keys.meta-key :as mk]))

(defn adm-export-meta-key [meta-key]
  (-> meta-key

      (assoc :hints (sd/transform_ml (:hints meta-key))
             :labels (sd/transform_ml (:labels meta-key))
             :descriptions (sd/transform_ml (:descriptions meta-key))
             :documentation_urls (sd/transform_ml (:documentation_urls meta-key))

             ;:labels_2 (sd/transform_ml (:labels_2 meta-key))
             ;:descriptions_2 (sd/transform_ml (:descriptions_2 meta-key))
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
             )
      ))

(defn handle_adm-query-meta-keys [req]
  (let [db-result (mkindex/db-query-meta-keys req) 
        result (map adm-export-meta-key db-result)]
    (sd/response_ok {:meta-keys result})))

(defn handle_usr-query-meta-keys [req]
  (let [db-result (mkindex/db-query-meta-keys req)
        result (map user-export-meta-key db-result)]
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
  (let [result {}]
    (sd/response_ok result)))

(defn handle_update_meta-key [req]
  (let [result {}]
    (sd/response_ok result)))

(defn handle_delete_meta-key [req]
  (let [result {}]
    (sd/response_ok result)))

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

   :allowed_people_subtypes [(s/enum "People" "PeopleGroup")] ; TODO check more people subtypes?!?
   :text_type s/Str
   :allowed_rdf_class (s/maybe s/Str)

   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)
   :hints (s/maybe sd/schema_ml_list)
   :documentation_urls (s/maybe sd/schema_ml_list)

   :admin_comment (s/maybe s/Str)

   })

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
            :responses {200 {:body s/Any }
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
     :put {:summary (sd/sum_todo "Create meta-key.")
           :handler handle_update_meta-key
           :swagger {:produces "application/json" :consumes "application/json"}
           :middleware [(sd/wrap-check-valid-meta-key :id)
                        (wwrap-find-meta_key :id :id true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}
           :responses {200 {:body schema_export-meta-key-adm}
                       404 {:body {:message s/Str}}
                       422 {:body {:message s/Str}}}
           }
     
     :delete {:summary (sd/sum_todo "Create meta-key.")
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
