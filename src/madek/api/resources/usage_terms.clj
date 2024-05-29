(ns madek.api.resources.usage-terms
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [info]]))

(defn handle_list-usage_term
  [req]
  (let [full-data (true? (-> req :parameters :query :full_data))
        qd (if (true? full-data) :usage_terms.* :usage_terms.id)
        tx (:tx req)
        db-result (sd/query-find-all :usage_terms qd tx)]
    ;(->> db-result (map :id) set)
    ;(info "handle_list-usage_term" "\nqd\n" qd "\nresult\n" db-result)
    (sd/response_ok db-result)))

(defn handle_get-usage_term
  [req]
  (let [usage_term (-> req :usage_term)]
    ;(info "handle_get-usage_term" usage_term)
    ; TODO hide some fields
    (sd/response_ok usage_term)))

(defn handle_create-usage_terms [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            sql-query (-> (sql/insert-into :usage_terms)
                          (sql/values [data])
                          (sql/returning :*)
                          sql-format)
            ins-res (jdbc/execute-one! (:tx req) sql-query)]

        (info "handle_create-usage_term: " "\ndata:\n" data "\nresult:\n" ins-res)

        (if ins-res
          (sd/response_ok ins-res)
          (sd/response_failed "Could not create usage_term." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_update-usage_terms [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :parameters :path :id)
            dwid (assoc data :id id)
            sql-query (-> (sql/update :usage_terms)
                          (sql/set dwid)
                          (sql/where [:= :id id])
                          (sql/returning :*)
                          sql-format)
            upd-result (jdbc/execute-one! (:tx req) sql-query)]

        (info "handle_update-usage_terms: " "\nid\n" id "\ndwid\n" dwid "\nupd-result:" upd-result)

        (if upd-result
          (sd/response_ok upd-result)
          (sd/response_failed "Could not update usage_term." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_delete-usage_term [req]
  (try
    (catcher/with-logging {}
      (let [olddata (-> req :usage_term)
            id (-> req :parameters :path :id)
            sql-query (-> (sql/delete-from :usage_terms)
                          (sql/where [:= :id id])
                          sql-format)
            delresult (jdbc/execute-one! (:tx req) sql-query)]

        (if (= 1 (::jdbc/update-count delresult))
          (sd/response_ok olddata)
          (sd/response_failed "Could not delete usage term." 422))))
    (catch Exception e (sd/response_exception e))))

(defn wwrap-find-usage_term [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :usage_terms :id
                                    :usage_term true))))

;(def schema_import_usage_terms
;  {;:id is db assigned or optional
;   :title s/Str
;   :version s/Str
;   :intro s/Str
;   :body s/Str})
;
;(def schema_update_usage_terms
;  {;:id s/Uuid
;   (s/optional-key :title) s/Str
;   (s/optional-key :version) s/Str
;   (s/optional-key :intro) s/Str
;   (s/optional-key :body) s/Str})
;
;; TODO Inst coercion
;(def schema_export_usage_term
;  {:id s/Uuid
;   (s/optional-key :title) s/Str
;   (s/optional-key :version) s/Str
;   (s/optional-key :intro) s/Str
;   (s/optional-key :body) s/Str
;   (s/optional-key :created_at) s/Any ; TODO as Inst
;   (s/optional-key :updated_at) s/Any})

; TODO auth admin
; TODO response coercion
; TODO docu
; TODO tests
(def admin-routes

  ["/usage-terms"
   {:swagger {:tags ["admin/usage-terms"] :security [{"auth" []}]}}
   ["/"
    {:post {:summary (sd/sum_adm "Create usage_terms.")
            :handler handle_create-usage_terms
            ;:middleware [(wwrap-find-usage_term :id "id" false)]
            :coercion reitit.coercion.schema/coercion
            :middleware [wrap-authorize-admin!]
            :parameters {:body (get-schema :usage_terms.schema_import_usage_terms)}
            :responses {200 {:body (get-schema :usage_terms.schema_export_usage_term)}
                        406 {:body s/Any}}}

     ; usage_term list / query
     :get {:summary (sd/sum_adm "List usage_terms.")
           :handler handle_list-usage_term
           :coercion reitit.coercion.schema/coercion
           :middleware [wrap-authorize-admin!]
           :parameters {:query {(s/optional-key :full_data) s/Bool}}
           :responses {200 {:body [(get-schema :usage_terms.schema_export_usage_term)]}
                       406 {:body s/Any}}}}]

   ; edit usage_term
   ["/:id"
    {:get {:summary (sd/sum_adm "Get usage_terms by id.")
           :handler handle_get-usage_term
           :middleware [wrap-authorize-admin!
                        (wwrap-find-usage_term :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body (get-schema :usage_terms.schema_export_usage_term)}
                       404 {:description "Not found."
                            :schema s/Str
                            :examples {"application/json" {:message "No such entity in :usage_terms as :id with <id>"}}}}}

     :put {:summary (sd/sum_adm "Update usage_terms with id.")
           :handler handle_update-usage_terms
           :middleware [wrap-authorize-admin!
                        (wwrap-find-usage_term :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body (get-schema :usage_terms.schema_update_usage_terms)}
           :responses {200 {:body (get-schema :usage_terms.schema_export_usage_term)}
                       404 {:description "Not found."
                            :schema s/Str
                            :examples {"application/json" {:message "No such entity in :usage_terms as :id with <id>"}}}
                       406 {:body s/Any}}}

     :delete {:summary (sd/sum_adm "Delete usage_term by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-usage_term
              :middleware [wrap-authorize-admin!
                           (wwrap-find-usage_term :id)]
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:body (get-schema :usage_terms.schema_export_usage_term)}

                          404 {:description "Not found."
                               :schema s/Str
                               :examples {"application/json" {:message "No such entity in :usage_terms as :id with <id>"}}}}}}]])

; TODO usage_terms get the most recent one ?!?
(def user-routes
  ["/usage-terms"
   {:swagger {:tags ["usage-terms"]}}
   ["/"
    {:get {:summary (sd/sum_pub "List usage_terms.")
           :handler handle_list-usage_term
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full_data) s/Bool}}
           :responses {200 {:body [(get-schema :usage_terms.schema_export_usage_term)]}}}}]

   ["/:id"
    {:get {:summary (sd/sum_pub "Get usage_terms by id.")
           :handler handle_get-usage_term
           :middleware [(wwrap-find-usage_term :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body (get-schema :usage_terms.schema_export_usage_term)}
                       404 {:body s/Any}}}}]])