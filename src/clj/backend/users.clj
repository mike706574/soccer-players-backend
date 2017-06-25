(ns backend.users
  (:require [buddy.hashers :as hashers]
            [clojure.spec.alpha :as s]))

(s/def :backend/username string?)
(s/def :backend/password string?)
(s/def :backend/credentials (s/keys :req [:backend/username :backend/password]))

(defprotocol UserManager
  "Abstraction around user storage and authentication."
  (add! [this user] "Adds a user.")
  (authenticate [this credentials] "Authenticates a user."))

(s/def :backend/user-manager (partial satisfies? UserManager))

(s/fdef add!
  :args (s/cat :user-manager :backend/user-manager
               :credentials :backend/credentials)
  :ret :backend/credentials)

(defn ^:private find-by-username
  [users username]
  (when-let [user (first (filter (fn [[user-id user]] (= (:backend/username user) username)) @users))]
    (val user)))

(defrecord AtomicUserManager [counter users]
  UserManager
  (add! [this user]
    (swap! users assoc (str (swap! counter inc))
           (update user :backend/password hashers/encrypt))
    (dissoc user :backend/password))

  (authenticate [this {:keys [:backend/username :backend/password]}]
    (when-let [user (find-by-username users username)]
      (when (hashers/check password (:backend/password user))
        (dissoc user :backend/password)))))

(defmulti user-manager :backend/user-manager-type)

(defmethod user-manager :default
  [{user-manager-type :backend/user-manager-type}]
  (throw (ex-info (str "Invalid user manager type: " (name user-manager-type))
                  {:user-manager-type user-manager-type})))

(defmethod user-manager :atomic
  [config]
  (let [user-manager (AtomicUserManager. (atom 0) (atom {}))]
    (when-let [users (:backend/users config)]
      (doseq [[username password] users]
        (add! user-manager {:backend/username username
                            :backend/password password})))
    user-manager))
