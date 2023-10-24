(ns madek.api.utils.cli
  (:require
    [cuerdas.core :as string :refer [snake kebab upper human]]
    ))

(defn long-opt-for-key [k]
  (str "--" (kebab k) " " (-> k snake upper)))
