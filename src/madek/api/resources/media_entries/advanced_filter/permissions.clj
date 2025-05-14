(ns madek.api.resources.media-entries.advanced-filter.permissions
  (:require
   [honey.sql.helpers :as sql]
   [madek.api.utils.helper :refer [gen-from-order-by]]
   [madek.api.utils.helper :refer [to-uuid]]))

(defn- delegation-ids-subquery [user_id]
  {:union
   [(-> (sql/select :delegation_id)
        ;(sql/from :delegations_groups)
        (gen-from-order-by :delegations_groups)
        (sql/where [:in :delegations_groups.group_id (->
                                                      (sql/select :group_id)
                                                      ;(sql/from :groups_users)
                                                      (gen-from-order-by :groups_users)

                                                      (sql/where [:= :groups_users.user_id (to-uuid user_id)]))]))

    (-> (sql/select :delegation_id)
        ;(sql/from :delegations_users)
        (gen-from-order-by :delegations_users)
        (sql/where [:= :delegations_users.user_id (to-uuid user_id)]))]})

;(defn- api-client-authorized-condition [perm id]
;  [:or
;   [:= (keyword (str "media_entries." perm)) true]
;   [:exists (-> (sql/select true)
;                (sql/from [:media_entry_api_client_permissions :meacp])
;                (sql/where [:= :meacp.media_entry_id :media_entries.id])
;                (sql/where [:= (keyword (str "meacp." perm)) true])
;                (sql/where [:= :meacp.api_client_id id]))]])

(defn- group-permission-exists-condition [perm id]
  [:exists (-> (sql/select true)
               (sql/from [:media_entry_group_permissions :megp])
               (sql/where [:= :megp.media_entry_id :media_entries.id])
               (sql/where [:= (keyword (str "megp." perm)) true])
               (sql/where [:= :megp.group_id (to-uuid id)]))])

(defn- user-permission-exists-condition [perm id]
  [:exists (-> (sql/select true)
               (sql/from [:media_entry_user_permissions :meup])
               (sql/where [:= :meup.media_entry_id :media_entries.id])
               (sql/where [:= (keyword (str "meup." perm)) true])
               (sql/where [:= :meup.user_id (to-uuid id)]))])

(defn- group-permission-for-user-exists-condition [perm id]
  [:exists (-> (sql/select true)
               (sql/from [:media_entry_group_permissions :megp])
               (sql/where [:= :megp.media_entry_id :media_entries.id])
               (sql/where [:= (keyword (str "megp." perm)) true])
               (sql/join :groups
                         [:= :groups.id :megp.group_id])
               (sql/join [:groups_users :gu]
                         [:= :gu.group_id :groups.id])
               (sql/where [:= :gu.user_id (to-uuid id)]))])

(defn- user-authorized-condition [perm id]
  [:or
   [:= (keyword (str "media_entries." perm)) true]
   [:= :media_entries.responsible_user_id (to-uuid id)]
   [:in :media_entries.responsible_delegation_id (delegation-ids-subquery id)]
   (user-permission-exists-condition perm id)
   (group-permission-for-user-exists-condition perm id)])

(defn- user-authorized-condition-edit-md [perm id]
  [:or
   ;[:= (keyword (str "media_entries." perm)) true]
   [:= :media_entries.responsible_user_id (to-uuid id)]
   [:in :media_entries.responsible_delegation_id (delegation-ids-subquery id)]
   (user-permission-exists-condition perm id)
   (group-permission-for-user-exists-condition perm id)])

(defn- user-authorized-condition-edit-perms [perm id]
  [:or
   ;[:= (keyword (str "media_entries." perm)) true]
   [:= :media_entries.responsible_user_id (to-uuid id)]
   [:in :media_entries.responsible_delegation_id (delegation-ids-subquery id)]
   (user-permission-exists-condition perm id)
   ;(group-permission-for-user-exists-condition perm id)
   ])

(defn- filter-by-permission-for-auth-entity [sqlmap permission authenticated-entity]
  (case (:type authenticated-entity)
    "User" (sql/where sqlmap (user-authorized-condition
                              permission (:id authenticated-entity)))
    ; TODO token
    ; TODO session
    ;"ApiClient" (sql/where sqlmap (api-client-authorized-condition
    ;                                      permission (:id authenticated-entity)))
    (throw (ex-info (str "Filtering for " permission " requires a signed-in entity.")
                    {:status 422}))))

(defn filter-by-query-params [sqlmap query-params authenticated-entity]

  (doseq [true_param ["me_get_full_size"
                      "me_get_metadata_and_previews"
                      "me_edit_metadata"
                      "me_edit_permissions"]]
    (when (contains? query-params (keyword true_param))
      (when (not= (get query-params (keyword true_param)) true)
        (throw (ex-info (str "Value of " true_param " must be true when present.")
                        {:status 422})))))

  (cond-> sqlmap

    (:public_get_metadata_and_previews query-params)
    (sql/where [:= :media_entries.get_metadata_and_previews true])

    (:public_get_full_size query-params)
    (sql/where [:= :media_entries.get_full_size true])

    (= (:me_get_full_size query-params) true)
    (filter-by-permission-for-auth-entity "get_full_size" authenticated-entity)

    (= (:me_get_metadata_and_previews query-params) true)
    (filter-by-permission-for-auth-entity "get_metadata_and_previews" authenticated-entity)

    (= (:me_edit_metadata query-params) true)
      ;(filter-by-permission-for-auth-entity "edit_metadata" authenticated-entity)
    (sql/where (user-authorized-condition-edit-md
                "edit_metadata" (:id authenticated-entity)))

    (= (:me_edit_permissions query-params) true)
      ;(filter-by-permission-for-auth-entity "edit_permissions" authenticated-entity)
    (sql/where (user-authorized-condition-edit-perms
                "edit_permissions" (:id authenticated-entity)))))

(defn- sql-merge-where-permission-spec [sqlmap permission-spec]
  (case (:key permission-spec)
    "public"
    (-> sqlmap
        (sql/where
         [:=
          :media_entries.get_metadata_and_previews
          (case (:value permission-spec)
            "true" true
            "false" false
            :else (throw
                   (ex-info
                    (str "Invalid filter for \"public\" permission!")
                    {:status 422})))]))

    "responsible_user"
    (-> sqlmap
        (sql/where [:=
                    :media_entries.responsible_user_id
                    (to-uuid (:value (to-uuid permission-spec)))]))

    "entrusted_to_user"
    (-> sqlmap
        (sql/where
         [:or
          (user-permission-exists-condition "get_metadata_and_previews"
                                            (:value permission-spec))
          (group-permission-for-user-exists-condition "get_metadata_and_previews"
                                                      (:value permission-spec))]))

    "entrusted_to_group"
    (-> sqlmap
        (sql/where
         (group-permission-exists-condition "get_metadata_and_previews"
                                            (:value permission-spec))))))

(defn sql-filter-by [sqlmap permission-specs]
  (if-not (empty? permission-specs)
    (reduce sql-merge-where-permission-spec
            sqlmap
            permission-specs)
    sqlmap))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'filter-by-permissions)
;(debug/wrap-with-log-debug #'build-query)
