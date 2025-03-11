(ns madek.api.resources.shared.json_query_param_helper
  (:require [cheshire.core :as cheshire]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.media_resource_helper :as mrh]
            [madek.api.resources.shared.meta_data_helper :as mdh]))

(defn generate-swagger-pagination-params []
  {:produces "application/json"
   :parameters [{:name "page"
                 :in "query"
                 :description "Page number, defaults to 1"
                 :required true
                 :value 1
                 :default 1
                 :type "number"
                 :pattern "^[1-9][0-9]*$"}
                {:name "count"
                 :in "query"
                 :description "Number of items per page, defaults to 100"
                 :required true
                 :value 100
                 :default 100
                 :type "number"
                 :pattern "^[1-9][0-9]*$"}]})

; begin json query param helpers

(defn try-as-json
  ([value]
   (try (cheshire/parse-string value)
        (catch Exception _
          value)))
  ([value default]
   (try (cheshire/parse-string value)
        (catch Exception _
          (cheshire/parse-string default)))))

(defn map-as-json!
  ([value]
   (try
     (cond
       (not (map? value)) (throw (Exception. "Value is not a map")))
     (cheshire/parse-string (cheshire/generate-string value))
     (catch Exception _
       (throw (ex-info "Value is not in a valid JSON format" {:status 400})))))
  ([value key]
   (try
     (when (not (map? value)) (throw (Exception. "Value is not a map")))
     (cheshire/parse-string (cheshire/generate-string value))
     (catch Exception _
       (throw (ex-info (str "Value is not in a valid JSON format: " (name key) "=" value) {:status 400}))))))

(defn- *ring-wrap-parse-json-query-parameters [request handler]
  (handler (assoc request :query-params
                  (->> request :query-params
                       (map (fn [[k v]] [k (try-as-json v)]))
                       (into {})))))

; end json query param helpers

; begin wrappers

(defn ring-wrap-add-media-resource [handler]
  (fn [request]
    (mrh/ring-add-media-resource request handler (:tx request))))

(defn ring-wrap-add-meta-datum-with-media-resource [handler]
  (fn [request]
    (mdh/ring-add-meta-datum-with-media-resource request handler)))

(defn ring-wrap-authorization-view [handler]
  (fn [request]
    (mrh/authorize-request-for-media-resource request handler :view)))

(defn ring-wrap-authorization-download [handler]
  (fn [request]
    (mrh/authorize-request-for-media-resource request handler :download)))

(defn ring-wrap-authorization-edit-metadata [handler]
  (fn [request]
    (mrh/authorize-request-for-media-resource request handler :edit-md)))

(defn ring-wrap-authorization-edit-permissions [handler]
  (fn [request]
    (mrh/authorize-request-for-media-resource request handler :edit-perm)))

(defn ring-wrap-parse-json-query-parameters [handler]
  (fn [request]
    (*ring-wrap-parse-json-query-parameters request handler)))

(defn wrap-check-valid-meta-key [param]
  (fn [handler]
    (fn [request]
      (let [meta-key-id (-> request :parameters :path param)]
        (if (re-find #"^[a-z0-9\-\_\:]+:[a-z0-9\-\_\:]+$" meta-key-id)
          (handler request)
          (sd/response_failed (str "Wrong meta_key_id format! See documentation."
                                   " (" meta-key-id ")") 422))))))

(defn wrap-check-valid-meta-key-new [param]
  (fn [handler]
    (fn [request]
      (let [meta-key-id (-> request :path-params param)]
        (if (:and (not (nil? meta-key-id)) (re-find #"^[a-z0-9\-\_\:]+:[a-z0-9\-\_\:]+$" meta-key-id))
          (handler request)
          (sd/response_failed (str "Wrong meta_key_id format! See documentation."
                                   " (" meta-key-id ")") 422))))))

;(debug/debug-ns *ns*)
