(ns madek.api.utils.pagination
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [honey.sql.helpers :as sql]
   [madek.api.resources.shared.core :as sd]
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






(defn create-swagger-ui-param

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
         final-config (merge default-config config)
         p (println ">o> final-config=" final-config)
         ]

     {:name (get final-config :name)
      :in (get final-config :in)
      :description (get final-config :description)
      :required (get final-config :required)
      :value (get final-config :value)}

     )))






(defn append-parameter [data new-param]
  (update data :parameters conj new-param))


(defn merge-parameters [data new-parameters]
  (update data :parameters into new-parameters))

(defn swagger-ui-pagination
  "Returns a map with the swagger-ui parameters for pagination.

   Map-Attributes:
   - :page-val / page-req
   - :size-val / size-req
   - :produces
  "

  ([]
   (swagger-ui-pagination {}))


  ([config]
   (swagger-ui-pagination config [])
   )

  ([config additional-params]
   (let [page-val (str (get config :page-val "0"))
         size-val (str (get config :size-val "5"))
         page-req (get config :size-val false)
         size-req (get config :size-val false)
         produces (get config :produces "application/json")

         p (println ">o> additional-params=" additional-params)

         merged (merge-parameters {:parameters [{:name "page"
                                                 :in "query"
                                                 :description (str "Page number, defaults to " page-val)
                                                 :required page-req
                                                 :value page-val}
                                                {:name "size"
                                                 :in "query"
                                                 :description (str "Number of items per page, defaults to " size-val)
                                                 :required size-req
                                                 :value size-val}

                                                ;;; TODO: example how to declare uuid-type-formatting
                                                ;{:name "test-uuid"
                                                ; :in "query"
                                                ; ;:description (str "Number of items per page, defaults to " size-val)
                                                ; :required size-req
                                                ; :value "123e4567-e89b-12d3-a456-426614174000"
                                                ;
                                                ; :type "string"
                                                ; :format "uuid"
                                                ;
                                                ; }

                                                ]
                                   :produces produces}
                  additional-params)
         p (println ">o> merged=" merged)
         ]
     merged

     ))

  )






;(defn execute-fn [m key v]
;  (if-let [fn-vec (key m)]
;    (let [[fn-symbol & args] fn-vec
;          fn (resolve fn-symbol)
;          eval-args (mapv #(if (= % 'v) v %) args)]
;      (apply fn eval-args))
;    v))
;
;
;
;(defn cast-fnc
;
;  ([m] cast-fnc m {})
;
;  ([m my-map]
;   (println ">o> [cast]" m)
;   (reduce-kv
;     (fn [acc k v]
;       (assoc acc k
;              (cond
;                (contains? my-map k) (execute-fn my-map k v)
;                (= k :page) (str-to-int v v)
;                (= k :size) (str-to-int v v)
;                :else v)))
;     {}
;     m))
;
;  )



(defn pagination-handler
  "Required query-fields: page & size

    Workflow:
      - rename :page to :count (needed for internal handling).
      - cast :page & :size to int.
      - validate :page & :size (exception returns response with status-code=400 and validation-details).
    "


  ;([]
  ; (let [my-map {}]
  ;   (pagination-handler my-map)))
  ;
  ;([my-map]
  ; (let [my-map (if (nil? my-map) {} my-map)]
  ;   (pagination-handler
  ;     {(s/optional-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
  ;      (s/optional-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")}
  ;     my-map
  ;     )))


  ([]
   ;(let [my-map (if (nil? my-map) {} my-map)]
     (pagination-handler
       {(s/optional-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
        (s/optional-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")}
       ))
  ;)



  ([schema ]
   (fn [handler]
     (fn [request]
       ;(try
         (let [

               cast-fnc (fn [m]
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



               p (println ">o> params1.type!1!!!= params" (-> request :params))
               p (println ">o> params1.type!2!!! query=" (-> request :parameters :query))
               p (println ">o> params1.type!2!!! query=" (-> request :parameters :body))

               p (println ">o> -------------------------------\n\n")

               p (println ">o> params1.type!1!!! H col-id params" (type (:collection_id (-> request :params)))) ;; not converted
               p (println ">o> params1.type!2!!! H col-id query=" (type (:collection_id (-> request :parameters :query)))) ;; converted
               p (println ">o> params1.type!3!!! H uuid query=" (type (:test-uuid (-> request :parameters :query)))) ;; converted
               p (println ">o> params1.type!4!!! B my_coll body=" (type (:my_collection_id (-> request :parameters :body)))) ;; converted
               p (println ">o> params1.type!5!!! B my_publ body=" (type (:my_public_get_metadata_and_previews (-> request :parameters :body)))) ;; converted

               ;p (println ">o> -------------------------------\n\n")
               ;p (println ">o> params1.type!1!!! pa= params" (type (:page (-> request :params))))
               ;p (println ">o> params1.type!2!!! pa= query=" (type (:page (-> request :parameters :query))))
               ;
               ;p (println ">o> -------------------------------\n\n")
               ;p (println ">o> params1.type!1!!! si= params" (type (:size (-> request :params))))
               ;p (println ">o> params1.type!2!!! si= query=" (type (:size (-> request :parameters :query))))

               p (println ">o> -------------------------------\n\n")


               ;; original
               ;request (let [casted-vals (cast-fnc (-> request :params))]
               ;          (let [request (assoc-in request [:params] casted-vals)]
               ;            (assoc-in request [:parameters :query] casted-vals)))


               ;request
               ;(let [request (assoc-in request [:params] (cast-fnc (-> request :params) my-map))]
               ;  (assoc-in request [:parameters :query] (cast-fnc (-> request :parameters :query) my-map)))

               request
               (let [request (assoc-in request [:params] (cast-fnc (-> request :params) ))]
                 (assoc-in request [:parameters :query] (cast-fnc (-> request :parameters :query) )))


               p (println ">o> ph1.req.params" (get request :params))
               p (println ">o> ph1.req.query" (get request :parameters :query))
               p (println ">o> ph1.req.is_admin" (get request :is_admin))

               ;params (-> request :params)
               ;params (-> request :parameters :query)       ;; this contains already casted data

               ;; already casted data
               params (merge (-> request :parameters :query) (-> request :parameters :body))


               ;p (println ">o> params1=" params)
               p (println ">o> params1=" params)
               p (println ">o> params1.type!!!!=" (type (:collection_id params)))

               _ (s/validate schema params)


               p (println ">o> ph.req.params2" (get request :params))
               ]

           (let [key-map {:size :count}
                 request (let [params-renamed (rename-keys-fnc params key-map)]
                           (let [request (assoc-in request [:params] params-renamed)]
                             (assoc-in request [:parameters :query] params-renamed)))

                 ;    request
                 ;    ;(let [params-renamed (rename-keys-fnc params key-map)]
                 ;              (let [request (assoc-in request [:params] params-renamed)]
                 ;                (assoc-in request [:parameters :query] params-renamed))
                 ;;)

                 p (println ">o> ph.req.params" (get request :params))
                 p (println ">o> ph.req.query" (get request :parameters :query))
                 p (println ">o> ph.req.is_admin" (get request :is_admin))
                 ]

             (handler request)))
       ;  (catch Exception ex
       ;
       ;
       ;    ;(println ex)
       ;    (sd/response_bad_request (str ">o> Invalid query parameters: " (.getMessage ex)) (.getData ex))))
       ;    ;(sd/response_bad_request (str ">o> Invalid query parameters: " (.getMessage ex)) )))
       ;)

   ))))

(defn pagination-optional-handler
  ([]
   (pagination-handler
     {(s/optional-key :page) s/Int
      (s/optional-key :size) s/Int})))

;{(s/optional-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
;     (s/optional-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")})))

;(s/defschema ItemQueryParams
;  {:page (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
;   :size (s/constrained s/Int #(>= % 1) "Must be a positive integer")})

(s/defschema ItemQueryParams
  {(s/optional-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
   (s/optional-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")

   }

  )

(s/defschema ItemQueryParams-required
  {(s/required-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
   (s/required-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
