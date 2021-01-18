(ns rabat.components.hikari-cp
  (:require
   [clojure.java.jdbc :as jdbc]
   [com.stuartsierra.component :as c]
   [hikari-cp.core :as hikari]
   [rabat.edge.logger :refer [info]])
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

(defn- create-db-sql-string
  [database-name]
  (str "CREATE DATABASE " database-name))

(defn- drop-db-sql-string
  [database-name]
  (str "DROP DATABASE IF EXISTS " database-name))

(def ^:private db-type->db-name
  {"postgresql" "postgres"})

(defn- pool-spec->db-spec
  [{:keys [adapter database-name username password]}]
  (cond-> {:dbtype adapter
           :dbname database-name}
    (some? username)
    (assoc :user username)

    (some? password)
    (assoc :password password)))

(defn- pool-spec->def-db-spec
  [{:keys [adapter] :as pool-spec}]
  (when-let [dbname (db-type->db-name adapter)]
    (pool-spec->db-spec (assoc pool-spec :database-name dbname))))

(defn- create-sql-db!
  [{:keys [database-name] :as pool-spec}]
  (when-let [def-db-spec (pool-spec->def-db-spec pool-spec)]
    (let [sql-string (create-db-sql-string database-name)]
      (jdbc/db-do-commands def-db-spec false sql-string))))

(defn- drop-sql-db!
  [{:keys [database-name] :as pool-spec}]
  (when-let [def-db-spec (pool-spec->def-db-spec pool-spec)]
    (let [sql-string (drop-db-sql-string database-name)]
      (jdbc/db-do-commands def-db-spec false sql-string))))

(defrecord HikariCPEphemeralImpl [pool-spec]
  c/Lifecycle
  (start [this]
    (try
      (drop-sql-db! pool-spec)
      (create-sql-db! pool-spec)
      (catch Throwable _
        nil))
    this)
  (stop [this]
    (try
      (drop-sql-db! pool-spec)
      (catch Throwable _
        nil))
    this))

(defn hikari-cp-ephemeral-impl
  [pool-spec]
  (map->HikariCPEphemeralImpl {:pool-spec pool-spec}))

(defrecord HikariCPDurableImpl [pool-spec]
  c/Lifecycle
  (start [this]
    (try
      (create-sql-db! pool-spec)
      (catch Throwable _
        nil))
    this)
  (stop [this]
    this))

(defn hikari-cp-durable-impl
  [pool-spec]
  (map->HikariCPDurableImpl {:pool-spec pool-spec}))

(defrecord HikariCPNoopImpl [pool-spec])

(defn hikari-cp-noop-impl
  [pool-spec]
  (map->HikariCPNoopImpl {:pool-spec pool-spec}))

(def ^:private hikari-cp-impls-map
  {:ephemeral hikari-cp-ephemeral-impl
   :durable   hikari-cp-durable-impl
   :noop      hikari-cp-noop-impl})

(defn hikari-cp-impl
  [{:keys [lifecycle] :as pool-spec}]
  (let [ctor       (or (get hikari-cp-impls-map lifecycle)
                       (:noop hikari-cp-impls-map))
        -pool-spec (dissoc pool-spec :lifecycle)]
    (ctor -pool-spec)))

(defrecord HikariCP [impl logger datasource]
  c/Lifecycle
  (start [this]
    (if (some? datasource)
      this
      (let [pool-spec (or (:pool-spec impl)
                          (throw (ex-info "missing `:pool-spec`" {})))
            _         (info logger ::hikari-cp
                        {:lifecycle :start
                         :pool-spec (sanitize-pool-spec pool-spec)})
            ds        (cond-> (hikari/make-datasource pool-spec)
                        (some? logger)
                        (wrap-with-logger logger))]
        (assoc this :datasource ds))))
  (stop [this]
    (when (some? datasource)
      (info logger ::hikari-cp
        {:lifecycle :stop})
      (hikari/close-datasource (unwrap-logger datasource)))
    (assoc this :datasource nil)))

(defn hikari-cp
  []
  (map->HikariCP {}))
