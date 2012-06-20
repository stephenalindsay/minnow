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

(ns minnow.leiningen
  (:require 
    [clojure.java.shell :as shell]
    [leiningen.core.classpath :as lein.cp]
    [leiningen.core.project :as lein.project])
  (:import [java.io File]))

(defn read-project-file
  [path]
  (lein.project/read path))

(defn get-main-fn
  [path]
  (:main (read-project-file path)))

(defn get-classpath
  [project-file-path]
  (lein.cp/get-classpath (read-project-file project-file-path)))

(defn new-project
  [lein-path parent-dir name]
  (println lein-path parent-dir name)
  (shell/sh lein-path "new" name :dir parent-dir)
  (shell/sh lein-path "deps" :dir (str 
                                    parent-dir 
                                    (File/separator)
                                    name)))

