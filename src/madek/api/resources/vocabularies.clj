(ns madek.api.resources.vocabularies
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]

   [madek.api.db.dynamic_schema.common :refer [get-schema]]
   [madek.api.resources.shared.shared :as sd]
   [madek.api.resources.shared :refer [generate-swagger-pagination-params]]
   [madek.api.resources.vocabularies.delete :as delete]

   [madek.api.resources.vocabularies.get :as get]
   [madek.api.resources.vocabularies.index :refer [get-index]]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [madek.api.resources.vocabularies.post :as post]
   [madek.api.resources.vocabularies.put :as put]
   [madek.api.resources.vocabularies.vocabulary :refer [get-vocabulary]]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [cast-to-hstore convert-map-if-exist mslurp]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [info]]))

;(def schema_export-vocabulary
;  {:id s/Str
;   :position s/Int
;   :labels (s/maybe sd/schema_ml_list)
;   :descriptions (s/maybe sd/schema_ml_list)
;   (s/optional-key :admin_comment) (s/maybe s/Str)})
;
;(def schema_export-vocabulary-admin
;  {:id s/Str
;   :enabled_for_public_view s/Bool
;   :enabled_for_public_use s/Bool
;   :position s/Int
;   :labels (s/maybe sd/schema_ml_list)
;   :descriptions (s/maybe sd/schema_ml_list)
;   (s/optional-key :admin_comment) (s/maybe s/Str)})
;
;(def schema_import-vocabulary
;  {:id s/Str
;   :enabled_for_public_view s/Bool
;   :enabled_for_public_use s/Bool
;   :position s/Int
;   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
;   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
;   (s/optional-key :admin_comment) (s/maybe s/Str)})
;
;(def schema_update-vocabulary
;  {;(s/optional-key :enabled_for_public_view) s/Bool
;   ;(s/optional-key :enabled_for_public_use) s/Bool
;   (s/optional-key :position) s/Int
;   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
;   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
;   (s/optional-key :admin_comment) (s/maybe s/Str)})
;
;(def schema_perms-update
;  {(s/optional-key :enabled_for_public_view) s/Bool
;   (s/optional-key :enabled_for_public_use) s/Bool})
;
;(def schema_perms-update-user-or-group
;  {(s/optional-key :use) s/Bool
;   (s/optional-key :view) s/Bool})
;
;(def schema_export-user-perms
;  {:id s/Uuid
;   :user_id s/Uuid
;   :vocabulary_id s/Str
;   :use s/Bool
;   :view s/Bool})
;
;(def schema_export-group-perms
;  {:id s/Uuid
;   :group_id s/Uuid
;   :vocabulary_id s/Str
;   :use s/Bool
;   :view s/Bool})
;
;;(def schema_export-perms_all
;;  {:vocabulary {:id s/Str
;;                :enabled_for_public_view s/Bool
;;                :enabled_for_public_use s/Bool}
;;
;;   :users [schema_export-user-perms]
;;   :groups [schema_export-group-perms]})

; TODO vocab permission
(def admin-routes
  ["/vocabularies"
   {:swagger {:tags ["admin/vocabularies"] :security [{"auth" []}]}}
   ["/"
    {:get get/admin.vocabularies

     :post post/admin.vocabularies}]

   ["/:id"
    {:get get/admin.vocabularies.id

     :put put/admin.vocabularies.id

     :delete delete/admin.vocabularies.id}]

   ["/:id/perms"
    ["/"
     {:get get/admin.vocabularies.id.perms

      :put put/admin.vocabularies.id.perms}]

    ["/users"
     {:get get/admin.vocabularies.users}]

    ["/user/:user_id"
     {:get get/admin.vocabularies.users.user_id

      :post post/admin.vocabularies.users.user_id

      :put put/admin.vocabularies.users.user_id

      :delete delete/admin.vocabularies.user.user_id}]

    ["/groups"
     {:get get/admin.vocabularies.groups}]

    ["/group/:group_id"
     {:get get/admin.vocabularies.group.group_id

      :post post/admin.vocabularies.group.group_id

      :put put/admin.vocabularies.group.group_id

      :delete delete/admin.vocabularies.group.group_id}]]])

(def user-routes
  ["/vocabularies"
   {:swagger {:tags ["vocabulary"]}}
   ["/" {:get get/user.vocabularies}]

   ["/:id" {:get get/user.vocabularies.id}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
