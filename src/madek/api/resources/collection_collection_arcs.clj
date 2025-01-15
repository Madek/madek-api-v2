(ns madek.api.resources.collection-collection-arcs
  (:require
   [clojure.spec.alpha :as sa]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.pagination :as pagination]
   [madek.api.resources.collection-media-entry-arcs :refer [schema_collection-collection-arc-export]]
   [madek.api.resources.shared.core :as fl]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.coercion.spec-alpha-definition-nil :as sp-nil]
   [madek.api.utils.helper :refer [to-uuid]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]
   [schema.core :as s]
   [spec-tools.core :as st]))

(defn arc-query [request]
  (-> (sql/select :*)
      (sql/from :collection_collection_arcs)
      (sql/where [:= :id (-> request :parameters :path :id to-uuid)])
      sql-format))

(defn handle_get-arc [req]
  (let [query (arc-query req)
        db-result (jdbc/execute! (:tx req) query)

        te_print (println "db-result: " db-result)
        te_print (println "db-result1: " (first db-result))
        ]
    (if-let [arc (first db-result)]
      (sd/response_ok arc)
      (sd/response_failed "No such collection-collection-arc" 404))))

(defn arc-query-by-parent-and-child [req]
  (-> (sql/select :*)
      (sql/from :collection_collection_arcs)
      (sql/where [:= :parent_id (-> req :parameters :path :parent_id)])
      (sql/where [:= :child_id (-> req :parameters :path :child_id)])
      sql-format))

(defn handle_arc-by-parent-and-child [req]
  (let [query (arc-query-by-parent-and-child req)
        db-result (jdbc/execute! (:tx req) query)]
    (if-let [arc (first db-result)]
      (sd/response_ok arc)
      (sd/response_failed "No such collection-collection-arc" 404))))

(defn arcs-query [query-params]
  (-> (sql/select :*)
      (sql/from :collection_collection_arcs)
      (dbh/build-query-param query-params :child_id)
      (dbh/build-query-param query-params :parent_id)
      (pagination/sql-offset-and-limit query-params)
      sql-format))

(defn handle_query-arcs [req]
  (let [query (arcs-query (-> req :parameters :query))
        db-result (jdbc/execute! (:tx req) query)]
    (sd/response_ok {:collection-collection-arcs db-result})))

(defn handle_create-col-col-arc [req]
  (try
    (catcher/with-logging {}
      (let [parent-id (-> req :parameters :path :parent_id)
            child-id (-> req :parameters :path :child_id)
            data (-> req :parameters :body)
            ins-data (assoc data :parent_id parent-id :child_id child-id)
            sql-map {:insert-into :collection_collection_arcs
                     :values [ins-data]}
            sql (-> sql-map sql-format)]
        (if-let [ins-res (next.jdbc/execute! (:tx req) [sql ins-data])]
          (sd/response_ok ins-res)
          (sd/response_failed "Could not create collection-collection-arc" 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_update-arc [req]
  (try
    (catcher/with-logging {}
      (let [parent-id (-> req :parameters :path :parent_id)
            child-id (-> req :parameters :path :child_id)
            data (-> req :parameters :body)
            query (-> (sql/update :collection_collection_arcs)
                      (sql/set data)
                      (sql/where [:= :parent_id parent-id
                                  := :child_id child-id])
                      sql-format)
            tx (:tx req)
            result (next.jdbc/execute! tx query)]

        (if (= 1 (first result))
          (sd/response_ok (dbh/query-eq-find-one
                           :collection_collection_arcs
                           :parent_id parent-id
                           :child_id child-id tx))
          (sd/response_failed "Could not update collection collection arc." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_delete-arc [req]
  (try
    (catcher/with-logging {}
      (let [parent-id (-> req :parameters :path :parent_id)
            child-id (-> req :parameters :path :child_id)
            tx (:tx req)
            ;; TODO: fetch old data by delete-query
            olddata (dbh/query-eq-find-one
                     :collection_collection_arcs
                     :parent_id parent-id
                     :child_id child-id
                     tx)
            tx (:tx req)
            query (-> (sql/delete-from :collection_collection_arcs)
                      (sql/where [:= :parent_id parent-id
                                  := :child_id child-id])
                      sql-format)

            delresult (next.jdbc/execute! tx query)]

        (if (= 1 (first delresult))
          (sd/response_ok olddata)
          (sd/response_failed "Could not delete collection collection arc." 422))))
    (catch Exception e (sd/response_exception e))))

(def schema_collection-collection-arc-update
  {(s/optional-key :highlight) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :position) s/Int})

(def schema_collection-collection-arc-create
  {(s/optional-key :highlight) s/Bool
   (s/optional-key :order) s/Num
   (s/optional-key :position) s/Int})

(sa/def ::group-id-query-def (sa/keys :opt-un [::sp/child_id ::sp/parent_id ::sp/page ::sp/size]))
(sa/def ::group-id-resp-def (sa/keys :req-un [::sp/id ::sp/child_id ::sp/parent_id ::sp/highlight]
                                     :opt-un [::sp-nil/order ::sp-nil/created_at ::sp-nil/updated_at ::sp-nil/position]))

(sa/def ::collection-collection-arc-resp-def (sa/keys :req-un [::sp/id ::sp/child_id ::sp/parent_id ::sp-nil/highlight
                                                               ::sp-nil/order ::sp-nil/created_at ::sp-nil/updated_at ::sp-nil/position]))

(sa/def :list-of/collection-collection-arcs (st/spec {:spec (sa/coll-of ::collection-collection-arc-resp-def)
                                                      :description "A list of collection-collection-arcs"}))
(sa/def ::response-groups-body (sa/keys :req-un [:list-of/collection-collection-arcs]))

; TODO add permission checks
(def ring-routes
  ["/collection-collection-arcs"
   {:openapi {:tags ["api/collection"]}}
   [""
    {:get
     {:summary (fl/?no-auth? "Query collection collection arcs.")
      :handler handle_query-arcs
      :coercion spec/coercion
      :parameters {:query ::group-id-query-def}
      :responses {200 {:description "Returns the collection collection arcs."
                       :body ::response-groups-body}}}}]
   ; TODO rename param to collection_id
   ; TODO add permission checks
   ["/:id"
    {:get
     {:summary (fl/?no-auth? "Get collection collection arcs. c0569b22-3077-4e37-ac44-fb8fd12b6d12")
      :handler handle_get-arc
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Str}}
      :responses {200 {:description "Returns the collection collection arc."
                       :body schema_collection-collection-arc-export}
                  404 {:description "Collection collection arc not found."
                       :body s/Any}} ; TODO response coercion
      }}]])
; TODO rename param use middleware for permissions
(def collection-routes
  ["/collection/:parent_id"
   {:openapi {:tags ["api/collection"]}}
   ;["/collection-arcs"
   ; {:get
   ;  {:summary "List collection collection arcs."
   ;   :handler arcs
   ;   :swagger {:produces "application/json"}
   ;   :coercion reitit.coercion.schema/coercion
   ;   :parameters {:path {:parent_id s/Uuid}}
   ;   :responses {200 {:body s/Any}} ; TODO response coercion
   ;   }
   ; }
   ;]
   ["/collection-arc/:child_id"
    {:post
     {:summary (sd/sum_todo "Create collection collection arc")
      :handler handle_create-col-col-arc
      :swagger {:produces "application/json"
                :consumes "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:parent_id s/Uuid
                          :child_id s/Uuid}
                   :body schema_collection-collection-arc-create}
      :responses {200 {:description "Returns the created collection collection arc."
                       :body schema_collection-collection-arc-export}
                  406 {:description "Could not create collection collection arc"
                       :body s/Any}}}

     :get
     {:summary "Get collection collection arcs."
      :handler handle_arc-by-parent-and-child
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:parent_id s/Uuid
                          :child_id s/Uuid}}
      :responses {200 {:description "Returns the collection collection arc."
                       :body schema_collection-collection-arc-export}
                  404 {:description "Collection collection arc not found."
                       :body s/Any}} ; TODO response coercion
      }

     ; TODO col col arc update tests
     :put
     {:summary (sd/sum_usr "Update collection collection arc")
      :handler handle_update-arc
      :swagger {:produces "application/json"
                :consumes "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:parent_id s/Uuid
                          :child_id s/Uuid}
                   :body schema_collection-collection-arc-update}
      :responses {200 {:description "Returns the updated collection collection arc."
                       :body schema_collection-collection-arc-export}
                  404 {:description "Collection collection arc not found."
                       :body s/Any}
                  406 {:description "Could not update collection collection arc"
                       :body s/Any}}}

     ; TODO col col arc delete tests
     :delete
     {:summary (sd/sum_usr "Delete collection collection arc")
      :handler handle_delete-arc
      :swagger {:produces "application/json"}
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:parent_id s/Uuid
                          :child_id s/Uuid}}
      :responses {200 {:description "Returns the deleted collection collection arc."
                       :body schema_collection-collection-arc-export}
                  404 {:description "Collection collection arc not found."
                       :body s/Any}
                  406 {:description "Could not delete collection collection arc"
                       :body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
