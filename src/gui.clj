(ns gui
  (:require [clojure.string :as str])
  (:import [java.awt BorderLayout Color Cursor Desktop Dimension FlowLayout
            Font Graphics2D GridLayout Insets Point Rectangle RenderingHints]
           [java.net URI]
           [javax.swing BorderFactory JButton JCheckBox JDialog JFrame JLabel
            JPanel JEditorPane JList JMenu JMenuBar JOptionPane JScrollPane
            JTextArea JTextField SwingConstants JSlider JMenuItem UIManager
            JWindow ListSelectionModel Scrollable Timer WindowConstants]
           [javax.swing.border AbstractBorder]
           [javax.swing.event HyperlinkEvent$EventType]))

(def background (Color. 246 248 252))
(def surface Color/WHITE)
(def text-primary (Color. 24 34 48))
(def text-secondary (Color. 91 103 120))
(def border-color (Color. 218 225 234))
(def blue (Color. 37 99 235))
(def blue-hover (Color. 29 78 216))
(def green (Color. 5 150 105))
(def green-hover (Color. 4 120 87))
(def red (Color. 220 38 38))
(def red-hover (Color. 185 28 28))
(def ui-font "Segoe UI")

(defn- enable-antialiasing! [graphics]
  (.setRenderingHint graphics RenderingHints/KEY_ANTIALIASING
                     RenderingHints/VALUE_ANTIALIAS_ON))

(defn- rounded-border [color]
  (proxy [AbstractBorder] []
    (paintBorder [_ graphics x y width height]
      (let [graphics-2d (.create ^Graphics2D graphics)]
        (enable-antialiasing! graphics-2d)
        (.setColor graphics-2d color)
        (.drawRoundRect graphics-2d x y (dec width) (dec height) 12 12)
        (.dispose graphics-2d)))
    (getBorderInsets [_]
      (Insets. 9 12 9 12))))

(defn- shadow-panel [layout]
  (doto
   (proxy [JPanel] [layout]
     (paintComponent [graphics]
       (let [graphics-2d (.create ^Graphics2D graphics)
             width (.getWidth this)
             height (.getHeight this)]
         (enable-antialiasing! graphics-2d)
         (.setColor graphics-2d (Color. 15 23 42 22))
         (.fillRoundRect graphics-2d 4 6 (- width 8) (- height 10) 18 18)
         (.setColor graphics-2d surface)
         (.fillRoundRect graphics-2d 0 0 (- width 8) (- height 10) 18 18)
         (.dispose graphics-2d))))
    (.setOpaque false)
    (.setBorder (BorderFactory/createEmptyBorder 18 18 22 22))))

(defn- scrollable-panel [layout]
  (proxy [JPanel Scrollable] [layout]
    (getPreferredScrollableViewportSize []
      (.getPreferredSize this))
    (getScrollableUnitIncrement [_visible-rect _orientation _direction]
      24)
    (getScrollableBlockIncrement [^Rectangle visible-rect orientation _direction]
      (if (= orientation SwingConstants/VERTICAL)
        (- (.height visible-rect) 24)
        (- (.width visible-rect) 24)))
    (getScrollableTracksViewportWidth [] true)
    (getScrollableTracksViewportHeight [] false)))

(defn- rounded-button [text color hover-color]
  (doto
   (proxy [JButton] [text]
     (paintComponent [graphics]
       (let [graphics-2d (.create ^Graphics2D graphics)
             model (.getModel this)
             fill-color (cond
                          (not (.isEnabled this)) (Color. 148 163 184)
                          (or (.isPressed model) (.isRollover model)) hover-color
                          :else color)]
         (.setFont graphics-2d (.getFont this))
         (let [metrics (.getFontMetrics graphics-2d)
               text-x (/ (- (.getWidth this) (.stringWidth metrics text)) 2)
               text-y (+ (/ (- (.getHeight this) (.getHeight metrics)) 2)
                         (.getAscent metrics))]
           (enable-antialiasing! graphics-2d)
           (.setColor graphics-2d fill-color)
           (.fillRoundRect graphics-2d 0 0 (.getWidth this) (.getHeight this) 14 14)
           (.setColor graphics-2d Color/WHITE)
           (.drawString graphics-2d text (int text-x) (int text-y)))
         (.dispose graphics-2d))))
    (.setFont (Font. ui-font Font/BOLD 14))
    (.setPreferredSize (Dimension. 160 42))
    (.setContentAreaFilled false)
    (.setBorderPainted false)
    (.setFocusPainted false)
    (.setCursor (Cursor/getPredefinedCursor Cursor/HAND_CURSOR))))

(defn- style-field! [field]
  (.setFont field (Font. ui-font Font/PLAIN 14))
  (.setForeground field text-primary)
  (.setBackground field surface)
  (.setBorder field (rounded-border border-color)))

(defn- add-row! [panel label component]
  (let [label-component (JLabel. label SwingConstants/LEFT)]
    (.setFont label-component (Font. ui-font Font/PLAIN 13))
    (.setForeground label-component text-secondary)
    (.add panel label-component))
  (.add panel component))

(defn- escape-html [value]
  (str/escape value {\& "&amp;" \< "&lt;" \> "&gt;" \" "&quot;"}))

(defn- web-uri [address]
  (let [address (str/trim address)
        address (if (re-find #"(?i)^https?://" address)
                  address
                  (str "https://" address))
        uri (URI. address)]
    (when (contains? #{"http" "https"} (.toLowerCase (.getScheme uri)))
      uri)))

(defn show-link! [link-pane address]
  (let [address (str/trim (or address ""))]
    (if (str/blank? address)
      (.setText link-pane "")
      (try
        (if-let [uri (web-uri address)]
          (.setText link-pane
                    (str "<html><a href=\"" (escape-html (str uri)) "\">"
                         (escape-html address) "</a></html>"))
          (.setText link-pane "Ogiltig webbadress"))
        (catch Exception _
          (.setText link-pane "Ogiltig webbadress"))))))

(defn show-current-object! [ui object-name]
  (.setText (:current-object-label ui) (str "Öppet objekt: " object-name))
  (.setTitle (:frame ui)
             (str object-name " · Svensk bolånekalkylator")))

(defn show-toast! [frame message]
  (let [toast (JWindow. frame)
        panel (proxy [JPanel] [(BorderLayout.)]
                (paintComponent [graphics]
                  (let [graphics-2d (.create ^Graphics2D graphics)]
                    (enable-antialiasing! graphics-2d)
                    (.setColor graphics-2d (Color. 15 23 42 45))
                    (.fillRoundRect graphics-2d 4 5
                                    (- (.getWidth this) 8)
                                    (- (.getHeight this) 9) 16 16)
                    (.setColor graphics-2d green)
                    (.fillRoundRect graphics-2d 0 0
                                    (- (.getWidth this) 8)
                                    (- (.getHeight this) 9) 16 16)
                    (.dispose graphics-2d))))
        label (JLabel. message SwingConstants/CENTER)
        timer (Timer. 2000 nil)]
    (.setOpaque panel false)
    (.setBorder panel (BorderFactory/createEmptyBorder 12 24 17 28))
    (.setFont label (Font. ui-font Font/BOLD 15))
    (.setForeground label Color/WHITE)
    (.add panel label BorderLayout/CENTER)
    (.setContentPane toast panel)
    (.setFocusableWindowState toast false)
    (.setAlwaysOnTop toast true)
    (.setBackground toast (Color. 0 0 0 0))
    (.pack toast)
    (let [frame-location (.getLocationOnScreen frame)
          x (- (+ (.x ^Point frame-location) (.getWidth frame))
               (.getWidth toast) 24)
          y (- (+ (.y ^Point frame-location) (.getHeight frame))
               (.getHeight toast) 48)]
      (.setLocation toast x y))
    (.addActionListener
     timer
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (.dispose toast))))
    (.setRepeats timer false)
    (.setVisible toast true)
    (.start timer)))

(defn show-error! [frame title-text message]
  (let [dialog (JDialog. frame title-text true)
        root (JPanel. (BorderLayout. 0 18))
        title (JLabel. title-text)
        body (JLabel. (str "<html><div style='width:320px'>"
                           (escape-html message) "</div></html>"))
        actions (JPanel. (FlowLayout. FlowLayout/RIGHT 0 0))
        close-btn (rounded-button "Okej" blue blue-hover)]
    (.setDefaultCloseOperation dialog WindowConstants/DISPOSE_ON_CLOSE)
    (.setBackground root background)
    (.setBorder root (BorderFactory/createEmptyBorder 24 24 22 24))
    (.setFont title (Font. ui-font Font/BOLD 22))
    (.setForeground title text-primary)
    (.setFont body (Font. ui-font Font/PLAIN 14))
    (.setForeground body text-secondary)
    (.setOpaque actions false)
    (.setPreferredSize close-btn (Dimension. 100 42))
    (.add actions close-btn)
    (.addActionListener
     close-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (.dispose dialog))))
    (.add root title BorderLayout/NORTH)
    (.add root body BorderLayout/CENTER)
    (.add root actions BorderLayout/SOUTH)
    (.setContentPane dialog root)
    (.setDefaultButton (.getRootPane dialog) close-btn)
    (.pack dialog)
    (.setResizable dialog false)
    (.setLocationRelativeTo dialog frame)
    (.setVisible dialog true)))

(defn confirm-delete! [frame object-name]
  (let [confirmed? (atom false)
        dialog (JDialog. frame "Radera sparat objekt" true)
        root (JPanel. (BorderLayout. 0 20))
        heading (JPanel. (GridLayout. 0 1 0 4))
        title (JLabel. "Radera objektet?")
        subtitle (JLabel. "Åtgärden går inte att ångra")
        content (shadow-panel (GridLayout. 0 1 0 8))
        object-label (JLabel. object-name)
        message (JLabel. (str "<html><div style='width:390px'>"
                              "Det sparade objektet och eventuella osparade "
                              "ändringar tas bort permanent."
                              "</div></html>"))
        actions (JPanel. (FlowLayout. FlowLayout/RIGHT 10 0))
        cancel-btn (rounded-button "Avbryt" (Color. 100 116 139)
                                   (Color. 71 85 105))
        delete-btn (rounded-button "Radera" red red-hover)]
    (.setDefaultCloseOperation dialog WindowConstants/DISPOSE_ON_CLOSE)
    (.setBackground root background)
    (.setBorder root (BorderFactory/createEmptyBorder 28 28 24 28))
    (.setOpaque heading false)
    (.setFont title (Font. ui-font Font/BOLD 24))
    (.setForeground title text-primary)
    (.setFont subtitle (Font. ui-font Font/PLAIN 14))
    (.setForeground subtitle red)
    (.add heading title)
    (.add heading subtitle)
    (.setFont object-label (Font. ui-font Font/BOLD 17))
    (.setForeground object-label text-primary)
    (.setFont message (Font. ui-font Font/PLAIN 13))
    (.setForeground message text-secondary)
    (.add content object-label)
    (.add content message)
    (.setOpaque actions false)
    (.setPreferredSize cancel-btn (Dimension. 110 42))
    (.setPreferredSize delete-btn (Dimension. 110 42))
    (.add actions cancel-btn)
    (.add actions delete-btn)
    (.addActionListener
     cancel-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (.dispose dialog))))
    (.addActionListener
     delete-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (reset! confirmed? true)
         (.dispose dialog))))
    (.add root heading BorderLayout/NORTH)
    (.add root content BorderLayout/CENTER)
    (.add root actions BorderLayout/SOUTH)
    (.setContentPane dialog root)
    (.setDefaultButton (.getRootPane dialog) cancel-btn)
    (.pack dialog)
    (.setResizable dialog false)
    (.setLocationRelativeTo dialog frame)
    (.setVisible dialog true)
    @confirmed?))

(defn show-about! [frame]
  (let [dialog (JDialog. frame "Om Bolånekalkylator" true)
        root (JPanel. (BorderLayout. 0 20))
        heading (JPanel. (GridLayout. 0 1 0 4))
        title (JLabel. "Bolånekalkylator")
        subtitle (JLabel. "Ett enklare beslutsunderlag för ditt nästa boende")
        content (shadow-panel (GridLayout. 0 1 0 10))
        description (JLabel. (str "<html><div style='width:420px'>"
                                  "Beräkna månadskostnad, ränta och amortering "
                                  "för svenska bolån. Spara flera objekt med "
                                  "adress, kommentar och länk till annonsen."
                                  "</div></html>"))
        license-title (JLabel. "Fri programvara")
        license-text (JLabel. (str "<html><div style='width:420px'>"
                                   "Programmet distribueras under GNU General "
                                   "Public License version 3 (GPLv3)."
                                   "<br><br>Copyright © 2026 Johan Andersson"
                                   "</div></html>"))
        actions (JPanel. (FlowLayout. FlowLayout/RIGHT 0 0))
        close-btn (rounded-button "Stäng" blue blue-hover)]
    (.setDefaultCloseOperation dialog WindowConstants/DISPOSE_ON_CLOSE)
    (.setBackground root background)
    (.setBorder root (BorderFactory/createEmptyBorder 28 28 24 28))
    (.setOpaque heading false)
    (.setFont title (Font. ui-font Font/BOLD 28))
    (.setForeground title text-primary)
    (.setFont subtitle (Font. ui-font Font/PLAIN 14))
    (.setForeground subtitle text-secondary)
    (.add heading title)
    (.add heading subtitle)
    (.setFont description (Font. ui-font Font/PLAIN 14))
    (.setForeground description text-primary)
    (.setFont license-title (Font. ui-font Font/BOLD 16))
    (.setForeground license-title green)
    (.setFont license-text (Font. ui-font Font/PLAIN 13))
    (.setForeground license-text text-secondary)
    (.add content description)
    (.add content license-title)
    (.add content license-text)
    (.setOpaque actions false)
    (.setPreferredSize close-btn (Dimension. 110 42))
    (.add actions close-btn)
    (.addActionListener
     close-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (.dispose dialog))))
    (.add root heading BorderLayout/NORTH)
    (.add root content BorderLayout/CENTER)
    (.add root actions BorderLayout/SOUTH)
    (.setContentPane dialog root)
    (.setDefaultButton (.getRootPane dialog) close-btn)
    (.pack dialog)
    (.setResizable dialog false)
    (.setLocationRelativeTo dialog frame)
    (.setVisible dialog true)))

(defn ask-maximum-cost! [frame]
  (let [value (atom nil)
        dialog (JDialog. frame "Maximal totalkostnad" true)
        root (JPanel. (BorderLayout. 0 18))
        heading (JPanel. (GridLayout. 0 1 0 4))
        title (JLabel. "Ange din månadsgräns")
        subtitle (JLabel. "Köpeskillingen anpassas efter din budget")
        input-panel (shadow-panel (GridLayout. 0 1 0 8))
        input-label (JLabel. "Maximal totalkostnad efter skattereduktion (kr/mån)")
        cost-field (JTextField.)
        hint (JLabel. (str "<html><div style='width:430px'>"
                           "Kontantinsats, månadsavgift och driftskostnad "
                           "behålls oförändrade."
                           "</div></html>"))
        actions (JPanel. (FlowLayout. FlowLayout/RIGHT 10 0))
        cancel-btn (rounded-button "Avbryt" (Color. 100 116 139)
                                   (Color. 71 85 105))
        apply-btn (rounded-button "Beräkna" blue blue-hover)
        apply-value! (fn []
                       (reset! value (str/trim (.getText cost-field)))
                       (.dispose dialog))]
    (.setDefaultCloseOperation dialog WindowConstants/DISPOSE_ON_CLOSE)
    (.setBackground root background)
    (.setBorder root (BorderFactory/createEmptyBorder 28 28 24 28))
    (.setOpaque heading false)
    (.setFont title (Font. ui-font Font/BOLD 24))
    (.setForeground title text-primary)
    (.setFont subtitle (Font. ui-font Font/PLAIN 14))
    (.setForeground subtitle text-secondary)
    (.add heading title)
    (.add heading subtitle)
    (.setFont input-label (Font. ui-font Font/BOLD 13))
    (.setForeground input-label text-primary)
    (style-field! cost-field)
    (.setPreferredSize cost-field (Dimension. 430 42))
    (.setFont hint (Font. ui-font Font/PLAIN 13))
    (.setForeground hint text-secondary)
    (.add input-panel input-label)
    (.add input-panel cost-field)
    (.add input-panel hint)
    (.setOpaque actions false)
    (.setPreferredSize cancel-btn (Dimension. 110 42))
    (.setPreferredSize apply-btn (Dimension. 120 42))
    (.add actions cancel-btn)
    (.add actions apply-btn)
    (.addActionListener
     cancel-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (.dispose dialog))))
    (.addActionListener
     apply-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (apply-value!))))
    (.add root heading BorderLayout/NORTH)
    (.add root input-panel BorderLayout/CENTER)
    (.add root actions BorderLayout/SOUTH)
    (.setContentPane dialog root)
    (.setDefaultButton (.getRootPane dialog) apply-btn)
    (.pack dialog)
    (.setResizable dialog false)
    (.setLocationRelativeTo dialog frame)
    (.requestFocusInWindow cost-field)
    (.setVisible dialog true)
    @value))

(defn choose-object! [frame labels]
  (let [selected-index (atom nil)
        dialog (JDialog. frame "Öppna objekt" true)
        root (JPanel. (BorderLayout. 0 18))
        heading (JPanel. (GridLayout. 0 1 0 4))
        title (JLabel. "Välj objekt")
        subtitle (JLabel. "Markera ett sparat objekt att öppna")
        object-list (JList. (into-array String labels))
        scroll-pane (JScrollPane. object-list)
        actions (JPanel. (FlowLayout. FlowLayout/RIGHT 10 0))
        cancel-btn (rounded-button "Avbryt" (Color. 100 116 139)
                                   (Color. 71 85 105))
        open-btn (rounded-button "Öppna objekt" blue blue-hover)
        open-selected! (fn []
                         (when (<= 0 (.getSelectedIndex object-list))
                           (reset! selected-index (.getSelectedIndex object-list))
                           (.dispose dialog)))]
    (.setDefaultCloseOperation dialog WindowConstants/DISPOSE_ON_CLOSE)
    (.setBackground root background)
    (.setBorder root (BorderFactory/createEmptyBorder 24 24 22 24))
    (.setOpaque heading false)
    (.setFont title (Font. ui-font Font/BOLD 24))
    (.setForeground title text-primary)
    (.setFont subtitle (Font. ui-font Font/PLAIN 14))
    (.setForeground subtitle text-secondary)
    (.add heading title)
    (.add heading subtitle)

    (.setSelectionMode object-list ListSelectionModel/SINGLE_SELECTION)
    (.setSelectedIndex object-list 0)
    (.setVisibleRowCount object-list 7)
    (.setFixedCellHeight object-list 42)
    (.setFont object-list (Font. ui-font Font/PLAIN 15))
    (.setForeground object-list text-primary)
    (.setBackground object-list surface)
    (.setSelectionBackground object-list (Color. 219 234 254))
    (.setSelectionForeground object-list (Color. 30 64 175))
    (.setBorder object-list (BorderFactory/createEmptyBorder 6 10 6 10))
    (.setBorder scroll-pane (rounded-border border-color))
    (.setPreferredSize scroll-pane (Dimension. 440 300))
    (.setBackground (.getViewport scroll-pane) surface)

    (.setOpaque actions false)
    (.setPreferredSize cancel-btn (Dimension. 110 42))
    (.setPreferredSize open-btn (Dimension. 150 42))
    (.add actions cancel-btn)
    (.add actions open-btn)
    (.addActionListener
     cancel-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (.dispose dialog))))
    (.addActionListener
     open-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (open-selected!))))
    (.addMouseListener
     object-list
     (proxy [java.awt.event.MouseAdapter] []
       (mouseClicked [event]
         (when (= 2 (.getClickCount event))
           (open-selected!)))))

    (.add root heading BorderLayout/NORTH)
    (.add root scroll-pane BorderLayout/CENTER)
    (.add root actions BorderLayout/SOUTH)
    (.setContentPane dialog root)
    (.getRootPane dialog)
    (.setDefaultButton (.getRootPane dialog) open-btn)
    (.pack dialog)
    (.setResizable dialog false)
    (.setLocationRelativeTo dialog frame)
    (.setVisible dialog true)
    @selected-index))

(defn create-ui []
  (doseq [key ["Label.font" "Menu.font" "MenuItem.font" "OptionPane.font"
               "CheckBox.font"]]
    (UIManager/put key (Font. ui-font Font/PLAIN 14)))
  (let [frame (JFrame. "Svensk bolånekalkylator")
        form (shadow-panel (GridLayout. 0 2 12 12))
        results (JTextArea.)
        calc-btn (rounded-button "Beräkna bolån" blue blue-hover)
        maximum-cost-btn (rounded-button "Ange maximal totalkostnad"
                 blue blue-hover)
        save-btn (rounded-button "Spara objekt" green green-hover)
        delete-btn (rounded-button "Radera" red red-hover)
        menu-bar (JMenuBar.)
        file-menu (JMenu. "File")
        new-object-item (JMenuItem. "Nytt objekt")
        objects-menu (JMenu. "Objekt")
        help-menu (JMenu. "Hjälp")
        about-item (JMenuItem. "Om Bolånekalkylator")
        rate-slider (JSlider. 0 200 0)
        rate-label (JLabel. "")
        name-f (JTextField.)
        address-f (JTextField.)
        comment-f (JTextField.)
        listing-url-f (JTextField.)
        listing-link (JEditorPane.)
        current-object-label (JLabel. "Öppet objekt: Nytt objekt"
                                      SwingConstants/CENTER)
        p-price-f (JTextField.)
        d-pay-f (JTextField.)
        fee-f (JTextField.)
        op-cost-f (JTextField.)
        income-f (JTextField.)
        t-low-f (JTextField.)
        t-high-f (JTextField.)
        extra-amort-cb (JCheckBox. "Räkna med extra amortering vid hög skuldkvot")]

    (.setDefaultCloseOperation frame JFrame/EXIT_ON_CLOSE)
    (.setSize frame 780 940)
    (.setMinimumSize frame (Dimension. 700 820))
    (.setLocationRelativeTo frame nil)
    (.add file-menu new-object-item)
    (.addSeparator file-menu)
    (.add file-menu objects-menu)
    (.add menu-bar file-menu)
    (.add help-menu about-item)
    (.add menu-bar help-menu)
    (.setJMenuBar frame menu-bar)
    (.setBackground menu-bar surface)
    (.setBorder menu-bar (BorderFactory/createMatteBorder 0 0 1 0 border-color))
    (.setEnabled delete-btn false)
    (doseq [field [name-f address-f comment-f listing-url-f p-price-f d-pay-f
             fee-f op-cost-f income-f t-low-f t-high-f]]
      (style-field! field))
    (.setFont extra-amort-cb (Font. ui-font Font/PLAIN 13))
    (.setForeground extra-amort-cb text-primary)
    (.setOpaque extra-amort-cb false)
    (.setOpaque rate-slider false)
    (.setForeground rate-slider blue)
    (.setFont rate-label (Font. ui-font Font/BOLD 13))
    (.setForeground rate-label blue)
    (.setEditable results false)
    (.setFont results (Font. "Consolas" Font/PLAIN 13))
    (.setBackground results surface)
    (.setForeground results text-primary)
    (.setMargin results (Insets. 16 16 16 16))
    (.setContentType listing-link "text/html")
    (.setEditable listing-link false)
    (.setOpaque listing-link false)
    (.setFont listing-link (Font. ui-font Font/PLAIN 13))
    (.setFont current-object-label (Font. ui-font Font/BOLD 13))
    (.setForeground current-object-label blue)
    (.setBorder current-object-label
                (BorderFactory/createCompoundBorder
                 (rounded-border (Color. 191 219 254))
                 (BorderFactory/createEmptyBorder 4 10 4 10)))
    (.addHyperlinkListener
     listing-link
     (reify javax.swing.event.HyperlinkListener
       (hyperlinkUpdate [_ event]
         (when (= HyperlinkEvent$EventType/ACTIVATED (.getEventType event))
           (try
             (when (Desktop/isDesktopSupported)
               (.browse (Desktop/getDesktop) (web-uri (.getDescription event))))
             (catch Exception _
               (JOptionPane/showMessageDialog
                frame "Kunde inte öppna länken." "Länkfel"
                JOptionPane/ERROR_MESSAGE)))))))

    (add-row! form "Objektnamn *:" name-f)
    (add-row! form "Adress:" address-f)
    (add-row! form "Kommentar:" comment-f)
    (add-row! form "Annonsens webbadress:" listing-url-f)
    (add-row! form "Sparad länk:" listing-link)
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

    (let [main-panel (scrollable-panel (BorderLayout. 0 16))
          header (JPanel. (BorderLayout.))
          heading (JPanel. (GridLayout. 0 1 0 2))
          title (JLabel. "Kalkylresultat")
          subtitle (JLabel. "Månadskostnad, ränta och amortering")
          results-scroll-pane (JScrollPane. results)
          results-panel (shadow-panel (BorderLayout.))
          btn-panel (JPanel.)
          app-scroll-pane (JScrollPane. main-panel)]
      (.setBackground main-panel background)
      (.setBorder main-panel (BorderFactory/createEmptyBorder 20 24 20 24))
      (.setPreferredSize main-panel (Dimension. 700 1120))
      (.setOpaque header false)
      (.setOpaque heading false)
      (.setFont title (Font. ui-font Font/BOLD 26))
      (.setForeground title text-primary)
      (.setFont subtitle (Font. ui-font Font/PLAIN 14))
      (.setForeground subtitle text-secondary)
      (.add heading title)
      (.add heading subtitle)
      (.add header heading BorderLayout/CENTER)
      (.add header current-object-label BorderLayout/EAST)
      (.setRows results 22)
      (.setColumns results 60)
      (.setBorder results-scroll-pane (rounded-border border-color))
      (.setBackground (.getViewport results-scroll-pane) surface)
      (.setPreferredSize results-panel (Dimension. 650 470))
      (.setMinimumSize results-panel (Dimension. 400 360))
      (.add results-panel results-scroll-pane BorderLayout/CENTER)
      (.setOpaque btn-panel false)
      (.setBorder btn-panel (BorderFactory/createEmptyBorder 4 0 0 0))
      (.setPreferredSize maximum-cost-btn (Dimension. 210 42))
      (.setPreferredSize calc-btn (Dimension. 145 42))
      (.setPreferredSize save-btn (Dimension. 140 42))
      (.setPreferredSize delete-btn (Dimension. 110 42))
      (.add main-panel form BorderLayout/NORTH)
      (.add results-panel header BorderLayout/NORTH)
      (.add main-panel results-panel BorderLayout/CENTER)
      (.add btn-panel maximum-cost-btn)
      (.add btn-panel calc-btn)
      (.add btn-panel save-btn)
      (.add btn-panel delete-btn)
      (.add main-panel btn-panel BorderLayout/SOUTH)
      (.setBorder app-scroll-pane nil)
      (.setHorizontalScrollBarPolicy
       app-scroll-pane JScrollPane/HORIZONTAL_SCROLLBAR_NEVER)
      (.setUnitIncrement (.getVerticalScrollBar app-scroll-pane) 20)
      (.setBackground (.getViewport app-scroll-pane) background)
      (.add frame app-scroll-pane))

    (.setVisible frame true)

    {:frame frame :results results :calc-btn calc-btn
      :maximum-cost-btn maximum-cost-btn :save-btn save-btn
      :delete-btn delete-btn
      :new-object-item new-object-item :objects-menu objects-menu
      :about-item about-item
    :name-f name-f :address-f address-f :comment-f comment-f
     :listing-url-f listing-url-f :listing-link listing-link
     :current-object-label current-object-label
     :rate-slider rate-slider :rate-label rate-label :p-price-f p-price-f
     :d-pay-f d-pay-f :fee-f fee-f :op-cost-f op-cost-f :income-f income-f
     :t-low-f t-low-f :t-high-f t-high-f :extra-amort-cb extra-amort-cb}))
