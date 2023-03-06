(ns madek.api.resources.shared
  (:require [cheshire.core :as cheshire]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [compojure.core :as cpj]
            [logbug.catcher :as catcher]
            [madek.api.authorization :refer [authorized?]]
            [madek.api.resources.media-entries.media-entry :refer [get-media-entry-for-preview]]
            [madek.api.semver :as semver]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]))

; begin request response helpers

(def uuid-matcher #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}" )

(def dead-end-handler
  (cpj/routes
    (cpj/GET "*" _ {:status 404 :body {:message "404 NOT FOUND"}})
    (cpj/ANY "*" _ {:status 501 :body {:message "501 NOT IMPLEMENTED"}})
    ))

(def internal-keys [:admin_comment])

(defn remove-internal-keys
  [resource]
  (apply dissoc resource internal-keys))

(defn response_not_found [msg]
  {:status 404 :body {:message msg}})

(defn get-path-params
  ([req] (let [params (get-in req [:parameters :path])] params))
  ([req pname] (let [param (get-in req [:parameters :path pname])] param)))


(def root
  {:status 200
   :body {:api-version (semver/get-semver)
          :message "Hello Madek User!"}})

(def no_impl
  {:status 501
   :body {:api-version (semver/get-semver)
          :message "Not Implemented! TODO!"}})

(defn show-params [req]
  {:status 200
   :body {:params (-> req :params)
          :parameters (-> req :parameters)
          :query-params (-> req :query-params)
          :query (-> req :query)
          :headers (-> req :headers)}})

; end request response helpers


; begin media resources helpers
(defn- get-media-resource
  ([request]
   (catcher/with-logging {}
     (or (get-media-resource request :media_entry_id "media_entries" "MediaEntry")
         (get-media-resource request :collection_id "collections" "Collection"))))
  ([request id-key table-name type]
   (when-let [id (or (-> request :params id-key) (-> request :parameters :path id-key))]
     (logging/info "get-media-resource" "\nid\n" id)
     (when-let [resource (-> (jdbc/query (get-ds)
                                         [(str "SELECT * FROM " table-name "
                                               WHERE id = ?") id]) first)]
       (assoc resource :type type :table-name table-name)))))


(defn- ring-add-media-resource-preview [request handler]
  (if-let [media-resource (get-media-entry-for-preview request)]
    (let [mmr (assoc media-resource :type "MediaEntry" :table-name "media_entries")
          request-with-media-resource (assoc request :media-resource mmr)]
      (handler request-with-media-resource))
    (response_not_found "No media-resource for preview")))


(defn- ring-add-media-resource [request handler]
  (if-let [media-resource (get-media-resource request)]
    (let [request-with-media-resource (assoc request :media-resource media-resource)]
      ;(logging/info "ring-add-media-resource" "\nmedia-resource\n" media-resource)
      (handler request-with-media-resource))
    {:status 404}))


; end media resources helpers

; begin meta-data helpers

(defn query-meta-datum [request]
  (let [id (or (-> request :params :meta_datum_id) (-> request :parameters :path :meta_datum_id))]
    (logging/info "query-meta-datum" "\nid\n" id)
    (or (-> (jdbc/query (get-ds)
                        [(str "SELECT * FROM meta_data "
                              "WHERE id = ? ") id])
            first)
        (throw (IllegalStateException. (str "We expected to find a MetaDatum for "
                                            id " but did not."))))))

(defn- query-media-resource-for-meta-datum [meta-datum]
  (or (when-let [id (:media_entry_id meta-datum)]
        (get-media-resource {:params {:media_entry_id id}}
                            :media_entry_id "media_entries" "MediaEntry"))
      (when-let [id (:collection_id meta-datum)]
        (get-media-resource {:params {:collection_id id}}
                            :collection_id "collections" "Collection"))
      (throw (IllegalStateException. (str "Getting the resource for "
                                          meta-datum "
                                          is not implemented yet.")))))


(defn- ring-add-meta-datum-with-media-resource [request handler]
  (if-let [meta-datum (query-meta-datum request)]
    (let [media-resource (query-media-resource-for-meta-datum meta-datum)]
      (logging/info "add-meta-datum-with-media-resource" "\nmeta-datum\n" meta-datum "\nmedia-resource\n" media-resource)
      (handler (assoc request
                      :meta-datum meta-datum
                      :media-resource media-resource)))
    (handler request)))

; end meta-data helpers

; begin media-resource auth helpers

(defn- public? [resource]
  (-> resource :get_metadata_and_previews boolean))

(defn- authorize-request-for-handler [request handler]
  (if-let [media-resource (:media-resource request)]
    (if (public? media-resource)
      (handler request)
      (if-let [auth-entity (:authenticated-entity request)]
        (if (authorized? auth-entity media-resource)
          (handler request)
          {:status 403 :body {:message "Not authorized for media-resource"}})
        {:status 401 :body {:message "Not authorized"}}))
    (let [response  {:status 500 :body {:message "No media-resource in request."}}]
      (logging/warn 'authorize-request-for-handler response [request handler])
      response)))

; end media-resource auth helpers

; begin json query param helpers

(defn try-as-json [value]
  (try (cheshire/parse-string value)
       (catch Exception _
         value)))

(defn- *ring-wrap-parse-json-query-parameters [request handler]
  ;((assoc-in request [:query-params2] (-> request :parameters :query))
  (handler (assoc request :query-params
                  (->> request :query-params
                       (map (fn [[k v]] [k (try-as-json v)]))
                       (into {})))))

; end json query param helpers

; begin wrappers

(defn ring-wrap-add-media-resource-preview [handler]
  (fn [request]
    (ring-add-media-resource-preview request handler)))

(defn ring-wrap-add-media-resource [handler]
  (fn [request]
    (ring-add-media-resource request handler)))

(defn ring-wrap-add-meta-datum-with-media-resource [handler]
  (fn [request]
    (ring-add-meta-datum-with-media-resource request handler)))

(defn ring-wrap-authorization [handler]
  (fn [request]
    (authorize-request-for-handler request handler)))

(defn ring-wrap-parse-json-query-parameters [handler]
  (fn [request]
    (*ring-wrap-parse-json-query-parameters request handler)))

;end wrappers

; begin swagger docu summary helpers
(def s_req_adm "Requires role: admin.")
(def s_cnv_acc "Convenience access.")

(defn sum_todo [text] (apply str "TODO: " text))

(defn sum_adm [text] (apply str text " " s_req_adm))
(defn sum_cnv [text] (apply str text " " s_cnv_acc))

(defn sum_cnv_todo [text] (sum_todo (sum_cnv text)))
(defn sum_adm_todo [text] (sum_todo (sum_adm text)))
(defn sum_cnv_adm_todo [text] (sum_todo (sum_cnv (sum_adm text))))
; end swagger docu summary helpers