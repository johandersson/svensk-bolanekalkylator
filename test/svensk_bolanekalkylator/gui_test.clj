(ns svensk-bolanekalkylator.gui-test
  (:require [calculator :as calc]
            [clojure.test :refer [deftest is]]
            [kalp :as kalp]
            [svensk-bolanekalkylator.kalp-test :as kalp-test]
            [gui :as gui])
  (:import [java.awt Container GraphicsEnvironment]
           [java.awt.datatransfer Clipboard DataFlavor]
           [java.awt.event ActionEvent WindowEvent]
           [javax.swing JButton JComponent JFrame JLabel KeyStroke SwingUtilities Timer
            JTextArea JTextField WindowConstants]))

(defn components [root]
  (tree-seq #(instance? Container %)
            #(seq (.getComponents ^Container %))
            root))

(defn component-labels [root]
  (->> (components root)
       (filter #(instance? JLabel %))
       (map #(.getText ^JLabel %))
       set))

(deftest comment-field-is-limited-to-4000-characters
  (let [field (gui/limit-text-length! (JTextField.) gui/comment-max-length)]
    (.setText field (apply str (repeat 4001 "a")))
    (is (= 4000 (count (.getText field))))
    (.select field 3998 4000)
    (.replaceSelection field "bbbb")
    (is (= 4000 (count (.getText field))))
    (is (.endsWith (.getText field) "bb"))))

(deftest about-help-covers-every-major-function
  (let [titles (set (map first gui/about-help-sections))
        help-text (apply str (map second gui/about-help-sections))]
    (is (= #{"Bolånekalkyl" "Maximal boendekostnad" "Kvar att leva på"
             "Spara och hantera objekt" "Resultat och jämförelse"}
           titles))
    (is (.contains help-text "kalkylr"))
    (is (.contains help-text "bankens kreditpr"))))

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
                   clipboard (Clipboard. "test")
                   toasts (atom [])
                   observed (atom nil)
                   timer (Timer.
                          50
                          (reify java.awt.event.ActionListener
                            (actionPerformed [_ _]
                              (reset! observed (.isShowing dialog))
                              (let [copy-btn
                                    (first (filter
                                            #(and (instance? JButton %)
                                                  (= "Kopiera till urklipp"
                                                     (.getText ^JButton %)))
                                            (components (.getContentPane dialog))))]
                                (with-redefs [gui/system-clipboard (fn [] clipboard)
                                              gui/show-toast!
                                              (fn [owner message]
                                                (swap! toasts conj [owner message]))]
                                  (.doClick ^JButton copy-btn))
                                (let [copied (.getData clipboard DataFlavor/stringFlavor)]
                                  (is (.startsWith ^String copied
                                                   "Din bolånekalkyl\nTest <b>bostad</b> · Testgatan 1\n\n"))
                                  (is (.endsWith ^String copied
                                                 "2 låntagare med jämn räntefördelning och tillräcklig skatt."))
                                  (is (not (.contains ^String copied "<html>")))
                                  (doseq [label (component-labels (.getContentPane dialog))
                                          :when (not (.startsWith ^String label "<html>"))]
                                    (is (.contains ^String copied label) label)))
                                (is (= [[dialog "Sparad till urklipp!"]] @toasts))
                                (is (.isShowing dialog))
                                (gui/show-toast! dialog "Sparad till urklipp!")
                                (let [toast (first (.getOwnedWindows dialog))]
                                  (is (some? toast))
                                  (when toast
                                    (is (.isShowing toast))
                                    (is (contains? (component-labels toast)
                                                   "Sparad till urklipp!"))
                                    (.dispose toast))))
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

(deftest failed-copy-does-not-show-success-toast
  (doseq [error [(IllegalStateException. "busy")
                 (SecurityException. "denied")]]
    (let [messages (atom [])
          toasts (atom [])]
      (with-redefs [gui/system-clipboard (fn [] (throw error))
                    gui/show-error! (fn [& args] (swap! messages conj args))
                    gui/show-toast! (fn [& args] (swap! toasts conj args))]
        (gui/copy-to-clipboard! nil "Exact text"))
      (is (= 1 (count @messages)))
      (is (= "Kunde inte kopiera" (second (first @messages))))
      (is (empty? @toasts)))))

(deftest kalp-tab-and-readable-summary
  (when-not (GraphicsEnvironment/isHeadless)
    (SwingUtilities/invokeAndWait
     (bound-fn []
       (let [ui (gui/create-ui)
             frame (:frame ui)
             tabs (:tabs ui)
             kalp-ui (:kalp ui)]
         (try
           (is (= ["Bolån" "Kvar att leva på"]
                  (mapv #(.getTitleAt tabs %) (range (.getTabCount tabs)))))
           (.setSelectedIndex tabs 1)
           (is (= "Beräkna Kvar att leva på" (.getText (:calc-btn kalp-ui))))
           (is (not (.isSelected (:tax-cb kalp-ui))))
           (gui/show-kalp-result! kalp-ui (kalp/calculate kalp-test/inputs))
           (doseq [size [940 820]]
             (.setSize frame size size)
             (.validate frame)
             (doseq [component (components (:panel kalp-ui))
                     :when (instance? JLabel component)]
               (is (>= (.getWidth ^JLabel component)
                       (.width (.getPreferredSize ^JLabel component)))
                   (.getText ^JLabel component))))
           (is (contains? (component-labels (:result kalp-ui)) "1000,00 kr/mån"))
           (is (some #(.contains ^String % "överskott")
                     (component-labels (:result kalp-ui))))
           (.setText (get-in kalp-ui [:fields :net-income]) "50000")
           (is (zero? (.getComponentCount (:result kalp-ui))))
           (gui/show-kalp-result! kalp-ui
                                  (kalp/calculate
                                   (assoc kalp-test/inputs :net-income 30000.0)))
           (is (some #(.contains ^String % "Underskott")
                     (component-labels (:result kalp-ui))))
           (gui/show-kalp-result! kalp-ui
                                  (kalp/calculate
                                   (assoc kalp-test/inputs :net-income 39000.0)))
           (is (some #(.contains ^String % "utan marginal")
                     (component-labels (:result kalp-ui))))
           (.doClick (:tax-cb kalp-ui))
           (is (zero? (.getComponentCount (:result kalp-ui))))
           (finally (.dispose frame))))))))
