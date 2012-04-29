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

(ns minnow.main
  ^{:doc "Entry points for Minnow"
    :author "Steve Lindsay"}  
  (:gen-class)
  (:require 
    [minnow.ui :as ui]
    [minnow.ui.state :as state]
    [minnow.ui.logging :as logging]
    [minnow.ui.repl :as repl])
  (:import
    [java.io File]))

(defn add-shutdown-hook []
  (.addShutdownHook (Runtime/getRuntime)
    (proxy [Thread] []
      (run [] (repl/shutdown-running-repls)))))

(defn valid-project-path?
  [project-path]
  (let [dir  (File. project-path)
        lein (File. dir "project.clj")
        pom  (File. dir "pom.xml")]
    (and
      (.exists dir)
      (.isDirectory dir)
      (not (seq (filter #(= % dir) (.listFiles @state/virtual-dir))))
      (or
        (.exists lein)
        (.exists pom)))))

(defn run 
  [project-path]
  (add-shutdown-hook)
  (logging/setup-logging)
  (ui/gui)
  (when (and
          project-path
          (valid-project-path? project-path))
    (ui/add-path-to-virtual-dir (File. project-path))))
    
(defn repl-run []
  (reset! state/standalone false)
  (run nil))

(defn -main 
  ([]
   (-main nil))
  ([project-path]
    (println "Project path : " project-path)
    (run project-path)))

