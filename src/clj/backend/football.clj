(ns backend.football
  (:require [aleph.http :as http]
            [backend.util :as util]
            [clojure.core.cache :as cache]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defprotocol FootballRepo
  (competition [this id] ""))

(defrecord StaticFootballRepo [competitions]
  FootballRepo
  (competition [this id]
    (assoc (get competitions id) :status :ok)))

(defn read-json-stream [stream]
  (json/read (io/reader stream) :key-fn keyword))

(defn parse-body [response]
  (let [content-type (get-in response [:headers "content-type"])]
    (if (and (contains? response :body) (-> content-type
                                            str/trim
                                            (str/starts-with? "application/json")))
      (update response :body read-json-stream)
      response)))

(def ^:dynamic *initial-wait* 0)
(def ^:dynamic *wait-increase* 200)
(def ^:dynamic *max-attempts* 20)

(defn retry?
  [status]
  (contains? #{403 429} status))

(defn fetch [url token]
  (log/debug (str "FETCH " url))
  (loop [i 1
         wait *initial-wait*]
    (let [response (parse-body @(http/get url
                                          {:headers {"X-Auth-Token" token}
                                           :throw-exceptions false}))
          {:keys [status body]} response]
      (if (retry? status)
        (let [label (str "FETCH => " status " => ")]
          (if (>= i *max-attempts*)
            (do (log/error (str label "Giving up after " *max-attempts* " attempts."))
                {:status :error
                 :response response
                 :max-attempts *max-attempts*})
            (do (log/warn (str label "Attempt " i " of " *max-attempts* " rejected. Sleeping for " wait " ms."))
                (Thread/sleep wait)
                (recur (inc i) (+ wait *wait-increase*)))))
        (if (= 200 status)
          (do (log/debug (str "FETCH => " status))
              {:status :ok :body body})
          (do (log/debug (str "FETCH => " status))
              {:status :error :response response}))))))

(defn transform-team [team]
  (select-keys team [:name :code :shortName :crestUrl]))

(defn transform-player [dirty-player]
  (let [player (-> dirty-player
                   (dissoc :marketValue))]
    (-> player
        (dissoc :marketValue)
        (assoc :nameWithoutDiacritics (util/remove-diacritics (:name player))))))

(defn fetch-competition [url token id]
  (letfn [(assoc-players [team]
            (let [team-name (:name team)
                  assoc-team-name #(assoc % :teamName team-name)
                  player-url (-> team :_links :players :href)
                  response (fetch player-url token)]
              (if (= (:status response) :ok)
                (update response :body #(map (comp transform-player assoc-team-name) (:players %)))
                response)))]
    (let [{:keys [status body] :as response} (fetch (str url "/competitions/" id "/teams") token)]
      (case status
        :ok (let [teams (:teams body)
                  responses (map assoc-players teams)
                  failures (filter #(not= (:status %) :ok) responses)]
              (if (empty? failures)
                (let [teams (map transform-team teams)
                      players (mapcat :body responses)]
                  (log/debug (str "Found " (count teams) " teams with " (count players) " players for competition " id "."))
                  {:status :ok :teams teams :players players})
                {:status :error :context :retrieving-players :responses failures :teams teams}))
        :error response))))

(defrecord HttpFootballRepo [url token cache]
  FootballRepo
  (competition [this id]
    (let [state (swap! cache #(if (cache/has? % id)
                                (do (log/debug (str "Cache hit: " id))
                                    (cache/hit % id))
                                (let [response (fetch-competition url token id)]
                                  (if (= (:status response) :ok)
                                    (do (log/debug (str "Cache miss: " id))
                                        (cache/miss % id response))
                                    (do (log/debug (str "Operation failed - not caching: " id))
                                        response)))))]
      (get state id))))

(defn repo
  [{:keys [:backend/football-repo-type
           :backend/football-competitions
           :backend/football-api-url
           :backend/football-api-token] :as config}]
  (case football-repo-type
    :static (map->StaticFootballRepo {:competitions football-competitions})
    :http (map->HttpFootballRepo {:url football-api-url
                                  :token football-api-token
                                  :cache (atom (cache/ttl-cache-factory {} :ttl (* 48 60 60 1000)))})))

(s/def :backend/football-repo-type #{:static :http})
(s/def :backend/football-competitions map?)
(s/def :backend/football-api-url string?)
(s/def :backend/football-api-token string?)

(defmulti repo-type :backend/football-repo-type)
(defmethod repo-type :static [_] (s/keys :req [:backend/football-competitions]))
(defmethod repo-type :http [_] (s/keys :req [:backend/football-api-url :backend/football-api-token]))
(s/def :backend/football-config (s/multi-spec repo-type :backend/football-repo-type))
