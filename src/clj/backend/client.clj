(ns backend.client
  (:require [aleph.http :as http]
            [manifold.stream :as s]
            [backend.message :as message]
            [backend.users :as users]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn parse
  [response]
  (let [content-type (get-in response [:headers "content-type"])]
    (if (contains? response :body)
      (update response :body (comp (partial message/decode content-type)))
      response)))

(defn add-user!
  [system username password]
  (users/add! (:user-manager system) {:backend/username username
                                      :backend/password password}))

(defn http-url [host] (str "http://" host))
(defn ws-url [host] (str "ws://" host))

(defprotocol Client
  (authenticate [this credentials])
  (search [this term])
  (search-players [this competition-id term]))

(defn maybe-parse
  [response]
  (let [content-type (get-in response [:headers "content-type"])]
    (if (contains? response :body)
      (update response :body (comp (partial message/decode content-type)))
      response)))

(defrecord ServiceClient [host token]
  Client
  (authenticate [this credentials]
    (let [response @(http/post (str (http-url host) "/api/tokens")
                               {:headers {"Content-Type" "application/json"
                                          "Accept" "text/plain"}
                                :body (String. (message/encode "application/json" credentials) "UTF-8")
                                :throw-exceptions false})]

      (when (= (:status response) 201)
        (assoc this :token (-> response :body slurp)))))

  (search [this term]
    (let [{:keys [status body] :as response} (parse @(http/get (str (http-url host) "/api/things")
                                       {:headers {"Accept" "application/json"
                                                  "Authorization" (str "Token " token)}
                                        :query-params {"term" (name term)}
                                        :throw-exceptions false}))]
      (if (= status 200)
        {:status :ok :things body}
        {:status :error :response response})))

  (search-players [this competition-id term]
    (let [{:keys [status body] :as response} (parse @(http/get (str (http-url host) "/api/players/" competition-id )
                                                               {:headers {"Accept" "application/json"
                                                                          "Authorization" (str "Token " token)}
                                                                :query-params {"name" (name term)}
                                                                :throw-exceptions false}))]
      (if (= status 200)
        {:status :ok :things body}
        {:status :error :response response}))))

(defn client
  [{:keys [host]}]
  (map->ServiceClient {:host host}))
