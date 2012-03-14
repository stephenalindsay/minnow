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
  (:import [java.util.prefs Preferences]))

(def preferences-key "org.pretendcow.minnow")

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
