(ns madek.api.resources.io-mappings
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]))


(defn handle_list-io-mappings
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data))
        qd (if (true? full-data) :* :io-mappings.id)
        db-result (sd/query-find-all :io_mappings qd)]
    ;(logging/info "handle_list-io-mapping" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-io-mapping
  [req]
  (let [io-mapping (-> req :io-mapping)]
    (logging/info "handle_get-io-mapping" io-mapping)
    ; TODO hide some fields
    (sd/response_ok io-mapping)))

(defn handle_create-io-mappings
  [req]
  (let [data (-> req :parameters :body)
        ; or TODO data with id
        ]
        ; create io-mapping entry
      (if-let [ins_res (jdbc/insert! (rdbms/get-ds) :io_mappings data)]
        ; TODO clean result
        (sd/response_ok (first ins_res))
        (sd/response_failed "Could not create io-mapping." 406))))

(defn handle_update-io-mappings
  [req]
  (let [data (-> req :parameters :body)
        id (-> req :parameters :path :id)
        dwid (assoc data :id id)
        old-data (-> req :io-mapping)
        upd-query (sd/sql-update-clause "id" (str id))
        ; or TODO data with id
        ]
        ; create io-mapping entry
    (logging/info "handle_update-io-mappings: " "\nid\n" id "\ndwid\n" dwid
                  "\nold-data\n" old-data
                  "\nupd-query\n" upd-query)
    (if-let [ins-res (jdbc/update! (rdbms/get-ds) :io_mappings dwid upd-query)]
        ; TODO clean result
      ;(if (= 1 ins-res)
        (
         let [new-data (sd/query-eq-find-one :io_mappings :id id)]
         (logging/info "handle_update-io-mappings:" "\nnew-data\n" new-data)
         (sd/response_ok new-data)
         )
       ; (sd/response_failed "Could not update io-mapping." 406)
       ; )
      (sd/response_failed "Could not update io-mapping." 406))))

(defn handle_delete-io-mapping
  [req]
  (let [io-mapping (-> req :io-mapping)
        io-mapping-id (-> req :io-mapping :id)]
    (if (= 1 (first (jdbc/delete! (rdbms/get-ds) :io_mappings ["id = ?" io-mapping-id])))
      (sd/response_ok io-mapping)
      (logging/error "Failed delete io-mapping " io-mapping-id))))

(defn wwrap-find-io-mapping [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :io_mappings :id :io-mapping true))))


(def schema_import_io-mappings
  {
   ;:id s/Uuid assign by db
   :io_interface_id s/Str
   :meta_key_id s/Str
   :key_map s/Str
   (s/optional-key :key_map_type) (s/maybe s/Str) ; TODO [null, Array, whatelse?]

  })

(def schema_update_io-mappings
  {:id s/Uuid
   :io_interface_id s/Str
   :meta_key_id s/Str
   :key_map s/Str
   :key_map_type (s/maybe s/Any) ; TODO [null, Array, whatelse?]

   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any})

; TODO Inst coercion
(def schema_export_io-mappings
  {:id s/Uuid
   :io_interface_id s/Str
   :meta_key_id s/Str
   :key_map s/Str
   :key_map_type (s/maybe s/Str) ; TODO [null, Array, whatelse?]

   :created_at s/Any
   :updated_at s/Any})

; TODO more checks
; TODO response coercion
; TODO docu
; TODO tests io_mappings
(def ring-routes

  ["/io-mappings" 
   ["/"
    {:post {:summary (sd/sum_adm "Create io-mappings.")
            :handler handle_create-io-mappings
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_import_io-mappings}
            :responses {200 {:body schema_export_io-mappings}
                        406 {:body s/Any}}
            }
    ; io-mapping list / query
     :get {:summary  (sd/sum_adm "List io-mappings.")
           :handler handle_list-io-mappings
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full-data) s/Bool}}}}]
    ; edit io-mapping
   ["/:id"
    {:get {:summary (sd/sum_adm "Get io-mappings by id.")
           :handler handle_get-io-mapping
           :middleware [(wwrap-find-io-mapping :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}}

     :put {:summary (sd/sum_adm "Update io-mappings with id.")
           :handler handle_update-io-mappings
           :middleware [(wwrap-find-io-mapping :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}
                        :body schema_update_io-mappings}
           :responses {200 {:body s/Any} ;schema_export_io-mappings}
                       406 {:body s/Any}}
           }

     :delete {:summary (sd/sum_adm "Delete io-mapping by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-io-mapping
              :middleware [(wwrap-find-io-mapping :id)]
              :parameters {:path {:id s/Str}}}}]]
   )