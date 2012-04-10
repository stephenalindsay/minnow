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
    [java.io File]
    clojure.tools.nrepl.transport.Transport))

(defn add-shutdown-hook []
  (.addShutdownHook (Runtime/getRuntime)
    (proxy [Thread] []
      (run [] 
           (repl/shutdown-running-repls)))))

(defn check-project
  [project-path]
  (let [f (File. project-path)]
    (when (and
            (.exists f)
            (.isDirectory f)
            (not (filter #(= % f) (.listFiles @state/virtual-dir)))
            (filter #(= % (File. "project.clj")) (.listFiles f)))
      (ui/add-path-to-virtual-dir f))))

(defn run 
  [project-path]
  (add-shutdown-hook)
  (logging/setup-logging)
  (ui/gui)
  (when project-path
    (check-project project-path)))
    
(defn repl-run []
  (reset! state/standalone false)
  (run nil))

(defn -main 
  ([]
   (-main nil))
  ([project-path]
   (run project-path)))  

