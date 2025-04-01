; madek/api/utils/pagination_new.clj
(ns madek.api.utils.pagination-new
  (:require
   ;[clojure.java.io :as io]
   ;[clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.utils.request :refer [query-params]]
   ;[leihs.inventory.server.utils.core :refer [single-entity-get-request?]]
   [next.jdbc.sql :as jdbc]
   [ring.middleware.accept]))

(def CONST_DEFAULT_PAGE 1)
(def CONST_DEFAULT_SIZE 10)

(defn single-entity-get-request? [request]
  (let [method (:request-method request)
        uri (:uri request)
        uuid-regex #"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$"]
    (and (= method :get)
         (boolean (re-find uuid-regex uri)))))

(defn- fetch-total-count [base-query tx]
  (-> (sql/select [[:raw "COUNT(*)"] :total_count])
      (sql/from [[base-query] :subquery])
      sql-format
      (->> (jdbc/query tx))
      first
      :total_count))

(defn- fetch-paginated-rows [base-query tx per_page offset]
  (let [paginated-query (-> base-query
                            (sql/limit per_page)
                            (sql/offset offset)
                            sql-format
                            (->> (jdbc/query tx)))]
    (mapv identity paginated-query)))

(defn create-paginated-response
  ([base-query tx size page]
   (create-paginated-response base-query tx size page nil))

  ([base-query tx size page post-data-fnc]
   (let [total-rows (fetch-total-count base-query tx)
         total-pages (int (Math/ceil (/ total-rows (float size))))
         offset (* (dec page) size)
         paginated-products (fetch-paginated-rows base-query tx size offset)
         pagination-info {:total_rows total-rows
                          :total_pages total-pages
                          :page page
                          :size size}

         paginated-products (if (nil? post-data-fnc) paginated-products
                                (post-data-fnc paginated-products))]
     {:data paginated-products
      :pagination pagination-info})))

(defn fetch-pagination-params
  "Fetch pagination parameters from the request, default: page=1, size=10"
  [request]
  (let [query-params (query-params request)
        page (Integer. (or (:page query-params) CONST_DEFAULT_PAGE))
        size (Integer. (or (:size query-params) CONST_DEFAULT_SIZE))]
    {:page page
     :size size}))

(defn fetch-pagination-params-raw [request]
  (let [query-params (query-params request)
        page (:page query-params)
        size (:size query-params)]
    {:page page
     :size size}))
(defn fetch-pagination-params-raw-or-nil [request]
  (let [query-params (query-params request)
        page (:page query-params)
        size (:size query-params)]
    (if (and (nil? page) (nil? size))
      nil
      {:page (Integer. (or page CONST_DEFAULT_PAGE))
       :size (Integer. (or size CONST_DEFAULT_SIZE))})))

(defn pagination-response
  ([request base-query pagination]
   (pagination-response request base-query pagination nil))

  ([request base-query pagination after-fnc]
   (let [{:keys [page size]} pagination
         tx (:tx request)]
     (create-paginated-response base-query tx size page after-fnc))))

(defn pagination-handler
  "To receive a paginated response, the request must contain the query parameters `page` or `size`."
  ([request base-query]
   (pagination-handler request base-query nil))

  ([request base-query wrap-name-of-result]
   (pagination-handler request base-query wrap-name-of-result nil))

  ([request base-query wrap-name-of-result after-fnc]
   (let [tx (:tx request)
         pagination (fetch-pagination-params-raw-or-nil request)
         with-pagination? (some? pagination)

         after-fnc (if (nil? after-fnc)
                     (fn [res] res)
                     after-fnc)]

     (cond
       (and (not with-pagination?) (single-entity-get-request? request))
       (after-fnc (jdbc/query tx (sql-format base-query)))

       with-pagination?
       (pagination-response request base-query pagination after-fnc)

       :else (let [result (jdbc/query tx (sql-format base-query))
                   result (after-fnc result)]
               (if wrap-name-of-result
                 {(keyword wrap-name-of-result) result}
                 result))))))


(defn is-with-pagination? [request]
  (let [{:keys [page size]} (fetch-pagination-params-raw request)]
    (and (some? page) (some? size))))
