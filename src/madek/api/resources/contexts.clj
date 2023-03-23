(ns madek.api.resources.contexts
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]))


(defn handle_list-contexts
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data))
        qd (if (true? full-data) :* :contexts.id)
        db-result (sd/query-find-all :contexts qd)]
    ;(logging/info "handle_list-context" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-context
  [req]
  (let [context (-> req :context)]
    (logging/info "handle_get-context" context)
    ; TODO hide some fields
    (sd/response_ok context)))

(defn handle_create-contexts
  [req]
  (let [data (-> req :parameters :body)
        ; or TODO data with id
        ]
        ; create context entry
      (if-let [ins_res (jdbc/insert! (get-ds) :contexts data)]
        ; TODO clean result
        (sd/response_ok (first ins_res))
        (sd/response_failed "Could not create context." 406))))

(defn handle_update-contexts
  [req]
  (let [data (-> req :parameters :body)
        id (-> req :parameters :path :id)
        dwid (assoc data :id id)
        ;old-data (sd/query-eq-find-one "contexts" "id" id)
        old-data (-> req :context)
        upd-query (sd/sql-update-clause "id" (str id))
        ; or TODO data with id
        ]
        ; create context entry
    (logging/info "handle_update-contexts: " "\nid\n" id "\ndwid\n" dwid
                  "\nold-data\n" old-data
                  "\nupd-query\n" upd-query)
    (if-let [ins-res (jdbc/update! (get-ds) :contexts dwid upd-query)]
        ; TODO clean result
      ;(if (= 1 ins-res)
        (
         let [new-data (sd/query-eq-find-one "contexts" "id" id)]
         (logging/info "handle_update-contexts:" "\nnew-data\n" new-data)
         (sd/response_ok new-data)
         )
       ; (sd/response_failed "Could not update context." 406)
       ; )
      (sd/response_failed "Could not update context." 406))))

(defn handle_delete-context
  [req]
  (let [context (-> req :context)
        context-id (-> req :context :id)]
    (if (= 1 (first (jdbc/delete! (get-ds) :contexts ["id = ?" context-id])))
      (sd/response_ok context)
      (logging/error "Failed delete context " context-id))))

(defn wwrap-find-context [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param "contexts" colname :context send404))))


(def schema_import_contexts
  {
   :id s/Str
   :admin_comment (s/maybe s/Str)
   :labels s/Str
   :descriptions s/Str
   })

(def schema_update_contexts
  {(s/optional-key :id) s/Str
   (s/optional-key :admin_comment) (s/maybe s/Str)
   (s/optional-key :labels) s/Any
   (s/optional-key :descriptions) s/Str
    
   })

(def schema_export_contexts
  {:id s/Str
   :admin_comment (s/maybe s/Str)
   ; TODO as json
   :labels s/Any
   ; TODO as json
   :descriptions s/Any
   })

; TODO more checks
; TODO response coercion
; TODO docu
; TODO tests
(def ring-routes

  ["/contexts" 
   ["/"
    {:post {:summary (sd/sum_adm_todo "Create contexts.")
            ; TODO labels and descriptions
            :handler handle_create-contexts
                   ;:middleware [(wwrap-find-context :id "id" false)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_import_contexts}
            :responses {200 {:body schema_export_contexts}
                        406 {:body s/Any}}
            }
    ; context list / query
     :get {:summary  (sd/sum_adm "List contexts.")
           :handler handle_list-contexts
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full-data) s/Bool}}}}]
    ; edit context
   ["/:id"
    {:get {:summary (sd/sum_adm "Get contexts by id.")
           :handler handle_get-context
           :middleware [(wwrap-find-context :id "id" true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}}
           :responses {200 {:body schema_export_contexts}
                       404 {:body s/Any}}
          }
     
     :put {:summary (sd/sum_adm "Update contexts with id.")
           ; TODO labels and descriptions
           :handler handle_update-contexts
           :middleware [(wwrap-find-context :id "id" true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Str}
                        :body schema_update_contexts}
           :responses {200 {:body s/Any} ;schema_export_contexts}
                       406 {:body s/Any}}}

     :delete {:summary (sd/sum_adm_todo "Delete context by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-context
              :middleware [(wwrap-find-context :id "id" true)]
              :parameters {:path {:id s/Str}}}}]]
   )