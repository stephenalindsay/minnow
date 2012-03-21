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

(ns minnow.ui.logging
  (:require 
    [fs.core :as fs])
  (:import 
    [java.util Date]
    [java.text SimpleDateFormat]
    [java.io File PrintStream FileOutputStream]))

(def df (SimpleDateFormat. "yyyyMMdd-HHmmss"))

(def today (.format df (Date.)))

(def home-dir (System/getProperty "user.home"))

(def minnow-home-dir (str home-dir "/.minnow"))
(def minnow-log-dir (str home-dir "/.minnow/log"))
(def minnow-log (str minnow-log-dir "/" today ".log"))

(defn setup-logging []
  (when-not (fs/directory? minnow-home-dir) (fs/mkdir minnow-home-dir))
  (when-not (fs/directory? minnow-log-dir) (fs/mkdir minnow-log-dir))
  (let [ps (PrintStream. (FileOutputStream. (File. minnow-log)))] 
    (System/setOut ps)
    (System/setErr ps)))


