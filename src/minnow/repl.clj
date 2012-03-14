;;  
;;   Copyright (c) Steve Lindsay, 2012. All rights reserved.
;;
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this 
;;   distribution.
;;
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;
;;   You must not remove this notice, or any other, from this software.
;;

(ns minnow.repl
  (:require 
    [clojure.tools.nrepl :as nrepl]
    [minnow.process :as process]))

(def ^:dynamic *startup-wait* 5000)

(defn- start-server
  [port working-dir classpath]
  (process/start-process
    ["java" "-cp" classpath "clojure.tools.nrepl.main" "--port" (str port)] 
    working-dir))

(defn start
  [port working-dir classpath]
  (when-let [process (start-server port working-dir classpath)]
    (let [{:keys [in err exit]} (process/get-process-output process *startup-wait*)]
      (if exit
        (throw (RuntimeException. (str {:exit exit :out in :err err})))
        (nrepl/connect :port port)))))

(defn stop 
  []
  (println "does nothing")
  ;nfi
  )

