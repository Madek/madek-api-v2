(ns madek.api.resources.previews.index
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc :as jdbc]))

(defn- get-first-or-30-percent [list]
  (if (> (count list) 1)
    (nth list (min (Math/ceil (* (/ (count list) 10.0) 3)) (- (count list) 1)))
    (first list)))

(defn- detect-ui-preview-id [sqlmap media-type tx]
  (if (= media-type "video")
    (let [query (-> sqlmap
                    (sql/where [:= :previews.media_type "image"]
                               [:= :previews.thumbnail "large"])
                    sql-format)]
      (let [previews (jdbc/execute! tx query)]
        (:id (get-first-or-30-percent previews))))
    nil))

(defn- add-preview-pointer-to [previews detected-id]
  (map #(if (= (:id %) detected-id) (assoc % :used_as_ui_preview true) %) previews))

(defn get-index [media-file tx]
  (let [sqlmap (-> (sql/select :previews.*)
                   (sql/from [:previews :previews])
                   (sql/join [:media_files :media_files] [:= :previews.media_file_id :media_files.id])
                   (sql/join [:media_entries :media_entries] [:= :media_entries.id :media_files.media_entry_id])
                   (sql/where [:= :previews.media_file_id (:id media-file)])
                   (sql/order-by [:previews.id :asc] [:media_files.id :asc]))]
    (let [detected-id (detect-ui-preview-id sqlmap (:media_type media-file) tx)]
      (add-preview-pointer-to
       (jdbc/execute! tx (-> sqlmap sql-format))
       detected-id))))

(def ^:private tier-order
  #{"maximum" "x_large" "large" "medium" "small"})

(defn normalize-media-size
  "Normalize ``media_size`` query (e.g. x-large → x_large)."
  [raw]
  (when-let [s (not-empty (clojure.string/trim (str raw)))]
    (let [k (-> s clojure.string/lower-case (clojure.string/replace "-" "_"))]
      (cond
        (= k "xlarge") "x_large"
        (tier-order k) k
        :else nil))))

(defn- tier-preference-order
  [pref]
  (let [p (or (normalize-media-size pref) "x_large")]
    (into [p] (remove #{p} ["maximum" "x_large" "large" "medium" "small"]))))

(defn filter-by-media-size
  "Keep previews for requested tier; fallback to nearest tier in preference order."
  [previews media-size]
  (if-let [pref (normalize-media-size media-size)]
    (let [exact (filter #(= (:thumbnail %) pref) previews)]
      (if (seq exact)
        (vec exact)
        (or (some (fn [t]
                    (let [tier (filter #(= (:thumbnail %) t) previews)]
                      (when (seq tier) (vec tier))))
                  (tier-preference-order pref))
            (vec previews))))
    (vec previews)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
