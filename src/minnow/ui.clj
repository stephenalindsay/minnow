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
            [clojure.pprint :as pp])
  (:import [java.awt Color Font Point Dimension]
           [java.util Date]
           [javax.swing JLabel JViewport SwingUtilities ]
           [org.jdesktop.swingx JXCollapsiblePane]
           [java.io File]
           [org.fife.ui.rsyntaxtextarea RSyntaxTextArea TextEditorPane SyntaxConstants Token FileLocation]
           [org.fife.ui.rtextarea RTextScrollPane RTextArea SearchEngine SearchContext]))

(declare evaluate-file)
(declare evaluate-selected)
(declare select-form)
(declare select-and-evaluate-form)
(declare get-active-output-area)
(declare set-namespace-to-active-file)

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

(defn scroll-caret-to-centre
  "From: http://www.camick.com/java/source/RXTextUtilities.java"
  [text-area]
  (if-let [view-port (SwingUtilities/getAncestorOfClass JViewport text-area)]
    (let [rect     (.modelToView text-area (.getCaretPosition text-area))
          extent-h (.height (.getExtentSize view-port))
          view-h   (.height (.getViewSize view-port))
          y        (max 0 (- (.y rect) (/ extent-h 2)))
          y        (min y (- view-h extent-h))]
      (.setViewPosition view-port (Point. 0 y)))))          

(defn skip-to-next-def
  [text-area forward] 
  (when (SearchEngine/find text-area (doto (SearchContext.)
                                       (.setSearchFor "^\\(")
                                       (.setSearchForward forward)
                                       (.setWholeWord false)
                                       (.setMatchCase false)
                                       (.setRegularExpression true)))
    (scroll-caret-to-centre text-area)))

(defn close-file 
  [file scroll-pane]
  (swap! open-files (fn [files] (into [] (filter #(not= (.getPath file) %) files))))
  (swap! pane-to-file-map dissoc scroll-pane)
  (prefs/set-open-files @open-files)
  (close-pane scroll-pane @state/editor-tab-pane))  

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
    (keymap/map-key text-area "alt DOWN" (fn [_] (skip-to-next-def text-area true)))
    (keymap/map-key text-area "alt UP" (fn [_] (skip-to-next-def text-area false)))    
    (keymap/map-key text-area "control PAGE_UP" (fn [_] (tab-skip-fn -1)))                                                  
    (keymap/map-key text-area "control PAGE_DOWN" (fn [_] (tab-skip-fn 1)))
    (keymap/map-key @state/main-frame 
                    "shift control F" (fn [_] (evaluate-file)))
    (keymap/map-key text-area "shift control E" (fn [_] (evaluate-selected)))    
    (keymap/map-key text-area "shift control S" (fn [_] (select-and-evaluate-form)))    
    (keymap/map-key text-area "control N" (fn [_] (set-namespace-to-active-file)))
    (keymap/map-key text-area "control S" (fn [_]
                                            (.save text-area)
                                            (swap! pane-to-file-map assoc-in [scroll-pane :last-modified] (.lastModified file))
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
    (prefs/set-open-files @open-files)))

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
  (prefs/set-project-list (into [] (map #(.getPath %) (.listFiles @state/virtual-dir)))))

(defn load-project-tree-pref []
  (if-let [projects (prefs/get-project-list)]
    (into [] (map #(java.io.File. %) projects))
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
  (let [previous-open-dir (prefs/get-last-open-dir)    
        project-file      (chooser/choose-file 
                            :filters [maven-project-filter lein-project-filter]
                            :dir (if previous-open-dir
                                   previous-open-dir
                                   (System/getProperty "user.home")))]
    (when project-file
      (prefs/set-last-open-dir (.getParent (.getParentFile project-file)))
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
  (if-let [lein-path (prefs/get-lein-path)]
    lein-path
    (do
      (seesaw/alert :text "Leiningen path not set, please set path to leiningen script.")
      (when-let [f (chooser/choose-file)]
        (let [path (.getPath f)]
          (prefs/set-lein-path path)
          path)))))

(defn new-project []
  (when-let [lein-path (get-lein-path)]
    (let [previous-open-dir (prefs/get-last-open-dir)]
      (if-let [project-dir  (chooser/choose-file
                              :dir (if previous-open-dir
                                     previous-open-dir
                                     (System/getProperty "user.home")))]
        (let [parent  (.getParent project-dir)
              name    (.getName project-dir)
              resp    (lein/new-project lein-path parent name)]
          (prefs/set-last-open-dir (.getParent (.getParentFile project-dir)))
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
      (when-let [ns (get-namespace (.replace (.getText text-area) (System/getProperty "line.separator") " "))]
        (let [output-area  (get-active-output-area)
              project-repl (get @state/output-area-to-repl-map output-area)]
          (when (and output-area project-repl)
            (repl/evaluate-code-in-repl project-repl (str "(ns " ns ")") output-area true)))))))
  
;; Menu 

(defn quit []
  (if-not @state/standalone
    (do
      (.hide @state/main-frame)
      (reset! state/main-frame nil))
    (System/exit 0)))

(defn main-menu []
  (seesaw/menubar :items  
                  [(seesaw/menu :text "Minnow"
                                :mnemonic \M
                                :items
                                [(seesaw/menu      :text "Preferences"
                                                   :items [(seesaw/menu-item :text "Editor Font"
                                                                             :listen [:action (fn [_] (update-default-editor-font))])
                                                           (seesaw/checkbox-menu-item :text "Show full stacktraces"
                                                                                      :listen [:action (fn [e]
                                                                                                         (if (seesaw/selection e)
                                                                                                           (repl/set-full-stacktraces-in-repl true)
                                                                                                           (repl/set-full-stacktraces-in-repl false)))])])
                                 (seesaw/menu-item :text "Quit"
                                                   :listen [:action (fn [_] (quit))])])
                   (seesaw/menu :text "Project"
                                :mnemonic \P
                                :items
                                [(seesaw/menu-item :text "New"
                                                   :listen [:action (fn [_] (new-project))])
                                 (seesaw/menu-item :text "Open"
                                                   :listen [:action (fn [_] (open-project))])])
                   (seesaw/menu :text "Source"
                                :mnemonic \S
                                :items
                                [(seesaw/menu-item :text "Evaluate File"
                                                   :mnemonic \F
                                                   :listen [:action (fn [_] (evaluate-file))])
                                 (seesaw/menu-item :text "Evaluate Selected"
                                                   :mnemonic \E
                                                   :listen [:action (fn [_] (evaluate-selected))])                                
                                 (seesaw/menu-item :text "Select Form"
                                                   :mnemonic \S
                                                   :listen [:action (fn [_] (select-form))])
                                 (seesaw/menu-item :text "Select And Evaluate Form"
                                                   :listen [:action (fn [_] (select-and-evaluate-form))])
                                 (seesaw/menu-item :text "Set repl namespace to file"
                                                   :mnemonic \N
                                                   :listen [:action (fn [_] (set-namespace-to-active-file))])
                                 (seesaw/menu-item :text "Re-indent Selection"
                                                   :mnemonic \S
                                                   :listen [:action (fn [_] (reindent-selection))])
                                 (seesaw/menu-item :text "Re-indent File"
                                                   :mnemonic \R
                                                   :listen [:action (fn [_] (reindent-file))])])]))

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

(defn get-start-of-form
  [text-area pos]
  (if (> pos 1)
    (let [text   (.getText text-area pos 2)]
      (if (= text "\n(")
        (+ 1 pos)
        (recur text-area (dec pos))))
    0))
  
(defn get-end-of-form
  [text-area pos]
  (if (> (.getLength (.getDocument text-area)) pos)
    (let [text   (.getText text-area pos 2)]
      (if (= text "\n(")
        pos
        (recur text-area (inc pos))))
    (.getLength (.getDocument text-area))))
  
(defn get-current-form-bounds
  [text-area]
  (let [pos   (.getCaretPosition text-area)
        start (get-start-of-form text-area pos)
        end   (get-end-of-form text-area pos)]
    {:start start :end end}))
  
;; arrggh, cut and paste code. TODO tidy up please.
  
(defn evaluate-selected []
  "Evaluate top level form relative to caret position"
  (when-let [selected-pane (.getSelectedComponent @state/editor-tab-pane)]
    (when-let [output-area (get-active-output-area)]
      (when-let [repl (get @state/output-area-to-repl-map output-area)]
        (let [text-area   (.getTextArea selected-pane)
              code        (.getSelectedText text-area)]
          (repl/evaluate-code-in-repl repl code output-area true))))))  
        
(defn select-form []
  "Evaluate top level form relative to caret position"
  (when-let [selected-pane (.getSelectedComponent @state/editor-tab-pane)]
    (let [text-area   (.getTextArea selected-pane)
          {:keys [start end]} (get-current-form-bounds text-area)
          form        (.getText text-area start (- end start))]
      (when form
        (.select text-area start end)))))

(defn select-and-evaluate-form []
  (select-form)
  (evaluate-selected))

(defn evaluate-file []
  (when-let [selected-pane (.getSelectedComponent @state/editor-tab-pane)]
    (when-let [output-area (get-active-output-area)]
      (when-let [r (get @state/output-area-to-repl-map output-area)]
        (.append output-area "\nEvaluating file...\n")
        (repl/evaluate-code-in-repl r (.getText (.getTextArea selected-pane)) output-area true)
        (.append output-area "\nDone.\n")))))

(defn search 
  [textbox forward wrap regex match-case]
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
                (.setCaretPosition text-area current-pos)))))))))

(defn editor-panel []
  (let [find        (seesaw/text :columns 15)
        replace     (seesaw/text :columns 15)
        reverse     (seesaw/checkbox :text "Reverse")        
        wrap        (seesaw/checkbox :text "Wrap" :selected? true)        
        regex       (seesaw/checkbox :text "Regex")
        match-case  (seesaw/checkbox :text "Match Case")
        replace-btn (seesaw/button :text "Replace"
                                   :enabled? false)        
        _ (seesaw/config! replace-btn :listen [:action (fn [_] 
                                                         (when-let [text-area (get-active-text-area)]
                                                           (.replaceSelection text-area (.getText replace))
                                                           (if (search find
                                                                       (not (.isSelected reverse)) 
                                                                       (.isSelected wrap)
                                                                       (.isSelected regex)
                                                                       (.isSelected match-case))
                                                             (.setEnabled replace-btn true)
                                                             (.setEnabled replace-btn false))))])
        toolbar     (seesaw/toolbar :floatable? false
                                   :orientation :horizontal
                                   :items [find
                                           (seesaw/button :text "Find"
                                                          :listen [:action (fn [_]
                                                                             (if (search find
                                                                                         (not (.isSelected reverse))
                                                                                         (.isSelected wrap)
                                                                                         (.isSelected regex)
                                                                                         (.isSelected match-case))
                                                                               (.setEnabled replace-btn true)
                                                                               (.setEnabled replace-btn false)))])
                                           replace 
                                           replace-btn
                                           reverse
                                           wrap
                                           regex
                                           match-case])
        collapsible (doto (JXCollapsiblePane.)
                      (.add toolbar)
                      (.setAnimated false)
                      (.setCollapsed true))
        panel       (mig/mig-panel
                      :constraints ["insets 0 0 0 0" "" ""]
                      :items [[@state/editor-tab-pane "height :3000, width :3000, wrap"]
                              [collapsible ""]])]
    (keymap/map-key panel "control F" (fn [_] 
                                        (.setEnabled replace-btn false)
                                        (.setCollapsed collapsible false)
                                        (doto find
                                          (.requestFocusInWindow)
                                          (.selectAll))))
    (keymap/map-key find "ENTER"  (fn [_] (if (search find
                                                      (not (.isSelected reverse))
                                                      (.isSelected wrap)
                                                      (.isSelected regex)
                                                      (.isSelected match-case))
                                            (.setEnabled replace-btn true)
                                            (.setEnabled replace-btn false))))
    (keymap/map-key toolbar "ESCAPE" (fn [_]
                                       (.setCollapsed collapsible true)
                                       (.setEnabled replace-btn false)
                                       (if-let [selected-pane (.getSelectedComponent @state/editor-tab-pane)]
                                         (.requestFocusInWindow (.getTextArea selected-pane)))))                                      
    (.setMinimumSize panel (Dimension. 100 100))
    panel))
  
(defn frame-content-1 []
  (seesaw/left-right-split
    (seesaw/scrollable
      @state/project-tree)
    (seesaw/top-bottom-split
      (editor-panel)
      @state/output-tab-pane
      :divider-location 3/5)
    :divider-location 200))

(defn frame-content-2 []
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
  (let [project-list (.listFiles @state/virtual-dir)
        lb           (seesaw/listbox
                       :model project-list
                       :renderer (fn [renderer info]
                                   (let [v (:value info)]
                                     (seesaw/config! renderer :text (.getName v)))))
        f            (seesaw/frame 
                       :title "Select Project"
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
    (seesaw/show! f)))

(defn main-frame [] 
  (let [f (seesaw/frame :title "Minnow"
                        :height 800
                        :width 1024
                        :menubar @state/main-menu
                        :content (frame-content-2))     
        n (atom frame-content-1)]
    (keymap/map-key f "shift control R" (fn [_] 
                                          (repl-project-select)))
    (keymap/map-key f "shift control A" (fn [_] 
                                          (seesaw/config! f :content ((swap! n (fn [p] 
                                                                                 (if (= p frame-content-1)
                                                                                   frame-content-2
                                                                                   frame-content-1)))))))
    f))

(defn set-global-keybindings
  [frame]
  (keymap/map-key 
    frame "control E" (fn [e] 
                        (if-let [scrollpane (.getSelectedComponent @state/editor-tab-pane)]
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
  (reset! open-files (into [] (prefs/get-open-files)))
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
             :load-file-fn load-file-into-editor
             :close-project-fn close-project }))
  (reset! state/output-tab-pane (seesaw/tabbed-panel))
  (reset! state/editor-tab-pane (seesaw/tabbed-panel))
  (seesaw/listen @state/editor-tab-pane :state-changed (fn [e] (check-for-fs-update)))
  (reset! state/main-frame (main-frame))
  (open-previously-open-files)
  (set-global-keybindings @state/main-frame))

(defn gui []
  (seesaw/native!)
  (init-ui-state)
  (seesaw/show! @state/main-frame))

