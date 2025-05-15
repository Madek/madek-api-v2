(ns madek.api.utils.html-utils
;(ns leihs.inventory.server.utils.html-utils
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [hickory.core :as h]
            [hickory.render :as render]
            [hickory.select :as s]))

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

(defn add-return-field [tree return-field]
  (walk/postwalk
   (fn [node]
     (if (and (map? node)
              (= (:tag node) :form))
       (if (not (some #(and (map? %) (= (:tag %) :input) (= (get-in % [:attrs :name]) "return-to")) (:content node)))
         (update node :content conj return-field)
         node)
       node))
   tree))

(defn add-error-message-field [tree error-message-field]
  (walk/postwalk
   (fn [node]
     (if (and (map? node)
              (= (:tag node) :form))
       (if (not (some #(and (map? %) (= (:tag %) :input) (= (get-in % [:attrs :class]) "message")) (:content node)))
         (update node :content conj error-message-field)
         node)
       node))
   tree))

(defn add-csrf-tags
  [html-str {:keys [authFlow csrfToken]}]
  (try
    (let [parsed-html (h/parse html-str)
          hickory-tree (h/as-hickory parsed-html)
          returnTo (:returnTo authFlow)
          csrf-name (:name csrfToken)
          csrf-value (:value csrfToken)
          return-field {:type :element
                        :tag :input
                        :attrs {:type "hidden" :name "return-to" :value returnTo}}
          updated-tree (as-> hickory-tree $
                         (add-meta-tag $ csrf-name csrf-value)
                         (update-csrf-input $ csrf-value)
                         (add-form-if-missing $ csrf-name csrf-value))
          raw-html (render/hickory-to-html updated-tree)]
      raw-html)

    (catch Exception e
      (println "Error in add-csrf-and-return-tags:" (.getMessage e))
      (.printStackTrace e)
      html-str)))

(defn add-or-create-return-to-tag
  [html-str {:keys [authFlow csrfToken]}]
  (try
    (let [parsed-html (h/parse html-str)
          hickory-tree (h/as-hickory parsed-html)
          csrf-name (:name csrfToken)
          csrf-value (:value csrfToken)
          returnTo (:returnTo authFlow)
          return-field {:type :element
                        :tag :input
                        :attrs {:type "hidden" :name "return-to" :value returnTo}}
          updated-tree (add-return-field hickory-tree return-field)
          html (render/hickory-to-html updated-tree)]
      html)

    (catch Exception e
      (println "Error in add-csrf-tags:" (.getMessage e))
      (.printStackTrace e)
      html-str)))

(defn add-or-create-error-tag
  [html-str {:keys [authFlow csrfToken]}]
  (try
    (let [parsed-html (h/parse html-str)
          hickory-tree (h/as-hickory parsed-html)
          csrf-name (:name csrfToken)
          csrf-value (:value csrfToken)
          returnTo (:returnTo authFlow)
          errorMessage (:errorMessage authFlow)
          errorMessageField {:type :element
                             :tag :div
                             :attrs {:class "message" :value errorMessage}}
          updated-tree (add-error-message-field hickory-tree errorMessageField)
          html (render/hickory-to-html updated-tree)]
      html)

    (catch Exception e
      (println "Error in add-csrf-tags:" (.getMessage e))
      (.printStackTrace e)
      html-str)))
