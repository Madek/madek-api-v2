(ns madek.api.resources.media-entries.permissions
  (:require
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    [madek.api.resources.media-resources.permissions :as mr-permissions :only [viewable-by-auth-entity? permission-by-auth-entity?]]
    ))

(defn viewable-by-auth-entity? [resource auth-entity]
  (mr-permissions/viewable-by-auth-entity?
    resource auth-entity "media_entry"))

(defn downloadable-by-auth-entity? [resource auth-entity]
  (mr-permissions/permission-by-auth-entity?
    resource auth-entity :get_full_size "media_entry"))

(defn editable-meta-data-by-auth-entity? [resource auth-entity]
  (mr-permissions/permission-by-auth-entity?
   resource auth-entity :edit_metadata "media_entry"))

(defn editable-permissions-by-auth-entity? [resource auth-entity]
  (mr-permissions/edit-permissions-by-auth-entity?
   resource auth-entity "media_entry"))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
