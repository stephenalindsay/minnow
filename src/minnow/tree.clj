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

(ns minnow.tree
  (:use [seesaw core keymap chooser tree cells])
  (:import [java.io File]))

(native!)

(defprotocol IVirtualDir
  (isDirectory [this])
  (listFiles [this])
  (addPath [this path]))

(defrecord VirtualDir [paths]
  IVirtualDir
  (isDirectory [this] true)
  (listFiles [this] @paths)
  (addPath [this path]
            (swap! paths conj path)))

(def data [(File. "/tmp")
           (File. "/var")])

(def vd (VirtualDir. (atom data)))

(defn tree-model
  [data]
  (simple-tree-model
    #(.isDirectory %)
    #(.listFiles %)
    data))

(def t 
  (tree :id :tree :model (tree-model vd) :root-visible? false
        :popup (fn [e] 
                 (if-let [s (selection e)]
                   (when (.isFile (last s))
                     [(menu-item :text "Open")])))))

(def f 
  (frame :title "File Explorer" :width 500 :height 500 :content
         ;(border-panel :border 5 :hgap 5 :vgap 5
         ;              :north (label :id :current-dir :text "Location")
         ;              :center (left-right-split
                                 (scrollable t)))
         ;                        (scrollable (listbox :id :list ))) 
         ;              :south (label :id :status :text "Ready"))))

(defn add-path
  [path]
  (.addPath vd path)
  (config! t :model (tree-model vd)))


