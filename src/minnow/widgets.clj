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

(ns minnow.widgets
  (:import  [javax.swing JLabel JPanel JButton AbstractButton BorderFactory]
            [java.awt FlowLayout Dimension BasicStroke Color]
            [java.awt.event MouseAdapter ActionListener]
            [javax.swing.plaf.basic BasicButtonUI]
            [java.awt Color Font]))

(defn tab-close-button
  "Tab close button for panel in tabbed pane"
  [panel pane close-fn]
  (let [mouse-over-fn (fn [e painted]
                        (let [component (.getComponent e)]
                          (when (instance? AbstractButton component)
                            (.setBorderPainted component painted))))
        button (doto (proxy
                       [JButton ActionListener]
                       []
                       (updateUI [])
                       (paintComponent [g]
                                       (proxy-super paintComponent g)
                                       (let [g2    (.create g)
                                             delta 6
                                             width-delta (- (.getWidth this) delta 1)
                                             height-delta (- (.getHeight this) delta 1)]
                                         (when (.isPressed (.getModel this))
                                           (.translate g2 1 1))
                                         (doto g2
                                           (.setStroke (BasicStroke. 2))
                                           (.setColor Color/BLACK)
                                           (.drawLine delta delta width-delta height-delta)
                                           (.drawLine width-delta delta delta height-delta))
                                         (when (.isRollover (.getModel this))
                                           (.setColor g2 Color/MAGENTA))
                                         (.dispose g2)))
                       (actionPerformed [e]
                                        (let [index (.indexOfTabComponent pane panel)]
                                          (when (not= -1 index)
                                            (.remove pane index)
                                            (when close-fn
                                              (close-fn))))))
                 (.setPreferredSize (Dimension. 17 17))
                 (.setToolTipText "Close this tab")
                 (.setUI (BasicButtonUI.))
                 (.setContentAreaFilled false)
                 (.setFocusable false)
                 (.setBorder (BorderFactory/createEtchedBorder))
                 (.setBorderPainted false)
                 (.setRolloverEnabled true)
                 (.addMouseListener (proxy [MouseAdapter] []
                                      (mouseEntered [e] (mouse-over-fn e true))
                                      (mouseExited  [e] (mouse-over-fn e false)))))]
    (.addActionListener button button)
    button))

(defn button-tab-component
  "Adds close button to tab pane. 
  See http://docs.oracle.com/javase/tutorial/uiswing/components/tabbedpane.html for details."
  [pane tab-string close-fn]
  (let [label  (doto (JLabel. tab-string)
                 (.setBorder (BorderFactory/createEmptyBorder 0 0 0 5)))
        panel  (proxy
                 [JPanel]
                 [(FlowLayout. FlowLayout/LEFT 0 0)]
                 (setDirty [b] (if b
                                 (.setText label (str tab-string " *"))
                                 (.setText label tab-string))))
        button (tab-close-button panel pane close-fn)]
    (doto panel
      (.setOpaque false)
      (.add label)
      (.add button)
      (.setBorder (BorderFactory/createEmptyBorder 2 0 0 2)))))
