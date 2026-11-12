(ns madek.api.resources.shared.media_resource_helper
  (:require [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.authorization :refer [authorized?]]
            [madek.api.utils.auth :as auth]
            [madek.api.utils.soft-delete :refer [->non-soft-deleted]]
            [next.jdbc :as jdbc]
            [taoensso.timbre :refer [warn]]))

; begin media resources helpers
(defn- ->uuid [v]
  (cond
    (uuid? v) v
    (string? v) (parse-uuid v)
    :else nil))

(defn get-media-resource
  "First checks for collection_id, then for media_entry_id.
   If creating collection-media-entry-arc, the collection permission is checked."
  ([params tx]
   (or (get-media-resource params :collection_id "collections" "Collection" tx)
       (get-media-resource params :media_entry_id "media_entries" "MediaEntry" tx)))

  ([params id-key table-name type tx]
   (when-let [id (some-> params :parameters :path id-key ->uuid)]
     (when-let [resource (jdbc/execute-one! tx
                                            (-> (sql/select :*)
                                                (sql/from (keyword table-name))
                                                (sql/order-by [:creator_id :desc] [:id :asc])
                                                (sql/where [:= :id id])
                                                ->non-soft-deleted
                                                sql-format))]
       (assoc resource :type type :table-name table-name)))))

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
        (if (auth/admin-route-authorized? request)
          ; admin routes only; on user routes admins need permissions as well
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
