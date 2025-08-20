(ns madek.api.resources.meta-data.meta-datum
  (:require
   [cheshire.core :as json]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.keywords.index :as keywords]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.helper :refer [to-uuid]]
   [next.jdbc :as jdbc]
   [ring.util.response :as ring-response]
   [taoensso.timbre :refer [info]]))

;### people ###################################################################

; TODO meta-datum groups will be moved to people, no point in implementing this
; here and now, the following is a Hack so the server so it won't fail when
; groups are requested
(defn groups-with-ids [meta-datum]
  [])

(defn get-people-index [meta-datum tx]
  (let [query (-> (sql/select :people.*)
                  (sql/from :people)
                  (sql/order-by [:people.institutional_id :asc] [:people.id :asc])
                  (sql/join :meta_data_people [:= :meta_data_people.person_id :people.id])
                  (sql/where [:= :meta_data_people.meta_datum_id (:id meta-datum)])
                  sql-format)]
    (jdbc/execute! tx query)))

;### meta-datum ###############################################################

; TODO people/get-index, keywords/get-index is very un-intuitive,
; it has nothing to do with HTTP get-index which it suggest; =>
; delete all those namespaces and move the stuff over here, somthing like (def
; people-with-ids [meta-datum] ...  and so on

(defn- prepare-meta-datum [meta-datum tx]
  (merge (select-keys meta-datum [:id :meta_key_id :type])
         {:value (let [meta-datum-type (:type meta-datum)]
                   (case meta-datum-type
                     "MetaDatum::JSON" (json/generate-string (:json meta-datum) {:escape-non-ascii false})
                ; TODO meta-data json value transport Q as string
                     "MetaDatum::Text" (:string meta-datum)
                     "MetaDatum::TextDate" (:string meta-datum)
                     (map #(select-keys % [:id])
                          ((case meta-datum-type
                             "MetaDatum::Keywords" keywords/get-index
                             "MetaDatum::People" get-people-index)
                           meta-datum tx))))}
         (->> (select-keys meta-datum [:media_entry_id :collection_id])
              (filter (fn [[k v]] v))
              (into {}))))

(defn get-meta-datum [request]
  (let [meta-datum (:meta-datum request)
        tx (:tx request)
        result (prepare-meta-datum meta-datum tx)]
    #_(info "get-meta-datum" "\nresult\n" result)
    (sd/response_ok result)))

; TODO Q? why no response status
(defn get-meta-datum-data-stream [request]
  (let [meta-datum (:meta-datum request)
        tx (:tx request)
        content-type (case (-> request :meta-datum :type)
                       "MetaDatum::JSON" "application/json; charset=utf-8"
                       "text/plain; charset=utf-8")
        value (-> meta-datum (prepare-meta-datum tx) :value)]
    (cond
      (nil? value) {:status 422}
      (str value) (-> {:body value}
                      (ring-response/header "Content-Type" content-type))
      :else {:body value})))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
