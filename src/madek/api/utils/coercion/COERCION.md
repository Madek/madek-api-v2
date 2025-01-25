
# Swagger Coercion

There are 2 ways to describe schema
1. reitit.coercion.schema (simple description of types)
    1. Key-Support: optional/required
    2. Value-Support: Int/Str/Any/Uuid & conversion
    3. Possible to define customized validations
2. reitit.coercion.spec (more options to define default-value/description/.. of swagger-ui-fields)
   1. .. but it blows up code

TODO
--
1. Avoid String-JSON parameters, cast it at BE
2. Clarify auth-creds
3. Unify: :meta_data vs :meta-data

Nice2Know
--
1. Not possible to mix schema-coercion in one endpoint
2. You can use both schema-coercions in one file
3. Example of error caused by mixed schema-definition (running of server works but )
```clojure
;; cause
:responses {200 {:body ::response-body-adm}
            406 {:body s/Any}}

;; swagger-ui
Failed to load API definition.
Fetch error
Internal Server Error /api-v2/swagger.json

;; log
2024-06-28T05:55:51.813Z NX-41294 WARN [madek.api.db.core:89] - Rolling back transaction because error status  500
2024-06-28T05:55:51.813Z NX-41294 WARN [madek.api.db.core:90] -    Details:  GET /api-v2/swagger.json
2024-06-28T05:55:51.937Z NX-41294 ERROR [madek.api.web:75] - COUGHT UNEXPECTED EXCEPTION Unable to resolve spec: :clojure.spec.alpha/unknown
2024-06-28T05:55:51.937Z NX-41294 ERROR [madek.api.web:46] - Exception Unable to resolve spec: :clojure.spec.alpha/unknown
2024-06-28T05:55:51.937Z NX-41294 WARN [madek.api.web:47] - Exception   THROWABLE: java.lang.Exception: Unable to resolve spec: :clojure.spec.alpha/unknown[..
```


Definition by reitit.coercion.schema
--
```clojure

[reitit.coercion.schema]
[schema.core :as s]


(def schema_export_keyword_usr
  {:id s/Uuid
   :meta_key_id s/Str
   :term s/Str
   :description (s/maybe s/Str)
   :position (s/maybe s/Int)
   :external_uris [s/Any]
   :external_uri (s/maybe s/Str)
   :rdf_class s/Str})

...

["keywords"
 {:get
  {:summary (sd/sum_pub (d "Query / list keywords."))
   :handler handle_usr-query-keywords

   :coercion reitit.coercion.schema/coercion

   :parameters {:query  {(s/optional-key :page) (s/constrained s/Int #(>= % 0) "Must be >=0 integer")
                         (s/optional-key :size) (s/constrained s/Int #(>= % 1) "Must be a positive integer")}}     ;; ok

   :responses {200 {:body {:keywords [schema_export_keyword_usr]}} 

               202 {:description "Successful response, list of items."
                    :schema {}
                    :examples {"application/json" {:message "Here are your items."
                                                   :page 1
                                                   :size 2
                                                   :items [{:id 1, :name "Item 1"}
                                                           {:id 2, :name "Item 2"}]}}}}}}]
```



Definition by reitit.coercion.spec
--
```clojure

[reitit.coercion.spec :as spec]
[spec-tools.core :as st]


(sa/def ::page (st/spec {:spec pos-int?
                         :description "Page number"
                         :json-schema/default 1}))

(sa/def ::size (st/spec {:spec pos-int?
                         :description "Number of items per page"
                         :json-schema/default 10}))


(sa/def ::id (st/spec {:spec uuid?}))
(sa/def ::meta_key_id (st/spec {:spec string?}))
(sa/def ::term (st/spec {:spec string?}))

(sa/def ::description
  (sa/or :nil nil? :string string?))

(sa/def ::position
  (sa/or :nil nil? :int int?))

(sa/def ::external_uris (st/spec {:spec (sa/coll-of any?)
                                  :description "An array of any types"}))

(sa/def ::external_uri
  (sa/or :nil nil? :string string?))

(sa/def ::rdf_class (st/spec {:spec string?}))

(sa/def ::basic (st/spec {:spec (sa/coll-of any?)
                          :description "An array of any types"}))


(sa/def ::person (sa/keys :req-un [::id ::meta_key_id ::term ::description ::position ::external_uris ::external_uri ::rdf_class]))

(sa/def ::response-body (sa/keys :req-un [::keywords]))

(sa/def ::keywords (st/spec {:spec (sa/coll-of ::person)
                             :description "A list of persons"}))


(def schema_query_pagination_only
  (sa/keys
    :opt-un [::page ::size]))

...
   ["keywords"
    {:get
     {:summary (sd/sum_pub (d "Query / list keywords."))
      :handler handle_usr-query-keywords

      :coercion spec/coercion
      :parameters {:query schema_query_pagination_only}
      :responses {200 {:body ::response-body}

                  202 {:description "Successful response, list of items."
                       :schema {}
                       :examples {"application/json" {:message "Here are your items."
                                                      :page 1
                                                      :size 2
                                                      :items [{:id 1, :name "Item 1"}
                                                              {:id 2, :name "Item 2"}]}}}}}}]

```

OpenApi3 - Examples
--
```clojure
       :responses {200 {:description "Success."
                        :body c/schema_export-vocabulary-admin}
                   400 {:description "Bad request."
                        :body s/Any}
                   
                   300 {:content {"application/json" {:description "Fetch a pizza as json"
                                                     :schema {
                                                              :color s/Str
                                                              :pineapple s/Bool}
                                                     :examples {:white {:description "White pizza with pineapple"
                                                                        :value {:color :white
                                                                                :pineapple true}}
                                                                :red {:description "Red pizza"
                                                                      :value {:color :red
   
                                                                              :pineapple false}}}}}}}
```

### Not really working
- TODO: Found no way to define simple string with example
- Working example with map
```clojure
   ;; works
   406 {:description "Not Acceptable."
        :content {"application/json" {:description "Fetch a pizza as json"
                                      :schema {
                                               :color s/Str
                                               :pineapple s/Bool}
                                      :examples {:white {:description "White pizza with pineapple"
                                                         :value {:color :white
                                                                 :pineapple true}}
                                                 :red {:description "Red pizza"
                                                       :value {:color :red
                                                               :pineapple false}}}}}}
   
   
   ;; example not working
   410 {:description "Not Acceptable."
        :content {"text/plain" {:description "Fetch a pizza as json"
                                :value "mei test"
                                }}}

   ;; example not working, schema works
   201 {:description "Returns the list of static_pages."
        :body [schema_export_static_page]
        ;:examples {"application/json" [{:id "uuid"
        ;                                :name "name"
        ;                                :contents [{:lang "de" :content "content"}]
        ;                                :created_at "2020-01-01T00:00:00Z"
        ;                                :updated_at "2020-01-01T00:00:00Z"}]}
        }
```

Issues with spec/coercion and attr with values nil/[]
--
```clojure
"path": [
        "meta-keys",
        "allowed_people_subtypes",
        "nil"
      ],
      "pred": "clojure.core/nil?",
      "val": [
        "Person",
        "PeopleGroup"
      ],

;(sa/def ::allowed_people_subtypes (nil-or list?)) ;; error
;(sa/def ::allowed_people_subtypes (st/spec {:spec list?}))  ;; error
(sa/def ::allowed_people_subtypes (st/spec {:spec any?})) ;;ok
```