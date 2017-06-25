(ns backend.things-test
  (:require [backend.things :as things]
            [clojure.test :refer [deftest testing is]]))

(def things
  [{:id "animal"}
   {:id "apple"}
   {:id "astronaut"}
   {:id "dog"}
   {:id "banana"}
   {:id "cat"}
   {:id "giraffe"}
   {:id "whale"}
   {:id "rocket"}
   {:id "monkey"}])

(def repo (things/repo {:backend/things things}))

(deftest searching
  (is (= [] (things/search repo "weasel")))
  (is (= [{:id "animal"} {:id "monkey"}] (things/search repo "m")))
  (is (= things (things/search repo ""))))
