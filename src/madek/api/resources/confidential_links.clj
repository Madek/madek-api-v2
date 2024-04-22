(ns madek.api.resources.confidential-links
  (:require [buddy.core.codecs :refer [bytes->b64u bytes->str]]
            [buddy.core.hash :as hash]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.resources.shared :as sd]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [schema.core :as s]
            [taoensso.timbre :refer [info]]))

(defn create-conf-link-token
  []
  (let [random (apply
                str
                (take
                 160
                 (repeatedly
                  #(rand-nth "0123456789abcdef"))))
        token (-> random hash/sha512 bytes->b64u bytes->str)
        cut (apply str (take 45 token))]
    (info "create-conf-link-token: "
          "\nrandom: " random
          "\n token: " token
          "\n cut: " cut)
    cut))

(defn handle_create-conf-link
  [req]
  (try
    (catcher/with-logging {}
      (let [u-id (-> req :authenticated-entity :id)
            mr (-> req :media-resource)
            mr-id (-> mr :id)
            mr-type (-> mr :type)
            data (-> req :parameters :body)
            token (create-conf-link-token)
            ins-data (-> data
                         (sd/try-instant-on-presence :expires_at)
                         (assoc
                          :user_id u-id
                          :resource_type mr-type
                          :resource_id mr-id
                          :token token))
            sql-map {:insert-into :confidential_links
                     :values [ins-data]}
            sql (-> sql-map sql-format)
            ins-result (jdbc/execute! (:tx req) [sql ins-data])]
        (if-let [result (first ins-result)]
          (sd/response_ok result)
          (sd/response_failed "Could not create confidential link." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_list-conf-links
  [req]
  (let [mr (-> req :media-resource)
        mr-id (-> mr :id)
        mr-type (-> mr :type)

        db-result (sd/query-eq-find-all :confidential_links
                                        :resource_id mr-id
                                        :resource_type mr-type
                                        (:tx req))]
    (sd/response_ok db-result)))

(defn handle_get-conf-link
  [req]
  (let [id (-> req :parameters :path :id)]
    (if-let [db-result (sd/query-eq-find-one :confidential_links :id id (:tx req))]
      (sd/response_ok db-result)
      (sd/response_not_found "No such confidential link"))))

(defn handle_update-conf-link
  [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)
            data (-> req :parameters :body)
            upd-data (sd/try-instant-on-presence data :expires_at)
            query (-> (sql/update :confidential_links)
                      (sql/set upd-data)
                      (sql/where [:= :id id])
                      sql-format)

            upd-result (jdbc/execute! (:tx req) query)]

        (sd/logwrite req (str "handle_update-conf-link:" "\nupdate data: " upd-data "\nresult: " upd-result))
        (if (= 1 (first upd-result))
          (sd/response_ok (sd/query-eq-find-one :confidential_links :id id (:tx req)))
          (sd/response_failed (str "Failed update confidential link: " id) 406))))

    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-conf-link
  [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)]
        (if-let [del-data (sd/query-eq-find-one :confidential_links :id id (:tx req))]
          (let [query (-> (sql/delete-from :confidential_links)
                          (sql/where [:= :id id])
                          sql-format)

                del-result (jdbc/execute! (:tx req) query)]
            (sd/logwrite req (str "handle_delete-conf-link:" "\ndelete data: " del-data "\nresult: " del-result))
            (if (= 1 (first del-result))
              (sd/response_ok del-data)
              (sd/response_failed (str "Failed delete confidential link: " id) 406)))
          (sd/response_not_found "No such confidential link"))))
    (catch Exception ex (sd/response_exception ex))))

(def schema_import_conf_link
  {;:id s/Uuid
   ;:resource_type s/Str
   ;:resource_id s/Uuid
   ;:token s/Str
   :revoked s/Bool
   (s/optional-key :description) (s/maybe s/Str)
   ;:created_at s/Any
   ;:updated_at s/Any
   (s/optional-key :expires_at) (s/maybe s/Inst)})

(def schema_update_conf_link
  {;:id s/Uuid
   ;:resource_type s/Str
   ;:resource_id s/Uuid
   ;:token s/Str
   (s/optional-key :revoked) s/Bool
   (s/optional-key :description) (s/maybe s/Str)
   ;:created_at s/Any
   ;:updated_at s/Any
   (s/optional-key :expires_at) (s/maybe s/Inst)})

(def schema_export_conf_link
  {:id s/Uuid
   :user_id s/Uuid
   :resource_type s/Str
   :resource_id s/Uuid
   :token s/Str
   :revoked s/Bool
   :description (s/maybe s/Str)
   :created_at s/Any
   :updated_at s/Any
   :expires_at (s/maybe s/Any)})

(def public-routes
  ["/confidential-link/:token/access"])

(def public-me-routes
  ["/media-entry/:media_entry_id/acess/:token"])

(def public-col-routes
  ["/collection/:collection_id/acess/:token"])

; TODO check can edit permissions
(def user-me-routes
  ["/media-entry/:media_entry_id"
   {:swagger {:tags ["api/media-entry"] :security [{"auth" []}]}}
   ["/conf-links"
    {:post {:summary (sd/sum_adm "Create confidential link.")
            :handler handle_create-conf-link
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Uuid}
                         :body schema_import_conf_link}
            :responses {200 {:body schema_export_conf_link}
                        406 {:body s/Any}}}

     :get {:summary (sd/sum_adm "List workflows.")
           :handler handle_list-conf-links
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}
                        :query {(s/optional-key :full_data) s/Bool}}
           :responses {200 {:body [schema_export_conf_link]}
                       406 {:body s/Any}}}}]

   ["/conf-link/:id"
    {:get {:summary (sd/sum_adm "Get confidential link by id.")
           :handler handle_get-conf-link
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid
                               :id s/Uuid}}
           :responses {200 {:body schema_export_conf_link}
                       404 {:body s/Any}}}

     :put {:summary (sd/sum_adm "Update confidential link with id.")
           :handler handle_update-conf-link
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid
                               :id s/Uuid}
                        :body schema_update_conf_link}
           :responses {200 {:body schema_export_conf_link}
                       404 {:body s/Any}
                       406 {:body s/Any}}}

     :delete {:summary (sd/sum_adm "Delete confidential link by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-conf-link
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :parameters {:path {:media_entry_id s/Uuid
                                  :id s/Uuid}}
              :responses {200 {:body schema_export_conf_link}
                          404 {:body s/Any}}}}]])

; TODO check can edit permissions
(def user-col-routes
  ["/collection/:collection_id"
   {:swagger {:tags ["api/collection/conf-links"] :security [{"auth" []}]}}
   ["/conf-links"
    {:post {:summary (sd/sum_adm "Create confidential link.")
            :handler handle_create-conf-link
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-permissions]
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Uuid}
                         :body schema_import_conf_link}
            :responses {200 {:body schema_export_conf_link}
                        406 {:body s/Any}}}

     :get {:summary (sd/sum_adm "List workflows.")
           :handler handle_list-conf-links
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid}}
           :responses {200 {:body [schema_export_conf_link]}
                       406 {:body s/Any}}}}]

   ["/conf-link/:id"
    {:get {:summary (sd/sum_adm "Get confidential link by id.")
           :handler handle_get-conf-link
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid
                               :id s/Uuid}}
           :responses {200 {:body schema_export_conf_link}
                       404 {:body s/Any}}}

     :put {:summary (sd/sum_adm "Update confidential link with id.")
           :handler handle_update-conf-link
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-permissions]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Uuid
                               :id s/Uuid}
                        :body schema_update_conf_link}
           :responses {200 {:body schema_export_conf_link}
                       404 {:body s/Any}
                       406 {:body s/Any}}}

     :delete {:summary (sd/sum_adm "Delete confidential link by id.")
              :coercion reitit.coercion.schema/coercion
              :handler handle_delete-conf-link
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :parameters {:path {:collection_id s/Uuid
                                  :id s/Uuid}}
              :responses {200 {:body schema_export_conf_link}
                          404 {:body s/Any}}}}]])
