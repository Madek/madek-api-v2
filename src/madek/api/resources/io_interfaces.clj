(ns madek.api.resources.io-interfaces
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [normalize-fields]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [error info]]))

;### handlers #################################################################

(defn handle_list-io_interface
  [req]
  (let [fields (normalize-fields req :io_interfaces)
        qd (if (empty? fields) [:io_interfaces.id] fields)
        tx (:tx req)
        db-result (dbh/query-find-all :io_interfaces qd tx)]

    ;(info "handle_list-io_interface" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-io_interface
  [req]
  (let [io_interface (-> req :io_interface)]
    ;(info "handle_get-io_interface" io_interface)
    (sd/response_ok io_interface)))

(defn handle_create-io_interfaces
  [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            query (-> (sql/insert-into :io_interfaces)
                      (sql/values [data])
                      (sql/returning :*)
                      sql-format)
            ins-res (jdbc/execute-one! (:tx req) query)]
        (info "handle_create-io_interfaces: " "\ndata:\n" data "\nresult:\n" ins-res)

        (if-let [result ins-res]
          (sd/response_ok result)
          (sd/response_failed "Could not create io_interface." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-io_interfaces
  [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :parameters :path :id)
            dwid (assoc data :id id)
            tx (:tx req)
            query (-> (sql/update :io_interfaces)
                      (sql/set dwid)
                      (sql/where [:= :id id])
                      sql-format)
            upd-result (jdbc/execute-one! tx query)]

        (info "handle_update-io_interfaces: " "id: " id "\nnew-data:\n" dwid "\nresult: " upd-result)

        (if (= 1 (::jdbc/update-count upd-result))
          (sd/response_ok (dbh/query-eq-find-one :io_interfaces :id id tx))
          (sd/response_failed "Could not update io_interface." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-io_interface
  [req]
  (try
    (catcher/with-logging {}
      (let [io_interface (-> req :io_interface)
            id (-> req :parameters :path :id)
            query (-> (sql/delete-from :io_interfaces)
                      (sql/where [:= :id id])
                      sql-format)
            del-result (jdbc/execute-one! (:tx req) query)]

        (if (= 1 (::jdbc/update-count del-result))
          (sd/response_ok io_interface)
          (error "Could not delete io_interface: " id))))
    (catch Exception e (sd/response_exception e))))

(defn wrap-find-io_interface [handler]
  (fn [request] (sd/req-find-data request handler :id
                                  :io_interfaces
                                  :id :io_interface true)))

;### swagger io schema ########################################################

(def schema_import_io_interfaces
  {:id s/Str
   :description s/Str})

(def schema_update_io_interfaces
  {;(s/optional-key :id) s/Str
   (s/optional-key :description) s/Str})

(def schema_export_io_interfaces_opt
  {(s/optional-key :id) s/Str
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any})

(def schema_export_io_interfaces
  {:id s/Str
   :description (s/maybe s/Str)
   :created_at s/Any
   :updated_at s/Any})

;### routes ###################################################################
; TODO docu
(def admin-routes
  ["/"
   {:openapi {:tags ["admin/io_interfaces"] :security ADMIN_AUTH_METHODS}}
   ["io_interfaces/"
    {:post
     {:summary (sd/sum_adm "Create io_interfaces.")
      :handler handle_create-io_interfaces
      :middleware [wrap-authorize-admin!]
      :coercion reitit.coercion.schema/coercion
      :parameters {:body schema_import_io_interfaces}
      :responses {200 {:description "Returns the created io_interface."
                       :body schema_export_io_interfaces}
                  406 {:description "Could not create io_interface."
                       :body s/Any}}}

     ; io_interface list / query
     :get
     {:summary (sd/sum_adm "List io_interfaces.")
      :handler handle_list-io_interface
      :middleware [wrap-authorize-admin!]
      :coercion reitit.coercion.schema/coercion
      :parameters {:query {(s/optional-key :fields) [(s/enum :id :description
                                                             :created_at :updated_at)]}}
      :responses {200 {:description "Returns the io_interfaces."
                       :body [schema_export_io_interfaces_opt]}}}}]

   ; edit io_interface
   ["io_interfaces/:id"
    {:get
     {:summary (sd/sum_adm "Get io_interfaces by id.")
      :handler handle_get-io_interface
      :middleware [wrap-authorize-admin!
                   wrap-find-io_interface]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Str}}
      :responses {200 {:description "Returns the io_interface."
                       :body schema_export_io_interfaces}
                  404 {:description "Not Found."
                       :body s/Any}}}

     :put
     {:summary (sd/sum_adm "Update io_interfaces with id.")
      :handler handle_update-io_interfaces
      :middleware [wrap-authorize-admin!
                   wrap-find-io_interface]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Str}
                   :body schema_update_io_interfaces}
      :responses {200 {:description "Returns the updated io_interface."
                       :body schema_export_io_interfaces}
                  404 {:description "Not Found."
                       :body s/Any}
                  406 {:description "Could not update io_interface."
                       :body s/Any}}}

     :delete
     {:summary (sd/sum_adm "Delete io_interface by id.")
      :coercion reitit.coercion.schema/coercion
      :handler handle_delete-io_interface
      :middleware [wrap-authorize-admin!
                   wrap-find-io_interface]
      :parameters {:path {:id s/Str}}
      :responses {200 {:description "Returns the deleted io_interface."
                       :body schema_export_io_interfaces}
                  404 {:description "Not Found."
                       :body s/Any}
                  406 {:description "Could not delete io_interface."
                       :body s/Any}}}}]])
