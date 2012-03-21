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
    [minnow.util.classpath :as cp]
    [minnow.util.process :as process])
  (:import 
    [java.io File]))

(def ^:dynamic *startup-wait* 5000)
(def nrepl-jar-path "/.m2/repository/org/clojars/stevelindsay/tools.nrepl/0.2.0-b2/tools.nrepl-0.2.0-b2.jar")

(defn stop 
  [{:keys [process]}]
  ; TODO - work out the graceful way to shutdown repl server
  (.destroy process))

(defn start
  [working-dir port]
  (let [home      (System/getProperty "user.home")
        classpath (str 
                    (reduce str (interpose (File/pathSeparator) 
                                       [(str home nrepl-jar-path) "src" "classes"])) 
                    (File/pathSeparator) 
                    (cp/build-classpath-from-dir (str working-dir (File/separator) "lib")))]
    (println classpath)
    (if (re-matches #".*clojure-1.*jar.*" classpath)
      (let [proc    (process/start-process
                      ["java" "-cp" classpath "clojure.tools.nrepl.main" "--port" (str port)] 
                      working-dir)
            {:keys [in err exit]} (process/get-process-output proc *startup-wait*)
            _       (when exit (throw (RuntimeException. (str {:exit exit :out in :err err}))))
            client  (nrepl/client (nrepl/connect :port port) 1000)
            session (nrepl/new-session client)]
        {:client client :session session :process proc})
      (throw (RuntimeException. "No clojure jar in classpath.")))))


