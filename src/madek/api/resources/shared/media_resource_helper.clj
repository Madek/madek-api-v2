(ns madek.api.resources.shared.media_resource_helper
  (:require [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.authorization :refer [authorized?]]
            [madek.api.utils.helper :refer [to-uuid]]
            [madek.api.utils.soft-delete :refer [->non-soft-deleted]]
            [next.jdbc :as jdbc]
            [taoensso.timbre :refer [error warn]]))

; begin media resources helpers
(defn get-media-resource
  "First checks for collection_id, then for media_entry_id.
   If creating collection-media-entry-arc, the collection permission is checked."
  ([params tx]
   (or (get-media-resource params :collection_id "collections" "Collection" tx)
       (get-media-resource params :media_entry_id "media_entries" "MediaEntry" tx)))

  ([params id-key table-name type tx]
   (try
     (when-let [id (-> params :parameters :path id-key)]
       ;(info "get-media-resource" "\nid\n" id)
       (when-let [resource (jdbc/execute-one! tx
                                              (-> (sql/select :*)
                                                  (sql/from (keyword table-name))
                                                  (sql/order-by [:creator_id :desc] [:id :asc])
                                                  (sql/where [:= :id (to-uuid id)])
                                                  ->non-soft-deleted
                                                  sql-format))]
         (assoc resource :type type :table-name table-name)))

     (catch Exception e
       (error "ERROR: get-media-resource: " (ex-data e))
       (merge (ex-data e)
              {:statuc 406, :body {:message (.getMessage e)}})))))

(defn ring-add-media-resource [request handler tx] ;;here
  (if-let [media-resource (get-media-resource request tx)]
    (let [request-with-media-resource (assoc request :media-resource media-resource)]
      (handler request-with-media-resource))
    {:status 404}))

; end media resources helpers

; begin media-resource auth helpers

(defn- public? [resource]
  (-> resource :get_metadata_and_previews boolean))

(defn authorize-request-for-media-resource [request handler scope]
  ;(
  ;(info "auth-request-for-mr"
  ;              "\nscope: " scope
  ;              "\nauth entity:\n" (-> request :authenticated-entity)
  ;              "\nis-admin:\n" (-> request :is_admin)
  ;              )
  (if-let [media-resource (:media-resource request)]

    (if (and (= scope :view) (public? media-resource))
      ; viewable if public
      (handler request)

      (if-let [auth-entity (-> request :authenticated-entity)]
        (if (-> request :is_admin true?)
          ; do all as admin
          (handler request)

          ; if not admin check user auth
          (if (authorized? auth-entity media-resource scope (:tx request))
            (handler request)
            ;else
            {:status 403 :body {:message "Not authorized for media-resource"}}))

        ;else
        {:status 401 :body {:message "Not authorized"}}))

    ; else
    (let [response {:status 500 :body {:message "No media-resource in request."}}]
      (warn 'mrh/authorize-request-for-media-resource response [request handler])
      response)))

; end media-resource auth helpers

;(debug/debug-ns *ns*)
