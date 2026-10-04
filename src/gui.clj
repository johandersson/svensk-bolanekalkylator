(ns gui
  (:require [calculator :as calc]
            [kalp :as kalp]
            [clojure.string :as str])
  (:import [java.awt BorderLayout Color Cursor Desktop Desktop$Action Dialog$ModalityType Dimension FlowLayout
            Font Graphics2D GridLayout Insets Point Rectangle RenderingHints
            Toolkit Window]
           [java.awt.datatransfer StringSelection]
           [java.awt.event InputEvent KeyEvent]
           [java.net URI]
           [java.text NumberFormat]
           [java.util Locale]
           [javax.swing AbstractAction BorderFactory JButton JCheckBox JComboBox JComponent
            JDialog JFrame JLabel JPanel JEditorPane JList JMenu JMenuBar
            JOptionPane JScrollPane JTextArea JTextField SwingConstants JSlider
            JMenuItem KeyStroke UIManager JWindow ListSelectionModel Scrollable
            Timer WindowConstants JTabbedPane]
           [javax.swing.border AbstractBorder]
           [javax.swing.event DocumentListener HyperlinkEvent$EventType]
           [javax.swing.text AbstractDocument DocumentFilter]))

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
(def comment-max-length 4000)

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

(defn limit-text-length! [field max-length]
  (let [document ^AbstractDocument (.getDocument field)]
    (.setDocumentFilter
     document
     (proxy [DocumentFilter] []
       (insertString [bypass offset text attributes]
         (let [available (- max-length (.getLength (.getDocument bypass)))
               accepted (subs (or text "") 0 (min available (count (or text ""))))]
           (when (seq accepted)
             (proxy-super insertString bypass offset accepted attributes))))
       (replace [bypass offset length text attributes]
         (let [available (+ (- max-length (.getLength (.getDocument bypass)))
                            length)
               accepted (subs (or text "") 0 (min available (count (or text ""))))]
           (proxy-super replace bypass offset length accepted attributes)))))
    field))

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

(defn bind-save-shortcut! [frame save!]
  (let [root-pane (.getRootPane frame)
        input-map (.getInputMap root-pane JComponent/WHEN_IN_FOCUSED_WINDOW)
        action-map (.getActionMap root-pane)
        action-key "save-object"]
    (.put input-map
          (KeyStroke/getKeyStroke KeyEvent/VK_S InputEvent/CTRL_DOWN_MASK)
          action-key)
    (.put action-map
          action-key
          (proxy [AbstractAction] []
            (actionPerformed [_]
              (save!))))))

(defn show-toast! [^Window frame message]
  (let [toast (JWindow. frame)
        panel (proxy [JPanel] [(BorderLayout.)]
                (paintComponent [graphics]
                  (let [graphics-2d (.create ^Graphics2D graphics)]
                    (enable-antialiasing! graphics-2d)
                    (.setColor graphics-2d (Color. 15 23 42 45))
                    (.fillRoundRect graphics-2d 4 5
                                    (- (.getWidth this) 8)
                                    (- (.getHeight this) 9) 16 16)
                    (.setColor graphics-2d
                               (Color. (.getRed green)
                                       (.getGreen green)
                                       (.getBlue green)
                                       210))
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
          x (+ (.x ^Point frame-location)
               (quot (- (.getWidth frame) (.getWidth toast)) 2))
          y (+ (.y ^Point frame-location)
               (quot (- (.getHeight frame) (.getHeight toast)) 2))]
      (.setLocation toast x y))
    (.addActionListener
     timer
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (.dispose toast))))
    (.setRepeats timer false)
    (.setVisible toast true)
    (.start timer)))

(defn- show-message! [^Window frame title-text message]
  (let [dialog (JDialog. frame title-text Dialog$ModalityType/APPLICATION_MODAL)
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

(defn show-error! [frame title-text message]
  (show-message! frame title-text message))

(defn show-info! [frame title-text message]
  (show-message! frame title-text message))

(def about-help-sections
  [["Bolånekalkyl"
    (str "Beräkna ränta, amortering, skattereduktion och total månadskostnad. "
         "Ränteslidern visar snabbt hur kostnaden påverkas av en annan ränta, "
         "och extra amortering kan räknas med vid hög skuldkvot.")]
   ["Maximal boendekostnad"
    (str "Ange hur mycket boendet högst får kosta per månad för att uppskatta "
         "en passande köpeskilling utifrån kontantinsats, avgift och drift.")]
   ["Kvar att leva på"
    (str "Gör en enkel hushållsbudget med inkomster, boende och övriga "
         "utgifter. Du ser vad som återstår både vid avtalad ränta och vid en "
         "högre kalkylränta. Det är särskilt användbart för att pröva "
         "marginalerna inför ett bostadsköp eller en räntehöjning, men "
         "ersätter inte bankens kreditprövning.")]
   ["Spara och hantera objekt"
    (str "Spara flera bostäder med namn, adress, kommentar och annonslänk. "
         "Öppna, uppdatera eller radera objekt via menyn; ändringar i ett "
         "öppnat objekt sparas automatiskt.")]
   ["Resultat och jämförelse"
    (str "Öppna den detaljerade kalkylen för en tydlig kostnadsöversikt och "
         "kopiera den till urklipp. Växla mellan sparade objekt för att "
         "jämföra olika bostäder och ekonomiska förutsättningar.")]])

(defn- help-section [title-text body-text]
  (let [panel (shadow-panel (BorderLayout. 0 8))
        title (JLabel. title-text)
        body (JLabel. (str "<html><div style='width:475px'>"
                           body-text
                           "</div></html>"))]
    (.setFont title (Font. ui-font Font/BOLD 16))
    (.setForeground title blue)
    (.setFont body (Font. ui-font Font/PLAIN 13))
    (.setForeground body text-secondary)
    (.add panel title BorderLayout/NORTH)
    (.add panel body BorderLayout/CENTER)
    panel))

(defn system-clipboard []
  (.getSystemClipboard (Toolkit/getDefaultToolkit)))

(defn copy-to-clipboard! [owner text]
  (let [copied? (try
                  (.setContents (system-clipboard)
                                (StringSelection. text) nil)
                  true
                  (catch IllegalStateException _
                    (show-error! owner "Kunde inte kopiera"
                                 "Urklipp är upptaget. Försök igen.")
                    false)
                  (catch SecurityException _
                    (show-error! owner "Kunde inte kopiera"
                                 "Programmet saknar behörighet att använda urklipp.")
                    false))]
    (when copied?
      (show-toast! owner "Sparad till urklipp!"))))

(defn- result-label [text size bold? color]
  (doto (JLabel. text)
    (.setFont (Font. ui-font (if bold? Font/BOLD Font/PLAIN) size))
    (.setForeground color)))

(defn- result-section [title-text rows]
  (let [panel (shadow-panel (BorderLayout. 0 12))
        fields (JPanel. (GridLayout. 0 2 16 10))]
    (.setOpaque fields false)
    (.add panel (result-label title-text 17 true text-primary)
          BorderLayout/NORTH)
    (doseq [[label value] rows]
      (.add fields (result-label label 14 false text-secondary))
      (.add fields (doto (result-label value 14 true text-primary)
                     (.setHorizontalAlignment SwingConstants/RIGHT))))
    (.add panel fields BorderLayout/CENTER)
    panel))

(defn create-calculation-dialog
  [^JFrame frame data {:keys [object-name address purchase-price down-payment
                              interest monthly-fee monthly-operating-cost]}]
  (let [dialog (JDialog. frame "Bolånekalkyl" true)
        root (JPanel. (BorderLayout. 0 16))
        heading (JPanel. (GridLayout. 0 1 0 6))
        body (scrollable-panel (GridLayout. 0 1 0 12))
        cards (JPanel. (BorderLayout. 0 12))
        sections (JPanel. (GridLayout. 0 1 0 12))
        summary (shadow-panel (GridLayout. 0 1 0 6))
        scroll-pane (JScrollPane. body)
        footer (JPanel. (BorderLayout. 12 0))
        actions (JPanel. (FlowLayout. FlowLayout/RIGHT 10 0))
        copy-btn (rounded-button "Kopiera till urklipp" green green-hover)
        close-btn (rounded-button "Stäng" blue blue-hover)
        currency (doto (NumberFormat/getNumberInstance
                        (Locale/forLanguageTag "sv-SE"))
                   (.setMinimumFractionDigits 2)
                   (.setMaximumFractionDigits 2))
        kronor (fn [value] (str (.format currency (double value)) " kr"))
        percentage (fn [value] (str (calc/percent value) " %"))
        context (str/join " · " (remove str/blank? [object-name address]))
        summary-title "Total månadskostnad efter skattereduktion"
        summary-value (str (kronor (:monthly-payment-after-tax data)) "/mån")
        before-tax (str "Före skattereduktion: "
                        (kronor (:monthly-payment-before-tax data)) "/mån")
        section-data
        [["Bostad och lån"
          [["Köpeskilling" (kronor purchase-price)]
           ["Kontantinsats" (kronor down-payment)]
           ["Lånebelopp" (kronor (:loan data))]
           ["Belåningsgrad" (percentage (* 100.0 (:loan-to-value data)))]
           ["Räntesats" (percentage interest)]
           ["Antal låntagare" (str (:borrower-count data))]]]
         ["Månadskostnad"
          [["Ränta före skattereduktion" (kronor (:monthly-interest-cost data))]
           ["Beräknad skattereduktion" (kronor (:monthly-tax-reduction data))]
           ["Ränta efter skattereduktion" (kronor (:monthly-interest-after-tax data))]
           ["Amortering" (kronor (:monthly-amortization data))]
           ["Månadsavgift" (kronor monthly-fee)]
           ["Driftskostnad" (kronor monthly-operating-cost)]]]
         ["Amortering"
          [["Grundläggande amortering"
            (str (percentage (:basic-amortization-percent data)) " per år")]
           ["Extra amortering"
            (str (percentage (:extra-amortization-percent data)) " per år")]
           ["Total amortering"
            (str (percentage (:total-amortization-percent data)) " per år")]]]]
        note (str "Genomsnitt per månad under första året.\n"
                  "Prognosen antar ett bolån med bostaden som säkerhet,\n"
                  (:borrower-count data)
                  " låntagare med jämn räntefördelning och tillräcklig skatt.")
        clipboard-text
        (str/join "\n\n"
                  (concat
                   [(str "Din bolånekalkyl"
                         (when-not (str/blank? context) (str "\n" context)))
                    (str summary-title "\n" summary-value "\n" before-tax)]
                   (map (fn [[title rows]]
                          (str title "\n"
                               (str/join "\n" (map (fn [[label value]]
                                                     (str label ": " value))
                                                   rows))))
                        section-data)
                   [note]))
        close-action (proxy [AbstractAction] []
                       (actionPerformed [_] (.dispose dialog)))]
    (.setDefaultCloseOperation dialog WindowConstants/DISPOSE_ON_CLOSE)
    (.setBackground root background)
    (.setBorder root (BorderFactory/createEmptyBorder 24 24 20 24))
    (doseq [panel [heading body cards sections footer actions]]
      (.setOpaque panel false))
    (.add heading (result-label "Din bolånekalkyl" 26 true text-primary))
    (when-not (str/blank? context)
      (.add heading
            (result-label (str "<html>" (escape-html context) "</html>")
                          14 false text-secondary)))
    (.add summary (result-label summary-title 15 false text-secondary))
    (.add summary (result-label summary-value 32 true blue))
    (.add summary (result-label before-tax 14 false text-secondary))
    (doseq [[title rows] section-data]
      (.add sections (result-section title rows)))
    (.add cards summary BorderLayout/NORTH)
    (.add cards sections BorderLayout/CENTER)
    (.add body cards)
    (.setBorder scroll-pane nil)
    (.setHorizontalScrollBarPolicy scroll-pane JScrollPane/HORIZONTAL_SCROLLBAR_NEVER)
    (.setBackground (.getViewport scroll-pane) background)
    (.setUnitIncrement (.getVerticalScrollBar scroll-pane) 24)
    (.add footer
          (result-label
           (str "<html>" (str/replace (escape-html note) "\n" "<br>") "</html>")
           12 false text-secondary)
          BorderLayout/CENTER)
    (.setPreferredSize copy-btn (Dimension. 180 42))
    (.setPreferredSize close-btn (Dimension. 100 42))
    (.add actions copy-btn)
    (.add actions close-btn)
    (.add footer actions BorderLayout/SOUTH)
    (.addActionListener
     copy-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (copy-to-clipboard! dialog clipboard-text))))
    (.addActionListener close-btn close-action)
    (.setDefaultButton (.getRootPane dialog) close-btn)
    (.put (.getInputMap (.getRootPane dialog) JComponent/WHEN_IN_FOCUSED_WINDOW)
          (KeyStroke/getKeyStroke KeyEvent/VK_ESCAPE 0) "close-results")
    (.put (.getActionMap (.getRootPane dialog)) "close-results" close-action)
    (.add root heading BorderLayout/NORTH)
    (.add root scroll-pane BorderLayout/CENTER)
    (.add root footer BorderLayout/SOUTH)
    (.setContentPane dialog root)
    (.setSize dialog
              (int (min 800 (* 0.85 (.getWidth frame))))
              (int (min 800 (* 0.85 (.getHeight frame)))))
    (.setResizable dialog false)
    (.setLocationRelativeTo dialog frame)
    dialog))

(defn show-calculation! [frame data details]
  (.setVisible (create-calculation-dialog frame data details) true))

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
        root (JPanel. (BorderLayout. 0 16))
        heading (JPanel. (GridLayout. 0 1 0 4))
        title (JLabel. "Bolånekalkylator")
        subtitle (JLabel. "Ett enklare beslutsunderlag för ditt nästa boende")
        content (scrollable-panel (GridLayout. 0 1 0 12))
        content-wrapper (JPanel. (BorderLayout.))
        content-scroll (JScrollPane. content)
        license-text (JLabel. (str "<html><div style='width:475px'>"
                                   "<b>Fri programvara</b> · GNU General Public "
                                   "License version 3 (GPLv3)<br>"
                                   "Copyright © 2026 Johan Andersson"
                                   "</div></html>"))
        actions (JPanel. (FlowLayout. FlowLayout/RIGHT 0 0))
        close-btn (rounded-button "Stäng" blue blue-hover)]
    (.setDefaultCloseOperation dialog WindowConstants/DISPOSE_ON_CLOSE)
    (.setBackground root background)
    (.setBorder root (BorderFactory/createEmptyBorder 24 24 20 24))
    (.setOpaque heading false)
    (.setFont title (Font. ui-font Font/BOLD 28))
    (.setForeground title text-primary)
    (.setFont subtitle (Font. ui-font Font/PLAIN 14))
    (.setForeground subtitle text-secondary)
    (.add heading title)
    (.add heading subtitle)
    (doseq [[section-title section-body] about-help-sections]
      (.add content (help-section section-title section-body)))
    (.setFont license-text (Font. ui-font Font/PLAIN 13))
    (.setForeground license-text text-secondary)
    (.add content license-text)
    (.setOpaque content false)
    (.setOpaque content-wrapper false)
    (.setBorder content-scroll nil)
    (.setHorizontalScrollBarPolicy
     content-scroll JScrollPane/HORIZONTAL_SCROLLBAR_NEVER)
    (.setUnitIncrement (.getVerticalScrollBar content-scroll) 18)
    (.setBackground (.getViewport content-scroll) background)
    (.add content-wrapper content-scroll BorderLayout/CENTER)
    (.setOpaque actions false)
    (.setPreferredSize close-btn (Dimension. 110 42))
    (.add actions close-btn)
    (.addActionListener
     close-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (.dispose dialog))))
    (.add root heading BorderLayout/NORTH)
    (.add root content-wrapper BorderLayout/CENTER)
    (.add root actions BorderLayout/SOUTH)
    (.setContentPane dialog root)
    (.setDefaultButton (.getRootPane dialog) close-btn)
    (.setSize dialog 610 720)
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
        create-btn (rounded-button "Skapa nytt objekt" green green-hover)
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
    (.setPreferredSize create-btn (Dimension. 170 42))
    (.setPreferredSize cancel-btn (Dimension. 110 42))
    (.setPreferredSize open-btn (Dimension. 150 42))
    (.add actions create-btn)
    (.add actions cancel-btn)
    (.add actions open-btn)
    (.addActionListener
     create-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (reset! selected-index :new-object)
         (.dispose dialog))))
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

(declare clear-kalp-result!)

(defn create-kalp-tab []
  (let [panel (scrollable-panel (BorderLayout. 0 16))
        form (shadow-panel (GridLayout. 0 2 12 12))
        fields (into {} (map (fn [[key _ default]]
                               [key (doto (JTextField. default) (style-field!))])
                             kalp/input-fields))
        tax-cb (JCheckBox. "Räkna med skattereduktion (kräver tillräcklig skatt)")
        calculate-btn (rounded-button "Beräkna Kvar att leva på" blue blue-hover)
        reference-btn (rounded-button "Konsumentverkets kostnader" blue blue-hover)
        result (JPanel. (BorderLayout.))
        source-label (result-label "Bostadsuppgifter hämtas från fliken Bolån."
                                   13 true blue)
        header (JPanel. (GridLayout. 0 1 0 8))
        scroll (JScrollPane. panel)]
    (.setBackground panel background)
    (.setBorder panel (BorderFactory/createEmptyBorder 20 24 20 24))
    (.setOpaque header false)
    (.add header (result-label "Kvar att leva på" 26 true text-primary))
    (.add header source-label)
    (.add header
          (result-label
           (str "<html>Hushållsbudget och stresstest, inte ett lånelöfte.<br>"
                "Alla kostnader anges per månad. Nettoinkomst är efter skatt.<br>"
                "Ange levnadskostnader för alla vuxna och barn, anpassat efter ålder.<br>"
                "Ta med mat, kläder, hygien, fritid och gemensamma hushållskostnader.<br>"
                "Konsumentverkets belopp inkluderar el, vatten och hemförsäkring.<br>"
                "Räkna dem inte igen om de ingår i driftskostnad eller avgift.<br>"
                "Lägg till transport, barnomsorg, vård och andra lån separat.<br>"
                "Kontrollera amorteringen mot bankens avtal. Den kan ändras här.<br>"
                "7 % är ett ändringsbart exempel, inte en gemensam bankstandard.<br>"
                "Skattereduktion är ett årsgenomsnitt, inte automatiskt en månadsutbetalning.</html>")
           13 false text-secondary))
    (doseq [[key label _] kalp/input-fields]
      (add-row! form label (get fields key)))
    (.setOpaque tax-cb false)
    (.setForeground tax-cb text-primary)
    (.add form tax-cb)
    (.add form (JLabel. "Utan skattereduktion som standard"))
    (.setPreferredSize calculate-btn (Dimension. 260 42))
    (.add form calculate-btn)
    (.add form (JLabel. "Sparas inte i objektet"))
    (.setPreferredSize reference-btn (Dimension. 260 42))
    (.add form reference-btn)
    (.add form (JLabel. "Referensvärden 2026, inte bankens schablon"))
    (.addActionListener
     reference-btn
     (reify java.awt.event.ActionListener
       (actionPerformed [_ _]
         (try
           (when-not (and (Desktop/isDesktopSupported)
                          (.isSupported (Desktop/getDesktop) Desktop$Action/BROWSE))
             (throw (UnsupportedOperationException. "Webbläsare saknas")))
           (.browse (Desktop/getDesktop)
                    (URI. "https://www.konsumentverket.se/ekonomi/vilka-kostnader-har-ett-hushall/"))
           (catch java.io.IOException _
             (show-error! (javax.swing.SwingUtilities/getWindowAncestor panel)
                          "Kunde inte öppna källan" "Kontrollera din webbläsare."))
           (catch UnsupportedOperationException _
             (show-error! (javax.swing.SwingUtilities/getWindowAncestor panel)
                          "Kunde inte öppna källan" "Webbläsare stöds inte på denna dator."))))))
    (.setOpaque result false)
    (.add panel header BorderLayout/NORTH)
    (.add panel form BorderLayout/CENTER)
    (.add panel result BorderLayout/SOUTH)
    (.setBorder scroll nil)
    (.setHorizontalScrollBarPolicy scroll JScrollPane/HORIZONTAL_SCROLLBAR_NEVER)
    (.setUnitIncrement (.getVerticalScrollBar scroll) 24)
    (let [clear-result (fn [] (clear-kalp-result! {:result result}))]
      (doseq [field (vals fields)]
        (.addDocumentListener
         (.getDocument field)
         (reify DocumentListener
           (insertUpdate [_ _] (clear-result))
           (removeUpdate [_ _] (clear-result))
           (changedUpdate [_ _] (clear-result)))))
      (.addActionListener
       tax-cb
       (reify java.awt.event.ActionListener
         (actionPerformed [_ _] (clear-result)))))
    {:panel scroll :fields fields :tax-cb tax-cb :calc-btn calculate-btn
     :source-label source-label
     :result result :source-state (atom nil)}))

(defn show-kalp-source! [ui object-name]
  (.setText (:source-label ui)
            (str "<html>Bostadsuppgifter från: "
                 (escape-html (if (str/blank? object-name) "Nytt objekt" object-name))
                 "</html>")))

(defn show-kalp-result! [ui data]
  (let [result (:result ui)
        {:keys [actual stress income expenses inputs]} data
        amount #(str (calc/money %) " kr/mån")
        rows [["Nettoinkomst inklusive bidrag" (amount income)]
              ["Övriga utgifter inklusive buffert" (amount expenses)]
              ["Boendekostnad vid avtalad ränta" (amount (:housing actual))]
              ["Ränta vid avtalad ränta" (amount (:interest actual))]
              ["Skattereduktion vid avtalad ränta" (amount (:tax-reduction actual))]
              ["Amortering" (amount (:amortization inputs))]
              ["Månadsavgift" (amount (:fee inputs))]
              ["Driftskostnad" (amount (:operating-cost inputs))]
              ["Kvar vid avtalad ränta" (amount (:remaining actual))]
              ["Boendekostnad vid kalkylränta" (amount (:housing stress))]
              ["Kvar vid kalkylränta" (amount (:remaining stress))]
              ["Ränta vid kalkylränta" (amount (:interest stress))]
              ["Skattereduktion vid kalkylränta" (amount (:tax-reduction stress))]]
        card (result-section "Sammanfattning · Kvar att leva på" rows)
        status (result-label
                (str "<html>"
                     (cond
                       (neg? (:remaining stress)) "Underskott vid kalkylräntan."
                       (zero? (:remaining stress)) "Budgeten är i balans utan marginal vid kalkylräntan."
                       :else "Budgeten har ett överskott vid kalkylräntan.")
                     "<br>Kalkylränta: " (calc/percent (:stress-interest inputs))
                     " %. Oförändrat lånebelopp och amortering."
                     "<br>Detta är inte bankens kreditprövning. Kontrollera alla kostnader.</html>")
                13 true (if (neg? (:remaining stress)) red green))]
    (.add card status BorderLayout/SOUTH)
    (.removeAll result)
    (.add result card BorderLayout/CENTER)
    (.revalidate result)
    (.repaint result)))

(defn clear-kalp-result! [ui]
  (.removeAll (:result ui))
  (.revalidate (:result ui))
  (.repaint (:result ui)))

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
        comment-f (limit-text-length! (JTextArea. 2 30) comment-max-length)
        comment-scroll-pane (JScrollPane. comment-f)
        listing-url-f (JTextField.)
        listing-link (JEditorPane.)
        current-object-label (JLabel. "Öppet objekt: Nytt objekt"
                                      SwingConstants/CENTER)
        p-price-f (JTextField.)
        d-pay-f (JTextField.)
        fee-f (JTextField.)
        op-cost-f (JTextField.)
        income-f (JTextField.)
        t-low-f (JTextField. "30")
        t-high-f (JTextField. "21")
        borrower-count-cb (JComboBox. (into-array Integer
                                                  [(Integer/valueOf 1)
                                                   (Integer/valueOf 2)]))
        extra-amort-cb (JCheckBox. "Räkna med extra amortering vid hög skuldkvot")
        tabs (JTabbedPane.)
        kalp-ui (create-kalp-tab)]

    (.setDefaultCloseOperation frame JFrame/EXIT_ON_CLOSE)
    (.setSize frame 940 940)
    (.setMinimumSize frame (Dimension. 820 820))
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
    (.setLineWrap comment-f true)
    (.setWrapStyleWord comment-f true)
    (.setMargin comment-f (Insets. 7 10 7 10))
    (.setBorder comment-f nil)
    (.setBorder comment-scroll-pane (rounded-border border-color))
    (.setHorizontalScrollBarPolicy
     comment-scroll-pane JScrollPane/HORIZONTAL_SCROLLBAR_NEVER)
    (.setVerticalScrollBarPolicy
     comment-scroll-pane JScrollPane/VERTICAL_SCROLLBAR_AS_NEEDED)
    (.setBackground (.getViewport comment-scroll-pane) surface)
    (.setEditable t-low-f false)
    (.setEditable t-high-f false)
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
    (add-row! form "Kommentar (max 4 000 tecken):" comment-scroll-pane)
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
    (add-row! form "Antal låntagare:" borrower-count-cb)
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
      (.setPreferredSize main-panel (Dimension. 860 1120))
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
      (.setColumns results 72)
      (.setBorder results-scroll-pane (rounded-border border-color))
      (.setBackground (.getViewport results-scroll-pane) surface)
      (.setPreferredSize results-panel (Dimension. 810 470))
      (.setMinimumSize results-panel (Dimension. 730 360))
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
      (.setFont tabs (Font. ui-font Font/BOLD 14))
      (.addTab tabs "Bolån" app-scroll-pane)
      (.addTab tabs "Kvar att leva på" (:panel kalp-ui))
      (.add frame tabs))

    (.setVisible frame true)

    {:frame frame :results results :calc-btn calc-btn :tabs tabs :kalp kalp-ui
     :maximum-cost-btn maximum-cost-btn :save-btn save-btn
     :delete-btn delete-btn
     :new-object-item new-object-item :objects-menu objects-menu
     :about-item about-item
     :name-f name-f :address-f address-f :comment-f comment-f
     :listing-url-f listing-url-f :listing-link listing-link
     :current-object-label current-object-label
     :rate-slider rate-slider :rate-label rate-label :p-price-f p-price-f
     :d-pay-f d-pay-f :fee-f fee-f :op-cost-f op-cost-f :income-f income-f
     :t-low-f t-low-f :t-high-f t-high-f :borrower-count-cb borrower-count-cb
     :extra-amort-cb extra-amort-cb}))
