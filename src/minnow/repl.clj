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
    [clojure.tools.nrepl.misc :as nrepl.misc]
    [clojure.tools.nrepl.server :as nrepl.server]
    [clojure.tools.nrepl.ack :as nrepl.ack]
    [minnow.leiningen :as lein]
    [minnow.util.process :as process])
  (:import 
    [java.io File]))

(def nrepl-jars ["/.m2/repository/minnow/nrepl/minnow.nrepl/0.1.0/minnow.nrepl-0.1.0.jar"
                 "/.m2/repository/org/clojure/tools.nrepl/0.2.0-beta5/tools.nrepl-0.2.0-beta5.jar"
                 "/.m2/repository/org/clojure/tools.logging/0.2.3/tools.logging-0.2.3.jar"
                 "/.m2/repository/clj-logging-config/clj-logging-config/1.9.6/clj-logging-config-1.9.6.jar"
                 "/.m2/repository/log4j/log4j/1.2.16/log4j-1.2.16.jar"
                 "/.m2/repository/com.cemerick/pomegranate-0.0.10.jar"])

(defn stop 
  [{:keys [process]}]
  ; TODO - work out the graceful way to shutdown repl server
  (.destroy process))

(defn start
  [working-dir]
  (let [proj-path (str working-dir (File/separator) "project.clj")
        home      (System/getProperty "user.home")
        classpath (str 
                    (reduce str (interpose (File/pathSeparator)
                                           (concat
                                             (map #(str home %) nrepl-jars) 
                                             ["src" "test"] ; TODO - needs to be configurable
                                             (lein/get-classpath proj-path)))))]
    (println "repl classapth : " classpath)
    (if (re-matches #".*clojure-1.*jar.*" classpath)
      (let [ack-server (promise)]
        (nrepl.ack/reset-ack-port!)
        ;(future 
        (send-off (agent nil)
          (fn [_]
            (deliver ack-server (nrepl.server/start-server :handler (nrepl.ack/handle-ack false)))))
        (if (deref ack-server 5000 false)
          (let [ack-port  (.getLocalPort (:ss @@ack-server))
                proc      (process/start-process
                            ["java" "-cp" classpath "clojure.main" "-m" "minnow.nrepl" (str ack-port)] 
                            working-dir)]
            (if-let [port (nrepl.ack/wait-for-ack 15000)]
              (let [client  (nrepl/client (nrepl/connect :port port) 60000)
                    session (nrepl/new-session client)]
                (println "nREPL started on port: " port)
                {:client client :session session :process proc})
              (throw (RuntimeException. "Timout waiting for ack"))))
          (throw (RuntimeException. "Took too long to start ack server"))))
      (throw (RuntimeException. "No clojure jar in classpath.")))))

(defn evaluate-code-in-repl
  [project-repl code]
  (let [command-id (nrepl.misc/uuid)
        resp (nrepl/message
               (:client project-repl)
               {:op :eval
                :code code
                :session (:session project-repl)})
        _  (println "resp : " resp)
        out   (apply str (map :out resp))]
    (assoc (apply merge resp) :out out)))
  
  
