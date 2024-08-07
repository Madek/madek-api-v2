(ns madek.api.authorization
  (:require
   [madek.api.resources.collections.permissions :as collection-perms :only [viewable-by-auth-entity?]]
   [madek.api.resources.media-entries.permissions :as media-entry-perms :only [viewable-by-auth-entity?]]
   [taoensso.timbre :refer [info debug]]))

(defn authorized-view? [auth-entity resource tx]
  (case (:type resource)
    "MediaEntry" (media-entry-perms/viewable-by-auth-entity?
                  resource auth-entity tx)
    "Collection" (collection-perms/viewable-by-auth-entity?
                  resource auth-entity tx)
    false))

(defn authorized-download? [auth-entity resource tx]
  (case (:type resource)
    "MediaEntry" (media-entry-perms/downloadable-by-auth-entity?
                  resource auth-entity tx)
    false))

(defn authorized-edit-metadata? [auth-entity resource tx]
  (let [auth-res (case (:type resource)
                   "MediaEntry" (media-entry-perms/editable-meta-data-by-auth-entity?
                                 resource auth-entity tx)
                   "Collection" (collection-perms/editable-meta-data-by-auth-entity?
                                 resource auth-entity tx)
                   ;"Collection" (mr-permissions/permission-by-auth-entity?
                   ;              resource auth-entity :edit_metadata_and_relations "collection")
                   false)]
    (info "auth-edit-metadata" auth-res)
    auth-res))

(defn authorized-edit-permissions? [auth-entity resource tx]
  (case (:type resource)
    "MediaEntry" (media-entry-perms/editable-permissions-by-auth-entity?
                  resource auth-entity tx)
    "Collection" (collection-perms/editable-permissions-by-auth-entity?
                  resource auth-entity tx)
    false))

(defn authorized-view?! [request resource]
  (or authorized-view?
      (throw (ex-info "Forbidden" {:status 403}))))

(defn authorized? [auth-entity resource scope tx]
  (let [auth-res (case scope
                   :view (authorized-view? auth-entity resource tx)
                   :download (authorized-download? auth-entity resource tx)
                   :edit-md (authorized-edit-metadata? auth-entity resource tx)
                   :edit-perm (authorized-edit-permissions? auth-entity resource tx)
                   false)]
    (debug 'authorized? scope auth-res)
    auth-res))

(defn wrap-authorized-user [handler]
  (fn [request]
    ;(if-let [id (-> request :authenticated-entity :user_id)]
    (if-let [id (-> request :authenticated-entity :id)]
      (handler request)
      {:status 401 :body {:message "Not authorized. Please login."}})))

(def destructive-methods #{:post :put :delete})

(defn wrap-authorize-http-method [handler]
  (fn [request]
    (if (and (= (request :authentication-method) "Session")
             (destructive-methods (request :request-method)))
      {:status 405,
       :body {:message "Destructive methods not allowed for session authentication!"}}
      (handler request))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
