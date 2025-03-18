(ns madek.api.utils.request
  (:require [clojure.string :as str]))

(def AUTHENTICATED_ENTITY :authenticated-entity)

(defn authenticated? [request]
  (-> request
      AUTHENTICATED_ENTITY
      boolean))

(defn get-auth-entity [request]
  (-> request
      AUTHENTICATED_ENTITY))

(defn not-authenticated? [request]
  (-> request
      AUTHENTICATED_ENTITY
      boolean not))

(defn path-params [request]
  (-> request :parameters :path))

(defn query-params [request]
  (-> request :parameters :query))

(defn body-params [request]
  (-> request :parameters :body))
