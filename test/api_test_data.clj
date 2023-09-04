(ns api-test-data
  (:require [cider-ci.open-session.bcrypt :refer [hashpw]]))


(def person1id "9404fdf4-13d6-4d9d-a7e4-2912bb9d7b39")
(def user1id "b657884f-cb08-4443-8351-78b726ac7b13")
(def auth1id "abe6711b-dcda-4f83-812c-8b6fe980e7e7")
(def admin1id "dc3a0889-2347-444d-8213-617bd4a1bd18")

(def person2id "9404fdf4-13d6-4d9d-a7e4-2912bb9d7b34")
(def user2id "b657884f-cb08-4443-8351-78b726ac7b17")
(def auth2id "5e9d3578-27af-434c-841d-cd8566ddadc2")

(def person1
  {:id person1id
   :subtype "Person"
   :first_name "fnp1"
   :last_name "lnp1"
   
   :description "desc"})

(def user1
  {:id user1id
   :person_id person1id
   :login "u1login"
   :email "u1@aliebaba.de"
   :accepted_usage_terms_id "543b49cc-ea69-4e6c-b3f5-1643d80da24b"
   ;:password_digest (hashpw "user1pw")
   })

(def auth1
  {:id auth1id
   :auth_system_id "password"
   :data  (hashpw "user1pw")
   :user_id user1id})

(def person2
  {:id person2id
   :subtype "Person"
   :first_name "fnp2"
   :last_name "lnp2"
   :description "desc2"})

(def user2
  {:id user2id
   :person_id person2id
   :login "u2login"
   :email "u2@aliebaba.de"
   :accepted_usage_terms_id "543b49cc-ea69-4e6c-b3f5-1643d80da24b"
   ;:password_digest (hashpw "user2pw")
   })

(def auth2
  {:id auth2id
   :auth_system_id "password"
   :data  (hashpw "user2pw")
   :user_id user2id})


(def admin1 {:id admin1id :user_id user1id})

(def dburldev {:url "postgresql://localhost:5432/madek_development" :user "madek_sql" :password "madek_sql"})
(def dburltest {:url "postgresql://localhost:5432/madek_test" :user "madek_sql" :password "madek_sql"})



; asdf add-plugin postgres
; asdf install postgres

; aptitude install linux-headers-$ (uname -r) build-essential libssl-dev libreadline-dev zlib1g-dev libcurl4-openssl-dev uuid-dev icu-devtool
; apt-get install build-essential libssl-dev libreadline-dev zlib1g-dev libcurl4-openssl-dev uuid-dev
; wget http://nz2.archive.ubuntu.com/ubuntu/pool/main/o/openssl/libssl1.1_1.1.1f-1ubuntu2.17_amd64.deb
; dpkg -i libssl1.1_1.1.1f-1ubuntu2.17_amd64.deb
