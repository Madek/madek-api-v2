(ns madek.api.utils.pagination
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [honey.sql.helpers :as sql]
   [madek.api.utils.helper :refer [parse-specific-keys str-to-int to-uuid]]
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

(defn sql-offset-and-limit [query params] "Caution: zero-based page numbers"
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

(defn ^:deprecated create-swagger-ui-param

  "Returns a map with the swagger-ui parameters.

 Map-Attributes:
 - :name :in :description :required :value
"
  ([]
   (create-swagger-ui-param {}))
  ([config]
   (let [default-config {:name "default-name"
                         :in "query"
                         :description "default description"
                         :required false
                         :value "default-value"}
         final-config (merge default-config config)]

     {:name (get final-config :name)
      :in (get final-config :in)
      :description (get final-config :description)
      :required (get final-config :required)
      :value (get final-config :value)})))

(defn merge-parameters [data new-parameters]
  (update data :parameters into new-parameters))

(defn ^:deprecated swagger-ui-pagination
  "Returns a map with the swagger-ui parameters for pagination.

   Map-Attributes:
   - :page-val / page-req
   - :size-val / size-req
   - :produces
  "

  ([]
   (swagger-ui-pagination {}))

  ([config]
   (swagger-ui-pagination config []))

  ([config additional-params]
   (let [page-val (str (get config :page-val "0"))
         size-val (str (get config :size-val (str DEFAULT_LIMIT)))
         page-req (get config :size-val false)
         size-req (get config :size-val false)
         produces (get config :produces "application/json")

         merged (merge-parameters {:parameters [{:name "page"
                                                 :in "query"
                                                 :description (str "Page number, defaults to " page-val)
                                                 :required page-req
                                                 :value page-val}
                                                {:name "size"
                                                 :in "query"
                                                 :description (str "Number of items per page, defaults to " size-val)
                                                 :required size-req
                                                 :value size-val}]
                                   :produces produces}
                                  additional-params)]
     merged)))

(defn ^:deprecated pagination-validation-handler
  "Required query-fields: page & size

    Workflow:
      - rename :page to :count (needed for internal handling).
      - cast :page & :size to int.
      - validate :page & :size (exception returns response with status-code=400 and validation-details).
    "

  ([]
   (pagination-validation-handler
    {(s/optional-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
     (s/optional-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")}))

  ([schema]
   (pagination-validation-handler schema {}))

  ([schema casting-map]
   (fn [handler]
     (fn [request]
       (let [cast-value (fn [k v casting-map]
                          (cond (not (contains? casting-map k)) v
                                (= (get casting-map k) "uuid") (to-uuid v)
                                :else v))

             cast-fnc (fn [m]
                        (reduce-kv
                         (fn [acc k v]
                           (assoc acc k
                                  (cond
                                    (= k :page) (str-to-int v v)
                                    (= k :size) (str-to-int v v)
                                    :else (cast-value k v casting-map))))
                         {}
                         m))

             rename-keys-fnc (fn [m key-map]
                               (reduce-kv
                                (fn [acc k v]
                                  (let [new-key (get key-map k k)]
                                    (assoc acc new-key v)))
                                {}
                                m))

             request (let [request (assoc-in request [:params] (cast-fnc (-> request :params)))]
                       (assoc-in request [:parameters :query] (cast-fnc (-> request :parameters :query))))
             params (merge (-> request :parameters :query) (-> request :parameters :body))
             _ (s/validate schema params)]

         (let [key-map {:size :count}
               request (let [params-renamed (rename-keys-fnc params key-map)]
                         (let [request (assoc-in request [:params] params-renamed)]
                           (assoc-in request [:parameters :query] params-renamed)))

               request (assoc-in request [:parameters :query] (merge
                                                               (-> request :parameters :query)
                                                               (-> request :params)))]
           (handler request)))))))

(s/defschema optional-pagination-params
  {(s/optional-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
   (s/optional-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
