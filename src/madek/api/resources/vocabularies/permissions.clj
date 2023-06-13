(ns madek.api.resources.vocabularies.permissions
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ;[madek.api.authentication.basic :refer [extract]]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    [madek.api.utils.sql :as sql]
    ))

(defn- execute-query
  [query]
  (jdbc/query (rdbms/get-ds) query))

(defn- group-ids
  [user-id]
  (if (nil? user-id)
    '()
    (let [query (-> (sql/select :group_id)
                    (sql/modifiers :distinct)
                    (sql/from :groups_users)
                    (sql/where [:= :groups_users.user_id user-id])
                    (sql/format))]
      (map :group_id (execute-query query)))))

(defn- user-permissions-query
  ([user-id] (user-permissions-query user-id "view"))
  ([user-id acc-type]
   ; acc-type: "view" or "use"
   (if user-id
     (-> (sql/select :vocabulary_id)
         (sql/from :vocabulary_user_permissions)
         (sql/merge-where
          [:= :vocabulary_user_permissions.user_id user-id]
          [:= (keyword ("vocabulary_user_permissions." acc-type)) true])
         (sql/format))
     nil))
  )

(defn- pluck-vocabulary-ids
  [query]
  (if (nil? query)
    '()
    (map :vocabulary_id (execute-query query))))

(defn- group-permissions-query
  ([user-id] (group-permissions-query user-id "view"))
  ([user-id acc-type]
   ; acc-type: "view" or "use"
   (let [groups-ids-result (group-ids user-id)]
     (if (empty? groups-ids-result)
       nil
       (-> (sql/select :vocabulary_id)
           (sql/from :vocabulary_group_permissions)
           (sql/where
            [:in :vocabulary_group_permissions.group_id (group-ids user-id)]
            [:= (keyword (apply str "vocabulary_group_permissions." acc-type)) true])
           (sql/format)))))
  )

(defn accessible-vocabulary-ids
  ([user-id] (accessible-vocabulary-ids user-id "view"))
  ([user-id acc-type]
   ; acc-type: "view" or "edit"
   (concat
    (pluck-vocabulary-ids (user-permissions-query user-id acc-type))
    (pluck-vocabulary-ids (group-permissions-query user-id acc-type))))
  )

;### Debug ####################################################################
;(debug/debug-ns *ns*)
