(ns madek.api.utils.json
  (:require
   #?(:clj [cheshire.core])
   [clojure.walk]))

#?(:clj (extend-protocol cheshire.generate/JSONable
          java.time.Instant
          (to-json [dt gen]
            (cheshire.generate/write-string gen (str dt)))))

(defn to-json [d]
  #?(:clj (cheshire.core/generate-string d)
     :cljs (js/JSON.stringify d)))

(def encode to-json)

(defn from-json [s]
  (clojure.walk/keywordize-keys
   #?(:clj (cheshire.core/parse-string s)
      :cljs (-> s js/JSON.parse js->clj))))

(def decode from-json)

(defn try-parse-json [x]
  (try
    (from-json x)
    (catch #?(:cljs js/Object
              :clj Exception) _
      x)))
