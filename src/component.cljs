(ns progress-bar-v17
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

(defn debug
  "Debug function that converts ClojureScript data to JavaScript objects for better console display"
  [label data]
  (js/console.log label (clj->js data))
  data)

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

;; Legacy function kept for backward compatibility

;; Legacy function kept for backward compatibility
(defn find-child-refs [block-uid]
  (flatten-block []
                 @(dr/pull '[:block/refs {:block/children ...}]
                          [:block/uid block-uid])))

;; Main approach to find all references in child blocks
;; so the issue here was that the top level embeded block is already being included i
;; in the todo count. to 'really' count embeds we'd have to get the children of the embed

(defn recurse-search [block-uid]
  (->> block-uid
       (find-child-refs) ;; a list of all blocks that reference pages
       (keep :block/refs) ;; pulls out just the refs for each block. this would include any pages
       (flatten)
       (map :db/id) ;; manipulate the data to get just the ids of the pages referenced in those blocks
       (map info-from-id) ;; gets a page name from the db id
      ;;  (#(debug "After info-from-id" %))
       (flatten)
      ;;  (#(debug "Final result" %))
      )
    )

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

(defn format-render-string [current-string style status-text show-percent]
  (if-let [code-block-uid (get-component-code-uid current-string)]
    (let [pattern #"\{\{(?:\[\[)?roam/render(?:\]\])?: *\(\([^)]+\)\).*?\}\}"
          replacement (str "{{roam/render: ((" code-block-uid ")) \"" 
                         style "\" \"" 
                         status-text "\" \"" 
                         show-percent "\"}}")]
      (clojure.string/replace current-string pattern replacement))
    current-string))

(defn update-block-string [block-uid style status-text show-percent]
  (let [current-block @(dr/pull '[:block/string] [:block/uid block-uid])
        current-string (:block/string current-block)
        new-string (format-render-string current-string style status-text show-percent)]
    (when (not= current-string new-string)
      (block/update
        {:block {:uid block-uid
                 :string new-string}}))))

;; Settings menu
(defn settings-menu [block-uid current-style current-status-text current-show-percent on-close]
  (let [*style (r/atom current-style)
        *status-text (r/atom current-status-text)
        *show-percent (r/atom current-show-percent)]
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
                   :autoFocus true
                   :onChange #(reset! *status-text (.. % -target -value))
                   :placeholder "Done"}]]
        
       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Show Percentage"]
        [bp-switch {:checked (or (= @*show-percent "true") (= @*show-percent true))
                    :class "dont-focus-block"
                    :onChange #(reset! *show-percent (str (.. % -target -checked)))}]]

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
                       (update-block-string block-uid @*style @*status-text @*show-percent)
                       (on-close))}
          "Apply"]
         ]])))

(defn horizontal-progress-bar [block-uid done total status-text show-percent on-settings-click]
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
                (when (or (= show-percent "true") (= show-percent true))
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

(defn circle-progress-bar [block-uid done total status-text show-percent on-settings-click]
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
              (when (or (= show-percent "true") (= show-percent true))
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
            show-percent (or (nth args 2 "false"))
            todo-refs (recurse-search block-uid)
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
                                   show-percent
                                   #(reset! *settings-open? false)])}
          (if (= style "radial")
            [circle-progress-bar block-uid (:done tasks) total status-text show-percent #(reset! *settings-open? true)]
            [horizontal-progress-bar block-uid (:done tasks) total status-text show-percent #(reset! *settings-open? true)])]]))

    (finally
      (js/clearInterval check-interval))))