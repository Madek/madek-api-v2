(ns madek.api.resources.static-pages
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS]]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [cast-to-hstore]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [info]]))

(defn handle_list-static_pages
  [req]
  (let [full-data (true? (-> req :parameters :query :full_data))
        qd (if (true? full-data) :static_pages.* :static_pages.id)
        tx (:tx req)
        db-result (dbh/query-find-all :static_pages qd tx)]
    (sd/response_ok db-result)))

(defn handle_get-static_page
  [req]
  (let [static_page (-> req :static_page)
        static_page (sd/transform_ml_map static_page)]
    (sd/response_ok static_page)))

(defn handle_create-static_page [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            ins-data (cast-to-hstore data)
            sql-query (-> (sql/insert-into :static_pages)
                          (sql/values [ins-data])
                          (sql/returning :*)
                          sql-format)
            ins-res (jdbc/execute-one! (:tx req) sql-query)
            ins-res (sd/transform_ml_map ins-res)]

        (info "handle_create-static-page:"
              "\ninsert data:\n" ins-data
              "\nresult:\n " ins-res)

        (if ins-res
          (sd/response_ok ins-res)
          (sd/response_failed "Could not create static_page." 406))))
    (catch Exception e (sd/parsed_response_exception e))))

(defn handle_update-static_page [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (-> req :parameters :path :id)
            dwid (cast-to-hstore data)
            sql-query (-> (sql/update :static_pages)
                          (sql/set dwid)
                          (sql/where [:= :id id])
                          (sql/returning :*)
                          sql-format)
            upd-result (jdbc/execute-one! (:tx req) sql-query)
            upd-result (sd/transform_ml_map upd-result)]

        (info "handle_update-static_pages: "
              "\nid:\n" id
              "\nnew-data:\n" dwid
              "\nupd-result:" upd-result)

        (if upd-result
          (sd/response_ok upd-result)
          (sd/response_failed "Could not update static_page." 406))))
    (catch Exception e (sd/parsed_response_exception e))))

(defn handle_delete-static_page [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)
            sql-query (-> (sql/delete-from :static_pages)
                          (sql/where [:= :id id])
                          (sql/returning :*)
                          sql-format)
            delresult (jdbc/execute-one! (:tx req) sql-query)
            delresult (sd/transform_ml_map delresult)]

        (info "handle_delete-static_page: "
              " id: " id
              " result: " delresult)
        (if delresult
          (sd/response_ok delresult)
          (sd/response_failed "Could not delete static page." 422))))
    (catch Exception e (sd/response_exception e))))

(defn wwrap-find-static_page [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :static_pages :id
                                    :static_page true))))

(def schema_create_static_page
  {:name s/Str
   :contents sd/schema_ml_list})

(def schema_update_static_page
  {(s/optional-key :name) s/Str
   (s/optional-key :contents) sd/schema_ml_list})

; TODO Inst coercion
(def schema_export_static_page
  {:id s/Uuid
   :name s/Str
   ;:contents sd/schema_ml_list                              ; TODO: fix this, "contents" : "(not (map? a-java.util.HashMap))"
   :contents s/Any
   :created_at s/Any ; TODO as Inst
   :updated_at s/Any})

; TODO auth admin
; TODO response coercion
; TODO docu
; TODO tests
; TODO user and admin routes
(def admin-routes

  ["/"
   {:openapi {:tags ["admin/static-pages"] :security ADMIN_AUTH_METHODS}}

   ["static-pages"
    {:post {:summary (sd/sum_adm "Create static_page.")
            :handler handle_create-static_page
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_create_static_page}
            :middleware [wrap-authorize-admin!]
            :responses {200 {:description "Returns the created static_page."
                             :body schema_export_static_page}


                        406 (sd/create-error-message-response "Not Acceptable." "Could not create static_page.")
                        409 (sd/create-error-message-response  "Conflict." "Entry already exists")


                        ;406 {:description "Not Acceptable."
                        ;     :body {:message s/Str}
                        ;     :example {:message "Could not create static_page."}}
                        ;409 {:description "Conflict."
                        ;     :body {:message s/Str}
                        ;     :example {:message "Entry already exists"}}


                        }}

     :get {:summary (sd/sum_adm "List static_pages.")
           :handler handle_list-static_pages
           :coercion reitit.coercion.schema/coercion
           :middleware [wrap-authorize-admin!]
           :responses {
                       ;200 {:description "Returns the list of static_pages."
                       ;     :body (s/->Either [[schema_export_static_page] [{:id s/Uuid}]])
                       ;     :example [{:id "uuid"
                       ;                                     :name "name"
                       ;                                     :contents [{:lang "de" :content "content"}]
                       ;                                     :created_at "2020-01-01T00:00:00Z"
                       ;                                     :updated_at "2020-01-01T00:00:00Z"}]}
                       ;201 {:description "Returns the list of static_pages."
                       ;     :body [schema_export_static_page]
                       ;     :example [{:id "uuid"
                       ;                                     :name "name"
                       ;                                     :contents [{:lang "de" :content "content"}]
                       ;                                     :created_at "2020-01-01T00:00:00Z"
                       ;                                     :updated_at "2020-01-01T00:00:00Z"}]}


                       200 (sd/create-examples-response  "Returns the list of static_pages." (s/->Either [[schema_export_static_page] [{:id s/Uuid}]])
                             [{:id "uuid"
                               :name "name"
                               :contents [{:lang "de" :content "content"}]
                               :created_at "2020-01-01T00:00:00Z"
                               :updated_at "2020-01-01T00:00:00Z"}]
                             )

                       201 (sd/create-examples-response  "Returns the list of static_pages." [schema_export_static_page]
                             [{:id "uuid"
                               :name "name"
                               :contents [{:lang "de" :content "content"}]
                               :created_at "2020-01-01T00:00:00Z"
                               :updated_at "2020-01-01T00:00:00Z"}]
                             )


                       404 (sd/create-error-message-response  "Not Found." "No static_pages found.")
                       422 (sd/create-error-message-response "Unprocessable Entity." "Could not list static_pages.")



                       ;404 {:description "Not Found."
                       ;     :body {:message s/Str}
                       ;     :example {:message "No static_pages found."}}
                       ;422 {:description "Unprocessable Entity."
                       ;     :body {:message s/Str}
                       ;     :example {:message "Could not list static_pages."}}


                       }}
           :parameters {:query {(s/optional-key :full_data) s/Bool}}}]

   ["static-pages/:id"
    {:get {:summary (sd/sum_adm "Get static_pages by id.")
           :handler handle_get-static_page
           :middleware [wrap-authorize-admin!
                        (wwrap-find-static_page :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:description "Returns the static_page."
                            :body schema_export_static_page}

                       404 (sd/create-error-message-response  "Not Found." "No such entity in :static_pages as :id with <id>")


                       ;404 {:description "Not Found."
                       ;     :body {:message s/Str}
                       ;     :example {:message "No such entity in :static_pages as :id with <id>"}}

                       }}

     :put {:summary (sd/sum_adm "Update static_pages with id.")
           :handler handle_update-static_page
           :middleware [wrap-authorize-admin!
                        (wwrap-find-static_page :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body schema_update_static_page}
           :responses {200 {:description "Returns the updated static_page."
                            :body schema_export_static_page}
                       406 {:description "Not Acceptable."
                            :body s/Any}

                       404 (sd/create-error-message-response  "Not Found." "No such entity in :static_pages as :id with <id>")

                       ;404 {:description "Not Found."
                       ;     :body {:message s/Str}
                       ;     :example {:message "No such entity in :static_pages as :id with <id>"}}


                       }}

     :delete {:summary (sd/sum_adm "Delete static_page by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-static_page
              :middleware [wrap-authorize-admin!
                           (wwrap-find-static_page :id)]
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:description "Returns the deleted static_page."
                               :body schema_export_static_page}


                          404 (sd/create-error-message-response  "Not Found." "Entry already exists")
                          422 (sd/create-error-message-response "Unprocessable Entity." "Could not delete static page.")


                          ;404 {:description "Not Found."
                          ;     :body {:message s/Str}
                          ;     ;:example {:message "No such entity in :static_pages as :id with <id>"}
                          ;     ;:examples {"application/json" {:message "Entry already exists"}}
                          ;     :content {"application/json" {
                          ;                                   ;:schema
                          ;                                   :message "Entry already exists"}}
                          ;     }
                          ;
                          ;
                          ;422 {:description "Unprocessable Entity."
                          ;     :body {:message s/Str}
                          ;     :example {:message "Could not delete static page."}}


                          }}}]])
