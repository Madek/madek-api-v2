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
    [clojure.string :as str]
    
    [madek.api.resources.shared :as sd]))

(defn- execute-query
  [query]
  ;(logging/info "execute-query: \n" query)
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
  ([user-id]
   ;(logging/info "user-permissions-query:" user-id)
   (user-permissions-query user-id "view"))
  
  ([user-id acc-type]
   ;(logging/info "user-permissions-query:" user-id ":" acc-type)
   ; acc-type: "view" or "use"
   (if (str/blank? (str user-id))
     nil
     (-> (sql/select :vocabulary_id)
         (sql/from :vocabulary_user_permissions)
         (sql/merge-where
          [:= :vocabulary_user_permissions.user_id user-id]
          [:= (keyword (apply str "vocabulary_user_permissions." acc-type)) true])
         (sql/format))
     )
   )
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
            [:in :vocabulary_group_permissions.group_id groups-ids-result]
            [:= (keyword (apply str "vocabulary_group_permissions." acc-type)) true])
           (sql/format)))))
  )

(defn accessible-vocabulary-ids
  ([user-id] (accessible-vocabulary-ids user-id "view"))
  ([user-id acc-type]
   ; acc-type: "view" or "edit"
   (if-not (str/blank? (str user-id))
     (concat
      (pluck-vocabulary-ids (user-permissions-query user-id acc-type))
      (pluck-vocabulary-ids (group-permissions-query user-id acc-type)))
     
     '()
     )
   )
  )

(defn handle_list-vocab-user-perms [req]
  (let [id (-> req :parameters :path :id)
        result (sd/query-eq-find-all
                :vocabulary_user_permissions
                :vocabulary_id id)]
    (sd/response_ok result
     ;{:vocabulary_id id
     ; :vocabulary_user_permissions result}
     )))

(defn handle_get-vocab-user-perms [req]
  (let [id (-> req :parameters :path :id)
        uid (-> req :parameters :path :user_id)]
    (if-let [result (sd/query-eq2-find-one
                     :vocabulary_user_permissions
                     :vocabulary_id id
                     :user_id uid)]
      (sd/response_ok result)
      (sd/response_not_found "No such vocabulary user permission."))))

(defn handle_create-vocab-user-perms [req]
  (try
    (catcher/with-logging {}
      (let [vid (-> req :parameters :path :id)
            uid (-> req :parameters :path :user_id)
            data (-> req :parameters :body)
            ins-data (assoc data
                            :vocabulary_id vid
                            :user_id uid)
            ins-result (jdbc/insert! (get-ds)
                                     :vocabulary_user_permissions
                                     ins-data)]
        (if-let [result (first ins-result)]
          (sd/response_ok result)
          (sd/response_failed "Could not create vocabulary user permission" 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-vocab-user-perms [req]
  (try
    (catcher/with-logging {}
      (let [vid (-> req :parameters :path :id)
            uid (-> req :parameters :path :user_id)
            upd-data (-> req :parameters :body)
            upd-clause (sd/sql-update-clause "vocabulary_id" vid "user_id" uid)
            upd-result (jdbc/update! (get-ds)
                                     :vocabulary_user_permissions
                                     upd-data upd-clause)]
        (if (= 1 (first upd-result))
          (sd/response_ok (sd/query-eq2-find-one 
                           :vocabulary_user_permissions
                           :vocabulary_id vid
                           :user_id uid))
          (sd/response_failed "Could not update vocabulary user permission" 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-vocab-user-perms [req]
  (try
    (catcher/with-logging {}
      (let [vid (-> req :parameters :path :id)
            uid (-> req :parameters :path :user_id)]
        (if-let [old-data (sd/query-eq2-find-one
                           :vocabulary_user_permissions
                           :vocabulary_id vid
                           :user_id uid)]
          (let [del-clause (sd/sql-update-clause "vocabulary_id" vid "user_id" uid)
                del-result (jdbc/delete! (get-ds)
                                         :vocabulary_user_permissions
                                         del-clause)]
            (if (= 1 (first del-result))
              (sd/response_ok old-data)
              (sd/response_failed "Could not delete vocabulary user permission" 406)))
          (sd/response_not_found "No such vocabulary user permission."))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_list-vocab-group-perms [req]
  (let [id (-> req :parameters :path :id)
        result (sd/query-eq-find-all 
                :vocabulary_group_permissions 
                :vocabulary_id id)]
    (sd/response_ok result)))

(defn handle_get-vocab-group-perms [req]
  (let [id (-> req :parameters :path :id)
        gid (-> req :parameters :path :group_id)]
    (if-let [result (sd/query-eq2-find-one
                     :vocabulary_group_permissions
                     :vocabulary_id id
                     :group_id gid)]
      (sd/response_ok result)
      (sd/response_not_found "No such vocabulary group permission."))))

(defn handle_create-vocab-group-perms [req]
  (try
    (catcher/with-logging {}
      (let [vid (-> req :parameters :path :id)
            gid (-> req :parameters :path :group_id)
            data (-> req :parameters :body)
            ins-data (assoc data
                            :vocabulary_id vid
                            :group_id gid)
            ins-result (jdbc/insert! (get-ds)
                                     :vocabulary_group_permissions
                                     ins-data)]
        (if-let [result (first ins-result)]
          (sd/response_ok result)
          (sd/response_failed "Could not create vocabulary group permission" 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-vocab-group-perms [req]
  (try
    (catcher/with-logging {}
      (let [vid (-> req :parameters :path :id)
            gid (-> req :parameters :path :group_id)]
        (if-let [old-data (sd/query-eq2-find-one
                           :vocabulary_group_permissions
                           :vocabulary_id vid
                           :group_id gid)]
          (let [upd-data (-> req :parameters :body)
                upd-clause (sd/sql-update-clause
                            "vocabulary_id" vid
                            "group_id" gid)
                upd-result (jdbc/update! (get-ds)
                                         :vocabulary_group_permissions
                                         upd-data upd-clause)]
            (if (= 1 (first upd-result))
              (sd/response_ok (sd/query-eq2-find-one
                               :vocabulary_group_permissions
                               :vocabulary_id vid
                               :group_id gid))
              (sd/response_failed "Could not update vocabulary group permission" 406)))
          (sd/response_not_found "No such vocabulary group permission"))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-vocab-group-perms [req]
  (try
    (catcher/with-logging {}
      (let [vid (-> req :parameters :path :id)
            gid (-> req :parameters :path :group_id)]
        (if-let [old-data (sd/query-eq2-find-one
                           :vocabulary_group_permissions
                           :vocabulary_id vid
                           :group_id gid)]
          (let [del-clause (sd/sql-update-clause
                            "vocabulary_id" vid
                            "group_id" gid)
                del-result (jdbc/delete!
                            (get-ds)
                            :vocabulary_group_permissions
                            del-clause)]
            (if (= 1 (first del-result))
              (sd/response_ok old-data)
              (sd/response_failed "Could not delete vocabulary group permission" 406)))
          (sd/response_not_found "No such vocabulary group permission."))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_list-vocab-perms [req]
  (let [id (-> req :parameters :path :id)
        resource-perms (sd/query-eq-find-one
                        :vocabularies :id id)
        user-perms (sd/query-eq-find-all
                    :vocabulary_user_permissions
                    :vocabulary_id id)
        group-perms (sd/query-eq-find-all
                     :vocabulary_group_permissions
                     :vocabulary_id id)
        result {:vocabulary (select-keys resource-perms
                                         [:id
                                         :enabled_for_public_view 
                                         :enabled_for_public_use])
                :users user-perms
                :groups group-perms}
        ]
    (sd/response_ok result))
  )
;### Debug ####################################################################
;(debug/debug-ns *ns*)
