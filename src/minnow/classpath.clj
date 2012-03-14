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

(ns minnow.classpath
  (:require [fs.core :as fs]))

(defn build-classpath-from-dir
  "Grab all the jars from a directory and build a classpath string"
  [dir]
  (if-let [files (fs/list-dir dir)]
    (let [jars  (filter #(.endsWith % ".jar") files)]
      (.substring (reduce (fn [x y] (str x(java.io.File/pathSeparator)dir"/"y)) "" jars) 1))
    ""))

