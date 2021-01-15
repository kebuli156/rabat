(ns rabat.edge.logger)

(defprotocol Logger
  (-log [this level ns-str file line id tag data]))

(extend-protocol Logger
  nil
  (-log [_ _ _ _ _ _ _ _]))

(defn- log-form
  [logger level tag data form]
  `(-log ~logger
         ~level
         ~(str *ns*)
         ~*file*
         ~(:line (meta form))
         (delay (java.util.UUID/randomUUID))
         ~tag
         ~data))

#?(:clj
   (defmacro log
     {:style/indent 3}
     ([logger level tag data]
      (log-form logger level tag data &form))
     ([logger level tag]
      (log-form logger level tag nil &form))))

#?(:clj
   (doseq [level '(trace debug info warn error fatal report)]
     (eval
       `(defmacro ~level
          {:style/indent 2}
          (~'[logger tag data]
           (log-form ~'logger ~(keyword level) ~'tag ~'data ~'&form))
          (~'[logger tag]
           (log-form ~'logger ~(keyword level) ~'tag nil ~'&form))))))

(defprotocol TimbreAppender
  (timbre-appender [this]))
