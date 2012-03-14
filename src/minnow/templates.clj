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

(ns minnow.templates)

(def preamble
"(defproject %s \"1.0.0-SNAPSHOT\"
  :description \"FIXME: write description\"")

(def clojure-1-3 "[org.clojure/clojure \"1.3.0\"]")

(defn lein-file
  [project-name dependencies]
  (let [project-def (format preamble project-name)
        deps-def (str ":dependencies ["
                      (reduce #(str %1 "\n  " %2) dependencies) "]")]
    (str project-def "\n  " deps-def ")")))

(defn simple-project [project-name] (lein-file project-name [clojure-1-3]))
