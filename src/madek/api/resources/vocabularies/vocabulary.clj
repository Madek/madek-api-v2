(ns madek.api.resources.vocabularies.vocabulary
  (:require
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.vocabularies.permissions :as permissions]
   [next.jdbc :as jdbc]))

(defn transform_ml [vocab]
  (assoc vocab
         :labels (sd/transform_ml (:labels vocab))
         :descriptions (sd/transform_ml (:descriptions vocab))))

;; TODO: not in use?
;(defn- add-fields-for-default-locale
;  [result]
;  (add-field-for-default-locale
;   "label" (add-field-for-default-locale
;            "description" result)))

(defn- where-clause
  [id user-id tx]
  (let [public [:= :vocabularies.enabled_for_public_view true]
        id-match [:= :vocabularies.id id]]
    (if user-id
      (let [vocabulary-ids (permissions/accessible-vocabulary-ids user-id tx)]
        [:and
         (if-not (empty? vocabulary-ids)
           [:or
            public
            [:in :vocabularies.id vocabulary-ids]]
           public)
         id-match])
      [:and public id-match])))

(defn build-vocabulary-query [id user-id tx]
  (-> (sql/select :*)
      (sql/from :vocabularies)
      (sql/where (where-clause id user-id tx))
      (sql-format)))

; TODO for admin do not remove internal keys (admin_comment)
; TODO add flag for default locale
(defn get-vocabulary [request]
  (let [id (-> request :parameters :path :id)
        user-id (-> request :authenticated-entity :id)
        tx (:tx request)
        query (build-vocabulary-query id user-id tx)
        is_admin_endpoint (str/includes? (-> request :uri) "/admin/")
        db-result (jdbc/execute-one! tx query)
        result (if (not (nil? db-result))
                 (if is_admin_endpoint
                   (-> db-result
                       transform_ml)
                   (-> db-result
                       transform_ml
                       sd/remove-internal-keys)))]
    (if result
      (sd/response_ok result)
      (sd/response_failed "Vocabulary could not be found!" 404))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
