(ns progress-bar-v19
  (:require
   [reagent.core :as r]
   [roam.datascript.reactive :as dr]
   [roam.block :as block]
   [blueprintjs.core :as bp-core]
   [clojure.string :as str]))

;; Circle configuration
(def base-size 11)
(def radius (* 0.75 base-size))
(def viewbox-size (* 2 base-size))
(def center-point base-size)

;; Hill diagram configuration
(def hill-w 200)
(def hill-h 90)
(def hill-baseline 73)
(def hill-amplitude 50)
(def hill-center (/ hill-w 2))
(def hill-sigma 40)

;; Multi-task color palette
(def task-colors ["#0d8050" "#1d7aad" "#bf7326" "#a82a2a" "#5c4485" "#00827c"])

(defn get-task-color [idx]
  (nth task-colors (mod idx (count task-colors))))

(defn hill-gaussian-y [x]
  (let [dx (- x hill-center)]
    (- hill-baseline (* hill-amplitude (js/Math.exp (- (/ (* dx dx) (* 2 hill-sigma hill-sigma))))))))

(defn make-hill-path []
  (let [n 80]
    (str/join " "
              (map-indexed (fn [i _]
                             (let [x (* (/ i n) hill-w)
                                   y (hill-gaussian-y x)]
                               (str (if (zero? i) "M" "L") " " x "," y)))
                           (range (inc n))))))

(def hill-dot-r 4)

(defn hill-dot-pos [percentage]
  (let [raw-x (* (/ percentage 100) hill-w)
        x (max hill-dot-r (min (- hill-w hill-dot-r) raw-x))
        y (hill-gaussian-y x)]
    [x y]))

(defn truthy? [value]
  (or (= value true) (= value "true")))

(defn calculate-coordinates [percentage]
  (let [angle (* (/ percentage 100) (* 2 js/Math.PI))
        x (+ center-point (* radius (js/Math.sin angle)))
        y (- center-point (* radius (js/Math.cos angle)))]
    [x y]))

(defn get-arc-path [percentage]
  (let [[x y] (calculate-coordinates percentage)
        large-arc (if (>= percentage 50) 1 0)
        start-y (- center-point radius)]
    (if (>= percentage 100)
      (str "M " center-point "," start-y " A " radius "," radius " 0 1,1 "
           center-point "," (+ start-y (* 2 radius)) " A " radius "," radius " 0 1,1 "
           center-point "," start-y)
      (str "M " center-point "," start-y " A " radius "," radius " 0 " large-arc ",1 "
           x "," y " L " center-point "," center-point " Z"))))

(defn is-block-ref?
  "Returns true if block-string is a pure block reference ((uid))."
  [block-string]
  (when block-string
    (boolean (re-matches #"^\s*\(\([a-zA-Z0-9_-]+\)\)\s*$" block-string))))

(defn count-status-from-ref [ref]
  (let [title (:node/title ref)]
    (cond
      (= title "TODO") [1 0]
      (= title "DONE") [0 1]
      :else (reduce (fn [[todo done] nested-ref]
                      (let [nested-title (:node/title nested-ref)]
                        (cond
                          (= nested-title "TODO") [(inc todo) done]
                          (= nested-title "DONE") [todo (inc done)]
                          :else [todo done])))
                    [0 0]
                    (:block/refs ref)))))

(defn count-status-from-block [block]
  (reduce (fn [[todo done] ref]
            (let [[todo+ done+] (count-status-from-ref ref)]
              [(+ todo todo+) (+ done done+)]))
          [0 0]
          (:block/refs block)))

(defn progress-counts [block-uid exclude-blockrefs?]
  (let [root @(dr/pull '[:block/string
                         {:block/refs [:node/title {:block/refs [:node/title]}]}
                         {:block/children ...}]
                       [:block/uid block-uid])]
    (loop [stack (if root [root] [])
           todo 0
           done 0]
      (if (empty? stack)
        {:todo todo :done done}
        (let [node (peek stack)
              next-stack (into (pop stack) (:block/children node))
              include-block? (or (not exclude-blockrefs?)
                                 (not (is-block-ref? (:block/string node))))
              [todo+ done+] (if include-block?
                              (count-status-from-block node)
                              [0 0])]
          (recur next-stack (+ todo todo+) (+ done done+)))))))

(defn progress-counts-multi [block-uid exclude-blockrefs?]
  (let [root @(dr/pull '[:block/string
                         {:block/refs [:node/title {:block/refs [:node/title]}]}
                         {:block/children ...}]
                       [:block/uid block-uid])]
    (->> (:block/children root)
         (map (fn [child]
                (let [counts (loop [stack [child]
                                    todo 0
                                    done 0]
                               (if (empty? stack)
                                 {:todo todo :done done}
                                 (let [node (peek stack)
                                       next-stack (into (pop stack) (:block/children node))
                                       include? (or (not exclude-blockrefs?)
                                                    (not (is-block-ref? (:block/string node))))
                                       [t+ d+] (if include?
                                                  (count-status-from-block node)
                                                  [0 0])]
                                   (recur next-stack (+ todo t+) (+ done d+)))))]
                  (assoc counts :name (:block/string child)))))
         (filter #(> (+ (:todo %) (:done %)) 0))
         vec)))

(defn strip-roam-markup [s]
  (when s
    (-> s
        (str/replace #"\{\{(?:\[\[)?(?:TODO|DONE)(?:\]\])?\}\}" "")
        (str/replace #"#\[\[([^\]]+)\]\]" "$1")
        (str/replace #"\[\[([^\]]+)\]\]" "$1")
        (str/replace #"\*\*([^*]*)\*\*" "$1")
        (str/replace #"__([^_]*)__" "$1")
        (str/replace #"\^\^([^^]*)\^\^" "$1")
        (str/replace #"~~([^~]*)~~" "$1")
        (str/replace #"\(\([^)]+\)\)" "")
        (str/replace #"#\S+" "")
        str/trim)))

(defn truncate-str [s max-len]
  (if (and s (> (count s) max-len))
    (str (subs s 0 max-len) "…")
    (or s "")))

(defn spread-group [group step]
  (let [n (count group)]
    (map-indexed (fn [i item]
                   (update item :y + (* step (- i (/ (dec n) 2.0)))))
                 group)))

(defn apply-jitter [items threshold step]
  (loop [remaining items
         result []
         group []]
    (if (empty? remaining)
      (into result (spread-group group step))
      (let [current (first remaining)
            last-g (last group)]
        (if (or (empty? group)
                (< (js/Math.abs (- (:x current) (:x last-g))) threshold))
          (recur (rest remaining) result (conj group current))
          (recur (rest remaining) (into result (spread-group group step)) [current]))))))

(def bp-button (r/adapt-react-class bp-core/Button))
(def bp-popover (r/adapt-react-class bp-core/Popover))
(def bp-select (r/adapt-react-class bp-core/HTMLSelect))
(def bp-input (r/adapt-react-class bp-core/InputGroup))
(def bp-switch (r/adapt-react-class bp-core/Switch))

(defn get-component-code-uid [block-string]
  (when block-string
    (let [pattern #"\{\{(?:\[\[)?roam/render(?:\]\])?: *\(\(([^)]+)\)\).*\}\}"
          matches (re-find pattern block-string)]
      (second matches))))

;; Render-string positional args: style, status-text, show-percent, exclude-blockrefs, show-hill-labels, multi-task
(defn format-render-string [current-string style status-text show-percent exclude-blockrefs show-hill-labels multi-task]
  (if-let [code-block-uid (get-component-code-uid current-string)]
    (let [pattern #"\{\{(?:\[\[)?roam/render(?:\]\])?: *\(\([^)]+\)\).*?\}\}"
          replacement (str "{{roam/render: ((" code-block-uid ")) \""
                           style "\" \""
                           status-text "\" \""
                           show-percent "\" \""
                           exclude-blockrefs "\" \""
                           show-hill-labels "\" \""
                           multi-task "\"}}")]
      (str/replace current-string pattern replacement))
    current-string))

(defn update-block-string [block-uid style status-text show-percent exclude-blockrefs show-hill-labels multi-task]
  (let [current-block @(dr/pull '[:block/string] [:block/uid block-uid])
        current-string (:block/string current-block)
        new-string (format-render-string current-string style status-text show-percent exclude-blockrefs show-hill-labels multi-task)]
    (when (not= current-string new-string)
      (block/update
       {:block {:uid block-uid
                :string new-string}}))))

(defn settings-menu [block-uid current-style current-status-text current-show-percent current-exclude-blockrefs current-show-hill-labels current-multi-task on-close]
  (let [*style (r/atom current-style)
        *status-text (r/atom current-status-text)
        *show-percent (r/atom current-show-percent)
        *exclude-blockrefs (r/atom current-exclude-blockrefs)
        *show-hill-labels (r/atom current-show-hill-labels)
        *multi-task (r/atom current-multi-task)]
    (fn []
      [:div.bp3-card.dont-focus-block {:style {:padding "10px" :min-width "250px"}
                                       :on-click (fn [e] (.stopPropagation e))}
       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Display Style"]
        [bp-select {:value @*style
                    :class "dont-focus-block"
                    :onChange #(reset! *style (.. % -target -value))
                    :options [{:value "horizontal" :label "Horizontal Bar"}
                              {:value "radial" :label "Radial/Circle"}
                              {:value "hill" :label "Hill Diagram"}]}]]

       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Status Text"]
        [bp-input {:value @*status-text
                   :class "dont-focus-block"
                   :autoFocus true
                   :onChange #(reset! *status-text (.. % -target -value))
                   :placeholder "Done"}]]

       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Show Percentage"]
        [bp-switch {:checked (truthy? @*show-percent)
                    :class "dont-focus-block"
                    :onChange #(reset! *show-percent (str (.. % -target -checked)))}]]

       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Exclude Reference-Only Blocks"]
        [bp-switch {:checked (truthy? @*exclude-blockrefs)
                    :class "dont-focus-block"
                    :onChange #(reset! *exclude-blockrefs (str (.. % -target -checked)))}]]

       (when (= @*style "hill")
         [:<>
          [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
           [:label.bp3-label.dont-focus-block "Show Hill Labels"]
           [bp-switch {:checked (truthy? @*show-hill-labels)
                       :class "dont-focus-block"
                       :onChange #(reset! *show-hill-labels (str (.. % -target -checked)))}]]
          [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
           [:label.bp3-label.dont-focus-block "Multi-task Mode"]
           [bp-switch {:checked (truthy? @*multi-task)
                       :class "dont-focus-block"
                       :onChange #(reset! *multi-task (str (.. % -target -checked)))}]]])

       [:div.button-group.dont-focus-block {:style {:display "flex" :justify-content "flex-end" :gap "8px"}}
        [bp-button {:onClick on-close
                    :class "dont-focus-block"
                    :minimal true}
         "Cancel"]
        [bp-button {:intent "primary"
                    :class "dont-focus-block"
                    :onClick (fn [e]
                               (.stopPropagation e)
                               (.preventDefault e)
                               (update-block-string block-uid @*style @*status-text @*show-percent @*exclude-blockrefs @*show-hill-labels @*multi-task)
                               (on-close))}
         "Apply"]]])))

(defn horizontal-progress-bar [done total status-text show-percent on-settings-click]
  (r/with-let [*hovered? (r/atom false)]
    [:span {:style {:display "inline-flex"
                    :align-items "center"
                    :gap "8px"
                    :vertical-align "middle"}
            :on-mouse-enter #(reset! *hovered? true)
            :on-mouse-leave #(reset! *hovered? false)}
     [:span {:style {:display "inline-block"
                     :width "150px"}}
      [:progress {:id "file"
                  :name "percent-done"
                  :value done
                  :max total
                  :style {:width "100%"}}]]
     [:span {:style {:white-space "nowrap"
                     :display "inline-flex"
                     :align-items "center"}}
      [:span (str done "/" total " " status-text
                  (when (truthy? show-percent)
                    (str " - " (if (zero? total) 0 (int (* (/ done total) 100))) "%")))]
      [:span {:style {:max-width (if @*hovered? "30px" "0px")
                      :overflow "hidden"
                      :margin-left (if @*hovered? "8px" "0")
                      :transition "all 0.3s ease-in-out"
                      :opacity (if @*hovered? "1" "0")
                      :display "inline-block"}}
       [bp-button
        {:icon "cog"
         :class "dont-focus-block"
         :minimal true
         :small true
         :onClick (fn [e]
                    (.stopPropagation e)
                    (on-settings-click))}]]]]))

(defn circle-progress-bar [done total status-text show-percent on-settings-click]
  (r/with-let [*hovered? (r/atom false)]
    (let [percentage (if (zero? total)
                       0
                       (* (/ done total) 100))]
      [:span.inline-flex.items-center.gap-2
       {:style {:vertical-align "middle"}
        :on-mouse-enter #(reset! *hovered? true)
        :on-mouse-leave #(reset! *hovered? false)}
       [:span.relative.inline-flex.items-center.justify-center.progress-circle
        [:svg
         {:width viewbox-size
          :height viewbox-size
          :viewBox (str "0 0 " viewbox-size " " viewbox-size)}
         [:circle
          {:cx center-point
           :cy center-point
           :r radius
           :fill "var(--circle-bg, #eee)"
           :stroke "var(--circle-bg, #eee)"
           :stroke-width "5px"}]
         [:path
          {:d (get-arc-path percentage)
           :fill "var(--circle-fill, #0d8050)"
           :stroke "none"}]]]
       [:span {:style {:display "inline-flex"
                       :align-items "center"}}
        [:span.text-base
         (str done "/" total " " status-text
              (when (truthy? show-percent)
                (str " - " (int percentage) "%")))]
        [:span {:style {:max-width (if @*hovered? "30px" "0px")
                        :overflow "hidden"
                        :margin-left (if @*hovered? "8px" "0")
                        :transition "all 0.3s ease-in-out"
                        :opacity (if @*hovered? "1" "0")
                        :display "inline-block"}}
         [bp-button
          {:icon "cog"
           :class "dont-focus-block"
           :minimal true
           :small true
           :onClick (fn [e]
                      (.stopPropagation e)
                      (on-settings-click))}]]]])))

(defn hill-progress-bar [done total status-text show-percent show-hill-labels multi-tasks on-settings-click]
  (r/with-let [*hovered? (r/atom false)
               *hovered-idx (r/atom nil)]
    (let [percentage (if (zero? total) 0 (* (/ done total) 100))
          [dot-x dot-y] (hill-dot-pos percentage)
          label-y (+ hill-baseline 12)
          peak-y (hill-gaussian-y hill-center)
          pole-top (+ peak-y 15)
          pole-bot hill-baseline
          done? (>= percentage 100)
          accent "var(--circle-fill, #0d8050)"
          positioned-tasks (when (seq multi-tasks)
                             (let [with-pos (map-indexed
                                             (fn [i t]
                                               (let [total-t (+ (:todo t) (:done t))
                                                     pct (if (zero? total-t) 0 (* (/ (:done t) total-t) 100))
                                                     [x y] (hill-dot-pos pct)]
                                                 (assoc t :x x :y y :color (get-task-color i) :idx i)))
                                             multi-tasks)
                                   sorted (sort-by :x with-pos)]
                               (apply-jitter sorted 8 5)))
          task-by-idx (when positioned-tasks
                        (into {} (map (juxt :idx identity) positioned-tasks)))]
      [:span {:style {:display "inline-flex"
                      :align-items "center"
                      :gap "8px"
                      :vertical-align "middle"}
              :on-mouse-enter #(reset! *hovered? true)
              :on-mouse-leave #(reset! *hovered? false)}
       [:div.hill-diagram {:style {:resize "horizontal"
                                   :overflow "hidden"
                                   :display "inline-block"
                                   :min-width "80px"
                                   :width (str hill-w "px")}}
        [:svg.hill-diagram__svg
         {:width "100%"
          :style {:display "block"
                  :aspect-ratio (str hill-w " / " hill-h)
                  :overflow "visible"}
          :viewBox (str "0 0 " hill-w " " hill-h)}
         ;; Baseline
         [:line.hill-diagram__baseline
          {:x1 0 :y1 hill-baseline :x2 hill-w :y2 hill-baseline
           :stroke "#ccc" :stroke-width "0.5"}]
         ;; Bell curve — turns accent color when done (flat mode only)
         [:path.hill-diagram__curve
          {:d (make-hill-path)
           :fill "none"
           :stroke (if (and (nil? multi-tasks) done?) accent "#aaa")
           :stroke-width "1.5"}]
         ;; Center divider
         [:line.hill-diagram__divider
          {:x1 hill-center :y1 peak-y
           :x2 hill-center :y2 hill-baseline
           :stroke "#ccc" :stroke-width "0.75"
           :stroke-dasharray "3,2"}]
         ;; Labels (optional)
         (when (truthy? show-hill-labels)
           [:g.hill-diagram__labels
            [:text.hill-diagram__label.hill-diagram__label--left
             {:x 2 :y label-y
              :font-size "6" :fill "#aaa"
              :font-family "sans-serif"
              :letter-spacing "0.4"}
             "FIGURING THINGS OUT"]
            [:text.hill-diagram__label.hill-diagram__label--right
             {:x (- hill-w 2) :y label-y
              :font-size "6" :fill "#aaa"
              :font-family "sans-serif"
              :text-anchor "end"
              :letter-spacing "0.4"}
             "MAKING IT HAPPEN"]])
         ;; Summit flag — shown in both modes when all tasks complete
         (when done?
           [:g.hill-diagram__flag
            [:line.hill-diagram__flag-pole
             {:x1 hill-center :y1 pole-top
              :x2 hill-center :y2 pole-bot
              :stroke accent :stroke-width "3.5"}]
            [:polygon.hill-diagram__flag-pennant
             {:points (str hill-center "," pole-top " "
                           (+ hill-center 14) "," (+ pole-top 9) " "
                           hill-center "," (+ pole-top 18))
              :fill accent}]])
         ;; Flat mode: single dot
         (when (nil? multi-tasks)
           [:circle.hill-diagram__dot
            {:cx dot-x :cy dot-y :r hill-dot-r
             :fill accent}])
         ;; Multi-task mode: one colored dot per task + hover tooltip
         (when (seq positioned-tasks)
           [:g.hill-diagram__dots
            (for [t positioned-tasks]
              ^{:key (:idx t)}
              [:circle.hill-diagram__dot.hill-diagram__dot--task
               {:cx (:x t)
                :cy (:y t)
                :r hill-dot-r
                :fill (:color t)
                :style {:cursor "pointer"}
                :on-mouse-enter (fn [e]
                                  (.stopPropagation e)
                                  (reset! *hovered-idx (:idx t)))
                :on-mouse-leave (fn [e]
                                  (.stopPropagation e)
                                  (reset! *hovered-idx nil))}])
            (when-let [idx @*hovered-idx]
              (let [t (get task-by-idx idx)]
                (when t
                  (let [label (truncate-str (strip-roam-markup (:name t)) 30)
                        tooltip-w 150
                        tooltip-h 18
                        tx (min (- hill-w tooltip-w 2) (max 2 (- (:x t) (/ tooltip-w 2))))
                        ty (- (:y t) hill-dot-r tooltip-h 3)]
                    [:g.hill-diagram__tooltip {:pointer-events "none"}
                     [:rect.hill-diagram__tooltip-bg
                      {:x tx :y ty
                       :width tooltip-w :height tooltip-h
                       :rx 3 :ry 3
                       :fill "rgba(0,0,0,0.75)"}]
                     [:text.hill-diagram__tooltip-text
                      {:x (+ tx 6) :y (+ ty 12)
                       :font-size "9"
                       :fill "white"
                       :font-family "sans-serif"}
                      label]]))))])]]
       [:span {:style {:white-space "nowrap"
                       :display "inline-flex"
                       :align-items "center"}}
        [:span (str done "/" total " " status-text
                    (when (truthy? show-percent)
                      (str " - " (if (zero? total) 0 (int percentage)) "%")))]
        [:span {:style {:max-width (if @*hovered? "30px" "0px")
                        :overflow "hidden"
                        :margin-left (if @*hovered? "8px" "0")
                        :transition "all 0.3s ease-in-out"
                        :opacity (if @*hovered? "1" "0")
                        :display "inline-block"}}
         [bp-button {:icon "cog"
                     :class "dont-focus-block"
                     :minimal true
                     :small true
                     :onClick (fn [e]
                                (.stopPropagation e)
                                (on-settings-click))}]]]])))

(defn extension-running? []
  (try
    (boolean (.-running js/window.todoProgressBarExtensionData))
    (catch :default _e
      false)))

(defn main [{:keys [block-uid]} & args]
  (r/with-let [*settings-open? (r/atom false)
               *ext-state (r/atom (if (extension-running?) :ready :loading))
               *check-interval (atom nil)
               *check-timeout (atom nil)
               _ (when (= @*ext-state :loading)
                   (reset! *check-interval
                           (js/setInterval
                            (fn []
                              (when (extension-running?)
                                (reset! *ext-state :ready)
                                (when-let [interval-id @*check-interval]
                                  (js/clearInterval interval-id)
                                  (reset! *check-interval nil))
                                (when-let [timeout-id @*check-timeout]
                                  (js/clearTimeout timeout-id)
                                  (reset! *check-timeout nil))))
                            500)))
               _ (when (= @*ext-state :loading)
                   (reset! *check-timeout
                           (js/setTimeout
                            (fn []
                              (when (= @*ext-state :loading)
                                (reset! *ext-state :missing))
                              (when-let [interval-id @*check-interval]
                                (js/clearInterval interval-id)
                                (reset! *check-interval nil)))
                            10000)))]
    (if (= @*ext-state :loading)
      [:div "Loading Todo Progress Bar..."]
      (if (= @*ext-state :missing)
        [:div [:strong {:style {:color "red"}}
               "Extension not installed. Please install Todo Progress Bar from Roam Depot."]]
      (let [style (or (first args) "horizontal")
            status-text (or (second args) "Done")
            show-percent (or (nth args 2 nil) "false")
            exclude-blockrefs (or (nth args 3 nil) "false")
            show-hill-labels (or (nth args 4 nil) "true")
            multi-task (or (nth args 5 nil) "false")
            multi-tasks (when (and (= style "hill") (truthy? multi-task))
                          (progress-counts-multi block-uid (truthy? exclude-blockrefs)))
            tasks (if multi-tasks
                    {:todo (reduce + (map :todo multi-tasks))
                     :done (reduce + (map :done multi-tasks))}
                    (progress-counts block-uid (truthy? exclude-blockrefs)))
            total (+ (:todo tasks) (:done tasks))]
        [:span.dont-focus-block {:on-click (fn [e] (.stopPropagation e))}
         [bp-popover
          {:isOpen @*settings-open?
           :onClose #(reset! *settings-open? false)
           :class "dont-focus-block"
           :position "auto"
           :content (r/as-element [settings-menu
                                   block-uid
                                   style
                                   status-text
                                   show-percent
                                   exclude-blockrefs
                                   show-hill-labels
                                   multi-task
                                   #(reset! *settings-open? false)])}
          (cond
            (= style "radial")
            [circle-progress-bar (:done tasks) total status-text show-percent #(reset! *settings-open? true)]
            (= style "hill")
            [hill-progress-bar (:done tasks) total status-text show-percent show-hill-labels multi-tasks #(reset! *settings-open? true)]
            :else
            [horizontal-progress-bar (:done tasks) total status-text show-percent #(reset! *settings-open? true)])]])))
    (finally
      (when-let [interval-id @*check-interval]
        (js/clearInterval interval-id))
      (when-let [timeout-id @*check-timeout]
        (js/clearTimeout timeout-id)))))
