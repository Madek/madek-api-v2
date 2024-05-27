(ns madek.api.resources.vocabularies.common
  (:require
   [madek.api.resources.shared.core :as sd]
   [reitit.coercion.schema]
   [schema.core :as s]))

(def schema_export-vocabulary
  {:id s/Str
   :position s/Int
   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)
   (s/optional-key :admin_comment) (s/maybe s/Str)})

(def schema_export-vocabulary-admin
  {:id s/Str
   :enabled_for_public_view s/Bool
   :enabled_for_public_use s/Bool
   :position s/Int
   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)
   (s/optional-key :admin_comment) (s/maybe s/Str)})

(def schema_import-vocabulary
  {:id s/Str
   :enabled_for_public_view s/Bool
   :enabled_for_public_use s/Bool
   :position s/Int
   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
   (s/optional-key :admin_comment) (s/maybe s/Str)})

(def schema_update-vocabulary
  {;(s/optional-key :enabled_for_public_view) s/Bool
   ;(s/optional-key :enabled_for_public_use) s/Bool
   (s/optional-key :position) s/Int
   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
   (s/optional-key :admin_comment) (s/maybe s/Str)})

(def schema_perms-update
  {(s/optional-key :enabled_for_public_view) s/Bool
   (s/optional-key :enabled_for_public_use) s/Bool})

(def schema_perms-update-user-or-group
  {(s/optional-key :use) s/Bool
   (s/optional-key :view) s/Bool})

(def schema_export-user-perms
  {:id s/Uuid
   :user_id s/Uuid
   :vocabulary_id s/Str
   :use s/Bool
   :view s/Bool})

(def schema_export-group-perms
  {:id s/Uuid
   :group_id s/Uuid
   :vocabulary_id s/Str
   :use s/Bool
   :view s/Bool})

(def schema_export-perms_all
  {:vocabulary {:id s/Str
                :enabled_for_public_view s/Bool
                :enabled_for_public_use s/Bool}

   :users [schema_export-user-perms]
   :groups [schema_export-group-perms]})
