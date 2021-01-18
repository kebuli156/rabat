(ns rabat.components.migratus
  (:require
   [com.stuartsierra.component :as c]
   [migratus.core :as migratus]
   [rabat.edge.logger :refer [warn]]))

(defrecord Migratus [config database logger]
  c/Lifecycle
  (start [this]
    (let [new-config (assoc config :db database)]
      (try
        (migratus/migrate new-config)
        (catch Throwable err
          (warn logger ::migrate err)))
      this))
  (stop [this]
    this))

(defn migratus
  [config]
  (map->Migratus {:config config}))
