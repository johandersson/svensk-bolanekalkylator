(ns svensk-bolanekalkylator.gui-test
  (:require [calculator :as calc]
            [clojure.test :refer [deftest is]]
            [gui :as gui])
  (:import [java.awt Container GraphicsEnvironment]
           [java.awt.event ActionEvent WindowEvent]
           [javax.swing JComponent JFrame JLabel KeyStroke SwingUtilities Timer
            WindowConstants]))

(defn component-labels [root]
  (->> (tree-seq #(instance? Container %)
                 #(seq (.getComponents ^Container %))
                 root)
       (filter #(instance? JLabel %))
       (map #(.getText ^JLabel %))
       set))

(deftest calculation-dialog-layout-and-lifecycle
  (if (GraphicsEnvironment/isHeadless)
    (println "Skipping window lifecycle test in a headless environment.")
    (SwingUtilities/invokeAndWait
     (bound-fn []
       (let [frame (doto (JFrame.)
                     (.setSize 940 940))
             data (calc/calculate-loan
                   3000000.0 500000.0 4.0 4000.0 1000.0 0.0 30.0 21.0 false 2)
             details {:object-name "Test <b>bostad</b>"
                      :address "Testgatan 1"
                      :purchase-price 3000000.0
                      :down-payment 500000.0
                      :interest 4.0
                      :monthly-fee 4000.0
                      :monthly-operating-cost 1000.0}]
         (try
           (.setVisible frame true)
           (doseq [size [940 820]
                   close-method [:button :escape :window]]
             (.setSize frame size size)
             (let [dialog (gui/create-calculation-dialog frame data details)
                   root-pane (.getRootPane dialog)
                   observed (atom nil)
                   timer (Timer.
                          50
                          (reify java.awt.event.ActionListener
                            (actionPerformed [_ _]
                              (reset! observed (.isShowing dialog))
                              (doseq [component
                                      (tree-seq
                                       #(instance? Container %)
                                       #(seq (.getComponents ^Container %))
                                       (.getContentPane dialog))
                                      :when (instance? JLabel component)]
                                (is (>= (.getWidth ^JLabel component)
                                        (.width (.getPreferredSize ^JLabel component)))
                                    (.getText ^JLabel component)))
                              (case close-method
                                :button (.doClick (.getDefaultButton root-pane))
                                :escape
                                (let [key (.get (.getInputMap
                                                 root-pane
                                                 JComponent/WHEN_IN_FOCUSED_WINDOW)
                                                (KeyStroke/getKeyStroke "ESCAPE"))]
                                  (.actionPerformed
                                   (.get (.getActionMap root-pane) key)
                                   (ActionEvent. dialog ActionEvent/ACTION_PERFORMED
                                                 "close")))
                                :window
                                (.dispatchEvent
                                 dialog
                                 (WindowEvent. dialog WindowEvent/WINDOW_CLOSING))))))]
               (try
                 (is (.isModal dialog))
                 (is (identical? frame (.getOwner dialog)))
                 (is (= WindowConstants/DISPOSE_ON_CLOSE
                        (.getDefaultCloseOperation dialog)))
                 (is (= (int (* 0.85 size)) (.getWidth dialog)))
                 (is (= (int (* 0.85 size)) (.getHeight dialog)))
                 (is (not (.isResizable dialog)))
                 (let [labels (component-labels (.getContentPane dialog))]
                   (doseq [label ["Din bolånekalkyl" "Bostad och lån"
                                  "Månadskostnad" "Amortering"
                                  "Total månadskostnad efter skattereduktion"
                                  "Antal låntagare" "2" "Ränta efter skattereduktion"]]
                     (is (contains? labels label)))
                   (is (some #(.contains ^String % "&lt;b&gt;bostad&lt;/b&gt;")
                             labels)))
                 (.setRepeats timer false)
                 (.start timer)
                 (.setVisible dialog true)
                 (is (true? @observed))
                 (is (not (.isDisplayable dialog)))
                 (is (.isShowing frame))
                 (finally
                   (.stop timer)
                   (.dispose dialog)))))
           (finally
             (.dispose frame))))))))
