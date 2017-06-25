(ns backend.server.system
  (:require [backend.server.authentication :as auth]
            [backend.server.connection :as conn]
            [backend.server.handler :as handler]
            [backend.server.service :as service]
            [backend.things :as things]
            [backend.users :as users]
            [backend.util :as util]
            [clojure.core.cache :as cache]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [manifold.bus :as bus]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]))

(defn configure-logging!
  [{:keys [:backend/id :backend/log-path] :as config}]
  (let [log-file (str log-path "/" id "-" (util/uuid))]
    (log/merge-config!
     {:appenders {:spit (appenders/spit-appender
                         {:fname log-file})}})))

(s/def :backend/id string?)
(s/def :backend/port integer?)
(s/def :backend/log-path string?)
(s/def :backend/user-manager-type #{:atomic})
(s/def :backend/users (s/map-of :backend/username :backend/password))
(s/def :backend/config (s/keys :req [:backend/id
                                    :backend/port
                                    :backend/log-path
                                    :backend/user-manager-type]
                              :opt [:backend/users]))

(defn build
  [config]
  (log/info (str "Building " (:backend/id config) "."))
  (configure-logging! config)
  {:thing-repo (things/repo config)
   :user-manager (users/user-manager config)
   :authenticator (auth/authenticator config)
   :conn-manager (conn/manager config)
   :handler-factory (handler/factory config)
   :app (service/aleph-service config)})

(defn system
  [config]
  (if-let [validation-failure (s/explain-data :backend/config config)]
    (do (log/error (str "Invalid configuration:\n"
                        (util/pretty config)
                        "Validation failure:\n"
                        (util/pretty validation-failure)))
        (throw (ex-info "Invalid configuration." {:config config
                                                  :validation-failure validation-failure})))
    (build config)))

(s/fdef system
  :args (s/cat :config :backend/config)
  :ret map?)
