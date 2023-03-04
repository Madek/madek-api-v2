(ns madek.api.resources.collections
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.pagination :as pagination]
    [madek.api.resources.collections.collection :refer [get-collection]]
    [madek.api.resources.collections.index :refer [get-index]]
    [madek.api.resources.shared :as shared]
    [madek.api.utils.rdbms :as rdbms]
    ))

(def routes
  (cpj/routes
    (cpj/GET "/collections/" _ get-index)
    (cpj/GET "/collections/:id" _ get-collection)
    (cpj/ANY "*" _ shared/dead-end-handler)))

(defn handle_get-index [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (logging/info "handle_get-index" "\nquery-params\n" query-params)
    (get-index qreq)
    )
  )

;(defn handle_get-collection [req]
;  (get-collection req))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
