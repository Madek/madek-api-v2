(ns madek.api.authorization
  (:require
   [madek.api.resources.collections.permissions :as collection-perms :only [viewable-by-auth-entity?]]
   [madek.api.resources.media-entries.permissions :as media-entry-perms :only [viewable-by-auth-entity?]]
   [taoensso.timbre :refer [info]]))

(defn authorized-view? [auth-entity resource ds]
  (case (:type resource)
    "MediaEntry" (media-entry-perms/viewable-by-auth-entity?
                  resource auth-entity ds)
    "Collection" (collection-perms/viewable-by-auth-entity?
                  resource auth-entity ds)
    false))

(defn authorized-download? [auth-entity resource ds]
  (case (:type resource)
    "MediaEntry" (media-entry-perms/downloadable-by-auth-entity?
                  resource auth-entity ds)
    false))

(defn authorized-edit-metadata? [auth-entity resource ds]
  (let [auth-res (case (:type resource)
                   "MediaEntry" (media-entry-perms/editable-meta-data-by-auth-entity?
                                 resource auth-entity ds)
                   "Collection" (collection-perms/editable-meta-data-by-auth-entity?
                                 resource auth-entity ds)
                   ;"Collection" (mr-permissions/permission-by-auth-entity?
                   ;              resource auth-entity :edit_metadata_and_relations "collection")
                   false)]
    (info "auth-edit-metadata" auth-res)
    auth-res))

(defn authorized-edit-permissions? [auth-entity resource ds]
  (case (:type resource)
    "MediaEntry" (media-entry-perms/editable-permissions-by-auth-entity?
                  resource auth-entity ds)
    "Collection" (collection-perms/editable-permissions-by-auth-entity?
                  resource auth-entity ds)
    false))

(defn authorized-view?! [request resource]
  (or authorized-view?
      (throw (ex-info "Forbidden" {:status 403}))))

(defn authorized? [auth-entity resource scope ds]
  (let [auth-res (case scope
                   :view (authorized-view? auth-entity resource ds)
                   :download (authorized-download? auth-entity resource ds)
                   :edit-md (authorized-edit-metadata? auth-entity resource ds)
                   :edit-perm (authorized-edit-permissions? auth-entity resource ds)
                   false)]
    (info 'authorized? scope auth-res)
    auth-res))

(defn wrap-authorized-user [handler]
  (fn [request]
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
