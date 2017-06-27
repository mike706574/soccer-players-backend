(ns backend.server.system-test
  (:require [aleph.http :as http]
            [backend.client :as client]
            [backend.macros :refer [with-system unpack-response]]
            [backend.message :as message]
            [backend.server.system :as system]
            [com.stuartsierra.component :as component]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest testing is]]
            [manifold.bus :as bus]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [taoensso.timbre :as log]))

(def port 9001)

(def competitions (edn/read-string (slurp "resources/competitions.edn")))

(def config {:backend/id "backend-server"
             :backend/port port
             :backend/log-path "/tmp"
             :backend/secret-key "secret"
             :backend/user-manager-type :atomic
             :backend/football-repo-type :static
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
                :team-name "Arsenal FC",
                :number 7,
                :date-of-birth "1988-12-19",
                :contract-until "2018-06-30"
                :name-without-diacritics "Alexis Sanchez"}]}
             (client/search-players client 445 "Sanchez"))))))
