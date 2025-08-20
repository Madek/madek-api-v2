(ns madek.api.resources.meta-data
  (:require [cheshire.core]
            [madek.api.resources.meta_data.delete :as delete]
            [madek.api.resources.meta_data.get :as get]
            [madek.api.resources.meta_data.post :as post]
            [madek.api.resources.meta_data.put :as put]
            [reitit.coercion.schema]
            [reitit.coercion.spec]))

; TODO response coercion
(def meta-data-routes
  ["/meta-data"
   {:openapi {:tags ["api/meta-data"]}}
   ["/:meta_datum_id" {:get get/meta_datum_id}]
   ["/:meta_datum_id/data-stream/" {:get get/meta_datum_id.data-stream}]
   ;:responses {200 {:body s/Any}
   ;422 {:body s/Any}}
   ])

(def collection-routes
  ["/collections"
   {:openapi {:tags ["api/collections"]}}
   ["/:collection_id/meta-data/"
    {:get get/collection_id.meta-data}]

   ["/:collection_id/meta-data-related/"
    {:get get/collection_id.meta-data-related}]

   ["/:collection_id/meta-data"
    ["/:meta_key_id"
     {:get get/collection_id.meta-datum.meta_key_id

      :delete delete/collection_id.meta-datum.meta_key_id}]

    ["/:meta_key_id/texts/"

     {:post post/collection_id.meta-datum:meta_key_id.text

      :put put/meta_key_id.text}]

    ["/:meta_key_id/text-dates/"
     {:post post/collection_id.meta-datum:meta_key_id.text-date
      :put put/text.meta_key_id.text-date}]

    ["/:meta_key_id/json/"
     {:post post/collection_id.meta_key_id.json
      :put put/collection.meta_key_id.json}]

    ["/:meta_key_id/keywords/"
     {:get get/collection.meta_key_id.keyword}]

    ["/:meta_key_id/keywords/:keyword_id"
     {:post post/collection_id.meta_key_id.keyword.keyword_id

      :delete delete/delete.meta_key_id.keyword.keyword_id}]

    ["/:meta_key_id/meta-data-people/"
     {:get get/collection_id.meta_key_id.meta_data_people
      :post post/collection_id.meta_key_id.meta_data_people}]

    ["/:meta_key_id/meta-data-people/:meta_datum_person_id"
     {:get get/collection_id.meta_key_id.meta_data_people.meta_datum_person_id
      :delete delete/collection_id.meta_key_id.meta_data_people.meta_datum_person_id}]

    ["/:meta_key_id/meta-data-people/:meta_datum_person_id/person"
     {:get get/collection_id.meta_key_id.meta_data_people.meta_datum_person_id.person}]
    ["/:meta_key_id/meta-data-people/:meta_datum_person_id/role"
     {:get get/collection_id.meta_key_id.meta_data_people.meta_datum_person_id.role}]]])

(def media-entry-routes
  ["/media-entries"
   {:openapi {:tags ["api/media-entries"]}}
   ["/:media_entry_id/meta-data/"
    {:get get/media-entry.media_entry_id.meta-data}]

   ["/:media_entry_id/meta-data-related/"
    {:get get/media_entry_id.meta-data-related}]

   ["/:media_entry_id/meta-data"
    ["/:meta_key_id"
     {:get get/media_entry_id.meta-datum.meta_key_id

      :delete delete/media_entry_id.meta-datum.meta_key_id}]

    ["/:meta_key_id/texts/"
     {:post post/meta-datum.meta_key_id.text

      :put put/media_entry.meta_key_id.text}]

    ["/:meta_key_id/text-dates/"
     {:post post/meta-datum.meta_key_id.text-date
      :put put/meta_key_id.text-date}]

    ["/:meta_key_id/json/"
     {:post post/meta-datum.meta_key_id.json

      :put put/media_entry.meta_key_id.json}]

    ["/:meta_key_id/keywords/"
     {:get get/media_entry.meta_key_id.keyword}]

    ["/:meta_key_id/keywords/:keyword_id"
     {:post post/meta-datum.meta_key_id.keyword.keyword_id

      :delete delete/meta_key_id.keyword.keyword_id2}]

    ["/:meta_key_id/meta-data-people/"
     {:get get/media_entry_id.meta_key_id.meta_data_people
      :post post/media_entry_id.meta_key_id.meta_data_people}]

    ["/:meta_key_id/meta-data-people/:meta_datum_person_id"
     {:get get/media_entry_id.meta_key_id.meta_data_people.meta_datum_person_id
      :delete delete/media_entry_id.meta_key_id.meta_data_people.meta_datum_person_id}]

    ["/:meta_key_id/meta-data-people/:meta_datum_person_id/person"
     {:get get/media_entry_id.meta_key_id.meta_data_people.meta_datum_person_id.person}]
    ["/:meta_key_id/meta-data-people/:meta_datum_person_id/role"
     {:get get/media_entry_id.meta_key_id.meta_data_people.meta_datum_person_id.role}]]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
