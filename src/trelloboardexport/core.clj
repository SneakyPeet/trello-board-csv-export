(ns trelloboardexport.core
  (:require [org.httpkit.client :as http]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clojure.core.async :as async
              :refer [>! <! >!! <!! go chan close! promise-chan]])
  (:gen-class))

;; request helpers
(def endpoint "https://api.trello.com/1/")

(defn create-endpoint
  "params in the form 'bar=foo'"
  [resource queryparams]
  (str endpoint resource "?" (s/join "&" queryparams)))

(defn create-fetch [board auth-params]
  (fn [resource params]
    (let [r (str "board/" board "/" resource)
          p (into auth-params params)]
      (->> (create-endpoint r p)
           http/get
           deref))))

(defn ->auth-params [key token]
  [(str "key=" key)
   (str "token=" token)])

(defn ->field-param [f, o]
  (str f "=" (s/join "," o)))

(def card-fields ["name" "dateLastActivity" "labels" "idMembers" "pos" "shortUrl"])
(def list-fields ["name" "pos"])
(def list-params [ "cards=open" (->field-param "card_fields" card-fields) (->field-param "fields" list-fields)])
(def member-fields ["fullName"])
(def member-params [ (->field-param "fields" member-fields)])

;;parsedata

(defn list->cards [{:keys [name cards pos]}]
  (map #(merge % {:list name :listPos pos}) cards))

(defn create-member-mapper [members]
  (let [idmap (reduce #(assoc %1 (keyword (:id %2)) (:fullName %2)) {} members)]
    (fn [card]
      (->> card
           :idMembers
           (map keyword)
           (map #(% idmap))
           (s/join " | ")
           (assoc card :members)))))

(defn flatten-labels [card]
  (->> card
       :labels
       (map :name)
       (s/join " | ")
       (assoc card :labels)))

(def csv-cols [:listPos :list :name :dateLastActivity :labels :members  :shortUrl :pos])

(defn ->csv [rows]
  (mapv #(mapv % csv-cols) rows))

(defn process-lists [delimiter lists members]
  (let [map-members (create-member-mapper members)]
    (->> lists
         (map list->cards)
         flatten
         (map map-members)
         (map flatten-labels)
         ->csv
         (cons (map name csv-cols))
         (map #(s/join delimiter %))
         (s/join \newline))))


(defn parse-json [b] (json/read-str b :key-fn keyword))

(defn parse [r]
  (parse-json (:body r)))

(defn process-board [delimiter {:keys [lists members]}]
  (let [failed (filter #(not= 200 (:status %)) [lists members])]
    (if (empty? failed)
      (process-lists delimiter (parse lists) (parse members))
      (str "ERRORS " (s/join ", " (set (map :body failed)))))))

;side effects

(defn slurp-board! [fetch]
  (let [lists (promise-chan)
        members (promise-chan)]
    (go (>! lists (fetch "lists" list-params)))
    (go (>! members (fetch "members" member-params)))
    (hash-map :lists (<!! lists) :members (<!! members))))

(defn -main
  [key token board delimiter]
  (->> (->auth-params key token)
       (create-fetch board)
       slurp-board!
       (process-board delimiter)
       (spit "output.csv")
       (println "see output.csv for details")))
