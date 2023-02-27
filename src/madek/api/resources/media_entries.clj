(ns madek.api.resources.media-entries
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.pagination :as pagination]
    [madek.api.resources.media-entries.index :refer [get-index]]
    [madek.api.resources.media-entries.media-entry :refer [get-media-entry]]
    [madek.api.resources.shared :as shared]
    [madek.api.utils.rdbms :as rdbms]
    ))


(def routes
  (cpj/routes
    (cpj/GET "/media-entries/" _ get-index)
    (cpj/GET "/media-entries/:id" _ get-media-entry)
    (cpj/ANY "*" _ shared/dead-end-handler)
    ))


(defn handle_get-index [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (get-index qreq)))

(defn handle_get-media-entry [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (get-media-entry qreq)
  ))
;### Debug ####################################################################
;(debug/debug-ns *ns*)
