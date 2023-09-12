(ns madek.api.resources.io-mappings
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [logbug.catcher :as catcher]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.auth :refer [wrap-authorize-admin!]]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [reitit.coercion.schema]
            [schema.core :as s]))


(defn handle_list-io-mappings
  [req]
  (let [full_data (true? (-> req :parameters :query :full_data))
        qd (if (true? full_data) :* :io-mappings.id)
        db-result (sd/query-find-all :io_mappings qd)]
    ;(logging/info "handle_list-io-mapping" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))


(defn handle_get-io-mapping
  [req]
  (let [io-mapping (-> req :io-mapping)]
    ;(logging/info "handle_get-io-mapping" io-mapping)
    (sd/response_ok io-mapping)))


(defn handle_create-io-mappings
  [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            ins-res (jdbc/insert! (rdbms/get-ds) :io_mappings data)]
        (logging/info "handle_create-io-mappings: " "\ndata:\n" data "\nresult:\n" ins-res)
        ; create io-mapping entry
        (if-let [result (first ins-res)]
          (sd/response_ok result)
          (sd/response_failed "Could not create io-mapping." 406))))
    (catch Exception ex (sd/response_exception ex))))


(defn handle_update-io-mappings
  [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :parameters :path :id)
            dwid (assoc data :id id)
        ;old-data (-> req :io-mapping)
            upd-query (sd/sql-update-clause "id" (str id))
            upd-result (jdbc/update! (rdbms/get-ds) :io_mappings dwid upd-query)]

        (logging/info "handle_update-io-mappings: " "id: " id "\nnew-data:\n" dwid "\nresult:\n" upd-result)

        (if (= 1 (first upd-result))
          (sd/response_ok (sd/query-eq-find-one :io_mappings :id id))
          (sd/response_failed "Could not update io-mapping." 406))))
    (catch Exception ex (sd/response_exception ex))))


(defn handle_delete-io-mapping
  [req]
  (try
    (catcher/with-logging {}
      (let [io-mapping (-> req :io-mapping)
            id (-> req :parameters :path :id)
            del-result (jdbc/delete! (rdbms/get-ds) :io_mappings ["id = ?" id])]
        (if (= 1 (first del-result))
          (sd/response_ok io-mapping)
          (logging/error "Could not delete io-mapping " id))))
    (catch Exception ex (sd/response_exception ex))))


(defn wwrap-find-io-mapping [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :io_mappings
                                    :id :io-mapping true))))


(def schema_import_io-mappings
  {
   ;:id s/Uuid assign by db
   :io_interface_id s/Str
   :meta_key_id s/Str
   :key_map s/Str
   (s/optional-key :key_map_type) (s/maybe s/Str) ; TODO [null, Array, whatelse?]

  })

(def schema_update_io-mappings
  {
   ;:id s/Uuid
   (s/optional-key :io_interface_id) s/Str
   (s/optional-key :meta_key_id) s/Str
   (s/optional-key :key_map) s/Str
   (s/optional-key :key_map_type) (s/maybe s/Str)

   })


(def schema_export_io-mappings
  {:id s/Uuid
   :io_interface_id s/Str
   :meta_key_id s/Str
   :key_map s/Str
   :key_map_type (s/maybe s/Str)

   :created_at s/Any
   :updated_at s/Any})

; TODO user routes ?
; TODO docu
; TODO tests io_mappings
(def admin-routes

  ["/io-mappings" 
   ["/"
    {:post {:summary (sd/sum_adm "Create io-mappings.")
            :handler handle_create-io-mappings
            :middleware [wrap-authorize-admin!]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_import_io-mappings}
            :responses {200 {:body schema_export_io-mappings}
                        406 {:body s/Any}}
            }
    ; io-mapping list / query
     :get {:summary  (sd/sum_adm "List io-mappings.")
           :handler handle_list-io-mappings
           :middleware [wrap-authorize-admin!]
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full_data) s/Bool}}
           :responses {200 {:body [schema_export_io-mappings]}}}
     }]
   
    ; edit io-mapping
   ["/:id"
    {:get {:summary (sd/sum_adm "Get io-mappings by id.")
           :handler handle_get-io-mapping
           :middleware [wrap-authorize-admin!
                        (wwrap-find-io-mapping :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body schema_export_io-mappings}
                       404 {:body s/Any}}}

     :put {:summary (sd/sum_adm "Update io-mappings with id.")
           :handler handle_update-io-mappings
           :middleware [wrap-authorize-admin!
                        (wwrap-find-io-mapping :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body schema_update_io-mappings}
           :responses {200 {:body schema_export_io-mappings}
                       404 {:body s/Any}
                       406 {:body s/Any}}}

     :delete {:summary (sd/sum_adm "Delete io-mapping by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-io-mapping
              :middleware [wrap-authorize-admin!
                           (wwrap-find-io-mapping :id)]
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:body schema_export_io-mappings}
                          404 {:body s/Any}}}
     }]]
   )