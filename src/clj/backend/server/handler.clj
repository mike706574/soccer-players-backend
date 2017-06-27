(ns backend.server.handler
  (:require [backend.server.routes :as routes]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer [wrap-defaults
                                              api-defaults]]
            [ring.middleware.params :refer [wrap-params]]
            [taoensso.timbre :as log]))

(defprotocol HandlerFactory
  "Builds a request handler."
  (handler [this]))

(defn wrap-logging
  [handler]
  (fn [{:keys [uri request-method] :as request}]
    (let [label (str (-> request-method name str/upper-case) " \"" uri "\"")]
      (try
        (log/debug label)
        (let [{:keys [status] :as response} (handler request)]
          (log/debug (str label " -> " status))
          response)
        (catch Exception e
          (log/error e label)
          {:status 500})))))

(defn handler
  [deps]
  (-> (routes/routes deps)
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :put :post :delete])
      (wrap-params)
      (wrap-defaults api-defaults)
      (wrap-logging)))

(defrecord BackendHandlerFactory [authenticator
                                  conn-manager
                                  football-repo
                                  event-bus
                                  user-manager]
  HandlerFactory
  (handler [this]
    (handler this)))

(defn factory
  [config]
  (component/using
   (map->BackendHandlerFactory {})
   [:authenticator :conn-manager :football-repo :user-manager]))
