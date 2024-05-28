(ns madek.api.resources.meta-data
  (:require [cheshire.core]
   ;[madek.api.resources.meta-data.common :as c]

            [madek.api.resources.meta_data.delete :as d]
            [madek.api.resources.meta_data.get :as get]
            [madek.api.resources.meta_data.post :as post]
            [madek.api.resources.meta_data.put :as put]

            [reitit.coercion.schema]
            [reitit.coercion.spec]))

;(def schema_export_meta-datum
;  {:id s/Uuid
;   :meta_key_id s/Str
;   :type s/Str
;   :value (s/->Either [[{:id s/Uuid}] s/Str])
;   (s/optional-key :media_entry_id) s/Uuid
;   (s/optional-key :collection_id) s/Uuid})

; TODO response coercion
(def meta-data-routes
  ["/meta-data"
   {:swagger {:tags ["api/meta-data"]}}
   ["/:meta_datum_id" {:get get/meta_datum_id}]
   ["/:meta_datum_id/data-stream" {:get get/meta_datum_id.data-stream}]
   ;:responses {200 {:body s/Any}
   ;422 {:body s/Any}}
   ])
;(def schema_export_mdrole
;  {:id s/Uuid
;   :meta_datum_id s/Uuid
;   :person_id s/Uuid
;   :role_id (s/maybe s/Uuid)
;   :position s/Int})

(def role-routes
  ["/meta-data-role"
   {:swagger {:tags ["api/meta-data-role"]}}
   ["/:meta_data_role_id"
    {:get get/meta-data-role.meta_data_role_id}]])

(def collection-routes
  ["/collection"
   {:swagger {:tags ["api/collection"]}}
   ["/:collection_id/meta-data"
    {:get get/collection_id.meta-data}]

   ["/:collection_id/meta-data-related"
    {:get get/collection_id.meta-data-related}]

   ["/:collection_id/meta-datum"
    ["/:meta_key_id"
     {:get get/collection_id.meta-datum.meta_key_id

      :delete d/collection_id.meta-datum.meta_key_id}]

    ["/:meta_key_id/text"

     {:post post/collection_id.meta-datum:meta_key_id.text

      :put put/meta_key_id.text}]

    ["/:meta_key_id/text-date"
     {:post post/collection_id.meta-datum:meta_key_id.text-date
      :put put/text.meta_key_id.text-date}]

    ["/:meta_key_id/json"
     {:post post/collection_id.meta_key_id.json
      :put put/collection.meta_key_id.json}]

    ["/:meta_key_id/keyword"
     {:get get/collection.meta_key_id.keyword}]

    ["/:meta_key_id/keyword/:keyword_id"
     {:post post/collection_id.meta_key_id.keyword.keyword_id

      :delete d/delete.meta_key_id.keyword.keyword_id}]

    ["/:meta_key_id/people"
     {:get get/meta_key_id.people2}]

    ["/:meta_key_id/people/:person_id"
     {:post post/collection_id.meta_key_id.people.person_id

;; TODO???
      :delete d/collection.meta_key_id.people.person_id}]

    ; TODO meta-data roles
    ["/:meta_key_id/role/:role_id"
     {:post post/collection_id.meta_key_id.role.role_id}]]])

(def media-entry-routes
  ["/media-entry"
   {:swagger {:tags ["api/media-entry"]}}
   ["/:media_entry_id/meta-data"
    {:get get/media-entry.media_entry_id.meta-data}]

   ["/:media_entry_id/meta-data-related"
    {:get get/media_entry_id.meta-data-related}]

   ["/:media_entry_id/meta-datum"
    ["/:meta_key_id"
     {:get get/media_entry_id.meta-datum.meta_key_id

      :delete d/media_entry_id.meta-datum.meta_key_id}]

    ["/:meta_key_id/text"
     {:post post/meta-datum.meta_key_id.text

      :put put/media_entry.meta_key_id.text}]

    ["/:meta_key_id/text-date"
     {:post post/meta-datum.meta_key_id.text-date
      :put put/meta_key_id.text-date}]

    ["/:meta_key_id/json"
     {:post post/meta-datum.meta_key_id.json

      :put put/media_entry.meta_key_id.json}]

    ["/:meta_key_id/keyword"
     {:get get/media_entry.meta_key_id.keyword}]

    ["/:meta_key_id/keyword/:keyword_id"
     {:post post/meta-datum.meta_key_id.keyword.keyword_id

      :delete d/meta_key_id.keyword.keyword_id2}]

    ["/:meta_key_id/people"
     {:get get/meta_key_id.people}]

    ["/:meta_key_id/people/:person_id"
     {:post post/media_entry_id.meta-datum.meta_key_id.people.person_id

      ;; TODO: ????
      :delete d/media_entry.meta_key_id.people.person_id}]

    ["/:meta_key_id/role"
     {:get get/meta_key_id.role}]

    ["/:meta_key_id/role/:role_id/:person_id"
     {:delete d/meta_key_id.role.role_id.person_id}]

    ["/:meta_key_id/role/:role_id/:person_id/:position"
     {:post post/media_entry_id.meta-datum.meta_key_id.role.role_id.person_id.position}]]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
