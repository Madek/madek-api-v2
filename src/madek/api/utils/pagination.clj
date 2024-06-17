(ns madek.api.utils.pagination
  (:require
   [clojure.spec.alpha :as sa]
   [clojure.walk :refer [keywordize-keys]]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.helper :refer [parse-specific-keys]]
   [madek.api.utils.helper :refer [str-to-int]]
   [schema.core :as s]))

(def DEFAULT_LIMIT 1000)

(defn page-number [params]
  (let [page (or (-> params :page) 0)]
    (if (> page 0) page 0)))

(defn page-count [params]
  (let [count (or (-> params :count) DEFAULT_LIMIT)]
    (if (> count 0) count DEFAULT_LIMIT)))

(defn compute-offset [params]
  (* (page-count params) (page-number params)))

(defn sql-offset-and-limit [query params]
  "Caution: zero-based page numbers"
  (let [defaults {:page 0 :count DEFAULT_LIMIT}
        params (merge defaults params)
        params (parse-specific-keys params defaults)
        off (compute-offset params)
        limit (page-count params)]
    (-> query
        (sql/offset off)
        (sql/limit limit))))

(defn next-page-query-query-params [query-params]
  (let [query-params (keywordize-keys query-params)
        i-page (page-number query-params)]
    (assoc query-params
           :page (+ i-page 1))))

;### Debug ####################################################################

(defn swagger-ui-pagination
  "Returns a map with the swagger-ui parameters for pagination.

   Map-Attributes:
   - :page-val.
   - :size-val
  "

  ([]
   (swagger-ui-pagination {}))

  ([config]
   (let [page-val (str (get config :page-val "0"))
         size-val (str (get config :size-val "5"))
         page-req (get config :size-val false)
         size-req (get config :size-val false)]
     {:parameters [{:name "page"
                    :in "query"
                    :description (str "Page number, defaults to " page-val)
                    :required page-req
                    :value page-val}
                   {:name "size"
                    :in "query"
                    :description (str "Number of items per page, defaults to " size-val)
                    :required size-req
                    :value size-val}]
      :produces "application/json"})))

(defn pagination-handler
  "Required query-fields: page & size

    Workflow:
      - rename :page to :count (needed for internal handling).
      - cast :page & :size to int.
      - validate :page & :size (exception returns response with status-code=400 and validation-details).
    "

  ([]
   (pagination-handler
    {(s/optional-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
     (s/optional-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")}))

  ([schema]
   (fn [handler]
     (fn [request]
       (try
         (let [cast-fnc (fn [m]
                          (println ">o> [cast]" m)
                          (reduce-kv
                           (fn [acc k v]
                             (assoc acc k
                                    (cond
                                      (= k :page) (str-to-int v v)
                                      (= k :size) (str-to-int v v)
                                      :else v)))
                           {}
                           m))

               rename-keys-fnc (fn [m key-map]
                                 (println ">o> [rename]" m key-map)
                                 (reduce-kv
                                  (fn [acc k v]
                                    (let [new-key (get key-map k k)]
                                      (assoc acc new-key v)))
                                  {}
                                  m))

               ;casted-vals (cast-fnc (-> request :params))
               ;p (println ">o> casted-vals=" casted-vals)

               request (let [casted-vals (cast-fnc (-> request :params))]
                         (let [request (assoc-in request [:params] casted-vals)]
                           (assoc-in request [:parameters :query] casted-vals)))

               p (println ">o> ph1.req.params" (get request :params))
               p (println ">o> ph1.req.query" (get request :parameters :query))
               p (println ">o> ph1.req.is_admin" (get request :is_admin))

               params (-> request :params)


               ;p (println ">o> params1=" params)
               ;p (println ">o> params1=" params)

               _ (s/validate schema params)


                 p (println ">o> ph.req.params2" (get request :params))
               ]

           (let [key-map {:size :count}
                 request (let [params-renamed (rename-keys-fnc params key-map)]
                           (let [request (assoc-in request [:params] params-renamed)]
                             (assoc-in request [:parameters :query] params-renamed)))

                 p (println ">o> ph.req.params" (get request :params))
                 p (println ">o> ph.req.query" (get request :parameters :query))
                 p (println ">o> ph.req.is_admin" (get request :is_admin))
                 ]

             (handler request)))
         (catch Exception ex
           (sd/response_bad_request (str ">o> Invalid query parameters: " (.getMessage ex)) (.getData ex))))))))

(defn pagination-optional-handler
  ([]
   (pagination-handler
    {(s/optional-key :page) s/Int
     (s/optional-key :size)  s/Int })))

;{(s/optional-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
;     (s/optional-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")})))

;(s/defschema ItemQueryParams
;  {:page (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
;   :size (s/constrained s/Int #(>= % 1) "Must be a positive integer")})

 (s/defschema ItemQueryParams
  {(s/optional-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
     (s/optional-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
