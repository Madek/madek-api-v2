(ns madek.api.resources.people.common
  (:require
   [clj-uuid :as uuid :refer [as-uuid]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.debug :as debug]
   [madek.api.utils.json :as json]
   [madek.api.resources.shared :as sd]
   [next.jdbc :as jdbc]
   [schema.core :as s]
   [taoensso.timbre :refer [debug error info spy warn]]))

;;; schema ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; sql ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def people-select-keys
  [:people.created_at
   :people.description
   :people.external_uris
   :people.id
   :people.first_name
   :people.institution
   :people.institutional_id
   :people.last_name
   :people.pseudonym
   :people.subtype
   :people.updated_at])


(def people-base-query
  (-> (apply sql/select people-select-keys)
      (sql/from :people)))

(defn person-query [uid]
  (-> people-base-query
      (sql/where
        (if (uuid/uuidable? uid)
          [:= :id (as-uuid uid)]
          (let [[institution institutional_id] (json/decode uid)]
            [:and
             [:= :people.institution institution]
             [:= :people.institutional_id institutional_id]])))))
