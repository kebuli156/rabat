(ns rabat.components.hikari-cp
  (:require
   [com.stuartsierra.component :as c]
   [hikari-cp.core :as hikari]
   [rabat.edge.logger :as rbt.edge.logger :refer [info]]
   [rabat.util.components :as rbt.u.c])
  (:import
   [javax.sql DataSource]
   [net.ttddyy.dsproxy QueryInfo]
   [net.ttddyy.dsproxy.support ProxyDataSourceBuilder]
   [net.ttddyy.dsproxy.listener QueryExecutionListener]))

(defn- query-parameter-lists
  [^QueryInfo query-info]
  (into []
        (map (fn [params]
               (->> params
                    (into [] (map (memfn getArgs)))
                    (sort-by #(aget % 0))
                    (into [] (map #(aget % 1))))))
        (.getParametersList query-info)))

(defn- logged-query
  [^QueryInfo query-info]
  (let [query  (.getQuery query-info)
        params (query-parameter-lists query-info)]
    (into [query] (if (= (count params) 1) (first params) params))))

(defn- logging-listener
  [logger]
  (reify QueryExecutionListener
    (beforeQuery [_ _ _])
    (afterQuery [_ exec-info query-infos]
      (let [elapsed (.getElapsedTime exec-info)
            queries (into [] (map logged-query) query-infos)]
        (if (= (count queries) 1)
          (info logger ::query
            {:query (first queries) :elapsed elapsed})
          (info logger ::batch-query
            {:queries queries :elapsed elapsed}))))))

(defn- wrap-with-logger
  [^DataSource datasource logger]
  (.. ProxyDataSourceBuilder
      (create datasource)
      (listener (logging-listener logger))
      (build)))

(defn- unwrap-logger
  [^DataSource datasource]
  (.unwrap datasource DataSource))

(defn- sanitize-pool-spec
  [pool-spec]
  (dissoc pool-spec :password))

(defn- get-logger
  [m k]
  (or (get m k)
      (rbt.u.c/get-satisfied m rbt.edge.logger/Logger)))

(defrecord HikariCP []
  c/Lifecycle
  (start [this]
    (if (some? (:datasource this))
      this
      (let [logger     (get-logger this :logger)
            pool-spec  (:pool-spec this)
            _          (info logger ::hikari-cp
                         {:lifecycle :starting
                          :pool-spec (sanitize-pool-spec pool-spec)})
            datasource (cond-> (hikari/make-datasource pool-spec)
                         (some? logger)
                         (wrap-with-logger logger))]
        (assoc this :datasource datasource))))
  (stop [this]
    (when-let [datasource (:datasource this)]
      (let [logger (get-logger this :logger)]
        (info logger ::hikari-cp
          {:lifecycle :stopping})
        (hikari/close-datasource (unwrap-logger datasource))))
    (assoc this :datasource nil)))

(defn hikari-cp
  [pool-spec]
  (map->HikariCP {:pool-spec pool-spec}))
