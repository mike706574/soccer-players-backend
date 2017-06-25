(ns backend.things
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defprotocol ThingRepo
  "Stores things."
  (-search [this search-term] "Searchs for things."))

(defrecord StaticThingRepo [things]
  ThingRepo
  (-search [this term]
    (filter #(str/includes? (str/upper-case (:id %)) (str/upper-case term)) things)))

(defn search [repo term]
  (log/debug (str "Searching for things containing \"" term "\"."))
  (-search repo term))

(defn repo
  [config]
  (map->StaticThingRepo {:things (:backend/things config)}))

(s/def :backend/thing-repo (partial satisfies? ThingRepo))
(s/def :backend/search-term string?)

(s/fdef search
  :args (s/cat :repo :backend/thing-repo
               :search-term :backend/search-term)
  :ret (s/coll-of map?))

(s/fdef repo
  :args (s/cat :config (s/keys :req [:backend/things]))
  :ret :backend/thing-repo)
