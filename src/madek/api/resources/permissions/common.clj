(ns madek.api.resources.permissions.common
  (:require
   [reitit.coercion.schema]))

; TODO delegations ?
; TODO clipboard_user
; TODO logwrite

(defn mr-table-type [media-resource]
  (case (:type media-resource)
    "MediaEntry" "media_entry"
    "Collection" "collection"
    :default (throw ((ex-info "Invalid media-resource type" {:status 500})))))

(defn get-entity-perms
  ([mr] (get-entity-perms mr (:type mr)))
  ([mr type]
   (case type
     "MediaEntry" (select-keys mr [:id
                                   :creator_id
                                   :responsible_user_id
                                   :is_published
                                   :get_metadata_and_previews
                                   :get_full_size
                                   ; TODO delegations
                                   ])
     "Collection" (select-keys mr [:id
                                   :creator_id
                                   :responsible_user_id
                                   :clipboard_user_id
                                   :workflow_id
                                   :get_metadata_and_previews
                                   ; TODO delegations
                                   ])
     :default (throw ((ex-info "Invalid media-resource type" {:status 500}))))))




