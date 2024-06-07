(ns madek.api.resources.keywords
  (:require
    [honey.sql :refer [format]
     :rename          {format sql-format}]
    [honey.sql.helpers :as sql]
    [logbug.catcher :as catcher]
    [madek.api.resources.keywords.keyword :as kw]
    [madek.api.resources.shared.core :as sd]
    [madek.api.utils.auth :refer [wrap-authorize-admin!]]
    [madek.api.utils.helper :refer [convert-map]]
    [madek.api.utils.helper :refer [d]]
    [madek.api.utils.helper :refer [str-to-int]]
    [next.jdbc :as jdbc]
    [reitit.coercion.schema]
    [schema.core :as s]))

;### swagger io schema ####################################################################

(def schema_create_keyword
  {:meta_key_id                    s/Str
   :term                           s/Str
   (s/optional-key :description)   (s/maybe s/Str)
   (s/optional-key :position)      (s/maybe s/Int)
   (s/optional-key :external_uris) [s/Str]
   (s/optional-key :rdf_class)     s/Str})

(def schema_update_keyword
  {;id
  ;(s/optional-key :meta_key_id) s/Str
    (s/optional-key :term)          s/Str
    (s/optional-key :description)   (s/maybe s/Str)
    (s/optional-key :position)      s/Int
    (s/optional-key :external_uris) [s/Str]
    (s/optional-key :rdf_class)     s/Str})

(def schema_export_keyword_usr
  {:id            s/Uuid
   :meta_key_id   s/Str
   :term          s/Str
   :description   (s/maybe s/Str)
   :position      (s/maybe s/Int)
   :external_uris [s/Any]
   :external_uri  (s/maybe s/Str)
   :rdf_class     s/Str})

(def schema_export_keyword_adm
  {:id            s/Uuid
   :meta_key_id   s/Str
   :term          s/Str
   :description   (s/maybe s/Str)
   :position      (s/maybe s/Int)
   :external_uris [s/Any]
   :external_uri  (s/maybe s/Str)
   :rdf_class     s/Str
   :creator_id    (s/maybe s/Uuid)
   :created_at    s/Any
   :updated_at    s/Any})

(def schema_query_keyword
  {(s/optional-key :id)          s/Uuid
   (s/optional-key :meta_key_id) s/Str
   (s/optional-key :term)        s/Str
   (s/optional-key :description) s/Str
   (s/optional-key :rdf_class)   s/Str
   (s/optional-key :page)        s/Int
   (s/optional-key :count)       s/Int})

(defn user-export-keyword [keyword]
  (->
   keyword
   ;(select-keys
   ; [:id :meta_key_id :term :description :external_uris :rdf_class
   ;  :created_at])
   (dissoc :creator_id :created_at :updated_at)
   (assoc ; support old (singular) version of field
    :external_uri (first (keyword :external_uris)))))

(defn adm-export-keyword [keyword]
  (->
   keyword
   (assoc ; support old (singular) version of field
    :external_uri (first (keyword :external_uris)))))

;### handlers get and query ####################################################################

(defn handle_adm-get-keyword
  [request]
  (let [keyword (-> request :keyword)]
    (sd/response_ok (adm-export-keyword keyword))))

(defn handle_usr-get-keyword
  [request]
  (let [keyword (-> request :keyword)]
    (sd/response_ok (user-export-keyword keyword))))

(defn handle_usr-query-keywords [request]
  (let [rq        (-> request :parameters :query)
        tx        (:tx request)
        db-result (kw/db-keywords-query rq tx)
        result    (map user-export-keyword db-result)]
    (sd/response_ok {:keywords result})))

(defn handle_adm-query-keywords [request]
  (let [rq        (-> request :parameters :query)
        tx        (:tx request)
        db-result (kw/db-keywords-query rq tx)
        result    (map adm-export-keyword db-result)]
    (sd/response_ok {:keywords result})))

;### handlers write ####################################################################

(defn handle_create-keyword [req]
  (try
    (catcher/with-logging {}
                          (let [uid        (-> req :authenticated-entity :id)
                                data       (-> req :parameters :body)
                                dwid       (assoc data :creator_id uid)
                                sql-query  (-> (sql/insert-into :keywords)
                                               (sql/values [(convert-map dwid)])
                                               (sql/returning :*)
                                               sql-format)

                                ins-result (jdbc/execute-one! (:tx req) sql-query)]
                            (if-let [result ins-result]
                              (sd/response_ok (adm-export-keyword result))
                              (sd/response_failed "Could not create keyword" 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-keyword [req]
  (try
    (catcher/with-logging {}
                          (let [id        (-> req :parameters :path :id)
                                data      (-> req :parameters :body)
                                tx        (:tx req)
                                sql-query (-> (sql/update :keywords)
                                              (sql/set (convert-map data))
                                              (sql/where [:= :id id])
                                              sql-format)
                                upd-res   (jdbc/execute-one! tx sql-query)]

                            (if (= 1 (:next.jdbc/update-count upd-res))
                              ;(sd/response_ok (adm-export-keyword (kw/db-keywords-get-one id)))
                              (-> id (kw/db-keywords-get-one tx)
                                  adm-export-keyword
                                  sd/response_ok)
                              (sd/response_failed "Could not update keyword." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-keyword [req]
  (try
    (catcher/with-logging {}
                          (let [id        (-> req :parameters :path :id)
                                old-data  (-> req :keyword)
                                sql-query (-> (sql/delete-from :keywords)
                                              (sql/where [:= :id id])
                                              sql-format)
                                del-res   (jdbc/execute-one! (:tx req) sql-query)]

                            ; logwrite
                            (if (= 1 (::jdbc/update-count del-res))
                              (sd/response_ok (adm-export-keyword old-data))
                              (sd/response_failed "Could not delete keyword." 406))))
    (catch Exception ex (sd/response_exception ex))))

;### routes ###################################################################

(defn validate-scheme [schema]
  (fn [handler]
    (fn [request]
      (let [p               (println ">o> request=" request)

            params          (-> request :parameters :query)
            [valid? errors] (s/validate schema params)]
        (if valid?
          (handler request)
          (sd/response_failed (str "Invalid query parameters: " errors) 400))))))


;(defn move-params [schema]
;
;  (fn [handler]
;    (fn [request]
;      (let [;           params (-> request :parameters :query)
;
;      ;; process casting of query parameters
;
;             cast            (fn [m]
;                               (reduce-kv
;                                (fn [acc k v]
;                                  (assoc acc k
;                                         (cond
;                                           (= k :page) (str-to-int v v)
;                                           (= k :size) (str-to-int v v)
;                                           :else       v)))
;                                {}
;                                m))
;             request         (assoc-in request [:params] (cast (-> request :params)))
;             params          (-> request :params)
;
;
;             _               (let [;                  p (println ">o> request=" request)
;
;
;        ;                  params (-> request :parameters :query)
;        ;                            params          (-> request :params)
;                                    [valid? errors] (s/validate schema params)
;                                    p               (println ">o> validation-result: " valid? errors)]
;                               (if-not valid?
;                                 ;               (handler request)
;                                 ;               (sd/response_bad_request (str "Invalid query parameters: " errors) 400)))
;                                 (sd/response_bad_request (str "Invalid query parameters: " errors) errors)))
;
;
;             params2         (-> request :params :query)
;             qparams         (-> request :query)
;
;
;             p               (println ">o> 1?params=" request);
;             p               (println ">o> 1params=" params)
;             p               (println ">o> 2params2=" params2)
;             p               (println ">o> 3qparams=" qparams)
;
;             ;; swagger-ui
;             p               (println ">o> 3qparams=" (-> request :params)) ;{:page 2, :size 3}
;             p               (println ">o> 3qparams=" (-> request :query-params)); {:page 2, :size 3}
;
;
;             ; move (-> request :params) to (-> request :parameters :query)
;
;             ;           request (assoc request :parameters (assoc (-> request :parameters) :query (-> request :params)))
;
;
;             key-map         {:size :count}
;             rename          (fn [m key-map]
;                               (reduce-kv
;                                (fn [acc k v]
;                                  (let [new-key (get key-map k k)] ; Get the new key from key-map or use the original key if not found
;                                    (assoc acc new-key v)))
;                                {}
;                                m))
;
;             p               (println ">o> 1cast.before" (-> request :params))
;             p               (println ">o> 2cast.after" (cast (-> request :params)))
;             p               (println ">o> 3cast.after" (rename (cast (-> request :params)) key-map))
;
;             ;           request (assoc-in request [:parameters :query] (cast (-> request :params)))
;
;             ;           request (assoc request :parameters (-> request :parameters :query))
;
;             p               (println ">o> before=" (-> request :parameters :query))
;
;             ;           request (assoc-in request [:parameters :query] params)
;             ;             request (assoc-in request [:parameters :query] (rename (cast (-> request :params)) key-map))
;             request         (assoc-in request [:parameters :query] (rename (-> request :params) key-map))
;             p               (println ">o> >>  1after=" (-> request :parameters :query))
;             p               (println ">o> 2after=" (-> request :parameters :query :page))
;             p               (println ">o> 3after=" (class (-> request :parameters :query :page)))
;
;
;
;             ;          page (or (get params :page) 0)
;             ;          size (or (get params :size) 10)
;             ;          new-params (assoc params :page page :size size)
;             ]
;        ;      (handler parameters :query new-params))))))
;        (handler request)))))


;(defn move-params [schema]
;  (fn [handler]
;    (fn [request]
;
;      (try
;
;
;        (let [; Function to cast parameters
;               cast            (fn [m]
;                                 (reduce-kv
;                                  (fn [acc k v]
;                                    (assoc acc k
;                                           (cond
;                                             (= k :page) (str-to-int v v)
;                                             (= k :size) (str-to-int v v)
;                                             :else       v)))
;                                  {}
;                                  m))
;
;               ; Function to rename keys in a map based on key-map
;               rename          (fn [m key-map]
;                                 (reduce-kv
;                                  (fn [acc k v]
;                                    (let [new-key (get key-map k k)] ; Get the new key from key-map or use the original key if not found
;                                      (assoc acc new-key v)))
;                                  {}
;                                  m))
;
;               ; Cast the parameters and update the request map
;               casted-vals     (cast (-> request :params))
;               request         (assoc-in request [:params] casted-vals)
;               request         (assoc-in request [:parameters :query] casted-vals)
;
;               params          (-> request :params)
;
;               ; Validate the schema
;               [valid? errors] (s/validate schema params)]
;
;          ; Print debug information
;          (println ">o> validation-result: " valid? errors)
;          (println ">o> 1?params=" request)
;          (println ">o> 1params=" params)
;
;          ;        (if-not valid?
;          ;          (do
;          ;            (println ">o> Validation failed")
;          ;            (sd/response_bad_request (str "Invalid query parameters: " errors) errors))
;          (do
;            ; Rename the keys in params based on key-map
;            (let [key-map        {:size :count}
;                  params-renamed (rename params key-map)]
;
;              ; Update the request with the renamed parameters
;              (let [request (assoc-in request [:parameters :query] params-renamed)]
;                (println ">o> 1cast.before" (-> request :params))
;                (println ">o> 2cast.after" (cast (-> request :params)))
;                (println ">o> 3cast.after" params-renamed)
;                (println ">o> before=" (-> request :parameters :query))
;                (println ">o> >>  1after=" (-> request :parameters :query))
;                (println ">o> 2after=" (-> request :parameters :query :page))
;                (println ">o> 3after=" (class (-> request :parameters :query :page)))
;
;                ; Call the handler with the updated request
;                (handler request))))
;          ;        )
;          )
;
;
;        (catch Exception ex
;
;          (println ">o> abc" ex)
;          (sd/response_bad_request ex
;                                   (sd/response_bad_request (str "Invalid query parameters: " (.getMessage ex)) ex)))))))

(defn move-params [schema]
  (fn [handler]
    (fn [request]
      (try
        (let [; Function to cast parameters
               cast (fn [m]
                        (println ">o> [cast]" m )
                      (reduce-kv
                       (fn [acc k v]
                         (assoc acc k
                                (cond
                                  (= k :page) (str-to-int v v)
                                  (= k :size) (str-to-int v v)
                                  :else v)))
                       {}
                       m))

               ; Function to rename keys in a map based on key-map
               rename (fn [m key-map]
                        (println ">o> [rename]" m key-map)
                        (reduce-kv
                         (fn [acc k v]
                           (let [new-key (get key-map k k)] ; Get the new key from key-map or use the original key if not found
                             (assoc acc new-key v)))
                         {}
                         m))

               ; Cast the parameters and update the request map
               casted-vals (cast (-> request :params))
               p (println ">o> abc1")
;               request (assoc-in request [:params] casted-vals)
;               request (assoc-in request [:params] casted-vals [:parameters :query] casted-vals)

               request (let [casted-vals (cast (-> request :params))]
                 (println ">o> abc1")
                 (let [request (assoc-in request [:params] casted-vals)]
                   (assoc-in request [:parameters :query] casted-vals)))


               p (println ">o> abc2")
               params (-> request :params)
               p (println ">o> abc3a" params)
               p (println ">o> abc3b" (-> request :parameters :query))
               p (println ">o> abc3c" (-> request :params))


               schema {:size s/Int :page s/Int}
               p (println ">o> abcx" )
               ; Validate the schema
               res (s/validate schema params)
               p (println ">o> abcy.after.val=" res)
               ]

          ; Print debug information
;          (println ">o> validation-result: " valid? errors)
          (println ">o> 1?params=" request)
          (println ">o> 1params=" params)

;          (if-not valid?
;            (do
;              (println ">o> Validation failed")
;              (sd/response_bad_request (str "Invalid query parameters: " errors) errors))
;            (do
              ; Rename the keys in params based on key-map
              (let [key-map {:size :count}
;                    params-renamed (rename params key-map)


;                ; Update the request with the renamed parameters
;                request                           (let [   (let [request (assoc-in request [:params] params-renamed)]
;                            (assoc-in request [:parameters :query] params-renamed))
;                    ])




                    p (println ">o> abc4")

                    request (let [params-renamed (rename params key-map)
                                  p (println ">o> ???? params-renamed=" params-renamed)
                                  ]
                              (println ">o> abc1")
                              (let [request (assoc-in request [:params] params-renamed)]
                                (assoc-in request [:parameters :query] params-renamed)))

                    p (println ">o> abc5")
                    ]

                  (handler request)
                ;))));)
                ))
        ;))




;                (let [request (assoc-in request [:parameters :query] params-renamed)]
;                  (println ">o> 1cast.before" (-> request :params))
;                  (println ">o> 2cast.after" (cast (-> request :params)))
;                  (println ">o> 3cast.after" params-renamed)
;                  (println ">o> before=" (-> request :parameters :query))
;                  (println ">o> >> 1after=" (-> request :parameters :query))
;                  (println ">o> 2after=" (-> request :parameters :query :page))
;                  (println ">o> 3after=" (class (-> request :parameters :query :page)))

                  ; Call the handler with the updated request

        (catch Exception ex
          (println ">o> abc" ex)
          (sd/response_bad_request (str "Invalid query parameters: " (.getMessage ex)) (.getData ex)))))));) ;;fixme



(defn wrap-find-keyword [handler]
  (fn [request]
    (sd/req-find-data request handler
                      :id
      :keywords :id
      :keyword true)))

(s/defschema ItemQueryParams
  {:page (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
   :size (s/constrained s/Int #(>= % 1) "Must be a positive integer")})

;; FIXME: broken endpoint to test doc
(def query-routes
  ["/keywords"
   {:swagger {:tags ["keywords"] :security []}}
   ["/"
    {:get
     {:summary    (sd/sum_pub (d "Query / list keywords."))
      :handler    handle_usr-query-keywords
      :coercion   reitit.coercion.schema/coercion

      ;      :parameters {:query ItemQueryParams}

      ;      :middleware [(validate-scheme ItemQueryParams)]
      ;      :middleware [move-params ] ;;ok
      :middleware [(move-params ItemQueryParams)]

      :swagger    {:parameters [{:name        "page"
                                 :in          "query"
                                 :description "Page number, defaults to 0"
                                 :required    false
                                 ;                              :value 0 ;; wont set as default
                                 :value       1
                                 ;                              :default 3
                                 ;                              :defaults 2
                                 ;                              :type "integer"
                                 ;                              :format "int32"
                                 ;                              :schema {:type "integer"
                                 ;                                       :format "int32"
                                 ;                                       :value 11
                                 ;                                       :defaults 22
                                 ;                                       :default 44}
                                 }
                                {:name        "size"
                                 :in          "query"
                                 :description "Number of items per page, defaults to 10"
                                 :required    false
                                 :value       11}]}

      :responses  {200 {:body {:keywords [schema_export_keyword_usr]}}
                   202 {:description "Successful response, list of items."
                        :schema      {}
                        ;; Define your response schema as needed
                        :examples    {"application/json" {:message "Here are your items."
                                                          :page    1
                                                          :size    2
                                                          :items   [{:id 1, :name "Item 1"}
                                                                    {:id 2, :name "Item 2"}]}}}}}}]

   ["/:id"
    {:get
     {:summary     (sd/sum_pub "Get keyword for id.")
      :handler     handle_usr-get-keyword
      :middleware  [wrap-find-keyword]
      :coercion    reitit.coercion.schema/coercion
      :parameters  {:path {:id s/Uuid}}
      :responses   {200 {:body schema_export_keyword_usr}
                    404 {:body s/Any}}
      :description "Get keyword for id. Returns 404, if no such keyword exists."}}]])

(def admin-routes
  ["/keywords"
   {:swagger {:tags ["admin/keywords"] :security [{"auth" []}]}}
   ["/"
    {:get
     {:summary     (sd/sum_adm "Query keywords")
      :handler     handle_adm-query-keywords
      :middleware  [wrap-authorize-admin!]
      :coercion    reitit.coercion.schema/coercion
      :parameters  {:query schema_query_keyword}
      :responses   {200 {:body {:keywords [schema_export_keyword_adm]}}}
      :description "Get keywords id list. TODO query parameters and paging. TODO get full data."}

     :post
     {:summary    (sd/sum_adm "Create keyword.")
      :coercion   reitit.coercion.schema/coercion
      :handler    handle_create-keyword
      :middleware [wrap-authorize-admin!]
      :parameters {:body schema_create_keyword}
      :responses  {200 {:body schema_export_keyword_adm}
                   406 {:body s/Any}}}}]
   ["/:id"
    {:get
     {:summary     (sd/sum_adm "Get keyword for id")
      :handler     handle_adm-get-keyword
      :middleware  [wrap-authorize-admin!
                    wrap-find-keyword]
      :coercion    reitit.coercion.schema/coercion
      :parameters  {:path {:id s/Uuid}}
      :responses   {200 {:body schema_export_keyword_adm}
                    404 {:body s/Any}}
      :description "Get keyword for id. Returns 404, if no such keyword exists."}

     :put
     {:summary    (sd/sum_adm "Update keyword.")
      :handler    handle_update-keyword
      :middleware [wrap-authorize-admin!
                   wrap-find-keyword]
      :coercion   reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}
                   :body schema_update_keyword}
      :responses  {200 {:body schema_export_keyword_adm}
                   404 {:body s/Any}
                   406 {:body s/Any}}}

     :delete
     {:summary    (sd/sum_adm "Delete keyword.")
      :handler    handle_delete-keyword
      :middleware [wrap-authorize-admin!
                   wrap-find-keyword]
      :coercion   reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses  {200 {:body schema_export_keyword_adm}
                   404 {:body s/Any}
                   406 {:body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
