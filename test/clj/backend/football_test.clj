(ns backend.football-test
  (:require [backend.football :as football]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]))

(def api-url "http://api.football-data.org/v1")
(def api-token (str/trim (slurp "resources/token.txt")))

(def competitions (edn/read-string (slurp "resources/competitions.edn")))

(def http-repo (football/repo {:backend/football-repo-type :http
                               :backend/football-api-url api-url
                               :backend/football-api-token api-token}))

(def static-repo
  (football/repo {:backend/football-repo-type :static
                  :backend/football-competitions competitions}))

(def expected (assoc (get competitions "445") :status :ok))

(deftest static
  (is (= expected (football/competition static-repo "445"))))

(deftest http
  (is (= expected (football/competition http-repo "445"))))
