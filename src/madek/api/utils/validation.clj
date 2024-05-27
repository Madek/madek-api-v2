(ns madek.api.utils.validation
  (:require [clojure.data.json :as json]
            [schema.core :as s]))

;; Define a regular expression for email validation
(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$")

; [madek.api.utils.validation :refer [email-validation]]
(def email-validation
  (s/constrained s/Str #(re-matches email-regex %)
                 "Invalid email address"))

(s/defn valid-vector-or-hashmap?
  ([json-str]
   (or (instance? clojure.lang.PersistentVector json-str) (instance? clojure.lang.PersistentHashMap json-str) (instance? clojure.lang.PersistentArrayMap json-str))))

(s/defn valid-json?
  ([json-str allow-str-only]
   (if (and (not (= true allow-str-only)) (or (instance? clojure.lang.PersistentVector json-str) (instance? clojure.lang.PersistentHashMap json-str)))
     true
     (try
       (json/read-str json-str)
       (catch Exception e
         false))))

  ([json-str]
   (valid-json? json-str true)))

(defn number-within-range? [number min-value max-value]
  (and (<= min-value number)
       (<= number max-value)))

(defn parse-str-to-int [s]
  (try
    (Integer/parseInt (str s))
    (catch NumberFormatException e
      nil)
    (catch Exception e
      nil)))

(defn valid-greater-equal-zero? [value]
  (let [value (parse-str-to-int value)
        res (and (not (nil? value)) (>= value 0))]
    res))

(defn valid-greater-zero? [value]
  (let [value (parse-str-to-int value)
        res (and (not (nil? value)) (> value 0))]
    res))

(defn valid-positive-number-min-to-max? [value min max]
  (let [value (parse-str-to-int value)
        res (and (int? value) (number-within-range? value min max))]
    res))

(def positive-number-0-to-100-validation
  (s/constrained s/Int (fn [x] (valid-positive-number-min-to-max? x 0 100)) "Invalid Number, 0-100"))

; [madek.api.utils.validation :refer [greater-zero-validation greater-equal-zero-validation]]
(def greater-zero-validation
  (s/constrained s/Int valid-greater-zero? "Invalid Number, required: value > 0"))

(def greater-equal-zero-validation
  (s/constrained s/Int valid-greater-equal-zero? "Invalid Number, required: value >= 0"))

(def positive-number-1-to-1000-validation
  (s/constrained s/Int (fn [x] (valid-positive-number-min-to-max? x 1 1000)) "Invalid Number, 1-1000"))

(def json-str-validation
  (s/constrained s/Any valid-json? "Invalid JSON-STRING format"))

(def vector-or-hashmap-validation
  (s/constrained s/Any valid-vector-or-hashmap? "Invalid JSON format"))
