(ns rabat.components.timbre
  (:require
   [com.stuartsierra.component :as c]
   [rabat.edge.logger :as rbt.edge.logger]
   [taoensso.timbre :as timbre :refer [log!]]))

(defrecord TimbreAppender [config appender-fn appender]
  c/Lifecycle
  (start [this]
    (assoc this :appender (appender-fn this)))
  (stop [this]
    (assoc this :appender nil)))

(defn timbre-appender
  ([appender-fn]
   (timbre-appender appender-fn {}))
  ([appender-fn config]
   (map->TimbreAppender {:appender-fn appender-fn :config config})))

(defn- collect-timbre-appenders
  [m]
  (into {}
        (keep (fn [[k v]]
                (when-let [appender (:appender v)]
                  [k appender])))
        m))

(defrecord TimbreLogger [config previous-settings settings]
  c/Lifecycle
  (start [this]
    (if (some? settings)
      this
      (let [appenders (collect-timbre-appenders this)
            -settings (assoc config :appenders appenders)
            -this     (assoc this :settings -settings)]
        (if (:set-root-config? -settings)
          (let [prev-settings timbre/*config*]
            (timbre/set-config! -settings)
            (assoc -this :previous-settings prev-settings))
          -this))))
  (stop [this]
    (when (some? previous-settings)
      (timbre/set-config! previous-settings))
    (assoc this :settings nil :previous-settings nil))

  rbt.edge.logger/Logger
  (-log [_ level ns-str file line id tag data]
    (cond
      (instance? Throwable data)
      (log! level :p (tag) {:config     settings
                            :?err       data
                            :?ns-str    ns-str
                            :?file      file
                            :?line      line
                            :?base-data {:id_ id}})

      (nil? data)
      (log! level :p (tag) {:config     settings
                            :?ns-str    ns-str
                            :?file      file
                            :?line      line
                            :?base-data {:id_ id}})

      :else
      (log! level :p (tag data) {:config     settings
                                 :?ns-str    ns-str
                                 :?file      file
                                 :?line      line
                                 :?base-data {:id_ id}}))))

(defn timbre-logger
  [config]
  (map->TimbreLogger {:config config}))
