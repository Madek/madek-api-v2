(ns madek.api.resources.meta-keys.meta-key
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc :as jdbc]))

;; TODO: not in use
;(defn add-fields-for-default-locale
;  [result tx]
;  (add-field-for-default-locale
;   "label" (add-field-for-default-locale
;            "description" (add-field-for-default-locale
;                           "hint" result tx) tx)tx))

(defn- get-io-mappings
  [id tx]
  (let [query (-> (sql/select :key_map, :io_interface_id)
                  (sql/from :io_mappings)
                  (sql/order-by [:meta_key_id :asc] [:io_interface_id :asc])
                  (sql/where [:= :io_mappings.meta_key_id id])
                  (sql-format))]
    (jdbc/execute! tx query)))

(defn- get-io-mappings-for-ids
  [ids tx]
  (if (empty? ids)
    []
    (let [query (-> (sql/select :meta_key_id :key_map :io_interface_id)
                    (sql/from :io_mappings)
                    (sql/order-by [:meta_key_id :asc] [:io_interface_id :asc])
                    (sql/where [:in :io_mappings.meta_key_id (vec ids)])
                    (sql-format))]
      (jdbc/execute! tx query))))

(defn- prepare-io-mappings-from
  [io-mappings]
  (let [groupped (group-by :io_interface_id io-mappings)]
    (mapv (fn [io-interface-id]
            {:id io-interface-id
             :keys (mapv (fn [row] {:key (:key_map row)})
                         (get groupped io-interface-id))})
          (keys groupped))))

(defn include-io-mappings
  [result id tx]
  (let [io-mappings (prepare-io-mappings-from (get-io-mappings id tx))]
    (assoc result :io_mappings io-mappings)))

(defn include-io-mappings-many
  "Attach `:io_mappings` to each meta-key (same shape as single GET / `/api`)."
  [meta-keys tx]
  (let [ids (mapv :id meta-keys)
        by-id (group-by :meta_key_id (get-io-mappings-for-ids ids tx))]
    (mapv (fn [mk]
            (assoc mk :io_mappings
                   (prepare-io-mappings-from (get by-id (:id mk) []))))
          meta-keys)))

(defn build-meta-key-query [id]
  (-> (sql/select :*)
      (sql/from :meta_keys)
      (sql/order-by [:vocabulary_id :asc] [:id :asc])
      (sql/where [:= :meta_keys.id id])
      (sql-format)))

;(defn get-meta-key [request]
;  (let [id (-> request :parameters :path :id)
;        query (build-meta-key-query id)]
;    (if-let [meta-key (first (jdbc/query (:tx request) query))]
;      (let [result (include-io-mappings
;                    (sd/remove-internal-keys
;                     (add-fields-for-default-locale meta-key)) id)]
;        (sd/response_ok result))
;      (sd/response_failed "Meta-Key could not be found!" 404))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
