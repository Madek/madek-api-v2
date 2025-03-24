(ns madek.api.utils.pagination-new
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.utils.request :refer [query-params]]
   ;[leihs.inventory.server.utils.core :refer [single-entity-get-request?]]
   [next.jdbc.sql :as jdbc]
   [ring.middleware.accept]
   [ring.util.response :refer [bad-request response status]]))


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
        size (:size query-params)

        res (if (or (nil? page) (nil? size))
                 nil
                 {:page (or page CONST_DEFAULT_PAGE)
                  :size (or size CONST_DEFAULT_SIZE)})

        ]
    res))

(defn pagination-response
  ([request base-query pagination ]
   (pagination-response request base-query pagination nil))

  ([request base-query pagination post-data-fnc]
   (let [{:keys [page size]} pagination
         tx (:tx request)]
     (create-paginated-response base-query tx size page post-data-fnc))))

(defn pagination-handler
  "To receive a paginated response, the request must contain the query parameters `page` and `size`."
  ;[request base-query with-pagination?]
  ;[request base-query with-pagination? pagination]
  [request base-query ]

  (let [
        ;{:keys [page size]} (fetch-pagination-params-raw request)
        tx (:tx request)

        pagination (fetch-pagination-params-raw-or-nil request)
        with-pagination? (not (nil? pagination))

        ]

    ;(if with-pagination?
    ;      (pagination/create-paginated-response base-query tx (:size pagination) (:page pagination))
    ;      (jdbc/query (:tx req) base-query))

    (cond
      (and (= with-pagination? false) (single-entity-get-request? request))
    ;  ;(jdbc/query (:tx request) (-> base-query sql-format))
    ;
    ;  (and (or (nil? with-pagination?) with-pagination?) (or (some? page) (some? size)))
    ;  (pagination-response request base-query)
    ;
      (and with-pagination?) (pagination-response request base-query pagination)

      :else (jdbc/query (:tx request) (-> base-query sql-format)))

    ))
