(ns rabat.util.systems)

(defn- ensure-map
  [x]
  (if (sequential? x)
    (apply zipmap (repeat 2 x))
    x))

(defn- normalize-deps-map
  [m]
  (reduce-kv
    (fn [s k v]
      (assoc s k (ensure-map v)))
    {}
    m))

(defn inject-deps-satisfying
  [deps-map system-map dep-k proto]
  (->> {dep-k (into []
                    (keep (fn [[k v]]
                            (when (and (satisfies? proto v) (not= dep-k k))
                              k)))
                    (seq system-map))}
       (normalize-deps-map)
       (merge-with merge (normalize-deps-map deps-map))))

(defn merge-dependencies
  [deps-map & new-deps]
  (apply merge-with
         merge
         (normalize-deps-map deps-map)
         (into [] (map normalize-deps-map) new-deps)))
