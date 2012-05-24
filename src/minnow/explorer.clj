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

(ns minnow.explorer
  (:require 
    [clojure.java.io :as io]
    [seesaw.core :as seesaw]
    [seesaw.tree :as tree]
    [seesaw.swingx :as swingx]
    [seesaw.keymap :as keymap]
    [minnow.preferences :as prefs]
    [minnow.ui.virtual-dir :as vd])
  (:import 
    [java.io File]))

(defn project-tree-model
  [data]
  (tree/simple-tree-model 
    #(.isDirectory %) 
    #(filter 
       (fn [x] (not (.startsWith (.getName x) ".")))
       (.listFiles %))
    data))

(defn- project-renderer
  [renderer info]
  (let [v    (:value info)
        text (.getName v)]
    (seesaw/config! renderer :text text)))

(defn get-expanded-paths
  [tree]
  (doall 
    (for [rownum (range (.getRowCount tree))] 
      (.getPathForRow tree rownum))))

(defn update-tree 
  [tree root]
  (let [expanded-paths (get-expanded-paths tree)]
    (seesaw/config! tree :model (project-tree-model root))
    (doseq [path expanded-paths]
      (.makeVisible tree path))))

(defn menu-item
  [tree root text the-fn & args]
  (seesaw/menu-item :text text
                    :listen [:action (fn [_] 
                                       (apply the-fn args)
                                       (update-tree tree root))]))

(defn load-project-tree-pref []
  (if-let [projects (prefs/get "project-list")]
    (into [] (map #(java.io.File. %) projects))
    []))

(defn- project-popup
  [e tree root model fn-map]
  (if-let [s (seesaw/selection e)]
    (let [file (last s)
          {:keys [start-repl-fn
                  new-file-fn
                  new-dir-fn
                  load-file-fn
                  close-project-fn
                  rename-file-fn
                  delete-file-fn]} fn-map
          menu-fn (fn [text f]
                    (when f
                      (menu-item tree root text f file)))]
      (if (and (some (set (.listFiles root)) [file]) start-repl-fn)
        [(menu-fn "Start REPL" start-repl-fn)
         (menu-fn "Close Project" close-project-fn)
         (menu-fn "New File" new-file-fn)
         (menu-fn "New Directory" new-dir-fn)
         (menu-fn "Reload" identity)]
        (if (and (.isDirectory file) (or new-file-fn new-dir-fn)) 
          (filter identity
                  [(menu-fn "New File" new-file-fn)
                   (menu-fn "New Directory" new-dir-fn)])
          (when (.isFile file)
            (filter identity
                    [(when load-file-fn (menu-fn "Open" load-file-fn))
                     (when rename-file-fn (menu-fn "Rename" rename-file-fn))
                     (when delete-file-fn (menu-fn "Delete" delete-file-fn))])))))))

(defn project-tree
  [model root fn-map]
  (let [tree (swingx/tree-x :id :project-tree
                            :model model
                            :root-visible? false
                            :renderer (:renderer-fn fn-map)
                            :listen [:mouse-pressed (:mouse-pressed-fn fn-map)])]
    (seesaw/config! tree :popup #(project-popup % tree root model fn-map))
    (keymap/map-key tree "ENTER" (:enter-press-fn fn-map))
    tree))

(defn delete-file
  [file]
  (when (seesaw/show! (seesaw/dialog :content "Are you sure you want to delete file?" 
                                     :option-type :yes-no
                                     :size [400 :by 200]))
    (.delete file)))

(defn new-file
  [parent-dir]
  (when-let [name (seesaw/input "Name of the new file?")]
    (let [new-file (File. (str parent-dir File/separator name))]
      (if (.exists new-file)
        (seesaw/alert "File already exists with that name!")
        (when-not (.createNewFile new-file)
          (seesaw/alert "Unable to create file"))))))

(defn new-dir
  [parent-dir]
  (when-let [name (seesaw/input "Name of the new directory?")]
    (let [new-dir (File. (str parent-dir File/separator name))]
      (if (.exists new-dir)
        (seesaw/alert "File already exists with that name!")
        (when-not (.mkdir new-dir)
          (seesaw/alert "Unable to create directory"))))))

(defn rename-file
  [file]
  (when-let [name (seesaw/input "New name for file?" :value (.getName file))]
    (.renameTo file (io/file (.getParent file) name))))

(defn new-project-tree
  [root fn-map]
  (project-tree
    (project-tree-model root)
    root
    (merge {:new-dir-fn new-dir
            :new-file-fn new-file
            :rename-file-fn rename-file
            :delete-file-fn delete-file
            :renderer-fn project-renderer} fn-map)))

(def test-frame
  (let [root (vd/new-dir (load-project-tree-pref))]
    (seesaw/frame
      :height 500
      :width 300
      :content (new-project-tree root
                 {:mouse-pressed-fn (fn [_] (println "click"))
                  :enter-press-fn (fn [_] (println "press enter"))
                  :start-repl-fn (fn [file] (println "start repl for : " file))
                  :load-file-fn (fn [file] (println "load file : " file))
                  :close-project-fn (fn [file] (println "close project : " file))}))))

(defn testy []
  (seesaw/native!)
  (seesaw/show! test-frame))

