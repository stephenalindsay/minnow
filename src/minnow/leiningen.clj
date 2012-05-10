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
  (:require [clojure.java.shell :as shell])
  (:import [java.io File]))

(defn get-main-fn
  [project-file]
  (when (> (count project-file) 0)
    (let [x (first project-file)]
      (if (= x :main)
        (second project-file)
        (recur (rest project-file))))))

(defn read-project-file
  [project-dir]
  (let [project-file (File. project-dir "project.clj")]
    (if (.exists project-file)
      (with-open
        [r (java.io.PushbackReader.
             (clojure.java.io/reader project-file))]
        (binding [*read-eval* false]
          (read r))))))

(defn new-project
  [lein-path parent-dir name]
  (println lein-path parent-dir name)
  (shell/sh lein-path "new" name :dir parent-dir)
  (shell/sh lein-path "deps" :dir (str 
                                    parent-dir 
                                    (java.io.File/separator)
                                    name)))

