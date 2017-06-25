(ns backend.server.system-test
  (:require [aleph.http :as http]
            [backend.client :as client]
            [backend.macros :refer [with-system unpack-response]]
            [backend.message :as message]
            [backend.server.system :as system]
            [backend.things :as things]
            [com.stuartsierra.component :as component]
            [clojure.test :refer [deftest testing is]]
            [manifold.bus :as bus]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [taoensso.timbre :as log]))

(def port 9001)
(def config {:backend/id "backend-server"
             :backend/port port
             :backend/log-path "/tmp"
             :backend/secret-key "secret"
             :backend/user-manager-type :atomic
             :backend/things [{:id "animal"}
                             {:id "apple"}
                             {:id "astronaut"}
                             {:id "dog"}
                             {:id "banana"}
                             {:id "cat"}
                             {:id "canine"}
                             {:id "corpse"}
                             {:id "rocket"}
                             {:id "monster"}
                             {:id "monster"}]
             :backend/users {"mike" "rocket"}})

(deftest simple-test
  (with-system (system/system config)
    (let [client (-> {:host (str "localhost:" port)}
                     (client/client)
                     (client/authenticate {:backend/username "mike"
                                           :backend/password "rocket"}))
          foo-1 {:backend/category :foo
                 :backend/closed? false
                 :count 4 }]
      ;; queyr

       (client/search client "a")
))

  )
