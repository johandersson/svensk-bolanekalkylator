(ns gui
  (:import [java.awt BorderLayout Color Dimension Font GridLayout Insets]
           [javax.swing BorderFactory JButton JCheckBox JFrame JLabel JPanel
            JScrollPane JTextArea JTextField SwingConstants JSlider]))

(defn- add-row! [panel label component]
  (.add panel (JLabel. label SwingConstants/RIGHT))
  (.add panel component))

(defn create-ui []
  (let [frame (JFrame. "Svensk bolånekalkylator")
        form (JPanel. (GridLayout. 0 2 8 8))
        results (JTextArea.)
        calc-btn (JButton. "Beräkna bolån")
        save-btn (JButton. "Spara inställningar")
        rate-slider (JSlider. 0 200 70)
        rate-label (JLabel. "3,50 %")
        p-price-f (JTextField. "3000000")
        d-pay-f (JTextField. "450000")
        fee-f (JTextField. "3500")
        op-cost-f (JTextField. "2500")
        income-f (JTextField. "600000")
        t-low-f (JTextField. "30")
        t-high-f (JTextField. "21")
        extra-amort-cb (JCheckBox. "Räkna med extra amortering vid hög skuldkvot")]

    (.setDefaultCloseOperation frame JFrame/EXIT_ON_CLOSE)
    (.setSize frame 650 760)
    (.setLocationRelativeTo frame nil)
    (.setBackground form (Color. 245 247 250))
    (.setBorder form (BorderFactory/createEmptyBorder 16 16 16 16))
    (.setFont calc-btn (Font. "SansSerif" Font/BOLD 14))
    (.setBackground calc-btn (Color. 44 115 175))
    (.setForeground calc-btn Color/WHITE)
    (.setEditable results false)
    (.setFont results (Font. "Monospaced" Font/PLAIN 13))
    (.setBackground results (Color. 25 29 35))
    (.setForeground results (Color. 220 230 220))
    (.setMargin results (Insets. 12 12 12 12))

    (add-row! form "Köpeskilling (kr):" p-price-f)
    (add-row! form "Kontantinsats (kr):" d-pay-f)
    (.add form (JLabel. "Räntesats:"))
    (let [rate-panel (JPanel. (BorderLayout. 8 0))]
      (.add rate-panel rate-slider BorderLayout/CENTER)
      (.add rate-panel rate-label BorderLayout/EAST)
      (.add form rate-panel))
    (add-row! form "Månadsavgift (kr):" fee-f)
    (add-row! form "Driftskostnad per månad (kr):" op-cost-f)
    (add-row! form "Hushållets bruttoinkomst per år (kr):" income-f)
    (add-row! form "Skattereduktion upp till 100 000 kr (%):" t-low-f)
    (add-row! form "Skattereduktion över 100 000 kr (%):" t-high-f)
    (.add form extra-amort-cb)
    (.add form (JLabel. ""))

    (let [main-panel (JPanel.)
          scroll-pane (JScrollPane. results)
          btn-panel (JPanel.)]
      (.setLayout main-panel (BorderLayout.))
      (.add main-panel form BorderLayout/NORTH)
      (.add main-panel scroll-pane BorderLayout/CENTER)
      (.add btn-panel calc-btn)
      (.add btn-panel save-btn)
      (.add main-panel btn-panel BorderLayout/SOUTH)
      (.add frame main-panel))

    (.setVisible frame true)

    {:frame frame :results results :calc-btn calc-btn :save-btn save-btn
     :rate-slider rate-slider :rate-label rate-label :p-price-f p-price-f
     :d-pay-f d-pay-f :fee-f fee-f :op-cost-f op-cost-f :income-f income-f
     :t-low-f t-low-f :t-high-f t-high-f :extra-amort-cb extra-amort-cb}))
