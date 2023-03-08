(ns madek.api.resources.io-interfaces
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]))


(defn handle_list-io_interface
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data))
        qd (if (true? full-data) :* :io_interfaces.id)
        db-result (sd/query-find-all :io_interfaces qd)]
    ;(->> db-result (map :id) set)
    (logging/info "handle_list-io_interface" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-io_interface
  [req]
  (let [io_interface (-> req :io_interface)]
    (logging/info "handle_get-io_interface" io_interface)
    ; TODO hide some fields
    (sd/response_ok io_interface)))

(defn handle_create-io_interfaces
  [req]
  (let [data (-> req :parameters :body)
        ; or TODO data with id
        ]
        ; create io_interface entry
      (if-let [ins_res (jdbc/insert! (rdbms/get-ds) :io_interfaces data)]
        ; TODO clean result
        (sd/response_ok (first ins_res))
        (sd/response_failed "Could not create io_interface." 406))))

(defn handle_update-io_interfaces
  [req]
  (let [data (-> req :parameters :body)
        id (-> req :parameters :path :id)
        dwid (assoc data :id id)
        ;old-data (sd/query-eq-find-one "io_interfaces" "id" id)
        old-data (-> req :io_interface)
        upd-query (sd/sql-update-clause "id" (str id))
        ; or TODO data with id
        ]
        ; create io_interface entry
    (logging/info "handle_update-io_interfaces: " "\nid\n" id "\ndwid\n" dwid
                  "\nold-data\n" old-data
                  "\nupd-query\n" upd-query)
    (if-let [ins-res (jdbc/update! (rdbms/get-ds) :io_interfaces dwid upd-query)]
        ; TODO clean result
      ;(if (= 1 ins-res)
        (
         let [new-data (sd/query-eq-find-one "io_interfaces" "id" id)]
         (logging/info "handle_update-io_interfaces:" "\nnew-data\n" new-data)
         (sd/response_ok new-data)
         )
       ; (sd/response_failed "Could not update io_interface." 406)
       ; )
      (sd/response_failed "Could not update io_interface." 406))))

(defn handle_delete-io_interface
  [req]
  (let [io_interface (-> req :io_interface)
        io_interface-id (-> req :io_interface :id)]
    (if (= 1 (first (jdbc/delete! (rdbms/get-ds) :io_interfaces ["id = ?" io_interface-id])))
      (sd/response_ok io_interface)
      (logging/error "Failed delete io_interface " io_interface-id))))

(defn wwrap-find-io_interface [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param "io_interfaces" colname :io_interface send404))))


(def schema_import_io_interfaces
  {
   :id s/Str
   :description s/Str
  })

(def schema_update_io_interfaces
  {:id s/Str
   :description s/Str
   
   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any})

; TODO Inst coercion
(def schema_export_io_interfaces
  {:id s/Str
   :description s/Str

   :created_at s/Any
   :updated_at s/Any})

; TODO response coercion
; TODO docu
; TODO tests
(def ring-routes

  ["/io_interfaces" 
   ["/"
    {:post {:summary (sd/sum_adm "Create io_interfaces.")
            :handler handle_create-io_interfaces
                   ;:middleware [(wwrap-find-io_interface :id "id" false)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_import_io_interfaces}
            :responses {200 {:body schema_export_io_interfaces}
                        406 {:body s/Any}}
            }
    ; io_interface list / query
     :get {:summary  (sd/sum_adm "List io_interfaces.")
           :handler handle_list-io_interface
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full-data) s/Bool}}}}]
    ; edit io_interface
   ["/:id"
    {:get {:summary (sd/sum_adm "Get io_interfaces by id.")
           :handler handle_get-io_interface
           :middleware [(wwrap-find-io_interface :id "id" true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}}

     :put {:summary (sd/sum_adm "Update io_interfaces with id.")
           :handler handle_update-io_interfaces
           :middleware [(wwrap-find-io_interface :id "id" true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}
                        :body schema_update_io_interfaces}
           :responses {200 {:body s/Any} ;schema_export_io_interfaces}
                       406 {:body s/Any}}
           }

     :delete {:summary (sd/sum_adm "Delete io_interface by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-io_interface
              :middleware [(wwrap-find-io_interface :id "id" true)]
              :parameters {:path {:id s/Str}}}}]]
   )