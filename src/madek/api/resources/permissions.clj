(ns madek.api.resources.permissions
  (:require
   [madek.api.resources.permissions.delete :as delete]
   [madek.api.resources.permissions.get :as get]
   [madek.api.resources.permissions.post :as post]
   [madek.api.resources.permissions.put :as put]
   [reitit.coercion.schema]))

(def media-entry-routes
  ["/media-entry/:media_entry_id/perms"
   {:openapi {:tags ["media-entry/perms"]}}
   ["/"
    {:get get/media-entry.media_entry_id.perms}]

   ; TODO patch for entity perms
   ; get_metadata_and_previews
   ; get_full_size
   ; responsible_user_id
   ; responsible_delegation_id
   ; TODO beware to let not update perm fields in media-entry or collection patch/update

   ["/resources"
    {:get get/media-entry.media_entry_id.perms.resources

     :put put/me.resources}]

   ["/resource/:perm_name/:perm_val"
    {:put put/me.resource.perm_name.perm_val}]

   ["/users"
    {:get get/media-entry.media_entry_id.perms.users}]

   ["/user/:user_id"
    {:get get/media-entry.media_entry_id.perms.user

     :post post/me.user.user_id

     :delete delete/me.user.user_id}]

   ["/user/:user_id/:perm_name/:perm_val"
    {:put put/me.user.user_id.perm_name.perm_val}]

   ["/groups"
    {:get get/media-entry.media_entry_id.perms.groups}]

   ["/group/:group_id"
    {:get get/media-entry.media_entry_id.perms.group.group_id

     :post post/me.group.group_id

     :delete delete/me.group.group_id}]

   ["/group/:group_id/:perm_name/:perm_val"
    {:put put/me.group.group_id.perm_name.permval}]])

(def collection-routes
  ["/collection/:collection_id/perms"
   {:openapi {:tags ["collection/perms"]}}
   ["/"
    {:get get/collection.collection_id.perms}]

   ["/resources"
    {:get get/collection.collection_id.perms.resources

     :put put/col.resources}]

   ["/resource/:perm_name/:perm_val"
    {:put put/col.resource.perm_name.perm_val}]

   ["/users"
    {:get get/collection.collection_id.perms.users}]

   ["/user/:user_id"
    {:get get/collection.collection_id.perms.user.user_id

     :post post/col.user.user_id

     :delete delete/col.user.user_id}]

   ["/user/:user_id/:perm_name/:perm_val"
    {:put put/col.user.user_id.perm_name.perm_val}]

   ["/groups"
    {:get get/collection.collection_id.perms.groups}]

   ["/group/:group_id"
    {:get get/collection.collection_id.perms.group.group_id

     :post post/col.group.group_id

     :delete delete/col.group.group_id}]

   ["/group/:group_id/:perm_name/:perm_val"
    {:put put/col.group.group_id.perm_name.perm_val}]])
