(ns madek.api.resources.media-entries.permissions
  (:require
   [madek.api.resources.media-resources.permissions :as mr-permissions :only [permission-by-auth-entity? viewable-by-auth-entity?]]))

(defn viewable-by-auth-entity? [resource auth-entity ds]
  (mr-permissions/viewable-by-auth-entity?
   resource auth-entity "media_entry" ds))

(defn downloadable-by-auth-entity? [resource auth-entity ds]
  (mr-permissions/permission-by-auth-entity?
   resource auth-entity :get_full_size "media_entry" ds))

(defn editable-meta-data-by-auth-entity? [resource auth-entity ds]
  (mr-permissions/permission-by-auth-entity?
   resource auth-entity :edit_metadata "media_entry" ds))

(defn editable-permissions-by-auth-entity? [resource auth-entity ds]
  (mr-permissions/edit-permissions-by-auth-entity?
   resource auth-entity "media_entry" ds))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
