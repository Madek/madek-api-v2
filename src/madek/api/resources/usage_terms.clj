(ns madek.api.resources.usage-terms
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   [madek.api.utils.sql :as sql]
   [reitit.coercion.schema]
   [schema.core :as s]))


(defn handle_list-usage_term
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data))
        qd (if (true? full-data) :usage_terms.* :usage_terms.id)
        db-result (sd/query-find-all :usage_terms qd)]
    ;(->> db-result (map :id) set)
    (logging/info "handle_list-usage_term" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-usage_term
  [req]
  (let [usage_term (-> req :usage_term)]
    (logging/info "handle_get-usage_term" usage_term)
    ; TODO hide some fields
    (sd/response_ok usage_term)))

(defn handle_create-usage_terms
  [req]
  (let [data (-> req :parameters :body)
        ; or TODO data with id
        ]
        ; create usage_term entry
      (if-let [ins_res (jdbc/insert! (rdbms/get-ds) :usage_terms data)]
        ; TODO clean result
        (sd/response_ok (first ins_res))
        (sd/response_failed "Could not create usage_term." 406))))

(defn handle_update-usage_terms
  [req]
  (let [data (-> req :parameters :body)
        id (-> req :parameters :path :id)
        dwid (assoc data :id id)
        ;old-data (sd/query-eq-find-one "usage_terms" "id" id)
        old-data (-> req :usage_term)
        upd-query (sd/sql-update-clause "id" (str id))
        ; or TODO data with id
        ]
        ; create usage_term entry
    (logging/info "handle_update-usage_terms: " "\nid\n" id "\ndwid\n" dwid
                  "\nold-data\n" old-data
                  "\nupd-query\n" upd-query)
    (if-let [ins-res (jdbc/update! (rdbms/get-ds) :usage_terms dwid upd-query)]
        ; TODO clean result
      ;(if (= 1 ins-res)
        (
         let [new-data (sd/query-eq-find-one "usage_terms" "id" id)]
         (logging/info "handle_update-usage_terms:" "\nnew-data\n" new-data)
         (sd/response_ok new-data)
         )
       ; (sd/response_failed "Could not update usage_term." 406)
       ; )
      (sd/response_failed "Could not update usage_term." 406))))

(defn handle_delete-usage_term
  [req]
  (let [usage_term (-> req :usage_term)
        usage_term-id (-> req :usage_term :id)]
    (if (= 1 (first (jdbc/delete! (rdbms/get-ds) :usage_terms ["id = ?" usage_term-id])))
      (sd/response_ok usage_term)
      (logging/error "Failed delete usage_term " usage_term-id))))

(defn wwrap-find-usage_term [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param "usage_terms" colname :usage_term send404))))


(def schema_import_usage_terms
  {
   ;:id is db assigned or optional
   :title s/Str
   :version s/Str
   :intro s/Str
   :body s/Str
  })

(def schema_update_usage_terms
  {:id s/Uuid
   :title s/Str
   :version s/Str
   :intro s/Str
   :body s/Str
   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any
   })

; TODO Inst coercion
(def schema_export_usage_terms
  {:id s/Uuid
   :title s/Str
   :version s/Str
   :intro s/Str
   :body s/Str
   :created_at s/Any
   :updated_at s/Any
   })

; TODO response coercion
; TODO docu
; TODO tests
; TODO user routes
(def ring-routes

  ["/usage_terms" 
   ["/"
    {:post {:summary (sd/sum_adm "Create usage_terms.")
            :handler handle_create-usage_terms
                   ;:middleware [(wwrap-find-usage_term :id "id" false)]
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_import_usage_terms}
            :responses {200 {:body schema_export_usage_terms}
                        406 {:body s/Any}}
            }
    ; usage_term list / query
     :get {:summary  (sd/sum_adm "List usage_terms.")
           :handler handle_list-usage_term
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full-data) s/Bool}}}}]
    ; edit usage_term
   ["/:id"
    {:get {:summary (sd/sum_adm "Get usage_terms by id.")
           :handler handle_get-usage_term
           :middleware [(wwrap-find-usage_term :id "id" true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}}

     :put {:summary (sd/sum_adm "Update usage_terms with id.")
           :handler handle_update-usage_terms
           :middleware [(wwrap-find-usage_term :id "id" true)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body schema_update_usage_terms}
           :responses {200 {:body s/Any} ;schema_export_usage_terms}
                       406 {:body s/Any}}
           }

     :delete {:summary (sd/sum_adm "Delete usage_term by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-usage_term
              :middleware [(wwrap-find-usage_term :id "id" true)]
              :parameters {:path {:id s/Uuid}}}}]]
   )