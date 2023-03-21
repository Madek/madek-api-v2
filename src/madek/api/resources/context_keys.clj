(ns madek.api.resources.context-keys
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]))


(defn handle_list-context_keys
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data))
        qd (if (true? full-data) :* :context_keys.id)
        db-result (sd/query-find-all :context_keys qd)]
    ;(logging/info "handle_list-context_key" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-context_key
  [req]
  (let [context_key (-> req :context_key)]
    (logging/info "handle_get-context_key" context_key)
    ; TODO hide some fields
    (sd/response_ok context_key)))

(defn handle_create-context_keys
  [req]
  (let [data (-> req :parameters :body)
        ; or TODO data with id
        ]
        ; create context_key entry
      (if-let [ins_res (jdbc/insert! (rdbms/get-ds) :context_keys data)]
        ; TODO clean result
        (sd/response_ok (first ins_res))
        (sd/response_failed "Could not create context_key." 406))))

(defn handle_update-context_keys
  [req]
  (let [data (-> req :parameters :body)
        id (-> req :parameters :path :id)
        dwid (assoc data :id id)
        ;old-data (sd/query-eq-find-one "context_keys" "id" id)
        old-data (-> req :context_key)
        upd-query (sd/sql-update-clause "id" (str id))
        ; or TODO data with id
        ]
        ; create context_key entry
    (logging/info "handle_update-context_keys: " "\nid\n" id "\ndwid\n" dwid
                  "\nold-data\n" old-data
                  "\nupd-query\n" upd-query)
    (if-let [ins-res (jdbc/update! (rdbms/get-ds) :context_keys dwid upd-query)]
        ; TODO clean result
      ;(if (= 1 ins-res)
        (
         let [new-data (sd/query-eq-find-one "context_keys" "id" id)]
         (logging/info "handle_update-context_keys:" "\nnew-data\n" new-data)
         (sd/response_ok new-data)
         )
       ; (sd/response_failed "Could not update context_key." 406)
       ; )
      (sd/response_failed "Could not update context_key." 406))))

(defn handle_delete-context_key
  [req]
  (let [context_key (-> req :context_key)
        context_key-id (-> req :context_key :id)]
    (if (= 1 (first (jdbc/delete! (rdbms/get-ds) :context_keys ["id = ?" context_key-id])))
      (sd/response_ok context_key)
      (logging/error "Failed delete context_key " context_key-id))))

(defn wwrap-find-context_key [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param "context_keys" colname :context_key send404))))


(def schema_import_context_keys
  {;:id s/Str
   :context_id s/Str
   :meta_key_id s/Str
   :is_required s/Bool
   (s/optional-key :length_max) (s/maybe s/Int)
   (s/optional-key :length_min) (s/maybe s/Int)
   :position s/Int
   (s/optional-key :admin_comment) (s/maybe s/Str)
   ; hstore
   (s/optional-key :labels) s/Any
   (s/optional-key :descriptions) s/Any ; {s/Str s/Str} 
   (s/optional-key :hints) s/Str
   (s/optional-key :documentation_urls) s/Str
   })

(def schema_update_context_keys
  {(s/optional-key :id) s/Str
   (s/optional-key :meta_key_id) s/Str
   (s/optional-key :is_required) s/Bool
   (s/optional-key :length_max) (s/maybe s/Int)
   (s/optional-key :length_min) (s/maybe s/Int)
   (s/optional-key :position) s/Int
   (s/optional-key :admin_comment) (s/maybe s/Str)
   (s/optional-key :labels) s/Any
   (s/optional-key :descriptions) s/Any ; {s/Str s/Str} 
   (s/optional-key :hints) s/Str
   (s/optional-key :documentation_urls) s/Str
   })

; TODO Inst coercion
(def schema_export_context_keys
  {:id s/Uuid
   :context_id s/Str
   :meta_key_id s/Str
   :is_required s/Bool
   :length_max (s/maybe s/Int)
   :length_min (s/maybe s/Int)
   :position s/Int
   :admin_comment (s/maybe s/Str)
   :labels s/Str
   :descriptions s/Str
   :hints s/Str
   :documentation_urls s/Str})

; TODO more checks
; TODO response coercion
; TODO docu
; TODO tests
(def ring-routes

  ["/context_keys" 
   ["/"
    {:post {:summary (sd/sum_adm_todo "Create context_keys.")
            ; TODO labels and descriptions
            :handler handle_create-context_keys
                   ;:middleware [(wwrap-find-context_key :id "id" false)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_import_context_keys}
            :responses {200 {:body schema_export_context_keys}
                        406 {:body s/Any}}
            }
    ; context_key list / query
     :get {:summary  (sd/sum_adm "List context_keys.")
           :handler handle_list-context_keys
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full-data) s/Bool}}}}]
    ; edit context_key
   ["/:id"
    {:get {:summary (sd/sum_adm "Get context_keys by id.")
           :handler handle_get-context_key
           :middleware [(wwrap-find-context_key :id "id" true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}}
     
     :put {:summary (sd/sum_adm "Update context_keys with id.")
           ; TODO labels and descriptions
           :handler handle_update-context_keys
           :middleware [(wwrap-find-context_key :id "id" true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}
                        :body schema_update_context_keys}
           :responses {200 {:body s/Any} ;schema_export_context_keys}
                       406 {:body s/Any}}}

     :delete {:summary (sd/sum_adm_todo "Delete context_key by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-context_key
              :middleware [(wwrap-find-context_key :id "id" true)]
              :parameters {:path {:id s/Str}}}}]]
   )