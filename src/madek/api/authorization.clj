(ns madek.api.authorization
  (:require
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    [madek.api.resources.collections.permissions :as collection-perms :only [viewable-by-auth-entity?]]
    [madek.api.resources.media-entries.permissions :as media-entry-perms :only [viewable-by-auth-entity?]]
  
  ))

(defn authorized-view? [auth-entity resource]
  (case (:type resource)
    "MediaEntry" (media-entry-perms/viewable-by-auth-entity?
                   resource auth-entity)
    "Collection" (collection-perms/viewable-by-auth-entity?
                   resource auth-entity)
    false))

(defn authorized-download? [auth-entity resource]
  (case (:type resource)
    "MediaEntry" (media-entry-perms/downloadable-by-auth-entity?
                  resource auth-entity)
    false))

(defn authorized-edit-metadata? [auth-entity resource]
  (let [auth-res (case (:type resource)
                   "MediaEntry" (media-entry-perms/editable-meta-data-by-auth-entity?
                                 resource auth-entity)
                   "Collection" (collection-perms/editable-meta-data-by-auth-entity?
                                 resource auth-entity)
                   false)]
    (logging/info "auth-edit-metadata" auth-res)
    auth-res))

(defn authorized-edit-permissions? [auth-entity resource]
  (case (:type resource)
    "MediaEntry" (media-entry-perms/editable-permissions-by-auth-entity?
                  resource auth-entity)
    "Collection" (collection-perms/editable-permissions-by-auth-entity?
                  resource auth-entity)
    false))

(defn authorized-view?! [request resource]
  (or authorized-view?
      (throw (ex-info "Forbidden" {:status 403}))))

(defn authorized? [auth-entity resource scope]
  (let [auth-res (case scope
                   :view (authorized-view? auth-entity resource)
                   :download (authorized-download? auth-entity resource)
                   :edit-md (authorized-edit-metadata? auth-entity resource)
                   :edit-perm (authorized-edit-permissions? auth-entity resource)
                   false)]
    (logging/info 'authorized? scope auth-res)
    auth-res)
  )
  
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
