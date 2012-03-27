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

(ns minnow.util.process
  (:require [clojure.java.io :as io]))

(defn start-process
  [cmd working-dir]
  (let [pb (ProcessBuilder. cmd)]
    (.directory pb (java.io.File. working-dir))
    (println "Starting server with command : " (.command pb))
    (.start pb)))

(defn msg-from-stream
  [stream]
  (let [reader (io/make-reader stream nil)]
    (when (> (.available stream) 0)
      (loop [resp nil]
        (let [i (.read reader)]
          (if (not= i -1)
            (recur (str resp (char i)))
            (reduce str resp)))))))

(defn get-process-output
  [process pause]
  (deref 
    (future
      (let [in   (msg-from-stream (.getInputStream process))
            err  (msg-from-stream (.getErrorStream process))
            exit (try
                   (.exitValue process)
                   (catch Exception e nil))]
        {:in in :err err :exit exit})) pause {:err "Process failed to start in time"}))

