(ns rabat.components.timbre
  (:require
   [com.stuartsierra.component :as c]
   [rabat.edge.logger :as rbt.edge.logger]
   [taoensso.timbre :as timbre :refer [log!]]))

(defrecord TimbreAppender []
  rbt.edge.logger/TimbreAppender
  (timbre-appender [this]
    ((:appender-fn this) this)))

(defn timbre-appender
  ([appender-fn]
   (timbre-appender appender-fn {}))
  ([appender-fn config]
   (map->TimbreAppender {:appender-fn appender-fn :config config})))

(defn- collect-timbre-appenders
  [component]
  (into {}
        (keep (fn [[k v]]
                (when (satisfies? rbt.edge.logger/TimbreAppender v)
                  [k (rbt.edge.logger/timbre-appender v)])))
        component))

(defrecord TimbreLogger []
  c/Lifecycle
  (start [this]
    (if (some? (:settings this))
      this
      (let [appenders (collect-timbre-appenders this)
            settings  (assoc (:config this) :appenders appenders)
            -this     (assoc this :settings settings)]
        (if (:set-root-config? settings)
          (let [prev-settings timbre/*config*]
            (timbre/set-config! settings)
            (assoc -this :previous-settings prev-settings))
          -this))))
  (stop [this]
    (when-let [prev-settings (:previous-settings this)]
      (timbre/set-config! prev-settings))
    (assoc this :settings nil :previous-settings nil))

  rbt.edge.logger/Logger
  (-log [this level ns-str file line id tag data]
    (cond
      (instance? Throwable data)
      (log! level :p (tag) {:config     (:settings this)
                            :?err       data
                            :?ns-str    ns-str
                            :?file      file
                            :?line      line
                            :?base-data {:id_ id}})

      (nil? data)
      (log! level :p (tag) {:config     (:settings this)
                            :?ns-str    ns-str
                            :?file      file
                            :?line      line
                            :?base-data {:id_ id}})

      :else
      (log! level :p (tag data) {:config     (:settings this)
                                 :?ns-str    ns-str
                                 :?file      file
                                 :?line      line
                                 :?base-data {:id_ id}}))))

(defn timbre-logger
  [config]
  (map->TimbreLogger {:config config}))
