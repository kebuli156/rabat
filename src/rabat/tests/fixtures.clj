(ns rabat.tests.fixtures
  (:require
   [com.stuartsierra.component :as c]))

;; Actually taken from
;; https://github.com/juxt/modular/blob/master/modules/test/src/modular/test.clj

(def ^:dynamic *system* nil)

(defmacro with-system
  [system & body]
  `(let [s# (c/start ~system)]
     (try
       (binding [*system* s#] ~@body)
       (finally
         (c/stop s#)))))

(defn with-system-fixture
  [system-fn]
  (fn [f]
    (with-system (system-fn)
      (f))))

(defn get-component!
  [k]
  (or (get *system* k)
      (throw (ex-info "missing component" {:component k}))))
