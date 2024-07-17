(ns madek.api.authentication.md5
  (:require
   ; [cider-ci.open-session.bcrypt :refer [checkpw]]
   ; ;[buddy.core.codecs :as codecs]
   ; [buddy.core.hash :as hash]

   [buddy.core.codecs :refer :all]
   [buddy.hashers :as hashers]
   ;          [buddy.hashers :as hashers]
   ; ;[clojure.java.io :as io]
   ; ;[clojure.java.nio.charset :as charset]
   ;          ))

   ;(:require
   [buddy.hashers :as hashers]
   [clojure.string :as str]
   [digest :refer [md5]]
   )
  (:import
   ;(org.apache.commons.codec.digest DigestUtils Md5Crypt)
   ;
   ;        (org.apache.commons.codec.digest DigestUtils)
   ;        (java.nio.charset StandardCharsets)
           (org.apache.commons.codec.digest Md5Crypt))

  )

;(def charset "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
;
;(defn apr1-base64-encode [input]
;      (let [byte-seq (map byte input)
;            encode-triplet (fn [b2 b1 b0]
;                             (str
;                              (nth charset (bit-and (bit-shift-right b2 2) 0x3f))
;                              (nth charset (bit-and (bit-or (bit-shift-left (bit-and b2 0x03) 4)
;                                                      (bit-shift-right b1 4)) 0x3f))
;                               (nth charset (bit-and (bit-or (bit-shift-left (bit-and b1 0x0f) 2)
;                                                       (bit-shift-right b0 6)) 0x3f))
;                               (nth charset (bit-and b0 0x3f))))]
;        (apply str (map #(apply encode-triplet %)
;                     (partition 3 3 [0 0] byte-seq)))))
;
;(defn apr1-hash [password salt]
;      (let [magic "$apr1$"
;            salt (subs salt 0 8)
;            context (str magic salt)
;            initial (str password context (apply str (repeat (count password) password)))
;            final (loop [i (count password)
;                         hash (hash/md5 (.getBytes initial "UTF-8"))]
;                    (if (pos? i)
;                      (recur (dec i) (hash/md5 (into hash (.getBytes password "UTF-8"))))
;                      (apr1-base64-encode hash)))]
;        (str magic salt "$" final)))
;
;(defn verify-hash [password hashed]
;      (let [[_ _ salt _] (str/split hashed #"\$")
;            new-hash (apr1-hash password salt)]
;        (= hashed new-hash)))

;; Usage

;(defn verify-apr1-password
;  [plain-password apr1-hash]
;  (let [computed-hash (Md5Crypt/apr1Crypt plain-password (second (re-find #"\$apr1\$[^$]+" apr1-hash)))]
;    (= computed-hash apr1-hash)))
;
;(defn verify-apr1-password
;  [plain-password hashed-password]
;  (let [salt (second (re-find #"\$apr1\$[^$]+" hashed-password))
;        computed-hash (Md5Crypt/md5Crypt (.getBytes plain-password StandardCharsets/UTF_8) salt)]
;    (= computed-hash hashed-password)))
;
;(defn verify-password
;  [plain-password hashed-password]
;  (hashers/check plain-password hashed-password {:alg :md5}))
;
;;; Example usage:
;(def plain-password "your-password")
;(def hashed-password "$apr1$Dfs2vLP$CQe6t5y8f7JtRE2BSjP/B0") ;; Example MD5 hashed password from htpasswd
;
;;(println (verify-password plain-password hashed-password))
;;
;;(def test-hash "$apr1$EtuvpScm$sMLTA.JaMN9eJKVhjqQCQ0")
;;(def password "test")
;
;
;;(:import [org.apache.commons.codec.digest DigestUtils]))
;
;(defn md5-verify
;  [input expected-hash]
;  (let [calculated-hash (DigestUtils/md5Hex input)]
;    (= calculated-hash expected-hash)))


(defn verify-md5-crypt [password hash]
  (let [[_ salt rest] (str/split hash #"\$")
        expected-hash (.substring rest 1)]
    ;(let [computed-hash (Md5Crypt/apr1Crypt (.toCharArray password) salt)]
    (let [computed-hash (Md5Crypt/apr1Crypt (.toByteArray password) (str salt))]
      (= computed-hash hash))))

(defn verify-md5-crypt [password hash]
  (let [[_ _ salt] (str/split hash #"\$" 4) ; Split the hash and extract the salt
        salt (str "$apr1$" salt) ; Reconstruct the salt with the apr1 prefix
        computed-hash (Md5Crypt/apr1Crypt (.getBytes password "UTF-8") salt)] ; Compute the hash using UTF-8 encoding
    (= computed-hash hash))) ; Compare the computed hash with the given hash


;; Example usage:
;(def input "password")
;(def expected-hash "5f4dcc3b5aa765d61d8327deb882cf99") ;; MD5 hash for "password"
;(println (md5-verify input expected-hash)) ;; true

;; create main
(defn -main [& args]
  ;(println (verify-hash password test-hash))
  ;(checkpw "test" "$apr1$EtuvpScm$sMLTA.JaMN9eJKVhjqQCQ0")

  (let [
        ;db-hash (checkpw "test" "$2a$10$Qa8Mvdwg1KpSqFvvwoex7ec4zl0PfStw9SrMIy8S5g9/P37XDssEG")
        ;p (println ">o> db-hash=" db-hash)

        ;md5-res (verify-password "test" "$apr1$EtuvpScm$sMLTA.JaMN9eJKVhjqQCQ0")
        ;md5-res (verify-password "test" "$apr1$Mp2PFSM5$ak3NURmcEcmxU.OxTO2yu1")
        ;md5-res (md5-verify "Madek" "$apr1$Mp2PFSM5$ak3NURmcEcmxU.OxTO2yu1")
        md5-res (verify-md5-crypt "test" "$apr1$EtuvpScm$sMLTA.JaMN9eJKVhjqQCQ0")
        p (println ">o> md5-res=" md5-res)

        ])

  )                                                         ; Should print true if the password matches
