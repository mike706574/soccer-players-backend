(ns backend.football-test
  (:require [backend.football :as football]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]))

(def api-url "http://api.football-data.org/v1")
(def api-token (str/trim (slurp "resources/token.txt")))

(def repo (football/repo {:backend/football-repo-type :http
                          :backend/football-api-url api-url
                          :backend/football-api-token api-token}))

(deftest retrieving-competition
  (is (= (edn/read-string (slurp "resources/445.edn"))
         (football/competition repo "445"))))
