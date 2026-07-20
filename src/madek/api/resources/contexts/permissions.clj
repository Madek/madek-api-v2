(ns madek.api.resources.contexts.permissions
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.utils.helper :refer [to-uuid]]
   [next.jdbc :as jdbc]))

(defn- execute-query
  [query tx]
  (jdbc/execute! tx query))

(defn- group-ids
  [user-id tx]
  (if (nil? user-id)
    '()
    (let [query (-> (sql/select-distinct :group_id)
                    (sql/from :groups_users)
                    (sql/where [:= :groups_users.user_id (to-uuid user-id)])
                    sql-format)]
      (map :group_id (execute-query query tx)))))

(defn- user-permissions-query
  ([user-id]
   (user-permissions-query user-id "view"))

  ([user-id acc-type]
   ; acc-type: "view" or "use"
   (if (str/blank? (str user-id))
     nil
     (-> (sql/select :context_id)
         (sql/from :context_user_permissions)
         (sql/order-by [:context_id :asc] [:user_id :asc])
         (sql/where
          [:= :context_user_permissions.user_id (to-uuid user-id)]
          [:= (keyword (apply str "context_user_permissions." acc-type)) true])
         sql-format))))

(defn- pluck-context-ids
  [query tx]
  (if (nil? query)
    '()
    (map :context_id (execute-query query tx))))

(defn- group-permissions-query
  ([user-id tx] (group-permissions-query user-id "view" tx))
  ([user-id acc-type tx]
   ; acc-type: "view" or "use"
   (let [groups-ids-result (group-ids user-id tx)]
     (if (empty? groups-ids-result)
       nil
       (-> (sql/select :context_id)
           (sql/from :context_group_permissions)
           (sql/order-by [:context_id :asc] [:group_id :asc])
           (sql/where
            [:in :context_group_permissions.group_id groups-ids-result]
            [:= (keyword (apply str "context_group_permissions." acc-type)) true])
           sql-format)))))

(defn accessible-context-ids
  ([user-id tx] (accessible-context-ids user-id "view" tx))
  ([user-id acc-type tx]
   ; acc-type: "view" or "use"
   (if-not (str/blank? (str user-id))
     (concat
      (pluck-context-ids (user-permissions-query user-id acc-type) tx)
      (pluck-context-ids (group-permissions-query user-id acc-type tx) tx))

     '())))
;### Debug ####################################################################
;(debug/debug-ns *ns*)
