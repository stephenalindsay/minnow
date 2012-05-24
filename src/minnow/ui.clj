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

(ns minnow.ui
  ^{:doc "UI functions for Minnow"
    :author "Steve Lindsay"}
  (:require [clojure.java.io :as io]
            [seesaw.core :as seesaw]
            [seesaw.tree :as tree]
            [seesaw.chooser :as chooser]
            [seesaw.keymap :as keymap]
            [seesaw.swingx :as swingx]
            [seesaw.event :as event]
            [seesaw.mig :as mig]
            [minnow.indent :as indent]
            [minnow.explorer :as explorer]
            [minnow.util.classpath :as cp]
            [minnow.preferences :as prefs]
            [minnow.ui.state :as state]
            [minnow.ui.completion :as completion]
            [minnow.ui.virtual-dir :as vd]
            [minnow.ui.widgets :as widgets]
            [minnow.ui.logging :as logging]    
            [minnow.ui.repl :as repl]
            [minnow.ui.text-area :as ta]
            [minnow.leiningen :as lein]
            [fontselector.core :as font]
            [clojure.pprint :as pp]
            [clojure.repl])
  (:import [java.awt Dimension]
           [javax.swing JLabel]
           [javax.swing.text Utilities]
           [org.jdesktop.swingx JXCollapsiblePane]
           [java.io File]
           [org.fife.ui.rtextarea RTextScrollPane SearchEngine SearchContext]))

(declare evaluate-file)
(declare evaluate-top-level-form)
(declare get-active-output-area)
(declare set-namespace-to-active-file)
(declare get-active-text-area)

(def open-files (atom []))

(def pane-to-file-map (atom {}))

;; File operations

(defn update-tab-dirty-status
  [text-area label file-name]
  (if (.isDirty text-area)
    (.setText label (str file-name " *"))
    (.setText label file-name)))

(defn close-pane
  [scroll-panel split-pane]
  (let [index (.indexOfComponent split-pane scroll-panel)]
    (when (not= -1 index)
      (.remove split-pane index))))

(defn close-file
  [file scroll-pane]
  (swap! open-files (fn [files] (into [] (filter #(not= (.getPath file) %) files))))
  (swap! pane-to-file-map dissoc scroll-pane)
  (prefs/set "open-files" @open-files)
  (close-pane scroll-pane @state/editor-tab-pane))

(defn get-active-repl []
  (get @state/output-area-to-repl-map (get-active-output-area)))

(defn show-doc []  
  (let [ta             (get-active-text-area)
        output-area    (get-active-output-area)
        pos            (.getCaretPosition (get-active-text-area))
        start          (Utilities/getWordStart ta pos)
        end            (Utilities/getWordEnd ta pos)
        word           (.getText ta start (- end start))
        preceding-char (when (> start 0)
                         (.getText ta (dec start) 1))
        next-char      (try 
                         (.getText ta end 1))
        update-fn      (fn [word]
                         (when (and word (> (count word) 0))
                           (when-let [project-repl (get-active-repl)]
                             (try 
                               (repl/eval-and-display (format "(clojure.repl/doc %s)" word) project-repl output-area)
                               (catch Exception e 
                                 (println (.getMessage e))
                                 (.printStackTrace e))))))]
    (if (= preceding-char "(")
      (if (= next-char "!")
        (update-fn (str word "!"))
        (update-fn word))
      (if (= preceding-char "/")
        (let [e (- start 2)
              s (Utilities/getWordStart ta e)
              preceding-word (.getText ta s (- e s -1))]
          (update-fn (str preceding-word "/" word)))
        (update-fn word)))))

(defn load-file-into-editor
  [file]
  (let [text-area    (ta/text-editor-pane file)
        scroll-pane  (RTextScrollPane. text-area)
        file-name    (.getName file)
        button-tab   (widgets/button-tab-component @state/editor-tab-pane file-name #(close-file file scroll-pane))
        label        (first (filter #(instance? JLabel %)(.getComponents button-tab)))
        new-tab      {:title button-tab
                      :content scroll-pane
                      :tip (.getAbsolutePath file)}
        tab-skip-fn  (fn [amount]
                       (if-let [tp @state/editor-tab-pane]
                         (let [idx     (.getSelectedIndex tp)
                               n       (.getTabCount tp)
                               new-idx (+ idx amount)
                               new-idx (if (>= new-idx n)
                                         0
                                         (if (< new-idx 0)
                                           (- n 1)
                                           new-idx))]
                           (.setSelectedIndex tp new-idx))))]
    (swap! pane-to-file-map assoc scroll-pane {:file file
                                               :last-modified (.lastModified file)})
    (keymap/map-key text-area "alt DOWN" (fn [_] (ta/skip-to-next-def text-area true)))
    (keymap/map-key text-area "alt UP" (fn [_] (ta/skip-to-next-def text-area false)))    
    (keymap/map-key text-area "control PAGE_UP" (fn [_] (tab-skip-fn -1)))                      
    (keymap/map-key text-area "control PAGE_DOWN" (fn [_] (tab-skip-fn 1)))
    (keymap/map-key @state/main-frame "shift control F" (fn [_] (evaluate-file)))
    (keymap/map-key text-area "control ENTER" (fn [_] (evaluate-top-level-form)))
    (keymap/map-key text-area "control N" (fn [_] (set-namespace-to-active-file)))
    (keymap/map-key text-area "control S" (fn [_]
                                            (.save text-area)
                                            (swap! pane-to-file-map assoc-in 
                                              [scroll-pane :last-modified] (.lastModified file))
                                            (update-tab-dirty-status text-area label file-name)))
    (keymap/map-key text-area "control W" (fn [_]
                                            (let [warning (fn [msg]
                                                            (when (seesaw/show!
                                                                    (seesaw/dialog :type :warning
                                                                                   :content msg
                                                                                   :size [400 :by 200]
                                                                                   :option-type :ok-cancel))
                                                              (close-file file scroll-pane)))]
                                              (if (.isDirty text-area)
                                                (warning "File has unsaved modifications.\nPress OK to discard changes and\n
                                                            close file, cancel to continue editing file.")
                                                (let [last-modified (get-in @pane-to-file-map [scroll-pane :last-modified])]
                                                  (if (not= last-modified (.lastModified file))
                                                    (do 
                                                      (swap! pane-to-file-map assoc-in [scroll-pane :last-modified] (.lastModified file))
                                                      (warning "File has been modifed outside editor. \nDiscard your changes and close file?"))
                                                    (close-file file scroll-pane)))))))                                                
    (swap! state/editor-tab-pane #(seesaw/config! % :tabs (conj (:tabs %) new-tab)))
    (.setSelectedIndex @state/editor-tab-pane (.indexOfComponent @state/editor-tab-pane scroll-pane))
    (seesaw/listen text-area
      #{:remove-update :insert-update}
      (fn [_] (when label (update-tab-dirty-status text-area label file-name))))
    (doto text-area
      (.requestFocusInWindow)
      (.setCaretPosition 0))
    (swap! open-files (fn [files]
                        (let [path (.getPath file)]
                          (if-not (some #{path} files)
                            (conj files path)
                            files))))
    (prefs/set "open-files" @open-files)))

;; Project filters - used for controlling what files are visible in file picker.

(def lein-project-filter 
  (chooser/file-filter "Leiningen Project" #(or 
                                              (= "project.clj" (.getName %)) 
                                              (.isDirectory %))))

(def maven-project-filter 
  (chooser/file-filter "Maven Project" #(or 
                                          (= "pom.xml" (.getName %)) 
                                          (.isDirectory %))))

(defn save-project-tree-pref []
  (prefs/set "project-list" (into [] (map #(.getPath %) (.listFiles @state/virtual-dir)))))

(defn load-project-tree-pref []
  (if-let [projects (prefs/get "project-list")]
    (into [] (map #(File. %) projects))
    []))

(defn add-path-to-virtual-dir
  [path]
  (.addPath @state/virtual-dir path)
  (explorer/update-tree @state/project-tree @state/virtual-dir)
  (save-project-tree-pref))

(defn open-leiningen-project
  [project-file]
  (let [parent (.getParentFile project-file)]
    (add-path-to-virtual-dir parent)))

(defn open-maven-project
  [project-file])

(defn open-project []
  (let [previous-open-dir (prefs/get "last-open-dir")    
        project-file      (chooser/choose-file 
                            :filters [maven-project-filter lein-project-filter]
                            :dir (if previous-open-dir
                                   previous-open-dir
                                   (System/getProperty "user.home")))]
    (when project-file
      (prefs/set "last-open-dir" (.getParent (.getParentFile project-file)))
      (condp = (.getName project-file)
        "project.clj" (open-leiningen-project project-file)
        "pom.xml" (open-maven-project project-file)
        (seesaw/alert "Unknown project type"))))) 

(defn get-active-text-area []
  (if-let [pane (.getSelectedComponent @state/editor-tab-pane)]
    (.getTextArea pane)))

(defn reindent-selection []
  (if-let [text-area (get-active-text-area)]
    (let [start (.getSelectionStart text-area)
          end   (.getSelectionEnd text-area)
          text  (.getText text-area start (- end start))]
      (.replaceSelection text-area (indent/reindent-string text)))))

(defn reindent-file []
  (if-let [text-area (get-active-text-area)]
    (let [caret-pos   (.getCaretPosition text-area)
          old-content (.getText text-area)
          new-content (indent/reindent-string old-content)]
      (.setText text-area new-content)
      (.setCaretPosition text-area (min caret-pos (count new-content))))))

(defn get-lein-path []
  (if-let [lein-path (prefs/get "lein-path")]
    lein-path
    (do
      (seesaw/alert :text "Leiningen path not set, please set path to leiningen script.")
      (when-let [f (chooser/choose-file)]
        (let [path (.getPath f)]
          (prefs/set "lein-path" path)
          path)))))

(defn new-project []
  (when-let [lein-path (get-lein-path)]
    (let [previous-open-dir (prefs/get "last-open-dir")]
      (if-let [project-dir  (chooser/choose-file
                              :dir (if previous-open-dir
                                     previous-open-dir
                                     (System/getProperty "user.home")))]
        (let [parent  (.getParent project-dir)
              name    (.getName project-dir)
              resp    (lein/new-project lein-path parent name)]
          (prefs/set "last-open-dir" (.getParent (.getParentFile project-dir)))
          (open-leiningen-project (io/file project-dir "project.clj")))))))

(defn update-default-editor-font [] 
  (let [default-font (prefs/get-default-font)]
    (when-let [f (seesaw/show! (if default-font 
                                 (font/selector default-font)
                                 (font/selector)))]
      (prefs/set-default-font f)
      (doseq [i (range (.getTabCount @state/editor-tab-pane))]
        (let [component (.getComponentAt @state/editor-tab-pane i)
              text-area (.getTextArea component)]
          (.setFont text-area f))))))

(defn get-namespace
  [text]
  (let [bais (java.io.ByteArrayInputStream. (.getBytes text))
        pbr  (java.io.PushbackReader. (clojure.java.io/reader bais))]
    (binding [*read-eval* false]
      (loop [code  (read pbr)
             n     100]
        (when (and 
                (> (count code) 0)
                (> n 0))
          (let [x (first code)]
            (if (= x 'ns)
              (second code)
              (recur (rest code) (dec n)))))))))

(defn get-active-text-area []
  (when @state/editor-tab-pane
    (when-let [active-tab (.getSelectedComponent @state/editor-tab-pane)]
      (.getTextArea active-tab)))) 
  
(defn set-namespace-to-active-file []
  (when-let [text-area (get-active-text-area)]
    (when (.endsWith (.getFileName text-area) ".clj")
      (when-let [ns (get-namespace (.getText text-area))]
        (let [output-area  (get-active-output-area)
              project-repl (get @state/output-area-to-repl-map output-area)]
          (when (and output-area project-repl)
            (repl/update-ns ns project-repl output-area)))))))
  
;; Menu 

(defn quit []
  (if-not @state/standalone
    (do
      (.hide @state/main-frame)
      (reset! state/main-frame nil))
    (System/exit 0)))

(defn menu-item
  ([text f]
    (seesaw/menu-item :text text :listen [:action (fn [_] (f))]))   
  ([text mnemonic f]
    (seesaw/menu-item :text text :mnemonic mnemonic :listen [:action (fn [_] (f))])))

(defn main-menu []
  (seesaw/menubar 
    :items
    [(seesaw/menu 
       :text "Minnow"
       :mnemonic \M
       :items [(seesaw/menu 
                 :text "Preferences"
                 :items [(menu-item "Editor Font" update-default-editor-font)
                         (seesaw/checkbox-menu-item 
                           :text "Show full stacktraces"
                           :selected? true 
                           :listen [:action #(if (seesaw/selection %)
                                               (reset! repl/set-full-stacktraces true)
                                               (reset! repl/set-full-stacktraces false))])])
               (menu-item "Quit" quit)])
     (seesaw/menu 
       :text "Project"
       :mnemonic \P
       :items [(menu-item "New" new-project)
               (menu-item "Open" open-project)])
     (seesaw/menu 
       :text "Source"
       :mnemonic \S
       :items [(menu-item "Evaluate File" \F evaluate-file)
               (menu-item "Set repl namespace to file" \N set-namespace-to-active-file)
               (menu-item "Re-indent Selection" \S reindent-selection)
               (menu-item "Re-indent File" \R reindent-file)])]))

;; Project tree functions

(defn- load-file-from-tree-selection 
  [e]
  (when-let [path (last (seesaw/selection e))]
    (when (.isFile path)
      (load-file-into-editor path))))

(defn- load-file-on-double-click
  [e]
  (when (== 2 (.getClickCount e))
    (load-file-from-tree-selection e)))

(defn get-active-output-area []
  (when-let [tc (.getSelectedComponent @state/output-tab-pane)]
    (.getView (first (.getComponents (.getTopComponent tc))))))
 
(defn evaluate-top-level-form []
  (when-let [ta (get-active-text-area)]
    (let [{:keys [start end]} (ta/get-current-form-bounds ta)
          text (.getText ta start (- end start))]
      (repl/eval-and-display text (get-active-repl) (get-active-output-area)))))

(defn evaluate-file []
  (when-let [text-area (get-active-text-area)]
    (when-let [output-area (get-active-output-area)]
      (when-let [project-repl (get @state/output-area-to-repl-map output-area)]
        (.append output-area "\nEvaluating file...\n")
        (repl/eval-and-display (.getText text-area) project-repl output-area)
        (.append output-area "\nDone.\n")))))

(defn search 
  ([textbox forward]
    (search textbox forward true true true))
  ([textbox forward wrap regex match-case]
    (when-let [selected-pane (.getSelectedComponent @state/editor-tab-pane)]
      (when-let [text (.getText textbox)] 
        (if (> (count text) 0)
          (let [text-area   (.getTextArea selected-pane)
                current-pos (.getCaretPosition text-area)
                sc          (doto (SearchContext.)
                              (.setSearchForward forward)
                              (.setMatchCase match-case)
                              (.setWholeWord false)
                              (.setRegularExpression regex)
                              (.setSearchFor text))]
            (if (SearchEngine/find text-area sc)
              true
              (when wrap
                (.setCaretPosition text-area 1)
                (if (SearchEngine/find text-area sc)
                  true
                  (.setCaretPosition text-area current-pos))))))))))

(defn editor-panel []
  (let [find        (seesaw/text :columns 10)
        replace     (seesaw/text :columns 10)
        reverse     (seesaw/checkbox :text "Reverse")        
        replace-btn (seesaw/button :text "Replace"
                                   :enabled? false)        
        _ (seesaw/config! replace-btn :listen [:action (fn [_] 
                                                         (when-let [text-area (get-active-text-area)]
                                                           (.replaceSelection text-area (.getText replace))
                                                           (if (search find (not (.isSelected reverse)))
                                                             (.setEnabled replace-btn true)
                                                             (.setEnabled replace-btn false))))])
        toolbar     (seesaw/toolbar :floatable? false
                                   :orientation :horizontal
                                   :items [find
                                           (seesaw/button :text "Find"
                                                          :listen [:action (fn [_]
                                                                             (if (search find (not (.isSelected reverse)))
                                                                               (.setEnabled replace-btn true)
                                                                               (.setEnabled replace-btn false)))])
                                           replace 
                                           replace-btn
                                           reverse])
        collapsible (doto (JXCollapsiblePane.)
                      (.add toolbar)
                      (.setAnimated false)
                      (.setCollapsed true))
        panel       (mig/mig-panel
                      :constraints ["insets 0 0 0 0" "fill" ""]
                      :items [[@state/editor-tab-pane "height :3000, width :3000, wrap"]
                              [collapsible ""]])]
    (keymap/map-key panel "alt D" (fn [_] (show-doc)))
    (keymap/map-key panel "control F" (fn [_] 
                                        (.setEnabled replace-btn false)
                                        (.setCollapsed collapsible false)
                                        (doto find
                                          (.requestFocusInWindow)
                                          (.selectAll))))
    (keymap/map-key find "ENTER"  (fn [_] (if (search find (not (.isSelected reverse)))
                                            (.setEnabled replace-btn true)
                                            (.setEnabled replace-btn false))))
    (keymap/map-key toolbar "ESCAPE" (fn [_]
                                       (.setCollapsed collapsible true)
                                       (.setEnabled replace-btn false)
                                       (if-let [selected-pane (.getSelectedComponent @state/editor-tab-pane)]
                                         (.requestFocusInWindow (.getTextArea selected-pane)))))                                      
    (.setMinimumSize panel (Dimension. 100 100))
    panel))
  
(defn frame-content []
  (.setMinimumSize @state/editor-tab-pane (Dimension.))
  (.setMinimumSize @state/output-tab-pane (Dimension.))
  (reset! 
    state/main-split (seesaw/left-right-split
                       (editor-panel)
                       nil))
  (seesaw/left-right-split
    (seesaw/scrollable
      @state/project-tree)
    @state/main-split
    :divider-location 200))

(defn start-repl-from-list-selection
  [listbox frame]
  (let [project-dir (seesaw/selection listbox)]
    (seesaw/hide! frame)
    (repl/start-project-repl project-dir)))

(defn repl-project-select []
  (let [project-list (.listFiles @state/virtual-dir)]
    (when (> (count project-list) 0)
      (let [lb           (seesaw/listbox
                           :model project-list
                           :renderer (fn [renderer info]
                                       (let [v (:value info)]
                                         (seesaw/config! renderer :text (.getName v)))))
            f            (seesaw/frame 
                           :title "Start REPL for project:"
                           :height 300
                           :width 300)
            p            (seesaw/border-panel
                           :center (seesaw/scrollable lb)
                           :south (seesaw/button :text "Cancel"
                                    :listen [:action (fn [e] 
                                                       (seesaw/hide! f))]))]
        (seesaw/listen lb :mouse-pressed (fn [e]
                                           (when (== 2 (.getClickCount e))
                                             (start-repl-from-list-selection lb f))))
        (keymap/map-key f "ENTER" (fn [e] (start-repl-from-list-selection lb f)))
        (keymap/map-key f "ESCAPE" (fn [e] (seesaw/hide! f)))
        (.setContentPane f p)
        (seesaw/show! f)))))

(defn main-frame [] 
  (let [f (seesaw/frame :title "Minnow"
                        :height 800
                        :width 1024
                        :menubar @state/main-menu
                        :content (frame-content))]
    (keymap/map-key f "shift control R" (fn [_] (repl-project-select)))
    f))

(defn set-global-keybindings
  [frame]
  (keymap/map-key 
    frame "control E" (fn [e] 
                        (when-let [scrollpane (.getSelectedComponent @state/editor-tab-pane)]
                          (.requestFocusInWindow (.getTextArea scrollpane)))))
  (keymap/map-key 
    frame "control R" (fn [e] 
                        (when-let [output (.getSelectedComponent @state/output-tab-pane)] 
                          (.requestFocusInWindow (.getBottomComponent output)))))
  (keymap/map-key frame "control P" (fn [e] (.requestFocusInWindow @state/project-tree))))

(defn close-project
  [project-dir]
  (swap! state/virtual-dir (fn [root]                             
                             (vd/removePath root project-dir)
                             (save-project-tree-pref)
                             root)))

(defn open-previously-open-files []
  (reset! open-files (into [] (prefs/get "open-files")))
  (doseq [file @open-files]
    (load-file-into-editor (File. file))))

(defn check-for-fs-update []
  (when-let [pane (.getSelectedComponent @state/editor-tab-pane)]
    (when-let [{:keys [file last-modified]} (get @pane-to-file-map pane)]
      (when (.exists file)
        (when (not= last-modified (.lastModified file))
          (swap! pane-to-file-map assoc-in [pane :last-modified] (.lastModified file))
          (let [resp (seesaw/show!
                  (seesaw/dialog 
                    :type :warning
                    :content "File has been modifed outside editor.\nDiscard your changes and reload?"
                    :size [400 :by 200]
                    :option-type :yes-no))]
            (when (= resp :success)
              (println "updating file!")
              (.setText (.getTextArea pane) (slurp file)))))))))
      
;; Application control
(defn init-ui-state []
  (reset! state/main-menu (main-menu))
  (reset! state/virtual-dir (vd/new-dir (load-project-tree-pref)))
  (reset! state/project-tree 
          (explorer/new-project-tree 
            @state/virtual-dir
            {:mouse-pressed-fn load-file-on-double-click
             :enter-press-fn load-file-from-tree-selection
             :start-repl-fn repl/start-project-repl
             :start-repl-fn-2 repl/start-project-repl-2
             :load-file-fn load-file-into-editor
             :close-project-fn close-project }))
  (reset! state/output-tab-pane (seesaw/tabbed-panel))
  (reset! state/editor-tab-pane (seesaw/tabbed-panel))
  (seesaw/listen @state/editor-tab-pane :state-changed (fn [e] (check-for-fs-update)))
  (reset! state/main-frame (main-frame))
  (open-previously-open-files)
  (set-global-keybindings @state/main-frame)
  (future
    (Thread/sleep 1000)
    (repl-project-select)))

(defn gui []
  (seesaw/native!)
  (init-ui-state)
  (seesaw/show! @state/main-frame))
