(ns backend.football
  (:require [aleph.http :as http]
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
    (get competitions id)))

(defn read-json-stream [stream]
  (json/read (io/reader stream) :key-fn keyword))

(defn parse-body [response]
  (let [content-type (get-in response [:headers "content-type"])]
    (if (and (contains? response :body) (-> content-type
                                            str/trim
                                            (str/starts-with? "application/json")))
      (update response :body read-json-stream)
      response)))

(defn fetch [url token]
  (log/debug (str "FETCH " url))
  (let [{:keys [status body] :as response} (parse-body @(http/get url {:headers {"X-Auth-Token" token}
                                                                       :throw-exceptions false}))]
    (log/debug (str "FETCH " url " => " status) )
    (if (= 200 status)
      {:status :ok :body body}
      {:status :error :response response})))

(defn transform-team [team]
  (-> team
      (select-keys [:name :code :shortName :crestUrl])
      (set/rename-keys {:shortName :short-name :crestUrl :crest-url})))

(defn transform-player [player]
  (-> player
      (dissoc :marketValue)
      (set/rename-keys {:jerseyNumber :number :dateOfBirth :date-of-birth :contractUntil :contract-until})))

(defn fetch-competition [url token id]
  (letfn [(assoc-players [team]
            (let [team-name (:name team)
                  assoc-team-name #(assoc % :team-name team-name)
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
                {:status :ok
                 :teams (map transform-team teams)
                 :players (mapcat :body responses)}
                {:status :error
                 :context :retrieving-players
                 :responses failures
                 :teams teams}))
        :error response))))

(defrecord HttpFootballRepo [url token cache]
  FootballRepo
  (competition [this id]
    (let [state (swap! cache #(if (cache/has? % id)
                                (cache/hit % id)
                                (cache/miss % id (fetch-competition url token id))))]
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
                                  :cache (atom (cache/ttl-cache-factory {} :ttl (* 5 60 1000)))})))

(s/def :backend/football-repo-type #{:static :http})
(s/def :backend/football-competitions string?)
(s/def :backend/football-api-url string?)
(s/def :backend/football-api-token string?)

(defmulti repo-type :backend/football-repo-type)
(defmethod repo-type :static [_] (s/keys :req [:backend/football-competitions]))
(defmethod repo-type :http [_] (s/keys :req [:backend/football-api-url :backend/football-api-token]))
(s/def :backend/football-config (s/multi-spec repo-type :backend/football-repo-type))
