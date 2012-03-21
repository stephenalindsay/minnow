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

(ns minnow.preferences
  (:import 
    [java.util.prefs Preferences]
    [java.awt Font]))

(def preferences-key "org.minnow-ide")

(def open-files "open-files")
(def editor-font "editor-font")
(def project-list "project-list")
(def last-open-dir "last-open-dir")
(def lein-path "lein-path")

(defn get-prefs []
  (.node (Preferences/userRoot) preferences-key))

(defn get-pref 
  [key]
  (try 
    (when-let [pref (.get (get-prefs) key nil)]
      (read-string pref))
    (catch Exception e (.printStackTrace e))))

(defn add-pref
  [key value]
  (try
    (let [prefs (get-prefs)]
      (.put prefs key (binding [*print-dup* true] 
                        (prn-str value)))          
      (.flush prefs))
    (catch Exception e (.printStackTrace e))))

(defn get-default-font []
  (let [{:keys [family
                style
                size]} (get-pref editor-font)]
    (when (and family style size)
      (Font. family style size))))

(defn set-default-font
  [font]
  (add-pref editor-font {:family (.getFamily font)
                         :style (.getStyle font)
                         :size (.getSize font)}))

(defn get-open-files [] (get-pref open-files))

(defn set-open-files
  [file-list]
  (add-pref open-files file-list))

(defn get-project-list [] (get-pref project-list))

(defn set-project-list
  [list]
  (add-pref project-list list))

(defn get-last-open-dir [] (get-pref last-open-dir))

(defn set-last-open-dir
  [dir]
  (add-pref last-open-dir dir))

(defn get-lein-path [] (get-pref lein-path))

(defn set-lein-path
  [path]
  (add-pref lein-path path))


