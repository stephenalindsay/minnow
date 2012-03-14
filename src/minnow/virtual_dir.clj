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

(ns minnow.virtual-dir)

(defprotocol IVirtualDir
  (isDirectory [this])
  (listFiles [this])
  (addPath [this path])
  (removePath [this path])
  (getName [this])
  (getParentFile [this]))

(defrecord VirtualDir [paths]
  IVirtualDir
  (isDirectory [this] true)
  (listFiles [this] (sort @paths))
  (addPath [this path] (swap! paths conj path))
  (removePath [this path] (swap! paths (fn [p] (remove #(= % path) p))))
  (getName [this] "")
  (getParentFile [this] nil))

(defn new-dir 
  [paths]
  (VirtualDir. (atom paths)))
