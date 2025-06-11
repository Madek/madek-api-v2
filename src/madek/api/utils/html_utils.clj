(ns madek.api.utils.html-utils
  (:require [clojure.walk :as walk]
            [hickory.core :as h]
            [hickory.render :as render]
            [taoensso.timbre :refer [error]]))

(defn add-meta-tag [tree csrf-name csrf-value]
  (walk/postwalk
   (fn [node]
     (if (and (map? node) (= (:tag node) :head))
       (update node :content conj {:type :element
                                   :tag :meta
                                   :attrs {:name csrf-name :content csrf-value}})
       node))
   tree))

(defn update-csrf-input [tree csrf-value]
  (walk/postwalk
   (fn [node]
     (if (and (map? node)
              (= (:tag node) :input)
              (= (get-in node [:attrs :name]) "csrfToken")
              (= (get-in node [:attrs :type]) "hidden"))
       (assoc-in node [:attrs :value] csrf-value)
       node))
   tree))

(defn add-form-if-missing [tree csrf-name csrf-value]
  (walk/postwalk
   (fn [node]
     (if (and (map? node) (= (:tag node) :body))
       (if (empty? (filter #(= (:tag %) :form) (:content node)))
         (update node :content conj {:type :element
                                     :tag :form
                                     :attrs {:name csrf-name}
                                     :content [{:type :element
                                                :tag :input
                                                :attrs {:type "hidden" :name csrf-name :value csrf-value}}]})
         node)
       node))
   tree))

(defn add-csrf-tags
  [html-str {:keys [authFlow csrfToken]}]
  (try
    (let [parsed-html (h/parse html-str)
          hickory-tree (h/as-hickory parsed-html)
          csrf-name (:name csrfToken)
          csrf-value (:value csrfToken)
          updated-tree (as-> hickory-tree $
                         (add-meta-tag $ csrf-name csrf-value)
                         (update-csrf-input $ csrf-value)
                         (add-form-if-missing $ csrf-name csrf-value))
          raw-html (render/hickory-to-html updated-tree)]
      raw-html)

    (catch Exception e
      (error "Error in add-csrf-and-return-tags:" (.getMessage e))
      (.printStackTrace e)
      html-str)))
