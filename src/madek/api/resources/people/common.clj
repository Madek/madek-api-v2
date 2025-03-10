(ns madek.api.resources.people.common
  (:require
   [clj-uuid :as uuid :refer [as-uuid]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.utils.json :as json]
   [next.jdbc :as jdbc]))

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
   :people.admin_comment
   :people.pseudonym
   :people.subtype
   :people.updated_at
   :people.identification_info])

(def people-admin-select-keys
  [:people.creator_id
   :people.updator_id])

(defn where-uid
  "Adds a where condition to the people people query against a unique id. The
  uid can be either the id, or the json encoded pair [insitution, institutional_id]."
  ([sql-map uid]
   (-> sql-map
       (sql/where
        (if (uuid/uuidable? uid)
          [:= :id (as-uuid uid)]
          (let [[institution institutional_id] (json/decode uid)]
            [:and
             [:= :people.institution institution]
             [:= :people.institutional_id institutional_id]]))))))

(defn base-query [auth-admin?]
  (-> (apply sql/select people-select-keys)
      (cond-> auth-admin?
        (apply sql/select people-admin-select-keys))))
      (sql/from :people)))

(defn person-query [uid auth-admin?]
  (-> (base-query auth-admin?)
      (where-uid uid)))

(defn find-person-by-uid [uid tx auth-admin?]
  (-> (person-query uid auth-admin?)
      sql-format
      (->> (jdbc/execute-one! tx))))
