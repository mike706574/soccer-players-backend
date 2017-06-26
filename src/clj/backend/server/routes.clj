(ns backend.server.routes
  (:require [backend.football :as football]
            [backend.things :as things]
            [backend.users :as users]
            [backend.server.authentication :as auth]
            [backend.server.http :refer [with-body
                                        handle-exceptions
                                        body-response
                                        not-acceptable
                                        parsed-body
                                        unsupported-media-type]]
            [clj-time.core :as time]
            [clojure.string :as str]
            [compojure.core :as compojure :refer [ANY DELETE GET PATCH POST PUT]]
            [compojure.route :as route]
            [taoensso.timbre :as log]))

(defn deaccent
  [s]
  (let [normalized (java.text.Normalizer/normalize s java.text.Normalizer$Form/NFD)]
    (str/replace normalized #"\p{InCombiningDiacriticalMarks}+" "")))

(defn retrieve-things
  [{:keys [thing-repo]} request]
  (handle-exceptions request
    (or (not-acceptable request)
        (if-let [term (get-in request [:params :term])]
          (let [things (things/search thing-repo term)]
            (Thread/sleep 300)
            (body-response 200 request things))
          (body-response 400 request {:backend.server/message (str "Missing required query parameter: term")})))))

(defn retrieve-players
  [{repo :football-repo} {{id :competition-id term :name} :params :as request}]
  (log/debug (str "Searching for players: " id ", " term))
  (let [pattern (re-pattern (str "(?i)" term))
        matches? (fn [player]
                   (let [player-name (deaccent (:name player))]
                     (re-find pattern player-name)))]
    (handle-exceptions request
      (or (not-acceptable request)
          (let [competition (football/competition repo id)
                players (:players competition)
                body (if-not term
                       players
                       (filter matches? players))]
            (log/debug (str (count body) " matching players found."))
            (Thread/sleep 300)
            (body-response 200 request body))))))

(defn create-token
  [{:keys [user-manager authenticator]} request]
  (try
    (or (not-acceptable request #{"text/plain"})
        (with-body [credentials :backend/credentials request]
          (if-let [user (users/authenticate user-manager credentials)]
            {:status 201
             :headers {"Content-Type" "text/plain"}
             :body (auth/token authenticator (:backend/username credentials))}
            {:status 401
             :headers {"Content-Type" "text/plain"}
             :body "Authentication failed."})))
    (catch Exception e
      (log/error e "An exception was thrown while processing a request.")
      {:status 500
       :headers {"Content-Type" "text/plain"}
       :body "An error occurred."})))

(defn routes
  [{:keys [user-manager authenticator] :as deps}]
  (letfn [(unauthenticated [request]
            (when-not (auth/authenticated? authenticator request)
              {:status 401}))]
    (compojure/routes
     (GET "/api/healthcheck" request {:status 200})
     (GET "/api/things" request (retrieve-things deps request))
     (GET "/api/players/:competition-id" request (retrieve-players deps request))
     (POST "/api/tokens" request (create-token deps request))
     (route/not-found {:status 404}))))
