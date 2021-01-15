(ns rabat.util.components)

(defn get-satisfied
  [m proto]
  (->> (vals m)
       (filter #(satisfies? proto %))
       (first)))

(defn xcollect
  [proto]
  (comp
    (map val)
    (filter #(satisfies? proto %))))
