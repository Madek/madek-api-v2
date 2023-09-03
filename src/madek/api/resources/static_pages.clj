(ns madek.api.resources.static-pages
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
   
   [reitit.coercion.schema]
   [schema.core :as s]))


(defn handle_list-static_pages
  [req]
  (let [full-data (true? (-> req :parameters :query :full_data))
        qd (if (true? full-data) :static_pages.* :static_pages.id)
        db-result (sd/query-find-all :static_pages qd)]
    (sd/response_ok db-result)))

(defn handle_get-static_page
  [req]
  (let [static_page (-> req :static_page)]
    (sd/response_ok static_page)))

(defn handle_create-static_page [req]
  (try
    (let [data (-> req :parameters :body)
          contents-json (sd/try-as-json (:contents data))
          ins-data (assoc data :contents contents-json)
          ins-res (jdbc/insert! (rdbms/get-ds)
                                :static_pages
                                ins-data)]
      
      (logging/info "handle_create-static-page:" 
                    "\ninsert data:\n" ins-data
                    "\nresult:\n " ins-res)

      (if-let [result (first ins-res) ]
        (sd/response_ok result)
        (sd/response_failed "Could not create static_page." 406)))
    (catch Exception e (sd/response_exception e))))

(defn handle_update-static_page [req]
  (try
    (let [data (-> req :parameters :body)
          id (-> req :parameters :path :id)
          contents-json (sd/try-as-json (:contents data))
          dwid (assoc data :id id :contents contents-json)
          upd-query (sd/sql-update-clause "id" (str id))
          upd-result (jdbc/update! (rdbms/get-ds)
                                   :static_pages
                                   dwid upd-query)]

      (logging/info "handle_update-static_pages: "
                    "\nid:\n" id
                    "\nnew-data:\n" dwid
                    "\nupd-result:" upd-result)

      (if (= 1 (first upd-result))
        (sd/response_ok (sd/query-eq-find-one :static_pages :id id))
        (sd/response_failed "Could not update static_page." 406)))
    (catch Exception e (sd/response_exception e))))

(defn handle_delete-static_page [req]
  (try
    (let [olddata (-> req :static_page)
          id (-> req :parameters :path :id)
          delresult (jdbc/delete! (rdbms/get-ds)
                                  :static_pages
                                  ["id = ?" id])]
      (logging/info "handle_delete-static_page: "
                    " id: " id
                    " result: " delresult)
      (if (= 1 (first delresult))
        (sd/response_ok olddata)
        (sd/response_failed "Could not delete static page." 422)))
    (catch Exception e (sd/response_exception e))))

(defn wwrap-find-static_page [param]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :static_pages :id
                                    :static_page true))))


(def schema_create_static_page
  {
   ;:id is db assigned or optional
   :name s/Str
   :contents s/Str ; TODO is json as hstore
  })

(def schema_update_static_page
  {
   ;:id s/Uuid
   (s/optional-key :name) s/Str
   (s/optional-key :contents) s/Str ; TODO is json as hstore
   
   })

; TODO Inst coercion
(def schema_export_static_page
  {
   :id s/Uuid
   :name s/Str
   :contents s/Str ; TODO is json as hstore
   :created_at s/Any ; TODO as Inst
   :updated_at s/Any
   })

; TODO auth admin
; TODO response coercion
; TODO docu
; TODO tests
; TODO user and admin routes
(def admin-routes

  ["/static-pages" 
   ["/"
    {:post {:summary (sd/sum_adm "Create static_page.")
            :handler handle_create-static_page
            :coercion reitit.coercion.schema/coercion
            :parameters {:body schema_create_static_page}
            :responses {200 {:body schema_export_static_page}
                        406 {:body s/Any}}
            }
    ; static_page list / query
     :get {:summary  (sd/sum_adm "List static_pages.")
           :handler handle_list-static_pages
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full_data) s/Bool}}}
     }]
    ; edit static_page
   ["/:id"
    {:get {:summary (sd/sum_adm "Get static_pages by id.")
           :handler handle_get-static_page
           :middleware [(wwrap-find-static_page :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}}
           :responses {200 {:body schema_export_static_page}
                       404 {:body s/Any}}}

     :put {:summary (sd/sum_adm "Update static_pages with id.")
           :handler handle_update-static_page
           :middleware [(wwrap-find-static_page :id)]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:id s/Uuid}
                        :body schema_update_static_page}
           :responses {200 {:body schema_export_static_page}
                       406 {:body s/Any}
                       404 {:body s/Any}}}

     :delete {:summary (sd/sum_adm "Delete static_page by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-static_page
              :middleware [(wwrap-find-static_page :id)]
              :parameters {:path {:id s/Uuid}}
              :responses {200 {:body schema_export_static_page}
                          404 {:body s/Any}}}
     }]]
   )