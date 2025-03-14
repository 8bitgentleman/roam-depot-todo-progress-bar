(ns progress-bar-v16
  (:require
   [reagent.core :as r]
   [datascript.core :as d]
   [roam.datascript.reactive :as dr]
   [roam.block :as block]
   [blueprintjs.core :as bp-core]))

;; Circle configuration
(def base-size 11)
(def radius (* 0.75 base-size))
(def viewbox-size (* 2 base-size))
(def center-point base-size)

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

;; Helper functions - OPTIMIZED WITH PULL QUERIES
(defn flatten-block [acc block]
  (reduce flatten-block
          (conj acc (dissoc block :block/children))
          (:block/children block)))

;; Using pull for page title lookup
(defn id-title [id]
  (:node/title @(dr/pull '[:node/title] id)))

(defn info-from-id [id]
  (or (:node/title @(dr/pull '[:node/title] id))
      (map id-title
           (map :db/id
                (:block/refs @(dr/pull '[:block/refs] id))))))

(defn count-occurrences [s slist]
  (->> slist
       flatten
       (filter #{s})
       count))

;; Embed detection and processing
(defn contains-embed? [block-string]
  (let [result (and block-string
                    (or (clojure.string/includes? block-string "{{embed:")
                        (clojure.string/includes? block-string "{{[[embed]]:")
                        (clojure.string/includes? block-string "{{embed-children:")
                        (clojure.string/includes? block-string "{{[[embed-children]]:")))]
    result))

(defn extract-uid-from-embed [block-string]
  (when (contains-embed? block-string)
    (let [patterns [#"\{\{embed: *\(\(([^)]+)\)\)\}\}"
                    #"\{\{\[\[embed\]\]: *\(\(([^)]+)\)\)\}\}"
                    #"\{\{embed-children: *\(\(([^)]+)\)\)\}\}"
                    #"\{\{\[\[embed-children\]\]: *\(\(([^)]+)\)\)\}\}"
                    #"\{\{\[\[embed-children\]\]: *\(\(\(\(([^)]+)\)\)\)\)\}\}"] ; Extra pattern for double parentheses
          ;; Try each pattern and collect all matches
          all-matches (mapcat #(re-seq % block-string) patterns)]
      ;; Extract just the UID from each match (second item in match array)
      (mapv second all-matches))))

(defn process-block-with-embeds
  ([block] (process-block-with-embeds block #{}))
  ([block visited-uids]
   (let [block-uid (:block/uid block)
         block-string (:block/string block)
         has-embed (contains-embed? block-string)
         embed-uids (when (and block-string
                               (not (contains? visited-uids block-uid))
                               has-embed)
                      (extract-uid-from-embed block-string))
         updated-visited (conj visited-uids block-uid)]
     (concat
      ;; Process this block normally
      (list (dissoc block :block/children))
      ;; Process its children
      (mapcat #(process-block-with-embeds % updated-visited) (:block/children block))
      ;; Process any embedded blocks
      (when (seq embed-uids)
        (mapcat (fn [uid]
                  (when-let [embedded-block
                             @(dr/pull '[:block/uid :block/string :block/refs {:block/children ...}]
                                      [:block/uid uid])]
                    ;; Only process if not already visited (prevent cycles)
                    (when-not (contains? updated-visited uid)
                      (process-block-with-embeds embedded-block updated-visited))))
                embed-uids))))))

(defn find-child-refs-with-embeds [block-uid]
  (let [block @(dr/pull '[:block/uid :block/string :block/refs {:block/children ...}] 
                        [:block/uid block-uid])]
    (process-block-with-embeds block)))

(defn recurse-search-with-embeds [block-uid]
  (->> block-uid
       (find-child-refs-with-embeds)
       (keep :block/refs)
       (flatten)
       (map :db/id)
       (map info-from-id)
       (flatten)))

;; Legacy function kept for backward compatibility
(defn find-child-refs [block-uid]
  (flatten-block []
                 @(dr/pull '[:block/refs {:block/children ...}]
                          [:block/uid block-uid])))

(defn recurse-search [block-uid]
  (->> block-uid
       (find-child-refs)
       (keep :block/refs)
       (flatten)
       (map :db/id)
       (map info-from-id)
       (flatten)))

;; BlueprintJS component adaptations
(def bp-button (r/adapt-react-class bp-core/Button))
(def bp-popover (r/adapt-react-class bp-core/Popover))
(def bp-menu (r/adapt-react-class bp-core/Menu))
(def bp-menu-item (r/adapt-react-class bp-core/MenuItem))
(def bp-select (r/adapt-react-class bp-core/HTMLSelect))
(def bp-input (r/adapt-react-class bp-core/InputGroup))
(def bp-switch (r/adapt-react-class bp-core/Switch))

;; Component configuration management
(defn get-component-code-uid [block-string]
  (when block-string
    (let [pattern #"\{\{(?:\[\[)?roam/render(?:\]\])?: *\(\(([^)]+)\)\).*\}\}"
          matches (re-find pattern block-string)]
      (second matches))))

(defn format-render-string [current-string style status-text include-embeds]
  (if-let [code-block-uid (get-component-code-uid current-string)]
    (let [pattern #"\{\{(?:\[\[)?roam/render(?:\]\])?: *\(\([^)]+\)\).*?\}\}"
          replacement (str "{{roam/render: ((" code-block-uid ")) \"" 
                         style "\" \"" 
                         status-text "\"" 
                         (when include-embeds " \"include-embeds\"") 
                         "}}")]
      (clojure.string/replace current-string pattern replacement))
    current-string))

(defn update-block-string [block-uid style status-text include-embeds]
  (let [current-block @(dr/pull '[:block/string] [:block/uid block-uid])
        current-string (:block/string current-block)
        new-string (format-render-string current-string style status-text include-embeds)]
    (when (not= current-string new-string)
      (block/update
        {:block {:uid block-uid
                 :string new-string}}))))

;; Settings menu
(defn settings-menu [block-uid current-style current-status-text current-include-embeds on-close]
  (let [*style (r/atom current-style)
        *status-text (r/atom current-status-text)
        *include-embeds (r/atom current-include-embeds)]
    (fn []
      [:div.bp3-card.dont-focus-block {:style {:padding "10px" :min-width "250px"}
                                       :on-click (fn [e] (.stopPropagation e))}
       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Display Style"]
        [bp-select {:value @*style
                    :class "dont-focus-block"
                    :onChange #(reset! *style (.. % -target -value))
                    :options [{:value "horizontal" :label "Horizontal Bar"}
                              {:value "radial" :label "Radial/Circle"}]}]]

       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Status Text"]
        [bp-input {:value @*status-text
                   :class "dont-focus-block"
                   :onChange #(reset! *status-text (.. % -target -value))
                   :placeholder "Done"}]]

       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [bp-switch {:checked @*include-embeds
                    :class "dont-focus-block"
                    :onChange #(swap! *include-embeds not)
                    :label "Include embedded blocks"}]]

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
                       (update-block-string block-uid @*style @*status-text @*include-embeds)
                       (on-close))}
          "Apply"]
         ]])))

(defn horizontal-progress-bar [block-uid done total status-text on-settings-click]
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
      [:span (str done "/" total " " status-text)]
      [:span {:style {:width (if @*hovered? "auto" "0")
                     :overflow "hidden" 
                     :margin-left (if @*hovered? "8px" "0")
                     :transition "all 0.2s ease-in-out"
                     :opacity (if @*hovered? "1" "0")}}
       [bp-button
        {:icon "cog"
         :class "dont-focus-block"
         :minimal true
         :small true
         :onClick (fn [e] 
                    (.stopPropagation e)
                    (on-settings-click))}]]]]))

(defn circle-progress-bar [block-uid done total status-text on-settings-click]
  (r/with-let [*hovered? (r/atom false)]
    (let [percentage (if (zero? total)
                      0
                      (* (/ done total) 100))]
      [:span.inline-flex.items-center.gap-4
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
         (str done "/" total " " status-text " - " (int percentage) "%")]
        [:span {:style {:width (if @*hovered? "auto" "0")
                       :overflow "hidden" 
                       :margin-left (if @*hovered? "8px" "0")
                       :transition "all 0.2s ease-in-out"
                       :opacity (if @*hovered? "1" "0")}}
         [bp-button
          {:icon "cog"
           :class "dont-focus-block"
           :minimal true
           :small true
           :onClick (fn [e] 
                      (.stopPropagation e)
                      (on-settings-click))}]]]])))
                      
(defn main [{:keys [block-uid]} & args]
  (r/with-let [is-running? #(try
                              (.-running js/window.todoProgressBarExtensionData)
                              (catch :default _e
                                false))
               *running? (r/atom (or (is-running?) nil))
               *settings-open? (r/atom false)
               check-interval (js/setInterval #(reset! *running? (is-running?)) 5000)]
    (case @*running?
      nil [:div [:strong "Loading progress bar extension..."]]
      false [:div [:strong {:style {:color "red"}}
                   "Extension not installed. Please install Todo Progress Bar from Roam Depot."]]
      (let [style (or (first args) "horizontal")
            status-text (or (second args) "Done")
            include-embeds (= (nth (vec args) 2 nil) "include-embeds")
            search-fn (if include-embeds recurse-search-with-embeds recurse-search)
            todo-refs (search-fn block-uid)
            tasks {:todo (count-occurrences "TODO" todo-refs)
                   :done (count-occurrences "DONE" todo-refs)}
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
                                   include-embeds
                                   #(reset! *settings-open? false)])}
          (if (= style "radial")
            [circle-progress-bar block-uid (:done tasks) total status-text #(reset! *settings-open? true)]
            [horizontal-progress-bar block-uid (:done tasks) total status-text #(reset! *settings-open? true)])]]))

    (finally
      (js/clearInterval check-interval))))