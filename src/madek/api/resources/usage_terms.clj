(ns madek.api.resources.usage-terms
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [logbug.catcher :as catcher]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.auth :refer [wrap-authorize-admin!]]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [reitit.coercion.schema]
            [schema.core :as s]))


(defn handle_list-usage_term
  [req]
  (let [full-data (true? (-> req :parameters :query :full_data))
        qd (if (true? full-data) :usage_terms.* :usage_terms.id)
        db-result (sd/query-find-all :usage_terms qd)]
    ;(->> db-result (map :id) set)
    ;(logging/info "handle_list-usage_term" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-usage_term
  [req]
  (let [usage_term (-> req :usage_term)]
    ;(logging/info "handle_get-usage_term" usage_term)
    ; TODO hide some fields
    (sd/response_ok usage_term)))

(defn handle_create-usage_terms [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            ins-res (jdbc/insert! (rdbms/get-ds) :usage_terms data)]

        (logging/info "handle_create-usage_term: " "\ndata:\n" data "\nresult:\n" ins-res)

        (if-let [result (first ins-res)]
        ; TODO clean result
          (sd/response_ok result)
          (sd/response_failed "Could not create usage_term." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_update-usage_terms [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :parameters :path :id)
            dwid (assoc data :id id)
            upd-query (sd/sql-update-clause "id" (str id))
            upd-result (jdbc/update! (rdbms/get-ds)
                                     :usage_terms
                                     dwid upd-query)]

        (logging/info "handle_update-usage_terms: " "\nid\n" id "\ndwid\n" dwid "\nupd-result:" upd-result)

        (if (= 1 (first upd-result))
          (sd/response_ok (sd/query-eq-find-one :usage_terms :id id))
          (sd/response_failed "Could not update usage_term." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_delete-usage_term [req] 
  (try
    (catcher/with-logging {}
      (let [olddata (-> req :usage_term)
            id (-> req :parameters :path :id)
            delresult (jdbc/delete! (rdbms/get-ds)
                                    :usage_terms
                                    ["id = ?" id])]
        (if (= 1 (first delresult))
          (sd/response_ok olddata)
          (sd/response_failed "Could not delete usage term." 422))))
    (catch Exception e (sd/response_exception e))))

(defn wwrap-find-usage_term [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :usage_terms :id
                                    :usage_term true))))


(def schema_import_usage_terms
  {
   ;:id is db assigned or optional
   :title s/Str
   :version s/Str
   :intro s/Str
   :body s/Str
  })

(def schema_update_usage_terms
  {
   ;:id s/Uuid
   (s/optional-key :title) s/Str
   (s/optional-key :version) s/Str
   (s/optional-key :intro) s/Str
   (s/optional-key :body) s/Str
   
   })

; TODO Inst coercion
(def schema_export_usage_term
  {
   :id s/Uuid
   (s/optional-key :title) s/Str
   (s/optional-key :version) s/Str
   (s/optional-key :intro) s/Str
   (s/optional-key :body) s/Str
   (s/optional-key :created_at) s/Any ; TODO as Inst
   (s/optional-key :updated_at) s/Any
   })

; TODO auth admin
; TODO response coercion
; TODO docu
; TODO tests
(def admin-routes

  ["/usage-terms" 
   ["/"
    {:post {:summary (sd/sum_adm "Create usage_terms.")
            :handler handle_create-usage_terms
                   ;:middleware [(wwrap-find-usage_term :id "id" false)]
            :coercion reitit.coercion.schema/coercion
            :middleware [wrap-authorize-admin!]
            :parameters {:body schema_import_usage_terms}
            :responses {200 {:body schema_export_usage_term}
                        406 {:body s/Any}}
            }
    ; usage_term list / query
     :get {:summary  (sd/sum_adm "List usage_terms.")
           :handler handle_list-usage_term
           :coercion reitit.coercion.schema/coercion
           :middleware [wrap-authorize-admin!]
           :parameters {:query {(s/optional-key :full_data) s/Bool}}
           :responses {200 {:body [schema_export_usage_term]}
                       406 {:body s/Any}}}}]
    ; edit usage_term
   ["/:id"
    {:get {:summary (sd/sum_adm "Get usage_terms by id.")
           :handler handle_get-usage_term
           :middleware [wrap-authorize-admin!
                        (wwrap-find-usage_term :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body s/Any} ;schema_export_usage_terms}
                       404 {:body s/Any}}
           }

     :put {:summary (sd/sum_adm "Update usage_terms with id.")
           :handler handle_update-usage_terms
           :middleware [wrap-authorize-admin!
                        (wwrap-find-usage_term :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body schema_update_usage_terms}
           :responses {200 {:body s/Any} ;schema_export_usage_terms}
                       404 {:body s/Any}
                       406 {:body s/Any}}
           }

     :delete {:summary (sd/sum_adm "Delete usage_term by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-usage_term
              :middleware [wrap-authorize-admin!
                           (wwrap-find-usage_term :id)]
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:body s/Any} ;schema_export_usage_terms}
                          404 {:body s/Any}}
              }}]]
   )


; TODO usage_terms get the most recent one ?!?
(def user-routes
  ["/usage-terms"
   ["/"
    {:get {:summary  (sd/sum_pub "List usage_terms.")
           :handler handle_list-usage_term
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full_data) s/Bool}}
           :responses {200 {:body [schema_export_usage_term]}}}}]
   
   ["/:id"
    {:get {:summary (sd/sum_pub "Get usage_terms by id.")
           :handler handle_get-usage_term
           :middleware [(wwrap-find-usage_term :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body schema_export_usage_term}
                       404 {:body s/Any}}}
     }]])