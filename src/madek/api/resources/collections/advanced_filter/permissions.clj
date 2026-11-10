(ns madek.api.resources.collections.advanced-filter.permissions
  (:require
   [honey.sql.helpers :as sql]
   [madek.api.resources.media-entries.advanced-filter.permissions :refer [delegation-ids-subquery]]
   [madek.api.utils.auth :as auth]
   [madek.api.utils.helper :refer [to-uuid]]))

;(defn- api-client-authorized-condition [perm id]
;  [:or
;   [:= (keyword (str "collections." perm)) true]
;   [:exists (-> (sql/select true)
;                (sql/from [:collection_api_client_permissions :cacp])
;                (sql/where [:= :cacp.collection_id :collections.id])
;                (sql/where [:= (keyword (str "cacp." perm)) true])
;                (sql/where [:= :cacp.api_client_id id]))]])

(defn- group-permission-exists-condition [perm id]
  [:exists (-> (sql/select true)
               (sql/from [:collection_group_permissions :cgp])
               (sql/where [:= :cgp.collection_id :collections.id])
               (sql/where [:= (keyword (str "cgp." perm)) true])
               (sql/where [:= :cgp.group_id id]))])

(defn- user-permission-exists-condition [perm id]
  [:exists (-> (sql/select true)
               (sql/from [:collection_user_permissions :cup])
               (sql/where [:= :cup.collection_id :collections.id])
               (sql/where [:= (keyword (str "cup." perm)) true])
               (sql/where [:= :cup.user_id id]))])

(defn- group-permission-for-user-exists-condition [perm id]
  [:exists (-> (sql/select true)
               (sql/from [:collection_group_permissions :cgp])
               (sql/where [:= :cgp.collection_id :collections.id])
               (sql/where [:= (keyword (str "cgp." perm)) true])
               (sql/join :groups
                         [:= :groups.id :cgp.group_id])
               (sql/join [:groups_users :gu]
                         [:= :gu.group_id :groups.id])
               (sql/where [:= :gu.user_id id]))])

(defn- user-authorized-condition [perm id]
  [:or
   ;[:= (keyword (str "collections." perm)) true]
   (when (= perm "get_metadata_and_previews")
     [:= (keyword (str "collections." perm)) true])

   [:= :collections.responsible_user_id id]

   (when (or (= perm "get_metadata_and_previews")
             (= perm "edit_metadata_and_relations")
             (= perm "edit_permissions"))
     (user-permission-exists-condition perm id))

   (when (or (= perm "get_metadata_and_previews")
             (= perm "edit_metadata_and_relations"))
     (group-permission-for-user-exists-condition perm id))])

(defn- user-view-condition [user-id]
  [:or
   [:= :collections.get_metadata_and_previews true]
   [:= :collections.responsible_user_id user-id]
   [:in :collections.responsible_delegation_id (delegation-ids-subquery user-id)]
   (user-permission-exists-condition "get_metadata_and_previews" user-id)
   (group-permission-for-user-exists-condition "get_metadata_and_previews" user-id)])

(defn filter-by-view-permission
  "Restricts the query to collections the client may view: public ones, and
  for signed-in users additionally those they are responsible for or have
  view permission on. Admins are only unrestricted on admin routes."
  [sqlmap {:keys [authenticated-entity] :as request}]
  (cond
    (auth/admin-route-authorized? request) sqlmap
    (= "User" (:type authenticated-entity)) (sql/where sqlmap (user-view-condition
                                                               (to-uuid (:id authenticated-entity))))
    :else (sql/where sqlmap [:= :collections.get_metadata_and_previews true])))

(defn- filter-by-permission-for-auth-entity [sqlmap permission authenticated-entity]
  (case (:type authenticated-entity)
    "User" (sql/where sqlmap (user-authorized-condition
                              permission (:id authenticated-entity)))
    ; TODO session
    ;"ApiClient" (sql/merge-where sqlmap (api-client-authorized-condition
    ;                                      permission (:id authenticated-entity)))
    (throw (ex-info (str "Filtering for " permission " requires a signed-in entity.")
                    {:status 422}))))

(defn filter-by-query-params [sqlmap query-params authenticated-entity]
  (doseq [true_param ["me_get_metadata_and_previews"
                      "me_edit_metadata_and_relations"
                      "me_edit_permission"]]
    (when (contains? query-params (keyword true_param))
      (when (not= (get query-params (keyword true_param)) true)
        (throw (ex-info (str "Value of " true_param " must be true when present.")
                        {:status 422})))))
  (cond-> sqlmap
    (:public_get_metadata_and_previews query-params)
    (sql/where [:= :collections.get_metadata_and_previews true])
    (= (:me_get_metadata_and_previews query-params) true)
    (filter-by-permission-for-auth-entity "get_metadata_and_previews" authenticated-entity)
    ; 
    (= (:me_edit_metadata_and_relations query-params) true)
    (filter-by-permission-for-auth-entity "edit_metadata_and_relations" authenticated-entity)
    ;
    (= (:me_edit_permission query-params) true)
    (filter-by-permission-for-auth-entity "edit_permission" authenticated-entity)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'filter-by-permissions)
;(debug/wrap-with-log-debug #'build-query)
