(ns madek.api.resources.full-texts
   (:require
    [clojure.tools.logging :as logging]
    [madek.api.resources.shared :as sd]
    [reitit.coercion.schema]
    [schema.core :as s]
    [clojure.java.jdbc :as jdbc]
    [madek.api.utils.rdbms :as rdbms]
    ))

(defn handle_list-full_texts
  [req]
  (let [full-data (true? (-> req :parameters :query :full-data))
        qd (if (true? full-data) :* :media_resource_id)
        db-result (sd/query-find-all :full_texts qd)]
    (logging/info "handle_list-full_texts" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-full_text
  [req]
  (let [ft (-> req :full_text)]
    (sd/response_ok ft)
  ))


(defn handle_create-full_texts
  [req]
  (let [rdata (-> req :parameters :body)
        mr-id (or (:media_resource_id rdata) (-> req :parameters :path :media_resource_id))
        data (assoc rdata :media_resource_id mr-id)]
    (if-let [ins-res (jdbc/insert! (rdbms/get-ds) :full_texts data)]
      (sd/response_ok (first ins-res))
      (sd/response_failed "Could not create full_text." 406)
      )))

(defn handle_update-full_texts
  [req]
  (let [data (-> req :parameters :body)
        mr-id (or (:media_resource_id data) (-> req :parameters :path :media_resource_id))
        dwid (assoc data :media_resource_id mr-id)
        ;old-data (-> req :full_text)
        upd-query (sd/sql-update-clause "media_resource_id" (str mr-id))]
    (if-let [ins-res (jdbc/update! (rdbms/get-ds) :full_texts dwid upd-query)]
      (let [new-data (sd/query-eq-find-one :full_texts :media_resource_id mr-id)]
        (sd/response_ok new-data))
      (sd/response_failed "Could not update full_text." 406)))
  )

(defn handle_delete-full_texts
  [req]
  (let [full-text (-> req :full_text)
        mr-id (:media_resource_id full-text)]
    ; TODO use shared update cl
    (if (= 1 (first (jdbc/delete! (rdbms/get-ds) :full_texts ["media_resource_id = ?" mr-id])))
           (sd/response_ok full-text)
           (logging/error "Failed delete full_text " mr-id)
    )
  ))

(defn wrap-find-full_text [param send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :full_texts :media_resource_id
                                    :full_text send404))))

; TODO tests
(def query-routes
  [
  ["/full_texts"
    {:get {:summary (sd/sum_usr "List full_texts.")
           :handler handle_list-full_texts
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full-data) s/Bool
                                ; TODO query by id, paging, count
                                ;(s/optional-key :media_resource_id) s/Uuid
                                }}}}]
   ["/full_texts/:media_resource_id"
    {:get {:summary (sd/sum_usr "Get full_text.")
           :handler handle_get-full_text
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_resource_id s/Str}}
           :middleware [(wrap-find-full_text :media_resource_id true)]
           }}]
   ])
     
; TODO tests
(def edit-routes
  [
   ["/full_text"
    {:post {:summary (sd/sum_adm "Create full_texts entry")
            :swagger {:consumes "application/json" :produces "application/json"}
            :handler handle_create-full_texts
            :coercion reitit.coercion.schema/coercion
            :parameters {:body {:text s/Str
                                :media_resource_id s/Str}}
           ;:middleware [wrap-authorize-admin]
            }}]
   
   ["/full_text/:media_resource_id"
    {:post {:summary (sd/sum_adm "Create full_texts entry")
            :swagger {:consumes "application/json" :produces "application/json"}
            :handler handle_create-full_texts
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_resource_id s/Str}
                         :body {:text s/Str}}
           ;:middleware [wrap-authorize-admin]
            }
     :patch {:summary (sd/sum_adm "Update full_text.")
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_resource_id s/Str}
                          :body {:text s/Str}}
             :middleware [(wrap-find-full_text :media_resource_id true)]
             :handler handle_update-full_texts}

    :delete {:summary (sd/sum_adm "Delete full_text.")
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_resource_id s/Str}}
             :middleware [(wrap-find-full_text :media_resource_id true)]
             :handler handle_delete-full_texts}}
   ]
  ]
  )
