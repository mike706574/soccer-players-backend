(ns backend.server.system-test
  (:require [aleph.http :as http]
            [backend.client :as client]
            [backend.macros :refer [with-system unpack-response]]
            [backend.message :as message]
            [backend.server.system :as system]
            [com.stuartsierra.component :as component]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]
            [taoensso.timbre :as log]))

(def port 9001)

(def competitions (edn/read-string (slurp "resources/competitions.edn")))

(def api-url "http://api.football-data.org/v1")
(def api-token (str/trim (slurp (io/resource "token.txt"))))

(def config {:backend/id "backend-server"
             :backend/port port
             :backend/log-path "/tmp"
             :backend/secret-key "secret"
             :backend/user-manager-type :atomic
             :backend/football-repo-type :http
             :backend/football-api-url api-url
             :backend/football-api-token api-token
             :backend/football-competitions competitions
             :backend/users {"mike" "rocket"}})

(deftest player-search-test
  (with-system (system/system config)
    (let [client (-> {:host (str "localhost:" port)}
                     (client/client)
                     (client/authenticate {:backend/username "mike"
                                           :backend/password "rocket"}))]
      (is (= {:status :ok,
              :players
              [{:name "Alexis Sánchez",
                :position "Left Wing",
                :nationality "Chile",
                :teamName "Arsenal FC",
                :jerseyNumber 7,
                :dateOfBirth "1988-12-19",
                :contractUntil "2018-06-30"
                :nameWithoutDiacritics "Alexis Sanchez"}]}
             (client/search-players client 445 "Sanchez"))))))
