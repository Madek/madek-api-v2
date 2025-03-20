(ns madek.api.resources.users.index
  (:require
   [clojure.spec.alpha :as sa]
   [cuerdas.core :refer [empty-or-nil?]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.authorization :refer [wrap-authorized-user]]
   [madek.api.pagination :as pagination]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.users.common :as common]
   [madek.api.resources.users.get :as get-user]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.helper :refer [f]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]))

(defn handle-email-clause [thread-obj params]
  (if-let [email (:email params)]
    (-> thread-obj
        (sql/where [:= :email email]))
    thread-obj))

(defn handle-person-clause [thread-obj params]
  (if-let [person-id (:person_id params)]
    (let [person-uid (parse-uuid person-id)]
      (if (nil? person-uid)
        thread-obj
        (-> thread-obj
            (sql/where [:= :person_id person-uid]))))
    thread-obj))

(defn search [query {term :search_term} col-name]
  (let [kw-col-name (keyword col-name)
        kw-similarity (str col-name "_similarity")]
    (if (empty-or-nil? term)
      query
      (-> query
          (dissoc :offset :limit)
          (sql/select [[:word_similarity term kw-col-name] kw-similarity])
          (update-in [:order-by] #(into [[kw-similarity :desc]] %))
          (sql/limit 15)
          (sql/where [:> [:word_similarity term kw-col-name] 0])))))

(defn handler
  "Get an index of the users. Query parameters are pending to be implemented."
  [{{params :query} :parameters tx :tx :as req}]
  (let [query (-> common/base-query
                  (pagination/sql-offset-and-limit params)
                  (handle-email-clause params)
                  (handle-person-clause params)
                  (search params 'searchable)
                  (sql-format :inline false))
        res (->> query
                 (jdbc/execute! tx)
                 ;(map #(dissoc % :searchable_similarity))
                 (assoc {} :users))
        res (sd/transform_ml_map res)]
    (sd/response_ok res)))

;(sa/def ::users-query-def (sa/keys :opt-un [::sp/email ::sp/page ::sp/size]))
(sa/def ::users-query-def (sa/keys :opt-un [::sp/email ::sp/search_term ::sp/size]))

(def route
  {:summary (sd/sum_adm (f "Get list of users ids." "no-list"))
   :description "Get list of users ids."
   :handler handler
   :middleware [wrap-authorize-admin!]
   :parameters {:query ::users-query-def}
   :coercion spec/coercion
   :responses {200 {:description "List of users ids."
                    :body ::get-user/users-body-resp-def}}})

(def user-route
  {:summary (sd/sum_adm (f "Get list of users ids." "no-list"))
   :description "Get list of users ids."
   :handler handler
   :middleware [wrap-authorized-user]
   :parameters {:query ::users-query-def}
   :coercion spec/coercion
   :responses {200 {:description "List of users ids."
                    :body ::get-user/users-body-resp-def}}})


