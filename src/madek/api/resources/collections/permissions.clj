(ns madek.api.resources.collections.permissions
  (:require
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]
    [madek.api.resources.media-resources.permissions :as mr-permissions]
    ))

(defn viewable-by-auth-entity? [resource auth-entity]
  (mr-permissions/viewable-by-auth-entity?
    resource auth-entity "collection"))

(defn editable-meta-data-by-auth-entity? [resource auth-entity]
  (mr-permissions/permission-by-auth-entity?
   resource auth-entity :edit_metadata "collection"))

(defn editable-permissions-by-auth-entity? [resource auth-entity]
  (mr-permissions/edit-permissions-by-auth-entity?
   resource auth-entity "collection"))
